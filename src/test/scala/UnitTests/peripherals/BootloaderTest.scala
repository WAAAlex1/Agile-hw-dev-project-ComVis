package UnitTests.peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import peripherals.Bootloader

/**
 * Bootloader by Alexander and Georg for the Wildcat
 */
class BootloaderTestByte extends AnyFlatSpec with
  ChiselScalatestTester {
  "bootloader" should "receive 1 byte" in {
    test(new Bootloader(10000000))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val BIT_CNT = ((10000000 + 115200 / 2) / 115200 - 1)

      dut.io.sleep.poke(false.B) //Set Active
      dut.io.rx.poke(1.U)

      //First byte:
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(0.U) //Start bit
      dut.clock.step(BIT_CNT)

      dut.io.rx.poke(0.U) //First data bit
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(1.U)
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(0.U)
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(1.U)
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(0.U)
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(1.U)
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(0.U)
      dut.clock.step(BIT_CNT)
      dut.io.rx.poke(1.U) //Last data bit
      dut.clock.step(100)

      dut.io.wrData.expect("haa000000".U)
      dut.io.wrEn.expect(0.U)

      dut.io.sleep.poke(true.B) //Set InActive


    }
  }
}

class BootloaderTestScala extends AnyFlatSpec with
  ChiselScalatestTester {
  "bootloader" should "Receive entire instruction and addr and enable writing" in {
    test(new Bootloader(10000000))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val BIT_CNT = ((10000000 + 115200 / 2) / 115200 - 1)

        val instrData = "h12345678".U
        val instrAddr = "haa54f08e".U

        //Start protocol
        def preByteProtocol() = {
          dut.io.rx.poke(1.U)
          dut.clock.step(BIT_CNT)
          dut.io.rx.poke(0.U)
          dut.clock.step(BIT_CNT)
        }

        def sendByte(n: UInt) = {
          preByteProtocol()

          for (j <- 0 until 8) { //0 until 8 means it runs from 0 to and with 7
            dut.io.rx.poke(n(j))
            dut.clock.step(BIT_CNT)
          }
        }

        //Little endian
        def send32bit(n: UInt) = {
          sendByte(n(7, 0))
          sendByte(n(15, 8))
          sendByte(n(23, 16))
          sendByte(n(31, 24))
        }

        dut.io.sleep.poke(false.B) //Set Active

        send32bit(instrAddr) //First send address
        dut.io.wrData.expect(instrAddr) //instrAddr should be in instrData space now
        send32bit(instrData) //Then send the instrData

        dut.io.wrAddr.expect(instrAddr)
        dut.io.wrData.expect(instrData)
        //dut.io.wrEnabled.expect(1.U) //This is not timed to the clock so will always fail but its okay

        dut.io.sleep.poke(true.B) //Set inactive

        //Test whether the bootloader is asleep now
        send32bit("h87654321".U)
        //We should expect no change to the bootbuffer:
        dut.io.wrData.expect(instrData)
        dut.io.wrAddr.expect(instrAddr)

      }
  }
}