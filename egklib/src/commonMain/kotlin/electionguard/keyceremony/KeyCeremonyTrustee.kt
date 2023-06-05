package electionguard.keyceremony

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import electionguard.core.*
import kotlin.experimental.xor

/**
 * A Trustee that knows its own secret key and polynomial.
 * KeyCeremonyTrustee must stay private. Guardian is its public info in the election record.
 */
class KeyCeremonyTrustee(
    val group: GroupContext,
    val id: String,
    val xCoordinate: Int,
    val quorum: Int,
) : KeyCeremonyTrusteeIF {
    // all the secrets are in here
    private val polynomial: ElectionPolynomial = group.generatePolynomial(id, xCoordinate, quorum)

    // Other guardians' public keys, keyed by other guardian id.
    internal val otherPublicKeys: MutableMap<String, PublicKeys> = mutableMapOf()

    // My share of other's key, keyed by other guardian id.
    private val myShareOfOthers: MutableMap<String, PrivateKeyShare> = mutableMapOf()

    // Other guardians share of my key, keyed by other guardian id. Only used in KeyCeremony
    private val othersShareOfMyKey: MutableMap<String, PrivateKeyShare> = mutableMapOf()

    init {
        require(id.isNotEmpty())
        require(xCoordinate > 0)
        require(quorum > 0)
    }

    override fun id(): String = id

    override fun xCoordinate(): Int = xCoordinate

    override fun electionPublicKey(): ElementModP = polynomial.coefficientCommitments[0]

    override fun coefficientCommitments(): List<ElementModP> = polynomial.coefficientCommitments

    override fun coefficientProofs(): List<SchnorrProof> = polynomial.coefficientProofs

    override fun publicKeys(): Result<PublicKeys, String> {
        return Ok(PublicKeys(
            id,
            xCoordinate,
            polynomial.coefficientProofs,
        ))
    }

    // P(ℓ) = (P1 (ℓ) + P2 (ℓ) + · · · + Pn (ℓ)) mod q. eq 3.
    override fun keyShare(): ElementModQ {
        var result: ElementModQ = polynomial.valueAt(group, xCoordinate)
        myShareOfOthers.values.forEach{ result += it.yCoordinate }
        return result
    }

    // debugging
    internal fun electionPrivateKey(): ElementModQ = polynomial.coefficients[0]

    // debug only
    internal fun valueAt(group: GroupContext, xcoord: Int) : ElementModQ {
        return polynomial.valueAt(group, xcoord)
    }

    /** Receive publicKeys from another guardian.  */
    override fun receivePublicKeys(publicKeys: PublicKeys): Result<Boolean, String> {
        val errors = mutableListOf<Result<Boolean, String>>()
        if (publicKeys.guardianId == this.id) {
            errors.add( Err("Cant send '${publicKeys.guardianId}' public keys to itself"))
        }
        if (publicKeys.guardianXCoordinate < 1) {
            errors.add( Err("${this.id} receivePublicKeys from '${publicKeys.guardianId}': guardianXCoordinate must be >= 1"))
        }
        if (publicKeys.coefficientProofs.size != quorum) {
            errors.add( Err("${this.id} receivePublicKeys from '${publicKeys.guardianId}': needs ($quorum) coefficientProofs"))
        }
        if (errors.isEmpty()) {
            val validProofs: Result<Boolean, String> = publicKeys.validate()
            if (validProofs is Err) {
                return validProofs
            }
            otherPublicKeys[publicKeys.guardianId] = publicKeys
        }
        return errors.merge()
    }

    /** Create another guardian's share of my key, encrypted. spec 1.9 p 20 Share encryption. */
    override fun encryptedKeyShareFor(otherGuardian: String): Result<EncryptedKeyShare, String> {
        var pkeyShare = othersShareOfMyKey[otherGuardian]
        if (pkeyShare == null) {
            val other : PublicKeys = otherPublicKeys[otherGuardian] // G_l's public keys
                ?: return Err("Trustee '$id', does not have public key for '$otherGuardian'")

            // Compute my polynomial's y value at the other's x coordinate = Pi(ℓ)
            val Pil: ElementModQ = polynomial.valueAt(group, other.guardianXCoordinate)
            // My encryption of Pil, using the other's public key. spec 1.9, section 3.2.2 eq 17.
            val EPil : HashedElGamalCiphertext = shareEncryption(Pil, other)

            pkeyShare = PrivateKeyShare(this.xCoordinate, this.id, other.guardianId, EPil, Pil)
            // keep track in case its challenged
            othersShareOfMyKey[other.guardianId] = pkeyShare
        }

        return Ok(pkeyShare.makeEncryptedKeyShare())
    }

    /** Receive and verify a secret key share. */
    override fun receiveEncryptedKeyShare(share: EncryptedKeyShare?): Result<Boolean, String> {
        if (share == null) {
            return Err("ReceiveEncryptedKeyShare '${this.id}' sent a null share")
        }
         if (share.secretShareFor != id) {
            return Err("ReceiveEncryptedKeyShare '${this.id}' sent share to wrong trustee '${this.id}', should be availableGuardianId '${share.secretShareFor}'")
        }

        // decrypt Pi(l)
        val pilbytes = shareDecryption(share)
            ?: return Err("Trustee '$id' couldnt decrypt EncryptedKeyShare for missingGuardianId '${share.polynomialOwner}'")
        val expectedPil: ElementModQ = pilbytes.toUInt256().toElementModQ(group) // Pi(ℓ)

        // The other's Kij
        val publicKeys = otherPublicKeys[share.polynomialOwner]
            ?: return Err("Trustee '$id' does not have public keys for missingGuardianId '${share.polynomialOwner}'")

        // Having decrypted each Pi (ℓ), guardian Gℓ can now verify its validity against
        // the commitments Ki,0 , Ki,1 , . . . , Ki,k−1 made by Gi to its coefficients by confirming that
        // g^Pi(ℓ) = Prod{ (Kij)^ℓ^j }, for j=0..k-1 eq 19
        if (group.gPowP(expectedPil) != calculateGexpPiAtL(this.xCoordinate, publicKeys.coefficientCommitments())) {
            return Err("Trustee '$id' failed to validate EncryptedKeyShare for missingGuardianId '${share.polynomialOwner}'")
        }

        // keep track of this result
        myShareOfOthers[share.polynomialOwner] = PrivateKeyShare(
            share.ownerXcoord,
            share.polynomialOwner,
            this.id,
            share.encryptedCoordinate,
            expectedPil,
        )
        return Ok(true)
    }

    /** Create another guardian's share of my key, not encrypted. */
    override fun keyShareFor(otherGuardian: String): Result<KeyShare, String> {
        val pkeyShare = othersShareOfMyKey[otherGuardian]
        return if (pkeyShare != null) Ok(pkeyShare.makeKeyShare())
            else Err("Trustee '$id', does not have KeyShare for '$otherGuardian'; must call encryptedKeyShareFor() first")
    }

    /** Receive and verify a key share. */
    override fun receiveKeyShare(keyShare: KeyShare): Result<Boolean, String> {
        val errors = mutableListOf<Result<Boolean, String>>()
        val myPublicKey = ElGamalPublicKey(this.electionPublicKey())

        if (keyShare.secretShareFor != id) {
            return Err("Sent KeyShare to wrong trustee '${this.id}', should be availableGuardianId '${keyShare.secretShareFor}'")
        }

        /* spec 1.52 says:
        If the recipient guardian Tℓ reports not receiving a suitable value Pi (ℓ), it becomes incumbent on the
        sending guardian Ti to publish this Pi (ℓ) together with the nonce Ri,ℓ it used to encrypt Pi (ℓ)
        under the public key Kℓ of recipient guardian Tℓ . If guardian Ti fails to produce a suitable Pi (ℓ)
        and nonce Ri,ℓ that match both the published encryption and the above equation, it should be
        excluded from the election and the key generation process should be restarted with an alternate
        guardian. If, however, the published Pi (ℓ) and Ri,ℓ satisfy both the published encryption and the
        equation above, the claim of malfeasance is dismissed, and the key generation process continues
        undeterred.19

        But in discussions with Josh 11/9/22, he says:

        As, I’m seeing things, the nonces aren’t relevant and never need to be supplied.  Guardian  is supposed to send
        Guardian  the share value  by encrypting it as  and handing it off.  If Guardian  claims to have not received a
        satisfactory , Guardian  is supposed to simply publish  so that anyone can check its validity directly.
        The verification is against the previously committed versions of coefficients  instead of against the
        transmitted encryption.  This prevents observers from needing to adjudicate whether or not Guardian
        sent a correct value initially.

        So im disabling the nonce exchange, and wait for spec 2 before continuing this.
         */

        /*
        val encryptedKeyShare = myShareOfOthers[keyShare.missingGuardianId] // what they sent us before
        if (encryptedKeyShare == null) {
            errors.add(Err("Trustee '$id', does not have encryptedKeyShare for missingGuardianId '${keyShare.missingGuardianId}';" +
                " must call receiveSecretKeyShare() first"))
        } else {
            // check that we can use the nonce to decrypt the El(Pi(ℓ)) that was sent previously
            val d = encryptedKeyShare.encryptedCoordinate.decryptWithNonce(myPublicKey, keyShare.nonce)
            if (d == null) {
                errors.add(Err("Trustee '$id' couldnt decrypt encryptedKeyShare for missingGuardianId '${keyShare.missingGuardianId}'"))
            } else {
                // Check if the decrypted value matches the Pi(ℓ) that was sent.
                val expected: ElementModQ = d.toUInt256().toElementModQ(group) // Pi(ℓ)
                if (expected != keyShare.coordinate) {
                    errors.add(Err("Trustee '$id' receiveKeyShare for '${keyShare.missingGuardianId}' decrypted KeyShare doesnt match"))
                }
            }
        }
         */

        val otherKeys = otherPublicKeys[keyShare.polynomialOwner]
        if (otherKeys == null) {
            errors.add(Err("Trustee '$id', does not have public key for missingGuardianId '${keyShare.polynomialOwner}'"))
        } else {
            // check if the Pi(ℓ) that was sent satisfies eq 16.
            // verify spec 1.52, sec 3.2.2 eq 16: g^Pi(ℓ) = Prod{ (Kij)^ℓ^j }, for j=0..k-1
            if (group.gPowP(keyShare.yCoordinate) != calculateGexpPiAtL(this.xCoordinate, otherKeys.coefficientCommitments())) {
                errors.add(Err("Trustee '$id' failed to validate KeyShare for missingGuardianId '${keyShare.polynomialOwner}'"))
            } else {
                // ok use it, but encrypt it ourself, dont use passed value, and use a new nonce
                val EPil = keyShare.yCoordinate.byteArray().hashedElGamalEncrypt(myPublicKey)
                myShareOfOthers[keyShare.polynomialOwner] = PrivateKeyShare(
                    keyShare.ownerXcoord,
                    keyShare.polynomialOwner,
                    keyShare.secretShareFor,
                    EPil,
                    keyShare.yCoordinate,
                )
            }
        }

        return errors.merge()
    }

    // guardian Gi encryption Eℓ of Pi(ℓ) at another guardian's Gℓ coordinate ℓ
    fun shareEncryption(
        Pil : ElementModQ,
        other: PublicKeys,
        nonce: ElementModQ = other.publicKey().context.randomElementModQ(minimum = 2)
    ): HashedElGamalCiphertext {

        val K_l = other.publicKey() // other's publicKey
        val hp = K_l.context.constants.hp.bytes
        val i = xCoordinate.toUShort()
        val l = other.guardianXCoordinate.toUShort()

        // (alpha, beta) = (g^R mod p, K^R mod p)  spec 1.9, p 20, eq 13
        // by encrypting a zero, we achieve exactly this
        val (alpha, beta) = 0.encrypt(K_l, nonce)
        // ki,ℓ = H(HP ; 11, i, ℓ, Kℓ , αi,ℓ , βi,ℓ ) eq 14
        val kil = hashFunction(hp, 0x11.toByte(), i, l, K_l.key, alpha, beta).bytes

        // This key derivation uses the KDF in counter mode from SP 800-108r1. The second input to HMAC contains
        // the counter in the first byte, the UTF-8 encoding of the string "share enc keys" as the Label (encoding is denoted
        // by b(. . . ), see Section 5.1.4), a separation 00 byte, the UTF-8 encoding of the string "share encrypt" concatenated
        // with encodings of the numbers i and ℓ of the sending and receiving guardians as the Context, and the final two bytes
        // specifying the length of the output key material as 512 bits in total.

        val label = "share enc keys"
        // context = b(”share encrypt”) ∥ b(i, 2) ∥ b(ℓ, 2)
        val context = "share encrypt"
        // k0 = HMAC(ki,ℓ , 01 ∥ label ∥ 00 ∥ context ∥ 0200) eq 15
        val k0 = hashFunction(kil, 0x01.toByte(), label, 0x00.toByte(), context, i, l, 512.toShort()).bytes
        // k1 = HMAC(ki,ℓ , 02 ∥ label ∥ 00 ∥ context ∥ 0200), eq 16
        val k1 = hashFunction(kil, 0x02.toByte(), label, 0x00.toByte(), context, i, l, 512.toShort()).bytes

        // eq 18
        // C0 = g^nonce == alpha
        val c0: ElementModP = alpha
        // C1 = b(Pi(ℓ),32) ⊕ k1, • The symbol ⊕ denotes bitwise XOR.
        val pilBytes = Pil.byteArray()
        val c1 = ByteArray(32) { pilBytes[it] xor k1[it] }
        // C2 = HMAC(k0 , b(Ci,ℓ,0 , 512) ∥ Ci,ℓ,1 )
        val c2 = hashFunction(k0, c0, c1)

        return HashedElGamalCiphertext(c0, c1, c2, pilBytes.size)
    }

    // Share decryption. After receiving the ciphertext (Ci,ℓ,0 , Ci,ℓ,1 , Ci,ℓ,2 ) from guardian Gi , guardian
    // Gℓ decrypts it by computing βi,ℓ = (Ci,ℓ,0 )sℓ mod p, setting αi,ℓ = Ci,ℓ,0 and obtaining ki,ℓ =
    // H(HP ; 11, i, ℓ, Kℓ , αi,ℓ , βi,ℓ ).
    // Now the MAC key k0 and the encryption key k1 can be computed as
    // above in Equations (15) and (16), which allows Gℓ to verify the validity of the MAC, namely that
    // Ci,ℓ,2 = HMAC(k0 , b(Ci,ℓ,0 , 512) ∥ Ci,ℓ,1 ). If the MAC verifies, Gℓ decrypts b(Pi (ℓ), 32) = Ci,ℓ,1 ⊕k1 .
    fun shareDecryption(share: EncryptedKeyShare): ByteArray?  {
        // αi,ℓ = Ci,ℓ,0
        // βi,ℓ = (Ci,ℓ,0 )sℓ mod p
        // ki,ℓ = H(HP ; 11, i, ℓ, Kℓ , αi,ℓ , βi,ℓ )
        val c0 = share.encryptedCoordinate.c0
        val c1 = share.encryptedCoordinate.c1

        val alpha = c0
        val beta = c0 powP this.electionPrivateKey()
        val hp = group.constants.hp.bytes
        val kil = hashFunction(hp, 0x11.toByte(), share.ownerXcoord.toShort(), xCoordinate.toShort(), electionPublicKey(), alpha, beta).bytes

        // Now the MAC key k0 and the encryption key k1 can be computed as above in Equations (15) and (16)
        val label = "share enc keys"
        // context = b(”share encrypt”) ∥ b(i, 2) ∥ b(ℓ, 2)
        val context = "share encrypt"
        // k0 = HMAC(ki,ℓ , 01 ∥ label ∥ 00 ∥ context ∥ 0200) eq 15
        val k0 = hashFunction(kil, 0x01.toByte(), label, 0x00.toByte(), context, share.ownerXcoord.toShort(), xCoordinate.toShort(), 512.toShort()).bytes
        // k1 = HMAC(ki,ℓ , 02 ∥ label ∥ 00 ∥ context ∥ 0200), eq 16
        val k1 = hashFunction(kil, 0x02.toByte(), label, 0x00.toByte(), context, share.ownerXcoord.toShort(), xCoordinate.toShort(), 512.toShort()).bytes

        // Gℓ can verify the validity of the MAC, namely that
        // Ci,ℓ,2 = HMAC(k0 , b(Ci,ℓ,0 , 512) ∥ Ci,ℓ,1 ). If the MAC verifies, Gℓ decrypts b(Pi (ℓ), 32) = Ci,ℓ,1 ⊕ k1 .
        val expectedC2 = hashFunction(k0, c0, c1)
        if (expectedC2 != share.encryptedCoordinate.c2) {
            return null
        }

        //  If the MAC verifies, Gℓ decrypts b(Pi (ℓ), 32) = Ci,ℓ,1 ⊕ k1 .
        val pilBytes = ByteArray(32) { c1[it] xor k1[it] }
        return pilBytes
    }

}

// internal use only
private data class PrivateKeyShare(
    val ownerXcoord: Int, // guardian i (owns the polynomial Pi) xCoordinate
    val polynomialOwner: String, // guardian i (owns the polynomial Pi)
    val secretShareFor: String, // guardian l with coordinate ℓ
    val encryptedCoordinate: HashedElGamalCiphertext, // El(Pi_(ℓ))
    val yCoordinate: ElementModQ, // Pi_(ℓ), trustee ℓ's share of trustee i's secret
) {
    init {
        require(polynomialOwner.isNotEmpty())
        require(secretShareFor.isNotEmpty())
    }

    fun makeEncryptedKeyShare() = EncryptedKeyShare(
        ownerXcoord,
        polynomialOwner,
        secretShareFor,
        encryptedCoordinate,
    )

    fun makeKeyShare() = KeyShare(
        ownerXcoord,
        polynomialOwner,
        secretShareFor,
        yCoordinate,
    )
}