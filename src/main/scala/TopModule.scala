import chisel3._
import chisel3.util._

import java.io.File

import BmpUtil.bmp2mem

class TopModule extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val out = Output(UInt(1.W))
    val done = Output(Bool())
  })

  io.done := false.B
  io.out := 0.U

  // Load BMP file and convert to memory (temporarily register) representation
  val template0 = VecInit((bmp2mem(new java.io.File("src/main/mnist/mnist_0_0.bmp"), 128)).map(_.U(32.W)))
  val template1 = VecInit((bmp2mem(new java.io.File("src/main/mnist/mnist_1_0.bmp"), 128)).map(_.U(32.W)))
  val inputImage = VecInit((bmp2mem(new java.io.File("src/main/mnist/mnistTest_1_0.bmp"), 128)).map(_.U(32.W)))

  val addrReg = RegInit(0.U(5.W))
  val confReg0 = RegInit(0.U(32.W))
  val confReg1 = RegInit(0.U(32.W))

  val xnor0 = ~(template0(addrReg) ^ inputImage(addrReg))
  val xnor1 = ~(template1(addrReg) ^ inputImage(addrReg))

  val popCount0 = PopCount(xnor0)
  val popCount1 = PopCount(xnor1) 
  
  // Instantiate modules
  //val evaluator = Module(new Eval(1, 10))

  val sIdle :: sWorking :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  switch(state) {
    is(sIdle) {

      confReg0 := confReg0
      confReg1 := confReg1
      addrReg := addrReg
      when(io.start) {
        state := sWorking
      }
    }
    is(sWorking) {

      confReg0 := confReg0 + popCount0
      confReg1 := confReg1 + popCount1
      addrReg := addrReg + 1.U
      when(addrReg === 31.U) {
        state := sDone
      }
    }
    is(sDone) {
      addrReg := 0.U
      io.done := true.B
      io.out := Mux(confReg0 > confReg1, 0.U, 1.U)
      when(io.start) {
        state := sIdle
      }
    }
  }
}

object TopModule extends App {
  println("Generating the hardware")
  emitVerilog(new TopModule(), Array("--target-dir", "generated"))
}