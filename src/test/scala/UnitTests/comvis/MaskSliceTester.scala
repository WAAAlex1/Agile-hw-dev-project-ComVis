package UnitTests.comvis

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import comvis.MaskSlice

class MaskSliceTester extends AnyFlatSpec with ChiselScalatestTester {

  "MaskSlice" should "find confidence 4" in {
    test(new MaskSlice(32)) { dut =>
      dut.io.maskSlice.poke("h00000001".U)
      dut.io.imgSlice.poke("h00000001".U)
      dut.io.confidence.expect(32)
    }
  }
}