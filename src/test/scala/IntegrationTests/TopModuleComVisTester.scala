package IntegrationTests

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.Tag
import java.io.{File, PrintWriter}

import comvis._

/**
 * Comprehensive test for TopModuleComVis
 * Tests the complete pipeline from image input to digit prediction
 */
class TopModuleComVisTester extends AnyFlatSpec with ChiselScalatestTester {

  // Define tags
  object FastTest extends Tag("fast")
  object SlowTest extends Tag("slow")

  /** Test configuration */
  case class TestConfig(
                         imgWidth: Int,
                         TPN: Int,
                         symbolN: Int,
                         name: String
                       ) {
    val totalTemplates = TPN * symbolN
    val maxScore = imgWidth * imgWidth * TPN
  }

  /** Generate simple test templates with predictable patterns
   *  Pattern: Template for digit D has value (D+1) * 0x11 on all lines
   *  This makes templates easily distinguishable
   */
  def generateTestTemplates(config: TestConfig): Seq[String] = {
    val dir = new File("genForTests")
    if (!dir.exists()) dir.mkdirs()

    println(s"\n[TestGen] Generating ${config.totalTemplates} test templates:")

    val templateFiles = (0 until config.symbolN).flatMap { digit =>
      (0 until config.TPN).map { templateIdx =>
        val absoluteIdx = digit * config.TPN + templateIdx
        val filename = s"genForTests/comvis_template_${absoluteIdx}.hex"
        val writer = new PrintWriter(new File(filename))

        // Pattern: Each digit has a unique repeating pattern
        // Digit 0: 0x11, Digit 1: 0x22, Digit 2: 0x33, etc.
        val pattern = ((digit + 1) * 0x11) & 0xFF
        val fullPattern = if (config.imgWidth == 8) {
          pattern
        } else {
          // For 32-bit width, repeat the pattern
          val byte = pattern & 0xFF
          (byte << 24) | (byte << 16) | (byte << 8) | byte
        }

        try {
          for (line <- 0 until config.imgWidth) {
            writer.println(f"${fullPattern}%08X")
          }
        } finally {
          writer.close()
        }

        if (templateIdx == 0) {
          println(f"  Digit $digit: pattern 0x${fullPattern}%08X")
        }

        filename
      }
    }

    println(s"[TestGen] Generated ${templateFiles.length} template files")
    templateFiles
  }

  /** Generate a test image matching a specific digit
   *  Returns the image data as a sequence of line values
   */
  def generateTestImage(config: TestConfig, matchDigit: Int): Seq[BigInt] = {
    val patternByte = ((matchDigit + 1) * 0x11) & 0xFF

    val fullPattern: BigInt = if (config.imgWidth == 8) {
      BigInt(patternByte)
    } else {
      // For 32-bit width, repeat the pattern - use BigInt to avoid signed issues!
      val byte = patternByte & 0xFF
      BigInt((byte << 24) | (byte << 16) | (byte << 8) | byte) & BigInt("FFFFFFFF", 16)
    }

    Seq.fill(config.imgWidth)(fullPattern)
  }

