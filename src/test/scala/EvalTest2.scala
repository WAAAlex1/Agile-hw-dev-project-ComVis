import chisel3._
import chiseltest._
import comvis.Eval
import org.scalatest.flatspec.AnyFlatSpec

class EvalTester2 extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "comvis.Eval"

  it should "find the highest confidence score for symbolN=2" in {

    val imgWidth = 32
    val TPN      = 1
    val symbolN  = 2

    test(new Eval(imgWidth, TPN, symbolN)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      def applyScores(scores: Seq[Int], expectedIdx: Int, expectedScore: Int): Unit = {

        for (i <- scores.indices) {
          dut.io.in.confScore(i).poke(scores(i).U)
        }

        dut.io.in.valid.poke(true.B)
        dut.clock.step()
        dut.io.in.valid.poke(false.B)

        dut.io.bestIdx.expect(expectedIdx.U)
        dut.io.bestScore.expect(expectedScore.U)

        dut.clock.step()
      }

      applyScores(
        Seq(10, 20),
        expectedIdx = 1,
        expectedScore = 20
      )

      applyScores(
        Seq(1, 0),
        expectedIdx = 0,
        expectedScore = 1
      )
    }
  }
}
