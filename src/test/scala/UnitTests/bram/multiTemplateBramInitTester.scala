package UnitTests.bram

import bram._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import java.io.{File, PrintWriter}
import mnist.BmpUtil

/**
 * Test for MultiTemplateBram initialization from hex files
 * Validates that templates are correctly loaded from files
 */
class multiTemplateBramInitTester extends AnyFlatSpec with ChiselScalatestTester {

  // Test configuration - small for fast testing
  val testTPN = 2      // 2 templates per number
  val testSymbolN = 3  // 3 symbols (0-2)
  val testImgWidth = 8 // 8x8 images for quick tests

  val totalTemplates = testTPN * testSymbolN

  /** Generate test hex files with known patterns */
  def generateTestTemplateFiles(): Seq[String] = {
    val dir = new File("genForTests")
    if (!dir.exists()) {
      dir.mkdirs()
    }

    val filenames = (0 until totalTemplates).map { templateIdx =>
      val filename = s"genForTests/test_template_${templateIdx}.hex"
      val writer = new PrintWriter(new File(filename))

      try {
        for (line <- 0 until testImgWidth) {
          val value = (templateIdx << 4) | line
          writer.println(f"${value}%02X")
        }
      } finally {
        writer.close()
      }
      filename
    }

    filenames
  }

  // ============================================================================
  // TEST 1: Basic initialization check
  // ============================================================================

  behavior of "MultiTemplateBram basic initialization"

  it should "load template files without errors" in {
    val templateFiles = generateTestTemplateFiles()

    println("\n" + "="*80)
    println("TEST 1: Basic Initialization Check")
    println("="*80)
    println(s"Generated ${templateFiles.length} test files")
    templateFiles.zipWithIndex.foreach { case (file, idx) =>
      println(s"  [$idx] $file")
    }

    test(new MultiTemplateBram(
      TPN = testTPN,
      symbolN = testSymbolN,
      imgWidth = testImgWidth,
      initFiles = Some(templateFiles)
    )) { dut =>
      println(s"\nHardware instantiated successfully")
      println(s"  Config: ${testTPN}x${testSymbolN} = ${totalTemplates} templates")
      println(s"  Size: ${testImgWidth}x${testImgWidth} = ${testImgWidth * testImgWidth} bits per template")

      // Just verify we can read without crashing
      dut.io.memIn.rdAddrIdx.poke(0.U)
      dut.io.memIn.rdEn.poke(true.B)
      dut.clock.step(1)

      println("SUCCESS: Basic instantiation successful")
    }
  }

  // ============================================================================
  // TEST 2: First line verification
  // ============================================================================

  behavior of "MultiTemplateBram first line data"

