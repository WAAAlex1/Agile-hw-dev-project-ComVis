package IntegrationTests

import chisel3._
import chisel3.util.{Cat, log2Up}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import topLevel._

class TopWrapperUartTest extends AnyFlatSpec with
  ChiselScalatestTester {
  "TopWrapperUART" should "Receive 8x8 image, sleep bl, and start then finish" in {
    test(new TopWrapperUART(10000000,8,1,2,"templates/minitemplate", false, false))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val BIT_CNT = ((10000000 + 115200 / 2) / 115200 - 1)

        val ledAddr = "hF0010000".U
        val bootSleepAddr = "hF1000000".U

        val imgData = "h000000E8".U

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

        //Send the 8x8 image
        for(i <- 0 to 7){
          send32bit(("h0000000" + i).U) //First send address
          send32bit(imgData) //Then send the instrData
        }

        //Send LED
        send32bit(ledAddr)
        send32bit("h000000FF".U)
        dut.io.led.expect("hFF".U)

        //Set bootloader to sleep
        send32bit(bootSleepAddr)
        send32bit("h00000001".U)

        //Start the comvis:
        dut.io.start.poke(true.B)
        dut.clock.step()
        dut.io.start.poke(false.B)
        dut.clock.step(11)
        dut.io.done.expect(true.B)
      }
  }
}

class TopWrapperUartToTemplatesTest extends AnyFlatSpec with
  ChiselScalatestTester {
  "TopWrapperUART" should "Write to templates and then start comvis" in {
    test(new TopWrapperUART(10000000,8,1,2,"", false, false))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val BIT_CNT = ((10000000 + 115200 / 2) / 115200 - 1)

        val imgWidth = 8
        val TPN = 1
        val symbolN = 2

        val ledAddr = "hF0010000".U
        val bootSleepAddr = "hF1000000".U

        val imgData = "h000000E8".U
        val templateData =
          Array.fill(symbolN,TPN,imgWidth)("h00000000".U)
        for(i <- 0 until symbolN){
          for(j <- 0 until TPN){
            for(k <- 0 until imgWidth) {
              templateData(i)(j)(k) = ((("h" + i) + j) + k).U(32.W)
            }
          }
        }


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

        def sendWord(addr: UInt, data: UInt) = {
          send32bit(addr)
          send32bit(data)
        }

        def closeBootloader() = {
          println("Setting LED and sleeping Bootloader...")

          //Send LED
          send32bit(ledAddr)
          send32bit("h000000FF".U)
          dut.io.led.expect("hFF".U)

          //Set bootloader to sleep
          send32bit(bootSleepAddr)
          send32bit("h00000001".U)
        }

        def toBinaryPadded(value: Int, maxValue: Int): String = {
          require(maxValue >= 0, "maxValue must be non-negative")
          require(value >= 0, "value must be non-negative")

          val width =
            if (maxValue == 0) 1
            else maxValue.toBinaryString.length

          val masked = value & ((1 << width) - 1)

          masked.toBinaryString.reverse.padTo(width, '0').reverse
        }

        def log2Ceil(x: BigInt): Int = {
          require(x > 0)
          if (x == 1) 0 else (x - 1).bitLength
        }

        val kWidth     = log2Ceil(BigInt(imgWidth))              // imgWidth = 8 → 3 bits
        val indexWidth = log2Ceil(BigInt(symbolN * TPN))        // 2 → 1 bit

        var templateAddr = 0

        for (i <- 0 until symbolN) {
          for (j <- 0 until TPN) {
            for (k <- 0 until imgWidth) {

              val temp: BigInt =
                (BigInt(templateAddr) << kWidth) | BigInt(k)

              sendWord(temp.U, temp.U)

              println(
                f"templateAddr=$templateAddr  k=$k  → addr=0b${temp.toString(2).reverse.padTo(indexWidth + kWidth, '0').reverse}"
              )
            }

            templateAddr += 1
          }
        }



        //Send the image
        for(i <- 0 to imgWidth){
          sendWord((("b" + (symbolN*TPN - 1).toBinaryString) + toBinaryPadded(i,imgWidth)).U,imgData)
          println("Sent imgData: " + imgData + " to addr: " + (("b" + (symbolN*TPN - 1).toBinaryString) + toBinaryPadded(i,imgWidth)))
        }



        //close:
        closeBootloader()

        //Start the comvis:
        dut.io.start.poke(true.B)
        dut.clock.step()
        dut.io.start.poke(false.B)
        dut.clock.step(imgWidth)
        dut.io.done.expect(true.B)
      }
  }
}