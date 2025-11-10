package unit_tests.bram

import bram._

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * Test for MultiTemplateBram with configurable number of templates
 * Default: 40 templates, 32x32 configuration
 *
 * To test with different number of templates, modify numTemplates below
 */
class multiTemplateBramTester extends AnyFlatSpec with ChiselScalatestTester {

  // Test configuration
  val defaultNumTemplates = 40
  val imageHeight = 32
  val imageWidth = 32

  behavior of s"MultiTemplateBram"

  it should s"write and read ${defaultNumTemplates} templates correctly" in {
    test(new MultiTemplateBram(
      numTemplates = defaultNumTemplates,
      numLines = imageHeight,
      lineWidth = imageWidth,
      initFiles = None
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      println(s"\n=== Testing MultiTemplateBram: ${defaultNumTemplates} templates, ${imageHeight}x${imageWidth} ===")

      // Test 1: Write unique patterns to each template
      println("Test 1: Writing unique patterns to all templates")
      for (templateIdx <- 0 until defaultNumTemplates) {
        for (line <- 0 until imageHeight) {
          // Create unique pattern: template index in upper bits, line in lower bits
          val pattern = (templateIdx << 16) | (line << 8) | 0xAA

          dut.io.wrEn.poke(true.B)
          dut.io.wrTemplate.poke(templateIdx.U)
          dut.io.wrAddr.poke(line.U)
          dut.io.wrData.poke(pattern.U)
          dut.clock.step(1)
        }
        if (templateIdx % 10 == 0) {
          println(s"  Written template ${templateIdx}")
        }
      }
      dut.io.wrEn.poke(false.B)
      println(s"  All ${defaultNumTemplates} templates written successfully")

      // Test 2: Read back all templates line by line
      println("Test 2: Reading back all templates in parallel")
      for (line <- 0 until imageHeight) {
        // Read this line from all templates simultaneously
        dut.io.lineAddr.poke(line.U)
        dut.io.lineEn.poke(true.B)
        dut.clock.step(1)

        // Verify data from each template
        for (templateIdx <- 0 until defaultNumTemplates) {
          val expected = (templateIdx << 16) | (line << 8) | 0xAA
          dut.io.lineData(templateIdx).expect(expected.U)
        }

        if (line % 8 == 0) {
          println(s"  Line ${line}: All ${defaultNumTemplates} templates verified successfully")
        }
      }
      println(s"  All lines verified across all templates successfully")

      // Test 3: Write and read specific template
      println("Test 3: Selective template access")
      val testTemplateIdx = defaultNumTemplates / 2
      val testLine = imageHeight / 2
      val testValue = 0x12345

      // Write to specific template and line
      dut.io.wrEn.poke(true.B)
      dut.io.wrTemplate.poke(testTemplateIdx.U)
      dut.io.wrAddr.poke(testLine.U)
      dut.io.wrData.poke(testValue.U)
      dut.clock.step(1)
      dut.io.wrEn.poke(false.B)

      // Read back entire line (all templates)
      dut.io.lineAddr.poke(testLine.U)
      dut.io.lineEn.poke(true.B)
      dut.clock.step(1)

      // Verify only the modified template changed
      for (templateIdx <- 0 until defaultNumTemplates) {
        if (templateIdx == testTemplateIdx) {
          dut.io.lineData(templateIdx).expect(testValue.U)
          println(f"  Template ${testTemplateIdx}, Line ${testLine}: 0x${testValue}%X")
        } else {
          val expected = ((templateIdx << 16) | (testLine << 8) | 0xAA)
          dut.io.lineData(templateIdx).expect(expected.U)
        }
      }
      println(s"  Selective write verified successfully")

      println(s"\n=== All tests passed for ${defaultNumTemplates} templates ===\n")
    }
  }
}
