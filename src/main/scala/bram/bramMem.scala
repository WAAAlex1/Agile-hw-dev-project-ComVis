package bram

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

import scala.io.Source

import comvis._
/** BramMem - Core block RAM memory module
 *
 * @param depth Number of addressable locations
 * @param width Bits per location
 * @param initFile Optional memory initialization file path (.hex format)
 * @param debug Enable debug output
 * @param uniqueId Unique identifier to prevent deduplication
 * @param useRomForInit If true, use ROM implementation for initialized memories (synthesis-friendly)
 *                      If false, use SyncReadMem with loadMemoryFromFileInline (simulation-only init)
 */
class BramMem(
               val depth: Int,
               val width: Int,
               val debug: Boolean = false,
               val uniqueId: Int = 0
             ) extends Module {

  require(depth > 0, "Depth must be positive")
  require(width > 0, "Width must be positive")

  if (debug && !isPow2(depth)) println(s"[BramMem] WARNING: Depth ${depth} is not power of 2")

  val addrWidth = log2Ceil(depth)
  override def desiredName = s"BramMem_$uniqueId"

  val io = IO(new Bundle {
    val addr   = Input(UInt(addrWidth.W))
    val en     = Input(Bool())
    val wrEn   = Input(Bool())
    val wrData = Input(UInt(width.W))
    val rdData = Output(UInt(width.W))
  })

  // Simple SyncReadMem - no initialization, but writable
  val mem = SyncReadMem(depth, UInt(width.W))

  // Write
  when(io.en && io.wrEn) {
    mem.write(io.addr, io.wrData)
  }

  // Read
  io.rdData := 0.U
  when(io.en && !io.wrEn) {
    io.rdData := mem.read(io.addr)
  }

  if (debug) println(s"[BramMem $uniqueId] Created ${depth} x ${width}-bit SyncReadMem")
}

/** BramMemWrapper - High-level wrapper for template storage
  *
  * Provides a clean interface for reading image templates line-by-line. Handles address generation.
  *
  * @param numLines
  *   Number of lines in the image (height)
  * @param lineWidth
  *   Number of bits per line (width)
  * @param initFile
  *   Optional initialization file
  * @param debug
  *   true for debug printing
  * @param uniqueId
  *   Integer as uniqueId for generation
  * @param useRomForInit
  *   true = ROM, false = syncReadMem BRAM
  */
class BramMemWrapper(
                      val numLines: Int,
                      val lineWidth: Int,
                      val debug: Boolean = false,
                      val uniqueId: Int = 0
                    ) extends Module {

  override def desiredName = s"BramMemWrapper_$uniqueId"

  val addrWidth = log2Ceil(numLines)

  val io = IO(new Bundle {
    val lineAddr = Input(UInt(addrWidth.W))
    val lineEn   = Input(Bool())
    val lineData = Output(UInt(lineWidth.W))
    val wrEn     = Input(Bool())
    val wrAddr   = Input(UInt(addrWidth.W))
    val wrData   = Input(UInt(lineWidth.W))
  })

  val bramCore = Module(new BramMem(numLines, lineWidth, debug, uniqueId))

  bramCore.io.wrEn   := io.wrEn
  bramCore.io.wrData := io.wrData
  bramCore.io.addr := Mux(io.wrEn, io.wrAddr, io.lineAddr)
  bramCore.io.en   := io.lineEn || io.wrEn
  io.lineData := bramCore.io.rdData
}

/** MultiTemplateBram - Example of how to instantiate multiple template memories
  *
  * This shows how to create parallel template memories for concurrent matching.
  *
  * @param numTemplates
  *   Number of parallel template memories
  * @param numLines
  *   Lines per template (image height)
  * @param lineWidth
  *   Bits per line (image width)
  * @param initFiles
  *   Optional list of init files (one per template), or None
  */
