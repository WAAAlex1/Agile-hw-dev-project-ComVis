package UnitTests.comvis

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import chisel3.util._
import comvis.Accumulator
import scala.util.Random

class AccumulatorTest extends AnyFlatSpec with ChiselScalatestTester {

  val imgWidth = 32
  val TPN = 10
  val symbolN = 2

  "Accumulator" should "accumulate all values" in {
    test(new Accumulator(imgWidth, TPN, symbolN))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        val rand = new Random()
        val sumArr = Array.fill(symbolN)(0L) // Using Long type just in case

        for(_ <- 0 until imgWidth){
          for (i <- 0 until symbolN) {
            for (j <- 0 until TPN) {

              // Generate a random number
              val reqWidth = log2Up(imgWidth)
              val randVal: Long = rand.nextLong() & ((1L << reqWidth) - 1)

              dut.io.din.sliceConf(i)(j).poke(randVal.U(reqWidth.W))

              sumArr(i) += randVal
              //println("randVal: " + randVal + " | bitWidth: " + log2Up(randVal) + " | sumArr(" + i + "): " + sumArr(i) + " | bitWidth: " + log2Up(sumArr(i)))
            }
          }
          dut.io.din.valid.poke(true.B)
          dut.clock.step()
        }

        dut.io.din.done.poke(true.B)
        dut.io.din.valid.poke(false.B)
        dut.io.out.valid.expect(true.B)

        for (i <- 0 until symbolN) {
          println("confScore("+ i +"): " + dut.io.out.confScore(i).peek().litValue)
          println("sumArr("+ i +"): " + sumArr(i))
          dut.io.out.confScore(i).expect(sumArr(i).U)
        }

        dut.clock.step()
        dut.io.din.done.poke(false.B)
        dut.io.out.valid.expect(false.B)

        dut.clock.step(16)
      }
  }
}


