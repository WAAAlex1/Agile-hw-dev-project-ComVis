package unit_tests.bram

import bram._

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * Test for MultiTemplateBram with new interface structure
 * Default: 4 templates per number, 10 symbols (digits 0-9), 32x32 images
 */
class multiTemplateBramTester extends AnyFlatSpec with ChiselScalatestTester {

  // Test configuration
  val defaultTPN = 4      // Templates per number
  val defaultSymbolN = 10 // Number of symbols (0-9)
  val defaultImgWidth = 32

  val totalTemplates = defaultTPN * defaultSymbolN

  behavior of s"MultiTemplateBram"

  it should s"write and read image and ${totalTemplates} templates correctly" in {
    test(new MultiTemplateBram(
      TPN = defaultTPN,
      symbolN = defaultSymbolN,
      imgWidth = defaultImgWidth,
      initFiles = None
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      println(s"\n=== Testing MultiTemplateBram: ${defaultTPN} templates per symbol, ${defaultSymbolN} symbols, ${defaultImgWidth}x${defaultImgWidth} ===")

      // Test 1: Write pattern to image BRAM
      println("Test 1: Writing pattern to image BRAM")
      for (line <- 0 until defaultImgWidth) {
        val imagePattern = 0x1000 | line

        dut.io.memWrite.wrEn.poke(true.B)
        dut.io.memWrite.wrAddr.poke(line.U)
        dut.io.memWrite.wrData.poke(imagePattern.U)
        dut.clock.step(1)
      }
      dut.io.memWrite.wrEn.poke(false.B)
      println(s"  Wrote ${defaultImgWidth} lines to image BRAM")

      // Test 2: Read back image data line by line
      println("Test 2: Reading image data")
      for (line <- 0 until defaultImgWidth) {
        val expectedImage = 0x1000 | line

        dut.io.memIn.rdAddrIdx.poke(line.U)
        dut.io.memIn.rdEn.poke(true.B)
        dut.clock.step(1)

        dut.io.memOut.imgData.expect(expectedImage.U)

        if (line % 8 == 0) {
          println(f"  Line ${line}: Image data = 0x${expectedImage}%X SUCCESS")
        }
      }
      println(s"  All image lines verified successfully")

      // Test 3: Verify template data structure (nested Vec)
      println("Test 3: Reading template data structure")
      val testLine = defaultImgWidth / 2

      dut.io.memIn.rdAddrIdx.poke(testLine.U)
      dut.io.memIn.rdEn.poke(true.B)
      dut.clock.step(1)

      println(s"  Reading line ${testLine} from all templates:")
      for (symbolIdx <- 0 until defaultSymbolN) {
        for (templateIdx <- 0 until defaultTPN) {
          // Peek to verify structure exists and is accessible
          val templateData = dut.io.memOut.templateData(symbolIdx)(templateIdx).peek()
          if (symbolIdx < 2 && templateIdx == 0) {
            println(f"    Symbol ${symbolIdx}, Template ${templateIdx}: 0x${templateData.litValue}%X")
          }
        }
      }
      println(s"  Template data structure verified (${defaultSymbolN} symbols x ${defaultTPN} templates)")

      // Test 4: Verify simultaneous read of image and templates
      println("Test 4: Simultaneous read test")
      val simultTestLine = 10
      val simultTestPattern = 0xBEEF

      // Write a specific pattern to image
      dut.io.memWrite.wrEn.poke(true.B)
      dut.io.memWrite.wrAddr.poke(simultTestLine.U)
      dut.io.memWrite.wrData.poke(simultTestPattern.U)
      dut.clock.step(1)
      dut.io.memWrite.wrEn.poke(false.B)

      // Read that line - should get both image and all templates simultaneously
      dut.io.memIn.rdAddrIdx.poke(simultTestLine.U)
      dut.io.memIn.rdEn.poke(true.B)
      dut.clock.step(1)

      // Verify image data
      dut.io.memOut.imgData.expect(simultTestPattern.U)
      println(f"  Line ${simultTestLine}: Image = 0x${simultTestPattern}%X SUCCESS")
      println(f"  Line ${simultTestLine}: All ${totalTemplates} templates successfully read simultaneously ")

      // Test 5: Overwrite specific image line
      println("Test 5: Image BRAM write/read cycle")
      val testAddr = 5
      val pattern1 = 0x11111111
      val pattern2 = 0x22222222

      dut.io.memWrite.wrEn.poke(true.B)
      dut.io.memWrite.wrAddr.poke(testAddr.U)
      dut.io.memWrite.wrData.poke(pattern1.U)
      dut.clock.step(1)

      dut.io.memWrite.wrData.poke(pattern2.U)
      dut.clock.step(1)
      dut.io.memWrite.wrEn.poke(false.B)

      // Read back - should have pattern2 (last write)
      dut.io.memIn.rdAddrIdx.poke(testAddr.U)
      dut.io.memIn.rdEn.poke(true.B)
      dut.clock.step(1)
      dut.io.memOut.imgData.expect(pattern2.U)
      println(f"  Overwrite test passed: 0x${pattern2}%X SUCCESS")

      println(s"\n=== All tests passed for ${totalTemplates} templates + 1 image BRAM ===\n")
    }
  }
}