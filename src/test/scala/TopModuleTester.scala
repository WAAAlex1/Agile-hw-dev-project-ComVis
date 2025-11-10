
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class TopModuleTester extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "TopModule"

  it should "classify the input image correctly" in {
    test(new TopModule).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // Initialize inputs
      dut.io.start.poke(false.B)
      dut.io.out.expect(0.U) // Initial output
      dut.io.done.expect(false.B) // Initial done signal

      dut.clock.step(1)

      // Start the classification process
      dut.io.start.poke(true.B)
      dut.clock.step(1)
      dut.io.start.poke(false.B)

      // Wait for the done signal
      while (dut.io.done.peek().litToBoolean == false) {
        dut.clock.step(1)
      }

      // Check the output classification result
      val result = dut.io.out.peek().litValue
      println(s"Classification result: $result")
    }
  }
}