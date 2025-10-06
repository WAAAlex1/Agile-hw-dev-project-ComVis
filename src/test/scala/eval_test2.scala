import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class EvalTester2 extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "Eval"

    it should "find the highest confidence score" in {
        test(new Eval(N = 2, WIDTH = 10)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            // test 1
            val scores1 = Seq(100, 200)
            for (i <- scores1.indices) {
                dut.io.c_scores(i).poke(scores1(i).U)
            }
            dut.clock.step()
            dut.io.best_idx.expect(1.U)
            dut.io.best_score.expect(200.U)

            // test 2
            val scores2 = Seq(1, 0)
            for (i <- scores2.indices) {
                dut.io.c_scores(i).poke(scores2(i).U)
            }
            dut.clock.step()
            dut.io.best_idx.expect(0.U)
            dut.io.best_score.expect(1.U)

        }
    }
}