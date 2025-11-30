package IntegrationTests

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import topLevel._

class TopWrapperUartTest extends AnyFlatSpec with
  ChiselScalatestTester {
  "TopWrapperUART" should "Receive 8x8 image, sleep bl, and start then finish" in {
    test(new TopWrapperUART(10000000,8,1,2,"minitemplate"))
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