package electionguard.encrypt

import com.github.michaelbull.result.getOrThrow
import electionguard.ballot.ElectionInitialized
import electionguard.core.productionGroup
import electionguard.input.RandomBallotProvider
import electionguard.publish.makeConsumer
import kotlin.test.Test
import kotlin.test.assertContains

class RunBatchEncryptionTest {
    val nthreads = 25

    @Test
    fun testRunBatchEncryptionNonces() {
        main(
            arrayOf(
                "-in",
                "src/commonTest/data/runWorkflowAllAvailable",
                "-ballots",
                "src/commonTest/data/runWorkflowAllAvailable/private_data/input",
                "-out",
                "testOut/testRunBatchEncryptionNoncesTest",
                "-invalid",
                "testOut/testRunBatchEncryptionNoncesTest/invalid_ballots",
                "-fixed",
                "-nthreads",
                "$nthreads",
            )
        )
    }

    @Test
    fun testRunBatchEncryption() {
        main(
            arrayOf(
                "-in",
                "src/commonTest/data/runWorkflowAllAvailable",
                "-ballots",
                "src/commonTest/data/runWorkflowAllAvailable/private_data/input",
                "-out",
                "testOut/testRunBatchEncryptionTest",
                "-invalid",
                "testOut/testRunBatchEncryptionTest/invalid_ballots",
                "-nthreads",
                "$nthreads",
            )
        )
    }

    @Test
    fun testRunBatchEncryptionEncryptTwice() {
        main(
            arrayOf(
                "-in",
                "src/commonTest/data/runWorkflowAllAvailable",
                "-ballots",
                "src/commonTest/data/runWorkflowAllAvailable/private_data/input",
                "-out",
                "testOut/testRunBatchEncryptionTest",
                "-invalid",
                "testOut/testRunBatchEncryptionTest/invalid_ballots",
                "-nthreads",
                "$nthreads",
                "-check",
                "EncryptTwice"
            )
        )
    }

    @Test
    fun testRunBatchEncryptionVerify() {
        main(
            arrayOf(
                "-in",
                "src/commonTest/data/runWorkflowAllAvailable",
                "-ballots",
                "src/commonTest/data/runWorkflowAllAvailable/private_data/input",
                "-out",
                "testOut/testRunBatchEncryptionTest",
                "-invalid",
                "testOut/testRunBatchEncryptionTest/invalid_ballots",
                "-nthreads",
                "$nthreads",
                "-check",
                "Verify",
            )
        )
    }

    @Test
    fun testRunBatchEncryptionVerifyDecrypt() {
        main(
            arrayOf(
                "-in",
                "src/commonTest/data/runWorkflowAllAvailable",
                "-ballots",
                "src/commonTest/data/runWorkflowAllAvailable/private_data/input",
                "-out",
                "testOut/testRunBatchEncryptionTest",
                "-invalid",
                "testOut/testRunBatchEncryptionTest/invalid_ballots",
                "-nthreads",
                "$nthreads",
                "-check",
                "DecryptNonce",
            )
        )
    }

    @Test
    fun testInvalidBallot() {
        val group = productionGroup()
        val inputDir = "src/commonTest/data/runWorkflowAllAvailable"
        val invalidDir = "testOut/testInvalidBallot"
        val consumerIn = makeConsumer(inputDir, group)
        val electionInit: ElectionInitialized =
            consumerIn.readElectionInitialized().getOrThrow { IllegalStateException(it) }
        val ballots = RandomBallotProvider(electionInit.manifest(), 1).ballots("badStyleId")

        batchEncryption(
            group,
            "src/commonTest/data/runWorkflowAllAvailable",
            "testOut/testInvalidBallot",
            ballots,
            invalidDir,
            false,
            1,
            "testInvalidBallot",
        )

        val consumerOut = makeConsumer(invalidDir, group)
        consumerOut.iteratePlaintextBallots(invalidDir, null).forEach {
            println("${it.errors}")
            assertContains(it.errors.toString(), "Ballot.A.1 Ballot Style 'badStyleId' does not exist in election")
        }

    }

}