package bram

import chisel3._
import chisel3.util._

/**
 * BramMem - Core block RAM memory module
 *
 * This module creates a synchronous read memory that should map to BRAM on FPGA.
 *
 * @param depth Number of addressable locations (e.g., number of image lines)
 * @param width Bits per location (e.g., pixels per line)
 * @param initFile Optional memory initialization file path (.hex format)
 *
 * Note on BRAM inference:
 * - Xilinx BRAM blocks are minimum 18Kb (RAMB18E1) or 36Kb (RAMB36E1)
 * - For small memories like 24x24 bits (576 bits), synthesis may use LUTs instead
 */
class BramMem(
   val depth: Int,
   val width: Int,
   val initFile: Option[String] = None) extends Module {

  require(depth > 0, "Depth must be positive")
  require(width > 0, "Width must be positive")
  // require(isPow2(depth), "Depth should be power of 2 for optimal BRAM utilization")  TODO: THIS FAILS FOR 24x24 and 28x28 - Smarter solution or remove?
  if (!isPow2(depth)) println(s"[BramMem] WARNING: Depth ${depth} is not power of 2 - may impact BRAM utilization")

  val addrWidth = log2Ceil(depth)

  val io = IO(new Bundle {
  val addr   = Input(UInt(addrWidth.W))
  val en     = Input(Bool())
  val wrEn   = Input(Bool())
  val wrData = Input(UInt(width.W))
  val rdData = Output(UInt(width.W))
  })

  // ================= MEMORY INSTANTIATION AND INITIALIZATION ===========================

  // SyncReadMem should map to BRAM. Synchronous read -> Next cycle data available.
  val mem = SyncReadMem(depth, UInt(width.W))

  // Initialize memory from file
  initFile match {
  case Some(file) =>
  // Chisel supports loading from hex/bin files
  // loadMemoryFromFile(mem, file)
  // Note: Actual loading depends on your synthesis tool support
  println(s"[BramMem] Init file specified: $file (manual loading may be required)")
  case None =>
  println(s"[BramMem] No init file - memory will be uninitialized")
  }

  // Print configuration info
  println(s"[BramMem] Configured: ${depth} x ${width}-bit = ${depth * width} bits total")
  if (depth * width < 1024) {
  println(s"[BramMem] WARNING: Memory size (${depth * width} bits) may be too small for BRAM inference")
  println(s"[BramMem]          Consider increasing size or checking synthesis reports")
  }

  // ========================= MEMORY INTERFACE ==========================================

  // Write
  when(io.en && io.wrEn) {
  mem.write(io.addr, io.wrData)
  }

  // Read
  io.rdData := 0.U
  when(io.en && !io.wrEn) {
  io.rdData := mem.read(io.addr)
  }

  // ======================================================================================
}

/**
 * BramMemWrapper - High-level wrapper for template storage
 *
 * Provides a clean interface for reading image templates line-by-line. Handles address generation.
 *
 * @param numLines Number of lines in the image (height)
 * @param lineWidth Number of bits per line (width)
 * @param initFile Optional initialization file
 */
class BramMemWrapper(
    val numLines: Int,
    val lineWidth: Int,
    val initFile: Option[String] = None
  ) extends Module {

val addrWidth = log2Ceil(numLines)

val io = IO(new Bundle {
// Simple read interface
val lineAddr = Input(UInt(addrWidth.W))
val lineEn   = Input(Bool())
val lineData = Output(UInt(lineWidth.W))

// Write interface (for initialization/updates), probably not needed if initialized from file.
val wrEn     = Input(Bool())
val wrAddr   = Input(UInt(addrWidth.W))
val wrData   = Input(UInt(lineWidth.W))
})

// Instantiate the core BRAM module
val bramCore = Module(new BramMem(numLines, lineWidth, initFile))

// Connect write path
bramCore.io.wrEn   := io.wrEn
bramCore.io.wrData := io.wrData

// Connect read/address path
// Priority: write address when writing, read address otherwise
bramCore.io.addr := Mux(io.wrEn, io.wrAddr, io.lineAddr)
bramCore.io.en   := io.lineEn || io.wrEn

// Connect output
io.lineData := bramCore.io.rdData

}

/**
 * MultiTemplateBram - Example of how to instantiate multiple template memories
 *
 * This shows how to create parallel template memories for concurrent matching.
 *
 * @param numTemplates Number of parallel template memories
 * @param numLines Lines per template (image height)
 * @param lineWidth Bits per line (image width)
 * @param initFiles Optional list of init files (one per template), or None
 */