class MultiTemplateBram(
                         val TPN: Int,
                         val symbolN: Int,
                         val imgWidth: Int,
                         val initFiles: Option[Seq[String]] = None,
                         val debug: Boolean = false
                       ) extends Module {

  val addrWidth      = log2Ceil(imgWidth)
  val totalTemplates = TPN * symbolN
  val totalBrams     = totalTemplates + 1  // templates + image BRAM

  // Address bits needed to select which BRAM
  val bramSelWidth = log2Ceil(totalBrams)

  // Total write address: [BRAM select bits | line address bits]
  val totalWrAddrWidth = bramSelWidth + addrWidth

  val io = IO(new Bundle {
    val memIn    = new MemIn(addrWidth)
    val memWrite = new MemWrite(totalWrAddrWidth, imgWidth, totalBrams)  // Updated
    val memOut   = new MemOut(imgWidth, TPN, symbolN)
  })

  // Decode write address
  val wrBramSel = io.memWrite.wrAddr(totalWrAddrWidth - 1, addrWidth)  // Upper bits
  val wrLineAddr = io.memWrite.wrAddr(addrWidth - 1, 0)                 // Lower bits

  // Instantiate template BRAMs
  val templates = (0 until totalTemplates).map { idx =>
    if (debug) println(s"[MultiTemplateBram] Creating writable template BRAM ${idx}")
    Module(new BramMemWrapper(imgWidth, imgWidth, debug, idx))
  }

  // Instantiate image BRAM
  val imageBram = Module(new BramMemWrapper(imgWidth, imgWidth, debug, totalTemplates))

  // Connect write logic - each BRAM checks if it is selected
  for ((templateInstance, idx) <- templates.zipWithIndex) {
    templateInstance.io.lineAddr := io.memIn.rdAddrIdx
    templateInstance.io.lineEn   := io.memIn.rdEn

    // Write enabled only if this specific BRAM is selected
    templateInstance.io.wrEn   := io.memWrite.wrEn && (wrBramSel === idx.U)
    templateInstance.io.wrAddr := wrLineAddr
    templateInstance.io.wrData := io.memWrite.wrData

    val symbolIdx   = idx / TPN
    val templateIdx = idx % TPN
    io.memOut.templateData(symbolIdx)(templateIdx) := templateInstance.io.lineData
  }

  // Connect image BRAM (index = totalTemplates = 100)
  imageBram.io.lineAddr := io.memIn.rdAddrIdx
  imageBram.io.lineEn   := io.memIn.rdEn
  imageBram.io.wrEn     := io.memWrite.wrEn && (wrBramSel === totalTemplates.U)
  imageBram.io.wrAddr   := wrLineAddr
  imageBram.io.wrData   := io.memWrite.wrData
  io.memOut.imgData     := imageBram.io.lineData

  if (debug) println(s"[MultiTemplateBram] Created ${totalTemplates} writable template BRAMs + 1 image BRAM")
  if (debug) println(s"[MultiTemplateBram] Write address: ${totalWrAddrWidth} bits [${bramSelWidth} BRAM sel | ${addrWidth} line addr]")
}

// Instantiation for testing
object BramMem24 extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using 24x24 configuration
  println("\n=== (10 templates per digit, 10 digits, 24x24, padding removed) ===")
  println(
    s"Config: ${BramConfig.MnistPaddingRemoved.TPN} templates x " +
      s"${BramConfig.MnistPaddingRemoved.symbolN} symbols, " +
      s"${BramConfig.MnistPaddingRemoved.imgWidth}x${BramConfig.MnistPaddingRemoved.imgWidth}"
  )
  emitVerilog(new ConfiguredBrams.t24x100(), Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!") // maybe remove??
}

// Instantiation for testing
object BramMem28 extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using 28x28 configuration
  println("\n=== (10 templates per digit, 10 digits, 28x28, standard) ===")
  println(
    s"Config: ${BramConfig.MnistStandard.TPN} templates x " +
      s"${BramConfig.MnistStandard.symbolN} symbols, " +
      s"${BramConfig.MnistStandard.imgWidth}x${BramConfig.MnistStandard.imgWidth}"
  )
  emitVerilog(new ConfiguredBrams.t28x100(), Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!")
}

// Instantiation for testing
object BramMem32 extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using 32x32 configuration
  println("\n=== (10 templates per digit, 10 digits, 32x32, padding added for better BRAM) ===")
  println(
    s"Config: ${BramConfig.MnistPaddingAdded.TPN} templates x " +
      s"${BramConfig.MnistPaddingAdded.symbolN} symbols, " +
      s"${BramConfig.MnistPaddingAdded.imgWidth}x${BramConfig.MnistPaddingAdded.imgWidth}"
  )
  emitVerilog(new ConfiguredBrams.t32x100(), Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!")
}

// Instantiation for testing ( uses 28x28 )
object BramMemFromFiles extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using standard config WITH init files
  println("\n=== Standard MNIST with init files ===")
  val totalTemplates = BramConfig.MnistStandard.TPN * BramConfig.MnistStandard.symbolN
  val templateFiles = (0 until totalTemplates)
    .map(i => s"templates/template_${i}.hex")
  emitVerilog(new ConfiguredBrams.t28x100(Some(templateFiles)), Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!")
}