  /** Run a single image test */
  def runSingleImageTest(
                          dut: TopModuleComVis,
                          config: TestConfig,
                          imageData: Seq[BigInt],
                          expectedDigit: Int,
                          testName: String,
                          debug: Boolean = true
                        ): Boolean = {

    println(s"\n--- $testName ---")
    println(f"Expected digit: $expectedDigit")

    // Write image to BRAM
    println("Writing image to BRAM...")
    for ((lineData, lineIdx) <- imageData.zipWithIndex) {
      dut.io.memWrite.wrEn.poke(true.B)
      dut.io.memWrite.wrAddr.poke(lineIdx.U)
      dut.io.memWrite.wrData.poke(lineData.U)
      dut.clock.step(1)
    }
    dut.io.memWrite.wrEn.poke(false.B)
    println(s"  Wrote ${imageData.length} lines")

    // Start processing
    dut.io.start.poke(false.B)
    dut.clock.step(2)

    println("Starting processing...")
    dut.io.start.poke(true.B)
    dut.clock.step(1)
    dut.io.start.poke(false.B)

    // Monitor processing
    var cycle = 0
    var validCycles = 0
    var doneDetected = false
    val maxCycles = config.imgWidth * 2 + 20

    if(debug) println("\nMonitoring pipeline:")
    while (cycle < maxCycles && !doneDetected) {
      val maskerValid = dut.io.debug.maskerValid.peek().litToBoolean
      val maskerDone = dut.io.debug.maskerDone.peek().litToBoolean
      val accumValid = dut.io.debug.accumValid.peek().litToBoolean
      val evalValid = dut.io.debug.evalValid.peek().litToBoolean
      val done = dut.io.done.peek().litToBoolean
      val rdEn = dut.io.debug.rdEn.peek().litToBoolean
      val rdAddr = dut.io.debug.rdAddr.peek().litValue.toInt

      if (maskerValid) validCycles += 1

      // Show detailed data for first few cycles
      if (cycle <= 10 || maskerDone || accumValid || done) {
        if(debug) println(f"  Cycle $cycle%2d: maskerV=$maskerValid, maskerD=$maskerDone, " +
          f"accumV=$accumValid, evalV=$evalValid, done=$done, rdAddr=$rdAddr")

        // Show memory data when reading
        if (rdEn && cycle <= 10) {
          val imgData = dut.io.debug.imgData.peek().litValue
          if(debug) println(f"    Image data: 0x${imgData}%08X")

          // Show template data for each symbol
          for (s <- 0 until math.min(config.symbolN, 3)) {
            val t0Data = dut.io.debug.templateData(s)(0).peek().litValue
            if(debug) print(f"    Template[$s][0]: 0x${t0Data}%08X")

            // Show slice confidence if valid
            if (maskerValid) {
              val sliceConf = dut.io.debug.sliceConf(s)(0).peek().litValue
              if(debug) print(f" -> conf=$sliceConf")
            }
            if(debug) println()
          }
        }

        // Show accumulator scores
        if(debug) print(f"    Accum scores: ")
        for (i <- 0 until config.symbolN) {
          val score = dut.io.debug.accumScores(i).peek().litValue
          if(debug) print(f"[$i]=$score ")
        }
        if(debug) println()
      }

      if (accumValid) {
        doneDetected = true
        if(debug) println(f"    Accum Done detected at cycle $cycle")

        // Also check accumulator scores for debugging
        if(debug) println("\nFinal accumulator scores:")
        for (i <- 0 until config.symbolN) {
          val score = dut.io.debug.accumScores(i).peek().litValue
          val percent = (score * 100) / config.maxScore
          val marker = if (i == expectedDigit) " <- expected" else ""
          if(debug) println(f"  Digit $i: $score%4d / ${config.maxScore} ($percent%3d%%) $marker")
        }
      }

      dut.clock.step(1)
      cycle += 1
    }

    if (!doneDetected) {
      println(f"    ERROR: Processing timed out after $cycle cycles")
      println(f"    Valid cycles seen: $validCycles (expected: ${config.imgWidth})")
      return false
    }

    // Read results
    val predictedDigit = dut.io.bestIdx.peek().litValue.toInt
    val confidence = dut.io.bestConf.peek().litValue.toInt

    val confidencePercent = (confidence * 100) / config.maxScore
    val correct = predictedDigit == expectedDigit

    println(f"\nResults:")
    println(f"  Predicted: $predictedDigit ${if (correct) "| SUCCESS" else "| FAILURE"}")
    println(f"  Confidence: $confidence / ${config.maxScore} ($confidencePercent%%)")
    println(f"  Valid cycles: $validCycles (expected: ${config.imgWidth})")

    if (validCycles != config.imgWidth) {
      println(f"  WARNING: Valid cycle count mismatch!")
    }

    if (!correct) {
      println(f"  FAILURE: Expected $expectedDigit, got $predictedDigit")
    }

    // Wait before next test
    dut.clock.step(5)

    correct
  }

