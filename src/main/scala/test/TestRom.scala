// Put this in a new file: src/main/scala/test/TestRom.scala
package test

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

class TestRom extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(5.W))
    val data = Output(UInt(32.W))
  })

  val mem = SyncReadMem(32, UInt(32.W))
  loadMemoryFromFileInline(mem, "templates/template_0.hex")

  io.data := mem.read(io.addr)
}

object TestRom extends App {

  println(System.getProperty("user.dir"))

  emitVerilog(new TestRom, Array("--target-dir", "generated"))
}