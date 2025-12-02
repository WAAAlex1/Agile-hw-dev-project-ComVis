package topLevel

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._

import comvis._
import peripherals._
import bram._
import mnist.BmpUtil._

class TopWrapper(
  val imgWidth: Int, // Width of the image
  val TPN: Int, // Templates per number (for processing)
  val IPN: Int, // Images per number (for checking)
  val symbolN: Int, // Number of symbols (0-9 symbolN = 10)
  val templatePath: String, // Path to template folder and start of name. Rest of name should be _i.hex.
  val imagePath: Option[String], // Total Path to image file. No restrictions on naming. Should be hex file.
  val debug: Boolean = false,
  val useDebouncer: Boolean = true,
  val includeDebugPorts: Boolean = true
) extends Module {

  // Validate parameters at elaboration time
  require(imgWidth > 0, "imgWidth must be positive")
  require(TPN > 0, "TPN must be positive")
  require(IPN > 0, "IPN must be positive")
  require(symbolN > 0 && symbolN <= 16, "symbolN must be 1-16 (limited by wrapper)")

  val io = IO(new Bundle {
    val start = Input(Bool())

    val digitSel = Input(UInt(4.W))
    val imgSel   = Input(UInt(4.W))

    val done     = Output(Bool())
    val anodes   = Output(UInt(8.W))
    val cathodes = Output(UInt(8.W))

    // Debug outputs for testing (could be wrapped in #ifdef for synthesis)
    val debug = if (includeDebugPorts) Some(Output(new Bundle {
      val bestIdx     = Output(UInt(log2Up(symbolN).W))
      val bestConf    = Output(UInt(log2Up((imgWidth * imgWidth) * TPN + 1).W))
      val romBusy     = Output(Bool())
      val romStartOut = Output(Bool())
    }))
    else None
  })

  // Build template file list and pass it to TopModuleComVis
  val totalTemplates = TPN * symbolN
  val templateFiles = (0 until symbolN).flatMap { digit =>
    (0 until TPN).map { templateIdx =>
      // matches the naming TopModuleComVis expects: template_<digit>_<templateIdx>.hex
      templatePath + s"_${digit}_${templateIdx}.mem"
    }
  }

  // instantiate top module
  val comVis = Module(
    new TopModuleComVis(
      imgWidth = imgWidth,
      TPN = TPN,
      symbolN = symbolN,
      initFiles = Some(templateFiles),
      debug = debug
    )
  )

  // seven seg
  val maxScore       = (imgWidth * imgWidth) * TPN;
  val sevenSegDriver = Module(new SevenSegDriver(maxScore = maxScore))

  // instantiate memory
  val initRom = Module(new InitRam(IPN = IPN, TPN = TPN, symbolN = symbolN, imgWidth = imgWidth, initFile = imagePath))

  // debouncer
  val startSignal = if (useDebouncer) {
    val debouncer = Module(new Debounce())
    debouncer.io.btn := io.start
    debouncer.io.stable
  } else {
    // Direct connection for testing
    io.start
  }

  // ============================================================================
  // OUTPUT HOLDING REGISTERS
  // ============================================================================
  // These registers capture and hold the results until start is pressed again

  val bestIdxHold  = RegInit(0.U(log2Up(symbolN).W))
  val bestConfHold = RegInit(0.U(log2Up((imgWidth * imgWidth) * TPN + 1).W))
  val doneHold     = RegInit(false.B)

  when(comVis.io.done) {
    doneHold     := comVis.io.done
    bestIdxHold  := comVis.io.bestIdx
    bestConfHold := comVis.io.bestConf
  }

  // ============================================================================
  // CONNECTIONS
  // ============================================================================

  initRom.io.start       := startSignal
  initRom.io.digitSelect := io.digitSel
  initRom.io.imgSelect   := io.imgSel

  // ROM to ComVis
  comVis.io.memWrite.wrData := initRom.io.writeOut.wrData
  comVis.io.memWrite.wrAddr := initRom.io.writeOut.wrAddr
  comVis.io.memWrite.wrEn   := initRom.io.writeOut.wrEn
  comVis.io.start           := initRom.io.startOut

  // ComVis to SevenSeg and digit select
  sevenSegDriver.io.digitA     := io.digitSel
  sevenSegDriver.io.digitB     := bestIdxHold // Use held value
  sevenSegDriver.io.confidence := bestConfHold // Use held value

  // outputs
  io.anodes   := sevenSegDriver.io.anodes
  io.cathodes := sevenSegDriver.io.cathodes
  io.done     := doneHold // Use held value

  // Debug outputs
  io.debug.foreach { port =>
    port.bestIdx     := comVis.io.bestIdx
    port.bestConf    := comVis.io.bestConf
    port.romBusy     := initRom.io.busy
    port.romStartOut := initRom.io.startOut
  }

}

object TopWrapper extends App {

  // Check if files exist, only generate if missing
  val templateRoot = "template"
  val imageFile    = "mnist_input.mem"

  val width               = 32
  val number_of_digits    = 10
  val templates_per_digit = 10

  val numImages = 10

  println("Generating template files")
  saveTemplates(width, 128, number_of_digits, templates_per_digit)
  saveInputsToHex(32, 128)

  println("Generating hardware")
  emitVerilog(
    new TopWrapper(
      imgWidth = width,
      TPN = templates_per_digit,
      IPN = numImages,
      symbolN = number_of_digits,
      templatePath = templateRoot,
      imagePath = Some(imageFile),
      debug = true,
      useDebouncer = true,
      includeDebugPorts = false
    ),
    Array(
      "--target-dir",
      "generated"
    )
  )
}
