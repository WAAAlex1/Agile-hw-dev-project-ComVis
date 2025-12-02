package topLevel

import chisel3._
import chisel3.util._
import comvis._

import peripherals._

class TopWrapperUART(
  val frequ: Int,
  val imgWidth: Int,
  val TPN: Int,
  val symbolN: Int,
  val templatePath: String,
  val debug: Boolean = false,
  val useDebouncer: Boolean = false
) extends Module {

  val io = IO(new Bundle {
    val start = Input(Bool())
    val rx    = Input(Bool())

    val led      = Output(UInt(8.W))
    val done     = Output(Bool())
    val anodes   = Output(UInt(8.W))
    val cathodes = Output(UInt(8.W))
  })

  // Build template file list and pass it to TopModuleComVis
  val totalTemplates = TPN * symbolN
  val templateFiles = (0 until symbolN).flatMap { digit =>
    (0 until TPN).map { templateIdx =>
      // matches the naming TopModuleComVis expects: template_<digit>_<templateIdx>.hex
      templatePath + s"_${digit}_${templateIdx}.hex"
    }
  }

  // instantiate top module
  val comVis = Module(new TopModuleComVis(imgWidth, TPN, symbolN, None, debug))

  // UART loader stuff
  val bootloader = Module(new Bootloader(frequ, 115200))

  // seven seg
  val maxScore       = imgWidth * imgWidth * TPN
  val sevenSegDriver = Module(new SevenSegDriver(maxScore = maxScore))

  val startSignal = if (useDebouncer) {
    val debouncer = Module(new Debounce())
    debouncer.io.btn := io.start
    debouncer.io.stable
  } else {
    // Direct connection for testing
    io.start
  }

  // LED reg:
  val ledReg = RegInit(0.U(8.W))

  // connections
  comVis.io.start := startSignal

  // UART and ComVis
  comVis.io.memWrite.wrData := bootloader.io.wrData(imgWidth - 1, 0) // Lazy bootloader fix
  comVis.io.memWrite.wrAddr := bootloader.io.wrAddr(log2Up(imgWidth) + log2Up(TPN * symbolN), 0) // Width translation
  comVis.io.memWrite.wrEn   := bootloader.io.wrEn
  bootloader.io.rx          := io.rx
  bootloader.io.sleep       := 0.U // Default

  // ComVis to SevenSeg. Label based??
  sevenSegDriver.io.digitA     := 0.U
  sevenSegDriver.io.digitB     := comVis.io.bestIdx
  sevenSegDriver.io.confidence := comVis.io.bestConf

  // UART Sleep logic:
  when(bootloader.io.wrEn === 1.U) {
    // LED Memory map
    when(bootloader.io.wrAddr === "hF0010000".U) {
      // Don't write to img mem when memmap
      comVis.io.memWrite.wrEn := 0.U
      ledReg                  := bootloader.io.wrData(7, 0)
    }

    // UART sleep memory map
    when(bootloader.io.wrAddr === "hF1000000".U) {
      // Don't write to img mem when memmap
      comVis.io.memWrite.wrEn := 0.U
      bootloader.io.sleep     := bootloader.io.wrData(0)
    }
  }

  // outputs
  io.anodes   := sevenSegDriver.io.anodes
  io.cathodes := sevenSegDriver.io.cathodes

  io.done := comVis.io.done
  io.led  := ledReg
}

object TopWrapperUART extends App {
  println("Generating the hardware")
  emitVerilog(
    new TopWrapperUART(100000000, 32, 10, 10, "", false, true),
    Array("--target-dir", "generated")
  )
}