  /** Test implementation - common code */
  private def runTestImpl(config: TestConfig, debug: Boolean = false): Unit = {
    println("\n" + "="*80)
    println(s"TEST: ${config.name}")
    println("="*80)
    println(s"Configuration:")
    println(s"  Image size: ${config.imgWidth}x${config.imgWidth}")
    println(s"  Symbols: ${config.symbolN}")
    println(s"  Templates per symbol: ${config.TPN}")
    println(s"  Max score: ${config.maxScore}")
    println("="*80)

    // Generate templates
    val templateFiles = generateTestTemplates(config)
    val templatePathBase = "genForTests/comvis_template"

    test(new TopModuleComVis(
      imgWidth = config.imgWidth,
      TPN = config.TPN,
      symbolN = config.symbolN,
      templatePath = templatePathBase,
      debug = false
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      println("\n" + "="*80)
      println("Running Tests")
      println("="*80)

      val results = scala.collection.mutable.ArrayBuffer[Boolean]()

      // Test each digit with a matching image
      for (digit <- 0 until config.symbolN) {
        val imageData = generateTestImage(config, digit)
        val correct = runSingleImageTest(
          dut,
          config,
          imageData,
          digit,
          s"Test ${digit + 1}/${config.symbolN}: Digit $digit",
          debug
        )
        results += correct
      }

      // Summary
      println("\n" + "="*80)
      println("TEST SUMMARY")
      println("="*80)

      val totalTests = results.length
      val correctPredictions = results.count(_ == true)
      val accuracy = (correctPredictions * 100.0) / totalTests

      println(f"\nOverall Statistics:")
      println(f"  Total tests: $totalTests")
      println(f"  Correct: $correctPredictions")
      println(f"  Incorrect: ${totalTests - correctPredictions}")
      println(f"  Accuracy: $accuracy%.1f%%")

      val failures = results.zipWithIndex.filter(!_._1).map(_._2)
      if (failures.nonEmpty) {
        println(f"\nFailed tests (expected digits): ${failures.mkString(", ")}")
      }

      // Always verify - expect 100% accuracy with perfect templates
      println("\n" + "="*80)
      println("VERIFICATION")
      println("="*80)
      println("Perfect-match templates: Expecting 100% accuracy")
      assert(accuracy == 100.0, f"Accuracy not perfect: $accuracy%.1f%%")
      println("| SUCCESS: All predictions correct")

      println("\n" + "="*80)
    }
  }

  /** Run a tagged test */
  def runComVisTestTagged(config: TestConfig, testName: String, tag: Tag, debug: Boolean = false): Unit = {
    it should testName taggedAs tag in {
      runTestImpl(config, debug)
    }
  }

  // ============================================================================
  // Test Configurations
  // ============================================================================

  val config8x8_small = TestConfig(
    imgWidth = 8,
    TPN = 2,
    symbolN = 3,
    name = "8x8 small (3 symbols, 2 templates)"
  )

  val config8x8_full = TestConfig(
    imgWidth = 8,
    TPN = 10,
    symbolN = 10,
    name = "8x8 full (10 symbols, 10 templates)"
  )

  val config32x32_small = TestConfig(
    imgWidth = 32,
    TPN = 2,
    symbolN = 3,
    name = "32x32 small (3 symbols, 2 templates)"
  )

  val config32x32_full = TestConfig(
    imgWidth = 32,
    TPN = 10,
    symbolN = 10,
    name = "32x32 full (10 symbols, 10 templates)"
  )

  // ============================================================================
  // Test Behaviors
  // ============================================================================

  behavior of "TopModuleComVis"

  // Fast tests (run in CI)
  runComVisTestTagged(config8x8_small, "verify 8x8 small config", FastTest)

  // Slow tests
  runComVisTestTagged(config8x8_full, "verify 8x8 full config", SlowTest)
  runComVisTestTagged(config32x32_small, "verify 32x32 small config", SlowTest)
  runComVisTestTagged(config32x32_full, "verify 32x32 full config", SlowTest)
}