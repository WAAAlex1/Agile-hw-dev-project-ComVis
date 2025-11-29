package comvis

import bram._
import chisel3._
import chisel3.util._

class TopModuleComVis(val imgWidth: Int, val TPN: Int, val symbolN: Int, val templatePath: String) extends Module {
  val io = IO(new Bundle {
    val start    = Input(Bool())
    val memWrite = new MemWrite(log2Up(imgWidth), imgWidth)
    val done     = Output(Bool())
    val bestIdx  = Output(UInt(log2Up(symbolN).W))
    val bestConf = Output(UInt(imgWidth.W))
  })

  // Modules:
  val masker        = Module(new MaskerTop(imgWidth, TPN, symbolN))
  val confAccu      = Module(new Accumulator(imgWidth, TPN, symbolN))
  val evaler        = Module(new Eval(imgWidth, TPN, symbolN))
  val templateFiles = (0 until TPN * symbolN).map(i => templatePath + s"_${i}.hex")
  val bram          = Module(new MultiTemplateBram(TPN, symbolN, imgWidth, initFiles = Some(templateFiles)))

  // IO connections:
  // INSERT BRAM IO
  bram.io.memWrite <> io.memWrite
  bram.io.memIn <> masker.io.memOut
  bram.io.memOut <> masker.io.din
  masker.io.extIn.start := io.start
  masker.io.out <> confAccu.io.din
  confAccu.io.out <> evaler.io.in
  io.bestIdx  := evaler.io.bestIdx
  io.bestConf := evaler.io.bestScore
  io.done     := evaler.io.done
}
