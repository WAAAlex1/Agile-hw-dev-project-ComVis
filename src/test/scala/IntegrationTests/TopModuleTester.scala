package IntegrationTests

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import comvis._

class TopModuleTester extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "TopModuleComVis"
  it should "load BRAM hex files and run a full evaluation" in {

    // Create template file list
    val initFilesTemp = scala.collection.mutable.ArrayBuffer[String]()
    for (i <- 0 until 10) {
      for (j <- 0 until 10) {
        initFilesTemp.append(s"templates/templates_0/template_${i}_${j}.mem")
      }
    }

    // Convert to Seq
    val initFiles: Seq[String] = initFilesTemp.toSeq

    test(new TopModuleComVis(
      imgWidth = 32,
      TPN = 10,
      symbolN = 10,
      initFiles = Some(initFiles),
      debug = true
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // Reset device
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      // Write image line data into BRAM through memWrite
      val testImage: Seq[Int] =
        scala.io.Source.fromFile(s"templates/templates_1/template_1_2.mem")
          .getLines()
          .filter(_.nonEmpty)
          .map { hex => Integer.parseInt(hex, 16) }
          .toSeq

      // Write image line data into DUT BRAM
      for ((pixel, addr) <- testImage.zipWithIndex) {
        dut.io.memWrite.wrEn.poke(true.B)
        dut.io.memWrite.wrAddr.poke(addr.U)
        dut.io.memWrite.wrData.poke(pixel.U)
        dut.clock.step()
      }
      dut.io.memWrite.wrEn.poke(false.B)

      // Start Processing
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.start.poke(false)

      // Wait for done
      while (!dut.io.done.peek().litToBoolean) {
        dut.clock.step()
      }

      println("==== Test result ====")
      println("Best index = " + dut.io.bestIdx.peek().litValue)
      println("Best conf  = " + dut.io.bestConf.peek().litValue + " | " + (dut.io.bestConf.peek().litValue.toInt * 100) / (32 * 32 * 10))
    }
  }
}
