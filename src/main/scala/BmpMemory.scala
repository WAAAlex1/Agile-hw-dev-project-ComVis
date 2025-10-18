import chisel3._
import chisel3.util._

class BmpMemory(val data: Seq[Int], val width: Int) extends Module {
  val io = IO(new Bundle {
    val out = Output(Vec(data.length, UInt(width.W)))
  })

  // Convert the Scala Seq[Int] into a hardware Vec[UInt]
  io.out := VecInit(data.map(_.U(width.W)))
}

