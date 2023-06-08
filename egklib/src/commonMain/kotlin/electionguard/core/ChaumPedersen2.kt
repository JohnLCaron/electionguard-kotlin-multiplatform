package electionguard.core

import com.github.michaelbull.result.*
import kotlin.collections.fold

// 3.3.5 p 26 eq 23-27
// Proves that (α, β) is an encryption of an integer in the range 0, 1, . . . , L.
// (Requires knowledge of encryption nonce ξ for which (α, β) is an encryption of ℓ.)
fun ElGamalCiphertext.makeChaumPedersen(
    plaintext: Int, // ℓ
    limit: Int,     // L
    aggNonce: ElementModQ,
    publicKey: ElGamalPublicKey, // K
    extendedBaseHash: ElementModQ, // He
    overrideErrorChecks: Boolean = false
): RangeChaumPedersenProofKnownNonce {
    if (!overrideErrorChecks && plaintext < 0) {
        throw ArithmeticException("negative plaintexts not supported")
    }
    if (!overrideErrorChecks && limit < 0) {
        throw ArithmeticException("negative limits not supported")
    }
    if (!overrideErrorChecks && limit < plaintext) {
        throw ArithmeticException("limit must be at least as big as the plaintext")
    }

    val (alpha, beta) = this
    val group = compatibleContextOrFail(pad, aggNonce, publicKey.key, extendedBaseHash, alpha, beta)

    // random nonces u_j
    val randomUj = Nonces(aggNonce, "range-chaum-pedersen-proof").take(limit + 1)
    // Random challenges c_j
    val randomCj = Nonces(aggNonce, "range-chaum-pedersen-proof-constants").take(limit + 1)

    // (aℓ , bℓ ) = (g^uℓ mod p, K^uℓ mod p), for j = ℓ (eq 23)
    // (aj , bj ) = (g^uj mod p, K^tj mod p), where tj = (uj +(ℓ−j)cj ), for j != ℓ (eq 24)
    val aList = randomUj.map { u -> group.gPowP(u) }
    val bList = randomUj.mapIndexed { j, u ->
        if (j == plaintext) {
            //  j = ℓ
            publicKey powP u
        } else {
            //  j != ℓ
            // We can't convert a negative number to an ElementModQ,
            // so we instead use the unaryMinus operator.
            val plaintextMinusIndex = if (plaintext >= j)
                (plaintext - j).toElementModQ(group)
            else
                -((j - plaintext).toElementModQ(group))

            publicKey powP (plaintextMinusIndex * randomCj[j] + u) // tj = (uj +(ℓ−j)cj )
        }
    }

    // (a1, a2, a3) x (b1, b2, b3) ==> (a1, b1, a2, b2, a3, b3, ...)
    val abList: List<ElementModP> = aList.zip(bList).flatMap { listOf(it.first, it.second) }

    // c = H(HE ; 21, K, α, β, a0 , b0 , a1 , b1 , . . . , aL , bL ). eq 25
    val c = hashFunction(extendedBaseHash.byteArray(), 0x21.toByte(), publicKey.key, alpha, beta, abList).toElementModQ(group)

    // cl = (c − (c0 + · · · + cℓ−1 + cℓ+1 + · · · + cL )) mod q. eq 26
    val cl = c -
            randomCj.filterIndexed { j, _ -> j != plaintext }
                .fold(group.ZERO_MOD_Q) { a, b -> a + b }

    // responses are computed for all 0 ≤ j ≤ L as vj = (uj − cj ξ) mod q (eq 27)
    val vList = randomUj.zip(randomCj).mapIndexed { j, (uj, cj) ->
        val cjActual = if (j == plaintext) cl else cj

        // Spec 1.9, page 31, equation 57 (v_j)
        uj - cjActual * aggNonce
    }

    // substitute cl into the random challenges when j = l
    val cListFinal = randomCj.mapIndexed { j, cj -> if (j == plaintext) cl else cj }
    // turn the challenges and responses into a list of GenericChaumPedersenProof(cj, vj)
    val cpgList : List<GenericChaumPedersenProof> = cListFinal.zip(vList).map{ (cj, vj) ->
        GenericChaumPedersenProof(cj, vj)
    }
    return RangeChaumPedersenProofKnownNonce(cpgList)

    // could also create ExpandedGenericChaumPedersenProof(a,b,c,v) and let the serialization layer handle the contraction
}

fun RangeChaumPedersenProofKnownNonce.validate2(
    ciphertext: ElGamalCiphertext,
    publicKey: ElGamalPublicKey, // K
    extendedBaseHash: ElementModQ, // He
    limit: Int
): Result<Boolean, String> {
    val group = compatibleContextOrFail(this.proofs[0].c, ciphertext.pad, publicKey.key, extendedBaseHash)
    val results = mutableListOf<Result<Boolean, String>>()

    if (limit + 1 != proofs.size) {
        return Err("    RangeProof expected ${limit + 1} proofs, only found ${proofs.size}")
    }

    val (alpha, beta) = ciphertext
    results.add(
        if (alpha.isValidResidue() && beta.isValidResidue()) Ok(true) else
            Err("    4.A,5.A invalid residue: alpha = ${alpha.inBounds()} beta = ${beta.inBounds()}")
    )

    val expandedProofs = proofs.mapIndexed { j, proof ->
        // recomputes all the a and b values
        val (cj, vj) = proof
        results.add(
            if (cj.inBounds() && vj.inBounds()) Ok(true) else
                Err("    4.B,5.B c = ${cj.inBounds()} v = ${vj.inBounds()} idx=$j")
        )

        val wj = (vj - j.toElementModQ(group) * cj)
        ExpandedGenericChaumPedersenProof(
            a = group.gPowP(vj) * (alpha powP cj), // 4.3
            b = (publicKey powP wj) * (beta powP cj), // 4.4
            c = cj,
            r = vj)
    }

    // sum of the proof.c
    val abList = expandedProofs.flatMap { listOf(it.a, it.b) }

    // c = H(HE ; 21, K, α, β, a0 , b0 , a1 , b1 , . . . , aL , bL ). eq 25, 4.5
    val c = hashFunction(extendedBaseHash.byteArray(), 0x21.toByte(), publicKey.key, alpha, beta, abList).toElementModQ(group)

    val cSum = this.proofs.fold(group.ZERO_MOD_Q) { a, b -> a + b.c }
    results.add(
        if (cSum == c) Ok(true) else Err("    4.C,5.C challenge sum is invalid")
    )

    return results.merge()
}