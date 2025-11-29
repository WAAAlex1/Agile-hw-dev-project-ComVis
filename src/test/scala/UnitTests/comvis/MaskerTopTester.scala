package UnitTests.comvis

import chisel3._

import chiseltest._
import comvis.MaskerTop
import org.scalatest.flatspec.AnyFlatSpec
import java.math.BigInteger

class MaskerTopTester extends AnyFlatSpec with ChiselScalatestTester {

  val imgWidth = 32
  val TPN = 2
  val symbolN = 2

  "MaskerTop" should "find confidence" in {
    test(new MaskerTop(32,2,2))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        def popCount(n: BigInt, width: Int): Int = {
          (0 until width).count(i => n.testBit(i))
        }

        val img = BigInt("deadbeef", 16)
        val maskValue = BigInt("12345678", 16)
        val xnorResult = ~(img ^ maskValue) & ((BigInt(1) << imgWidth) - 1)
        val expectedConfidence = popCount(xnorResult, imgWidth)

        //Connect the templates to some value
        for(templLine <- 0 until symbolN) {
          for(mask <- 0 until TPN) {
            dut.io.din.templateData(templLine)(mask).poke(maskValue)
          }
        }
        dut.io.din.imgData.poke(img)
        dut.clock.step()
        dut.io.extIn.start.poke(true.B)
        dut.clock.step()
        dut.io.extIn.start.poke(false.B)
        dut.io.memOut.rdEn.expect(true.B)
        dut.io.out.valid.expect(false.B)
        dut.clock.step()
        dut.io.out.valid.expect(true.B)
        dut.io.out.sliceConf(0)(0).expect(expectedConfidence.U)
        dut.clock.step(33)
        dut.io.out.valid.expect(false.B)
        dut.io.out.done.expect(true.B)

    }
  }
}