  it should "correctly load first line of all templates" in {
    val templateFiles = generateTestTemplateFiles()

    println("\n" + "="*80)
    println("TEST 2: First Line Verification")
    println("="*80)

    test(new MultiTemplateBram(
      TPN = testTPN,
      symbolN = testSymbolN,
      imgWidth = testImgWidth,
      initFiles = Some(templateFiles)
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // Read first line (address 0)
      dut.io.memIn.rdAddrIdx.poke(0.U)
      dut.io.memIn.rdEn.poke(true.B)
      dut.clock.step(1)

      println("\nExpected pattern: (templateIdx << 4) | lineIdx")
      println("Line 0: all templates should have pattern (templateIdx << 4) | 0")
      println("\nResults:")

      var allCorrect = true
      for (symbolIdx <- 0 until testSymbolN) {
        for (templateIdx <- 0 until testTPN) {
          val absoluteTemplateIdx = symbolIdx * testTPN + templateIdx
          val expectedValue = (absoluteTemplateIdx << 4) | 0  // line 0
          val actualValue = dut.io.memOut.templateData(symbolIdx)(templateIdx).peek().litValue.toInt

          val status = if (actualValue == expectedValue) "SUCCESS" else "FAILURE"
          println(f"  Template[$symbolIdx][$templateIdx] (abs=$absoluteTemplateIdx): " +
            f"expected=0x${expectedValue}%02X, actual=0x${actualValue}%02X $status")

          if (actualValue != expectedValue) {
            allCorrect = false
          }
        }
      }

      println(s"\nResult: ${if (allCorrect) "SUCCESS: ALL CORRECT" else "ERROR: FAILURES DETECTED"}")
      assert(allCorrect, "Some templates have incorrect first line data")
    }
  }

  // ============================================================================
  // TEST 3: Middle line verification
  // ============================================================================

  behavior of "MultiTemplateBram middle line data"

  it should "correctly load middle line of all templates" in {
    val templateFiles = generateTestTemplateFiles()
    val testLine = testImgWidth / 2

    println("\n" + "="*80)
    println("TEST 3: Middle Line Verification")
    println("="*80)
    println(s"Testing line ${testLine} of ${testImgWidth}")

    test(new MultiTemplateBram(
      TPN = testTPN,
      symbolN = testSymbolN,
      imgWidth = testImgWidth,
      initFiles = Some(templateFiles)
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      dut.io.memIn.rdAddrIdx.poke(testLine.U)
      dut.io.memIn.rdEn.poke(true.B)
      dut.clock.step(1)

      println(s"\nExpected pattern: (templateIdx << 4) | ${testLine}")
      println("\nResults:")

      var allCorrect = true
      for (symbolIdx <- 0 until testSymbolN) {
        for (templateIdx <- 0 until testTPN) {
          val absoluteTemplateIdx = symbolIdx * testTPN + templateIdx
          val expectedValue = (absoluteTemplateIdx << 4) | testLine
          val actualValue = dut.io.memOut.templateData(symbolIdx)(templateIdx).peek().litValue.toInt

          val status = if (actualValue == expectedValue) "SUCCESS" else "FAILURE"
          println(f"  Template[$symbolIdx][$templateIdx] (abs=$absoluteTemplateIdx): " +
            f"expected=0x${expectedValue}%02X, actual=0x${actualValue}%02X $status")

          if (actualValue != expectedValue) {
            allCorrect = false
          }
        }
      }

      println(s"\nResult: ${if (allCorrect) "SUCCESS: ALL CORRECT" else "ERROR: FAILURES DETECTED"}")
      assert(allCorrect, "Some templates have incorrect middle line data")
    }
  }

  // ============================================================================
  // TEST 4: Full memory dump per BRAM
  // ============================================================================

  behavior of "MultiTemplateBram full memory dump"

  it should "dump entire contents of each BRAM for inspection" in {
    val templateFiles = generateTestTemplateFiles()

    println("\n" + "="*80)
    println("TEST 4: Full Memory Dump Per BRAM")
    println("="*80)
    println(s"Dumping all ${testImgWidth} lines from each of ${totalTemplates} templates")
    println("="*80)

    test(new MultiTemplateBram(
      TPN = testTPN,
      symbolN = testSymbolN,
      imgWidth = testImgWidth,
      initFiles = Some(templateFiles)
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // For each template, dump its entire memory
      for (symbolIdx <- 0 until testSymbolN) {
        for (templateIdx <- 0 until testTPN) {
          val absoluteTemplateIdx = symbolIdx * testTPN + templateIdx

          println(s"\nTemplate[$symbolIdx][$templateIdx] (absolute index $absoluteTemplateIdx):")
          println(s"  Expected pattern: each line = 0x${absoluteTemplateIdx << 4}X (where X = line number)")
          print(s"  Actual data:      ")

          // Read all lines for this template
          val memoryContents = (0 until testImgWidth).map { line =>
            dut.io.memIn.rdAddrIdx.poke(line.U)
            dut.io.memIn.rdEn.poke(true.B)
            dut.clock.step(1)

            val value = dut.io.memOut.templateData(symbolIdx)(templateIdx).peek().litValue.toInt
            value
          }

          // Print entire memory on one line
          println(memoryContents.map(v => f"0x${v}%02X").mkString(" "))

          // Verify if it matches expected pattern
          val expectedContents = (0 until testImgWidth).map { line =>
            (absoluteTemplateIdx << 4) | line
          }

          val matches = memoryContents.zip(expectedContents).forall { case (actual, expected) =>
            actual == expected
          }

          if (matches) {
            println(s"  Status: CORRECT")
          } else {
            println(s"  Status: INCORRECT")
            print(s"  Expected data:    ")
            println(expectedContents.map(v => f"0x${v}%02X").mkString(" "))

            // Show which positions differ
            val differences = memoryContents.zip(expectedContents).zipWithIndex.collect {
              case ((actual, expected), idx) if actual != expected => idx
            }
            println(s"  Differences at positions: ${differences.mkString(", ")}")
          }
        }
      }

      println("\n" + "="*80)
      println("Memory Dump Analysis:")

      // Check if all templates have identical data (would indicate loading issue)
      val firstTemplateData = (0 until testImgWidth).map { line =>
        dut.io.memIn.rdAddrIdx.poke(line.U)
        dut.io.memIn.rdEn.poke(true.B)
        dut.clock.step(1)
        dut.io.memOut.templateData(0)(0).peek().litValue.toInt
      }

      var allTemplatesIdentical = true
      for (symbolIdx <- 0 until testSymbolN) {
        for (templateIdx <- 0 until testTPN) {
          if (symbolIdx != 0 || templateIdx != 0) {  // Skip first template
            val thisTemplateData = (0 until testImgWidth).map { line =>
              dut.io.memIn.rdAddrIdx.poke(line.U)
              dut.io.memIn.rdEn.poke(true.B)
              dut.clock.step(1)
              dut.io.memOut.templateData(symbolIdx)(templateIdx).peek().litValue.toInt
            }

            if (thisTemplateData != firstTemplateData) {
              allTemplatesIdentical = false
            }
          }
        }
      }

      if (allTemplatesIdentical) {
        println("  WARNING: ALL TEMPLATES HAVE IDENTICAL DATA!")
        println("  This suggests templates are not being loaded independently.")
        println("  All templates contain the same data as Template[0][0]")
      } else {
        println("  SUCCESS: Templates have different data")
      }

      println("="*80)
    }
  }


  // ============================================================================
  // TEST 5: Read persistence
  // ============================================================================

  behavior of "MultiTemplateBram read stability"

  it should "return consistent data across multiple reads" in {
    val templateFiles = generateTestTemplateFiles()
    val testLine = 3
    val numReads = 5

    println("\n" + "="*80)
    println("TEST 5: Read Stability Check")
    println("="*80)
    println(s"Reading line ${testLine} ${numReads} times to verify consistency")

    test(new MultiTemplateBram(
      TPN = testTPN,
      symbolN = testSymbolN,
      imgWidth = testImgWidth,
      initFiles = Some(templateFiles)
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // Track all reads
      val readResults = scala.collection.mutable.ArrayBuffer[(Int, Int)]()

      for (readNum <- 0 until numReads) {
        dut.io.memIn.rdAddrIdx.poke(testLine.U)
        dut.io.memIn.rdEn.poke(true.B)
        dut.clock.step(1)

        val value = dut.io.memOut.templateData(0)(0).peek().litValue.toInt
        readResults += ((readNum, value))
      }

      println("\nRead results for Template[0][0]:")
      val expectedValue = (0 << 4) | testLine
      readResults.foreach { case (readNum, value) =>
        val status = if (value == expectedValue) "SUCCESS" else "FAILURE"
        println(f"  Read $readNum: 0x${value}%02X (expected: 0x${expectedValue}%02X) $status")
      }

      val allSame = readResults.map(_._2).distinct.length == 1
      val allCorrect = readResults.forall(_._2 == expectedValue)

      if (allSame && allCorrect) {
        println(s"SUCCESS: ALL ${numReads} READS CONSISTENT AND CORRECT")
      } else if (allSame) {
        println(s"WARNING: All reads consistent but incorrect value")
      } else {
        println(s"ERROR: INCONSISTENT READS DETECTED")
      }

      assert(allCorrect, "Reads are not returning correct values")
    }
  }


}