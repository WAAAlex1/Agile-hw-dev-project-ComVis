import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class EvalTester2 extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Eval"

  it should "find the highest confidence score" in {
    test(new Eval(N = 2, WIDTH = 10)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      def applyScores(scores: Seq[Int], expectedIdx: Int, expectedScore: Int): Unit = {

        for (i <- scores.indices) {
          dut.io.confScoresVec(i).poke(scores(i).U)
        }

        dut.io.valid.poke(true.B)
        dut.clock.step()
        dut.io.valid.poke(false.B)

        dut.clock.step()

        dut.io.bestIdx.expect(expectedIdx.U)
        dut.io.bestScore.expect(expectedScore.U)

        dut.clock.step()
      }

      applyScores(
        Seq(100, 200),
        expectedIdx = 1, expectedScore = 200
      )

      applyScores(
        Seq(1, 0),
        expectedIdx = 0, expectedScore = 1
      )
    }
  }
}
