package IntegrationTests

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.Tag
import java.io.{File, PrintWriter}

import mnist.BmpUtil
import topLevel._

/**
 * Integration test for TopWrapper
 * Tests the complete digit recognition pipeline with configurable parameters
 */
class TopWrapperTester extends AnyFlatSpec with ChiselScalatestTester {

  // Define tags
  object FastTest extends Tag("fast")
  object SlowTest extends Tag("slow")

  /** Test configuration case class */
  case class TestConfig(
                         imgWidth: Int,
                         TPN: Int,              // Templates Per Number
                         symbolN: Int,          // Number of symbols to test
                         useMnistData: Boolean,
                         threshold: Int = 128,
                         name: String
                       ) {
    val totalTemplates = TPN * symbolN
  }

  /** Generate simple test templates with predictable patterns
   *  Pattern: Template for digit D has value (D+1) * 0x11 on all lines
   *  This makes templates easily distinguishable - templates are the same
   */
  def generateSyntheticTemplates(config: TestConfig): Seq[String] = {
    val dir = new File("genForTests")
    if (!dir.exists()) dir.mkdirs()

    println(s"\n[TestGen] Generating ${config.totalTemplates} test templates:")

    val templateFiles = (0 until config.symbolN).flatMap { digit =>
      (0 until config.TPN).map { templateIdx =>
        val filename = s"genForTests/synthetic_template_${digit}_${templateIdx}.mem"
        val writer = new PrintWriter(new File(filename))

        // Pattern: Each digit has a unique repeating pattern (just the digit itself repeated).
        val pattern = digit
        val fullPattern = if (config.imgWidth == 8) {
          pattern
        } else {
          // For 32-bit width, repeat the pattern
          (pattern << 24) | (pattern << 16) | (pattern << 8) | pattern
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

  /** Generate synthetic test images - one per digit
   *  Each image matches template 0 of its digit for perfect recognition
   */
  def generateSyntheticImages(config: TestConfig): String = {
    val dir = new File("genForTests")
    if (!dir.exists()) dir.mkdirs()

    val filename = s"genForTests/synthetic_images.hex"
    val writer = new PrintWriter(new File(filename))

    try {
      // Generate one image for each digit
      for (digit <- 0 until config.symbolN) {
        val patternByte = digit
        val byte = patternByte & 0xFF
        // Make each image match template 0 of its digit for perfect recognition
        for (line <- 0 until config.imgWidth) {
          val value = ((byte << 24) | (byte << 16) | (byte << 8) | byte)
          writer.println(f"${value}%08X")
        }
      }
    } finally {
      writer.close()
    }

    println(s"[TestGen] Generated synthetic image file with ${config.symbolN} images (one per digit)")
    filename
  }

  /** Generate MNIST-based test data */
  def generateMnistData(config: TestConfig): (Seq[String], String) = {
    println(s"[TestGen] Generating MNIST data (${config.imgWidth}x${config.imgWidth})...")
    BmpUtil.saveTemplates(config.imgWidth, config.threshold, config.symbolN, config.TPN)
    BmpUtil.saveInputsToHex(config.imgWidth, config.threshold)

    val templateFiles = (0 until config.totalTemplates).map { i =>
      s"templates/template_${i}.hex"
    }
    val imageFile = "templates/mnist_input.hex"

    println(s"[TestGen] Generated ${templateFiles.length} MNIST templates")
    (templateFiles, imageFile)
  }

  /** Run test implementation - common code for all tests */
  private def runTestImpl(config: TestConfig): Unit = {
    println("\n" + "=" * 80)
    println(s"TEST: ${config.name}")
    println("=" * 80)
    println(s"Configuration:")
    println(s"  Image size: ${config.imgWidth}x${config.imgWidth}")
    println(s"  Symbols: ${config.symbolN}")
    println(s"  Templates per symbol: ${config.TPN}")
    println(s"  Test images: ${config.symbolN} (one per digit)")
    println(s"  Data type: ${if (config.useMnistData) "MNIST" else "Synthetic"}")
    println("=" * 80)

    // Generate test data
    val (templateFiles, imageFile) = if (config.useMnistData) {
      generateMnistData(config)
    } else {
      val templates = generateSyntheticTemplates(config)
      val images = generateSyntheticImages(config)
      (templates, images)
    }

    val templatePathBase = if (config.useMnistData) {
      "templates/template"
    } else {
      "genForTests/synthetic_template"
    }

    test(new TopWrapper(
      imgWidth = config.imgWidth,
      TPN = config.TPN,
      IPN = 1,  // Always 1 image per digit
      symbolN = config.symbolN,
      templatePath = templatePathBase,
      imagePath = Some(imageFile),
      debug = false,
      useDebouncer = false
    )
    ).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      println("\nStarting test sequence...\n")

      // Track results
      case class TestResult(
                             digitSelect: Int,
                             predictedDigit: Int,
                             confidence: Int,
                             correct: Boolean
                           )

      val results = scala.collection.mutable.ArrayBuffer[TestResult]()

      // Test one image for each digit
      for (digit <- 0 until config.symbolN) {
        println(s"--- Test ${digit + 1}/${config.symbolN}: Digit $digit ---")

        // Set up digit selection (imgSelect is always 0 since we have 1 image per digit)
        dut.io.digitSel.poke(digit.U)
        dut.io.imgSel.poke(0.U)
        dut.clock.step(2)

        // Pulse start button
        println(s"  Pulsing start signal...")
        dut.io.start.poke(true.B)
        dut.clock.step(10)
        dut.io.start.poke(false.B)

        // Wait for ROM to start transferring
        println(s"  Waiting for ROM transfer to start...")
        var romStarted = false
        var waitCycles = 0
        val maxWaitForStart = 200
        while (!romStarted && waitCycles < maxWaitForStart) {
          if (dut.io.debug.romStartOut.peek().litToBoolean) {
            romStarted = true
            println(s"    ROM started after ${waitCycles} cycles")
          }
          dut.clock.step(1)
          waitCycles += 1
        }

        if (!romStarted) {
          println(s"    WARNING: ROM never started (waited ${waitCycles} cycles)")
        }

        // Wait for processing to complete
        println(s"  Waiting for processing to complete...")
        var cycles = 0
        val maxCycles = config.imgWidth * 2 + 100
        while (!dut.io.done.peek().litToBoolean && cycles < maxCycles) {
          dut.clock.step(1)
          cycles += 1
        }

        if (cycles >= maxCycles) {
          println(s"    ERROR: Timed out after ${cycles} cycles")
          println(s"    Final state: done=${dut.io.done.peek().litToBoolean}, romBusy=${dut.io.debug.romBusy.peek().litToBoolean}")
        } else {
          println(s"    Processing completed in ${cycles} cycles")
        }

        // Read results
        val predictedDigit = dut.io.debug.bestIdx.peek().litValue.toInt
        val confidence = dut.io.debug.bestConf.peek().litValue.toInt
        val maxScore = config.imgWidth * config.imgWidth * config.TPN
        val confidencePercent = (confidence * 100) / maxScore

        val correct = predictedDigit == digit
        val status = if (correct) "| SUCCESS" else "| FAILURE"

        println(f"  Expected digit: $digit")
        println(f"  Predicted digit: $predictedDigit $status")
        println(f"  Confidence: $confidence / $maxScore ($confidencePercent%%)")

        results += TestResult(
          digit,
          predictedDigit,
          confidence,
          correct
        )

        // Wait before next test
        dut.clock.step(10)
      }

      // Print summary
      println("\n" + "=" * 80)
      println("TEST SUMMARY")
      println("=" * 80)

      val totalTests = results.length
      val correctPredictions = results.count(_.correct)
      val accuracy = (correctPredictions * 100.0) / totalTests

      println(f"\nOverall Statistics:")
      println(f"  Total images tested: $totalTests")
      println(f"  Correct predictions: $correctPredictions")
      println(f"  Incorrect predictions: ${totalTests - correctPredictions}")
      println(f"  Accuracy: $accuracy%.1f%%")

      // Per-digit results
      println(f"\nPer-Digit Results:")
      for (result <- results) {
        val status = if (result.correct) "| SUCCESS" else "| FAILURE"
        println(f"  Digit ${result.digitSelect}: predicted ${result.predictedDigit} (conf: ${result.confidence}) $status")
      }

      // Show failures
      val failures = results.filter(!_.correct)
      if (failures.nonEmpty) {
        println(f"\nFailures:")
        failures.foreach { r =>
          println(f"  Digit ${r.digitSelect}: Expected ${r.digitSelect}, got ${r.predictedDigit} (confidence: ${r.confidence})")
        }
      }

      println("\n" + "=" * 80)
    }
  }

  /** Run a tagged test */
  def runTopWrapperTestTagged(config: TestConfig, testName: String, tag: Tag): Unit = {
    it should testName taggedAs tag in {
      runTestImpl(config)
    }
  }

  // ============================================================================
  // Test Configurations
  // ============================================================================

  val smallSyntheticConfig = TestConfig(
    imgWidth = 32,
    TPN = 2,
    symbolN = 3,
    useMnistData = false,
    name = "Small Synthetic (3 digits, 2 templates each)"
  )

  val fullSyntheticConfig = TestConfig(
    imgWidth = 32,
    TPN = 10,
    symbolN = 10,
    useMnistData = false,
    name = "Full Synthetic (10 digits, 10 templates each)"
  )

  val smallMnistConfig = TestConfig(
    imgWidth = 32,
    TPN = 2,
    symbolN = 3,
    useMnistData = true,
    name = "Small MNIST (3 digits, 2 templates each)"
  )

  val fullMnistConfig = TestConfig(
    imgWidth = 32,
    TPN = 10,
    symbolN = 10,
    useMnistData = true,
    name = "Full MNIST (10 digits, 10 templates each)"
  )


  // ============================================================================
  // Test Behaviors
  // ============================================================================

  behavior of "TopWrapper"

  // Fast tests (run in CI)
  runTopWrapperTestTagged(
    smallSyntheticConfig,
    "verify small synthetic data",
    FastTest
  )

  // Slow tests (run manually or in extended CI)
  runTopWrapperTestTagged(
    fullSyntheticConfig,
    "verify full synthetic data",
    SlowTest
  )

  runTopWrapperTestTagged(
    smallMnistConfig,
    "verify small MNIST data",
    SlowTest
  )


}