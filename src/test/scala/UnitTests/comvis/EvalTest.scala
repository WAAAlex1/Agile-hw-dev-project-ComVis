
package UnitTests.comvis

import chisel3._
import chiseltest._
import comvis.Eval
import org.scalatest.flatspec.AnyFlatSpec

class EvalTester extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "comvis.Eval"

  it should "find the highest confidence score" in {

    val imgWidth  = 32
    val TPN       = 1
    val symbolN   = 10

    test(new Eval(imgWidth, TPN, symbolN)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      def applyScores(scores: Seq[Int], expectedIdx: Int, expectedScore: Int): Unit = {

        // poke scores
        for (i <- scores.indices) {
          dut.io.in.confScore(i).poke(scores(i).U)
        }

        // pulse valid for 1 cycle
        dut.io.in.valid.poke(true.B)
        dut.clock.step()
        dut.io.in.valid.poke(false.B)

        // check outputs
        dut.io.bestIdx.expect(expectedIdx.U)
        dut.io.bestScore.expect(expectedScore.U)

        dut.clock.step()
      }

      // ---- Test cases ----

      applyScores(
        Seq(1, 2, 51, 25, 63, 8, 3, 4, 10, 5),
        expectedIdx = 4,
        expectedScore = 63
      )

      applyScores(
        Seq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        expectedIdx = 9,
        expectedScore = 9
      )

      applyScores(
        Seq(63, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        expectedIdx = 0,
        expectedScore = 63
      )

      applyScores(
        Seq.fill(10)(20),
        expectedIdx = 0,
        expectedScore = 20
      )
    }
  }
}
