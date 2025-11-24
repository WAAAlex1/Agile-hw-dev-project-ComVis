package comvis

import chisel3._
import chisel3.util._

class MaskIn extends Bundle {
  val start = Input(Bool())
}

class MemOut(val imgWidth: Int, val TPN: Int, val symbolN: Int) extends Bundle {
  val imgData = Output(UInt(imgWidth.W))

  // We need one template word for each template for each symbol.
  val templateData = Output(Vec(symbolN, Vec(TPN, UInt(imgWidth.W))))
}

class MemIn(val addrWidth: Int) extends Bundle {
  val rdEn      = Input(Bool())
  val rdAddrIdx = Input(UInt(addrWidth.W))
}

class MemWrite(val addrWidth: Int, val imgWidth: Int) extends Bundle {
  val wrEn   = Input(Bool())
  val wrAddr = Input(UInt(addrWidth.W))
  val wrData = Input(UInt(imgWidth.W))
}

class conAccIn(val imgWidth: Int, val TPN: Int, val symbolN: Int) extends Bundle {
  val valid = Input(Bool())
  val done  = Input(Bool())

  // We need One vec of template results for each symbol so a nested Vec.
  // Width needs to be log2(imgwidth^2)=11 bits for 32 width for the confidence of each template.
  val sliceConf = Input(Vec(symbolN, Vec(TPN, UInt(log2Up(imgWidth * imgWidth).W))))
}

class evalIn(val imgWidth: Int, val TPN: Int, val symbolN: Int) extends Bundle {
  val valid = Input(Bool())

  // This should give 14bitwidth for standard parameter values.
  val confScore = Input(Vec(symbolN, UInt(log2Up((imgWidth * imgWidth) * TPN).W)))
}
