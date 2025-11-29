import chisel3._
import chisel3.util._

import comvis._
import peripherals._
import bram._
import mnist.BmpUtil._

class TopWrapper(
        val imgWidth: Int,                  // Width of the image
        val TPN: Int,                       // Templates per number (for processing)
        val IPN: Int,                       // Images per number (for checking)
        val symbolN: Int,                   // Number of symbols (0-9 symbolN = 10)
        val templatePath: String,           // Path to template folder and start of name. Rest of name should be _i.hex.
        val imagePath: Option[String]       // Total Path to image file. No restrictions on naming. Should be hex file.
  ) extends Module {

  val io = IO(new Bundle {
    val start = Input(Bool())

    val digitSel = Input(UInt(4.W))
    val imgSel   = Input(UInt(4.W))

    val done     = Output(Bool())
    val anodes   = Output(UInt(8.W))
    val cathodes = Output(UInt(8.W))
  })

  // instantiate top module
  val comVis = Module(new TopModuleComVis(imgWidth = imgWidth, TPN = TPN,
                                          symbolN = symbolN, templatePath = templatePath))

  // seven seg
  val maxScore = (imgWidth * imgWidth) * TPN;

  val sevenSegDriver = Module(new SevenSegDriver(maxScore = maxScore))

  // instantiate memory
  val initRom = Module(new InitRom(IPN = IPN, symbolN = symbolN,
                                   imgWidth = imgWidth, initFile = imagePath))

  // debouncer
  val debouncer = Module(new Debounce())

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

  println("Generating the template files")

  saveTemplates(32, 128)
  val templatePath = "templates/template" // "_i.hex omitted (generated within modules).

  println("Generating the input files")

  saveInputsToHex(32, 128)
  val imagePath = "templates/mnist_input.hex"

  println("Generating hardware")

  emitVerilog(new TopWrapper( imgWidth      = 32,
                              TPN           = 10,
                              IPN           = 10,
                              symbolN       = 10,
                              templatePath  = templatePath,
                              imagePath     = Some(imagePath)
  ), Array("--target-dir", "generated"))
}