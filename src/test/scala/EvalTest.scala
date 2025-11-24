import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class EvalTester extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Eval"

  it should "find the highest confidence score" in {
    test(new Eval(N = 10, WIDTH = 10)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      def applyScores(scores: Seq[Int], expectedIdx: Int, expectedScore: Int): Unit = {

        // poke input vector
        for (i <- scores.indices) {
          dut.io.confScoresVec(i).poke(scores(i).U)
        }

        // valid pulse
        dut.io.valid.poke(true.B)
        dut.clock.step()
        dut.io.valid.poke(false.B)

        dut.clock.step()

        // expect results
        dut.io.bestIdx.expect(expectedIdx.U)
        dut.io.bestScore.expect(expectedScore.U)

        dut.clock.step()
      }

      applyScores(
        Seq(100, 200, 512, 256, 1023, 800, 300, 400, 1010, 500),
        expectedIdx = 4, expectedScore = 1023
      )

      applyScores(
        Seq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        expectedIdx = 9, expectedScore = 9
      )

      applyScores(
        Seq(900, 800, 700, 600, 500, 400, 300, 200, 100, 0),
        expectedIdx = 0, expectedScore = 900
      )

      applyScores(
        Seq.fill(10)(512),
        expectedIdx = 0, expectedScore = 512
      )
    }
  }
}
