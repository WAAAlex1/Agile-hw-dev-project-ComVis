package IntegrationTests

import mnist.BmpUtil._
import chisel3._
import chiseltest._
import comvis._
import org.scalatest.flatspec.AnyFlatSpec

class TopModuleComVisTester extends AnyFlatSpec with ChiselScalatestTester {

  "TopModule" should "find bestIdx and bestScore" in {
    test(new TopModuleComVis(8,1,2, "templates/minitemplate"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val imgWidth = 8
        val TPN = 1
        val symbolN = 2

        //Default IO
        dut.io.start.poke(false.B)

        //Write in the input image:
        for(i <- 0 until 8){
          dut.io.memWrite.wrEn.poke(true.B)
          dut.io.memWrite.wrData.poke("hE8".U)
          dut.io.memWrite.wrAddr.poke(i.U)
          dut.clock.step()
        }
        dut.io.memWrite.wrEn.poke(false.B)



        //waiting for start
        dut.clock.step(5)
        dut.io.start.poke(true.B)
        dut.clock.step()
        dut.io.start.poke(false.B)
        dut.clock.step(11)
        dut.io.done.expect(true.B)

    }
  }
}