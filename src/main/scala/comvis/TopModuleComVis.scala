package comvis

import bram._
import chisel3._
import chisel3.util._

class TopModuleComVis(
  val imgWidth: Int,
  val TPN: Int,
  val symbolN: Int,
  val initFiles: Option[Seq[String]] = None,
  val debug: Boolean = false
) extends Module {

  val io = IO(new Bundle {
    val start    = Input(Bool())
    val memWrite = new MemWrite(log2Up(imgWidth), imgWidth, symbolN * TPN + 1)
    val done     = Output(Bool())
    val bestIdx  = Output(UInt(log2Up(symbolN).W))
    val bestConf = Output(UInt(log2Up((imgWidth * imgWidth * TPN) + 1).W))

    // Debug outputs
    val debug = new Bundle {
      val maskerDone  = Output(Bool())
      val maskerValid = Output(Bool())
      val accumValid  = Output(Bool())
      val evalValid   = Output(Bool())
      val accumScores = Output(Vec(symbolN, UInt(log2Up((imgWidth * imgWidth) * TPN + 1).W)))

      // Memory interface debug
      val imgData      = Output(UInt(imgWidth.W))
      val templateData = Output(Vec(symbolN, Vec(TPN, UInt(imgWidth.W))))
      val rdAddr       = Output(UInt(log2Up(imgWidth).W))
      val rdEn         = Output(Bool())

      // Slice confidence debug (output from masker for current line)
      val sliceConf = Output(Vec(symbolN, Vec(TPN, UInt(log2Up(imgWidth + 1).W))))
    }
  })

  // Modules:
  val masker   = Module(new MaskerTop(imgWidth, TPN, symbolN))
  val confAccu = Module(new Accumulator(imgWidth, TPN, symbolN))
  val evaler   = Module(new Eval(imgWidth, TPN, symbolN))

  val bram = Module(new MultiTemplateBram(TPN, symbolN, imgWidth, initFiles = initFiles, debug = debug))

  // IO connections:
  bram.io.memWrite <> io.memWrite
  bram.io.memIn <> masker.io.memOut
  bram.io.memOut <> masker.io.din
  masker.io.extIn.start := io.start
  masker.io.out <> confAccu.io.din
  confAccu.io.out <> evaler.io.in
  io.bestIdx  := evaler.io.bestIdx
  io.bestConf := evaler.io.bestScore
  io.done     := evaler.io.done

  // Debug outputs
  io.debug.maskerDone  := masker.io.out.done
  io.debug.maskerValid := masker.io.out.valid
  io.debug.accumValid  := confAccu.io.out.valid
  io.debug.evalValid   := evaler.io.in.valid
  io.debug.accumScores := confAccu.io.out.confScore

  // Memory interface debug
  io.debug.imgData      := bram.io.memOut.imgData
  io.debug.templateData := bram.io.memOut.templateData
  io.debug.rdAddr       := masker.io.memOut.rdAddrIdx
  io.debug.rdEn         := masker.io.memOut.rdEn

  // Slice confidence debug
  io.debug.sliceConf := masker.io.out.sliceConf

}
