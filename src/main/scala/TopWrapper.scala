import chisel3._
import chisel3.util._

import comvis._
import peripherals._

class TopWrapper extends Module {

  val io = IO(new Bundle {
    val start = Input(Bool())

    val digitSel = Input(UInt(4.W))
    val imgSel   = Input(UInt(4.W))

    val done     = Output(Bool())
    val anodes   = Output(UInt(8.W))
    val cathodes = Output(UInt(8.W))
  })

  // instantiate top module
  val comVis = Module(new TopModuleComVis())

  // seven seg
  val sevenSegDriver = Module(new SevenSegDriver)

  // instantiate memory
  val initRom = Module(new InitRom(IPN = 1, symbolN = 10, imgWidth = 32, initFile = " "))

  // debouncer
  val debouncer = Module(new Debounce)

  // connections
  debouncer.io.btn       := io.start
  initRom.io.start       := debouncer.io.stable
  initRom.io.digitSelect := io.digitSel
  initRom.io.imgSelect   := io.imgSel

  // ROM to ComVis
  comVis.io.memWrite.wrData := initRom.io.writeOut.wrData
  comVis.io.memWrite.wrAddr := initRom.io.writeOut.wrAddr
  comVis.io.memWrite.wrEn   := initRom.io.writeOut.wrEn
  comVis.io.start           := initRom.io.startOut

  // ComVis to SevenSeg and digit select
  sevenSegDriver.io.digitA     := io.digitSel
  sevenSegDriver.io.digitB     := comVis.io.bestIdx
  sevenSegDriver.io.confidence := comVis.io.bestConf

  // outputs
  io.anodes   := sevenSegDriver.io.anodes
  io.cathodes := sevenSegDriver.io.cathodes

  io.done := comVis.io.done

}

object TopWrapper extends App {
  println("Generating the hardware")
  emitVerilog(new TopWrapper(), Array("--target-dir", "generated"))
}