class MultiTemplateBram(
   val numTemplates: Int,
   val numLines: Int,
   val lineWidth: Int,
   val initFiles: Option[Seq[String]] = None
 ) extends Module {

  val addrWidth = log2Ceil(numLines)

  val io = IO(new Bundle {
    // Read interface - one to all
    val lineAddr = Input(UInt(addrWidth.W))
    val lineEn   = Input(Bool())
    val lineData = Output(Vec(numTemplates, UInt(lineWidth.W)))

    // Write interface - one to one (specific).
    val wrEn       = Input(Bool())
    val wrTemplate = Input(UInt(log2Ceil(numTemplates).W))
    val wrAddr     = Input(UInt(addrWidth.W))
    val wrData     = Input(UInt(lineWidth.W))
  })

  // Instantiate multiple template memories with optional init files. Number of templates given by numTemplates.
  val templates = initFiles match {

    // If files are given. Check correct number of files. Assign each file to each mem template by order of input.
    case Some(files) =>
      require(files.length == numTemplates, s"Expected ${numTemplates} init files, got ${files.length}")
      files.zipWithIndex.map{
        case (file, idx) =>
          println(s"[MultiTemplateBram] Template ${idx} init file: ${file}")
          Module(new BramMemWrapper(numLines, lineWidth, Some(file)))
      }

    // If no files are given, then no data put in memories at initialization.
    case None =>
      Seq.fill(numTemplates)(Module(new BramMemWrapper(numLines, lineWidth, None)))
  }

  // Connect all templates
  for ((templateInstance, idx) <- templates.zipWithIndex) {
    // Read path - same address to all
    templateInstance.io.lineAddr := io.lineAddr
    templateInstance.io.lineEn   := io.lineEn
    io.lineData(idx)             := templateInstance.io.lineData // idx only really used here.

    // Write path - only to selected template
    templateInstance.io.wrEn   := io.wrEn && (io.wrTemplate === idx.U) // idx also used here for guiding write.
    templateInstance.io.wrAddr := io.wrAddr
    templateInstance.io.wrData := io.wrData
  }

  println(s"[MultiTemplateBram] Created ${numTemplates} parallel template memories")
  println(s"[MultiTemplateBram] Each: ${numLines} x ${lineWidth} bits = ${numLines * lineWidth} bits")
  println(s"[MultiTemplateBram] Total memory: ${numTemplates * numLines * lineWidth} bits")
}


// Instantiation for testing
object BramMem24 extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using 24x24 configuration
  println("\n=== (100 templates, 24x24, padding removed) ===")
  println(s"Config: ${BramConfig.MnistPaddingRemoved.totalTemplates} templates, " +
    s"${BramConfig.MnistPaddingRemoved.imageHeight}×${BramConfig.MnistPaddingRemoved.imageWidth}")
  emitVerilog(new ConfiguredBrams.t24x100(), Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!")
}

// Instantiation for testing
object BramMem28 extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using 28x28 configuration
  println("\n=== (100 templates, 28x28, standard) ===")
  println(s"Config: ${BramConfig.MnistStandard.totalTemplates} templates, " +
    s"${BramConfig.MnistStandard.imageHeight}×${BramConfig.MnistStandard.imageWidth}")
  emitVerilog(new ConfiguredBrams.t28x100(), Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!")
}

// Instantiation for testing
object BramMem32 extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using 32x32 configuration
  println("\n=== (100 templates, 32x32, padding added (better for BRAM?) ) ===")
  println(s"Config: ${BramConfig.MnistPaddingAdded.totalTemplates} templates, " +
    s"${BramConfig.MnistPaddingAdded.imageHeight}×${BramConfig.MnistPaddingAdded.imageWidth}")
  emitVerilog(new ConfiguredBrams.t32x100(), Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!")
}

// Instantiation for testing ( uses 28x28  )
object BramMemFromFiles extends App {
  println("Generating BramMem modules")
  println("=" * 80)

  // Using standard config WITH init files
  println("\n=== Standard MNIST with init files ===")
  val templateFiles = (0 until BramConfig.MnistStandard.totalTemplates)
    .map(i => s"templates/template_${i}.hex")
  emitVerilog(new ConfiguredBrams.t28x100(Some(templateFiles)),
    Array("--target-dir", "generated"))

  println("\n" + "=" * 80)
  println("Verilog generation complete!")
}