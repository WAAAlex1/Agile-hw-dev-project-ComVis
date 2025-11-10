package UnitTests

import scala.util.Random

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import comvis.{MaskSlice, Masker}

class MaskerTester extends AnyFlatSpec with ChiselScalatestTester {

  "Masker" should "find confidence" in {
    test(new Masker(32))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      /*
      val rand = new Random()
      val img = Vector.fill(32)(8.U(32.W))
      val mask = Vector.fill(32)(8.U(32.W))
      */

      val img = "hdeadbeef".U
      val mask = "hdeadbeef".U

      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.start.poke(false.B)
      for(i <- 0 to 31)
      {
        dut.io.imgIn.poke(img(i))
        dut.io.maskIn.poke(mask(i))
        dut.clock.step()
      }

      println("Expected Confidence was: " + 1024 + " and actually was " + dut.io.confidence.peek())
      dut.io.ready.expect(true.B)
      dut.io.confidence.expect(1024.U)
    }
  }
}