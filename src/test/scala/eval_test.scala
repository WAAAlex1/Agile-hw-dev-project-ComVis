import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class EvalTester extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "Eval"

    it should "find the highest confidence score" in {
        test(new Eval(N = 10, WIDTH = 10)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            // test 1
            val scores1 = Seq(100, 200, 512, 256, 1023, 800, 300, 400, 1010, 500)
            for (i <- scores1.indices) {
                dut.io.c_scores(i).poke(scores1(i).U)
            }
            dut.clock.step()
            dut.io.best_idx.expect(4.U)
            dut.io.best_score.expect(1023.U)

            // test 2
            val scores2 = Seq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            for (i <- scores2.indices) {
                dut.io.c_scores(i).poke(scores2(i).U)
            }
            dut.clock.step()
            dut.io.best_idx.expect(9.U)
            dut.io.best_score.expect(9.U)
            
            // test 3
            val scores3 = Seq(900, 800, 700, 600, 500, 400, 300, 200, 100, 0)
            for (i <- scores3.indices) {
                dut.io.c_scores(i).poke(scores3(i).U)
            }
            dut.clock.step()
            dut.io.best_idx.expect(0.U)
            dut.io.best_score.expect(900.U)

            // test 4
            val scores4 = Seq.fill(10)(512)
            for (i <- scores4.indices) {
                dut.io.c_scores(i).poke(scores4(i).U)
            }
            dut.clock.step()
            dut.io.best_idx.expect(0.U)
            dut.io.best_score.expect(512.U)
        }
    }
}