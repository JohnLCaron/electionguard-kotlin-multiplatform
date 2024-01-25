package electionguard.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SMexpTest {
    val group = productionGroup()

    @Test
    fun testSMexample() {
        val e0 = 30.toElementModQ(group)
        val e1 = 10.toElementModQ(group)
        val e2 = 24.toElementModQ(group)
        val es = listOf(e0, e1, e2)

        val bases = List(3) { group.gPowP( group.randomElementModQ()) }

        val sam = SMexp(group, bases, es)
        val result = sam.prodPowP()

        val check =  bases.mapIndexed { idx, it -> it powP es[idx] }.reduce { a, b -> (a * b) }

        assertEquals(check, result)

        val result2 = sam.prodPowP2()
        assertEquals(result2, result)
    }

    @Test
    fun testSMmatrix() {
        val e0 = 30.toElementModQ(group)
        val e1 = 10.toElementModQ(group)
        val e2 = 24.toElementModQ(group)
        val es = listOf(e0, e1, e2)

        val bases = List(3) { group.gPowP( group.randomElementModQ()) }

        val sam = SMexp(group, bases, es)
        val result = sam.prodPowP()
        val check =  bases.mapIndexed { idx, it -> it powP es[idx] }.reduce { a, b -> (a * b) }
        assertEquals(check, result)

        val result2 = sam.prodPowP2()
        assertEquals(result2, result)
    }

    @Test
    fun testSMsizes() {
        runSM(10)
        runSM(30)
        runSM(100)
        runSM(1000)
    }

    fun runSM(nrows : Int) {
        val exps = List(nrows) { group.randomElementModQ() }
        val bases = List(nrows) { group.gPowP( group.randomElementModQ()) }

        val result = SMexp(group, bases, exps).prodPowP()
        val check =  bases.mapIndexed { idx, it -> it powP exps[idx] }.reduce { a, b -> (a * b) }
        assertEquals(check, result)
    }

    @Test
    fun testSMtiming() {
        runSMrepeat(10, 100)
        runSMrepeat(100, 10)
        runSMrepeat(1000, 1)
    }

    fun runSMrepeat(nrows : Int, ntimes: Int) {
        println("runSMrepeat nrows = $nrows, ntimes = $ntimes")

        val exps = List(nrows) { group.randomElementModQ() }
        val bases = List(nrows) { group.gPowP(group.randomElementModQ()) }

        var starting1 = getSystemTimeInMillis()
        repeat (ntimes) {
            SMexp(group, bases, exps).prodPowP()
        }
        val time1 = getSystemTimeInMillis() - starting1

        var starting2 = getSystemTimeInMillis()
        repeat (ntimes) {
            bases.mapIndexed { idx, it -> it powP exps[idx] }.reduce { a, b -> (a * b) }
        }
        val time2 = getSystemTimeInMillis() - starting2
        println(" timeSM = $time1, timePowP = $time2")
    }

}