package electionguard.decrypt

import electionguard.core.ElGamalKeypair
import electionguard.core.ElementModP
import electionguard.core.ElementModQ
import electionguard.core.GroupContext
import electionguard.core.decrypt
import electionguard.core.randomElementModQ
import electionguard.core.toElementModQ
import electionguard.core.toUInt256
import electionguard.keyceremony.EncryptedKeyShare

/**
 * A Trustee that knows its own secret key, for the purpose of decryption.
 * DecryptingTrustee must stay private. Guardian is its public info in the election record.
 */
data class DecryptingTrustee(
    val id: String,
    val xCoordinate: Int,
    // My private and public key
    val electionKeypair: ElGamalKeypair,
    // My share of other's key, keyed by missing guardian id = Pj(ℓ)
    val encryptedKeyShares: Map<String, EncryptedKeyShare>,
) : DecryptingTrusteeIF {

    init {
        require(xCoordinate > 0)
    }

    override fun id(): String = id
    override fun xCoordinate(): Int = xCoordinate
    override fun electionPublicKey(): ElementModP = electionKeypair.publicKey.key

    // tm = wℓ * (Sum j∈V Pj(ℓ)) mod q, V = missing guardians
    private var tm: ElementModQ? = null
    private fun tm(): ElementModQ = this.tm ?: throw IllegalStateException()

    override fun setMissing(
        group: GroupContext,
        lagrangeCoeff: ElementModQ,     // wℓ for this trustee
        missingGuardians: List<String>, // guardian ids
    ) : Boolean {
        if (this.tm != null) {
            return false // could test they are the same
        }
        this.tm = if (missingGuardians.isEmpty()) group.ZERO_MOD_Q else {
            // compute tm = wℓ * (Sum j∈V Pj(ℓ)) mod q, V = missing guardians
            val pjls = missingGuardians.map { decryptKeyShare(group, it) }
            val sumPjls = with(group) { pjls.addQ() }
            lagrangeCoeff * sumPjls
        }
        return true
    }

    // decryption of key share of a missing guardian
    // encrypted: El(Pj(ℓ)) = spec 1.52, section 3.2.2 "share encryption"
    // decrypted = Pj(ℓ) = value of other's secret polynomial at my coordinate = "my share of other's secret key"
    private fun decryptKeyShare(group: GroupContext, missingGuardianId: String): ElementModQ {
        val encryptedKeyShare: EncryptedKeyShare = this.encryptedKeyShares[missingGuardianId]
            ?: throw IllegalStateException("DecryptingTrustee $id missing SecretKeyShare for $missingGuardianId")
        val byteArray = encryptedKeyShare.encryptedCoordinate.decrypt(this.electionKeypair.secretKey)
            ?: throw IllegalStateException("DecryptingTrustee $id couldnt decrypt SecretKeyShare for $missingGuardianId")
        return byteArray.toUInt256().toElementModQ(group)
    }

    override fun decrypt(
        group: GroupContext,
        texts: List<ElementModP>,
        nonce: ElementModQ? // LOOK needed? testing only?
    ): List<PartialDecryption> {
        val results: MutableList<PartialDecryption> = mutableListOf()
        for (text: ElementModP in texts) {
            val u = nonce?: group.randomElementModQ(2)
            val a = group.gPowP(u)
            val b = text powP u
            // ti = (si + wi * Sum(Pj(i))j∈V) (spec 1.52, eg 58)
            val ti = this.electionKeypair.secretKey.key + tm()
            val mbari = text powP ti // Mbar_i = A ^ (si + tm)
            results.add(PartialDecryption(id, mbari, u, a, b)) // controversial to send u, could cache it here.
        }
        return results
    }

    override fun challenge(
        group: GroupContext,
        challenges: List<ChallengeRequest>,
    ): List<ChallengeResponse> {
        return challenges.map {
            ChallengeResponse(it.id, it.nonce - it.challenge * ( electionKeypair.secretKey.key + tm()))
        }
    }
}