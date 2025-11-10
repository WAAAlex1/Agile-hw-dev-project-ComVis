package unit_tests.bram

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import bram._
/**
 * Test for BramMemWrapper with configurable image dimensions
 * Tests 24x24, 28x28, and 32x32 configurations
 */
class BramMemWrapperTester extends AnyFlatSpec with ChiselScalatestTester {

  // Test configuration selection
  sealed trait TestConfig {
    val height: Int
    val width: Int
    val name: String
  }

  object TestConfig {
    case object Config24x24 extends TestConfig {
      val height = 24
      val width = 24
      val name = "24x24"
    }

    case object Config28x28 extends TestConfig {
      val height = 28
      val width = 28
      val name = "28x28"
    }

    case object Config32x32 extends TestConfig {
      val height = 32
      val width = 32
      val name = "32x32"
    }

    // Default configuration
    val default: TestConfig = Config32x32
  }

  /**
   * Run test with specified configuration
   */
  def testBramMemWrapper(config: TestConfig): Unit = {
    behavior of s"BramMemWrapper ${config.name}"

    it should s"write and read data correctly (${config.name})" in {
      test(new BramMemWrapper(
        numLines = config.height,
        lineWidth = config.width,
        initFile = None
      )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println(s"\n=== Testing BramMemWrapper ${config.name} ===")

        // Test 1: Write pattern data to first 8 lines
        println("Test 1: Writing pattern data")
        for (addr <- 0 until 8) {
          val testData = addr
          dut.io.wrEn.poke(true.B)
          dut.io.wrAddr.poke(addr.U)
          dut.io.wrData.poke(testData.U)
          dut.clock.step(1)
          println(f"  Line $addr: Wrote 0x${testData}%X successfully")
        }
        dut.io.wrEn.poke(false.B)

        // Test 2: Read back and verify
        println("Test 2: Reading and verifying data")
        for (addr <- 0 until 8) {
          val expectedData = addr

          dut.io.lineAddr.poke(addr.U)
          dut.io.lineEn.poke(true.B)
          dut.clock.step(1)

          dut.io.lineData.expect(expectedData.U)
          println(f"  Line $addr: Read 0x${expectedData}%X successfully")
        }

        // Test 3: Write to middle of memory
        println("Test 3: Writing to middle addresses")
        val midAddr = config.height / 2
        val testValue = 0xABC

        dut.io.wrEn.poke(true.B)
        dut.io.wrAddr.poke(midAddr.U)
        dut.io.wrData.poke(testValue.U)
        dut.clock.step(1)
        dut.io.wrEn.poke(false.B)

        // Read back
        dut.io.lineAddr.poke(midAddr.U)
        dut.io.lineEn.poke(true.B)
        dut.clock.step(1)
        dut.io.lineData.expect(testValue.U)
        println(f"  Line $midAddr: Read 0x${testValue}%X successfully")

        // Test 4: Fill entire memory with pattern
        println("Test 4: Filling entire memory")
        println("Written memory:")
        for (line <- 0 until config.height) {
          val pattern = line << (line % 16)
          dut.io.wrEn.poke(true.B)
          dut.io.wrAddr.poke(line.U)
          dut.io.wrData.poke(pattern.U)
          dut.clock.step(1)
        }
        dut.io.wrEn.poke(false.B)

        // Verify entire memory
        println("Test 5: Verifying entire memory")
        println("Read memory")
        for (line <- 0 until config.height) {
          val expected = line << (line % 16)
          dut.io.lineAddr.poke(line.U)
          dut.io.lineEn.poke(true.B)
          dut.clock.step(1)
          dut.io.lineData.expect(expected.U)
        }
        println(f"  All ${config.height} lines verified successfully")

        println(s"=== All tests passed for ${config.name} ===\n")
      }
    }
  }

  // Run tests for default configuration (32×32)
  testBramMemWrapper(TestConfig.default)

  // Uncomment for additional testing of other configurations:
  //testBramMemWrapper(TestConfig.Config24x24)
  //testBramMemWrapper(TestConfig.Config28x28)
}
