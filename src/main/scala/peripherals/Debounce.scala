import chisel3._
import chisel3.util._

class Debounce extends Module {
  val io = IO(new Bundle {
    val btn    = Input(Bool())
    val stable = Output(Bool())
  })

  val btnSync = RegNext(RegNext(io.btn))

  val fac = 100000000 / 100

  val btnDebReg = RegInit(false.B)

  val cntReg = RegInit(0.U(32.W))
  val tick   = cntReg === (fac - 1).U

  cntReg := cntReg + 1.U
  when(tick) {
    cntReg    := 0.U
    btnDebReg := btnSync
  }

  io.stable := btnDebReg
}