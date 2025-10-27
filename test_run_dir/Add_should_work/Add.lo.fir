FIRRTL version 1.2.0
circuit Add :
  module Add :
    input clock : Clock
    input reset : UInt<1>
    input io_a : UInt<8> @[\\src\\main\\scala\\Add.scala 14:14]
    input io_b : UInt<8> @[\\src\\main\\scala\\Add.scala 14:14]
    output io_c : UInt<8> @[\\src\\main\\scala\\Add.scala 14:14]

    reg reg : UInt<8>, clock with :
      reset => (UInt<1>("h0"), reg) @[\\src\\main\\scala\\Add.scala 20:20]
    node _reg_T = add(io_a, io_b) @[\\src\\main\\scala\\Add.scala 21:15]
    node _reg_T_1 = tail(_reg_T, 1) @[\\src\\main\\scala\\Add.scala 21:15]
    io_c <= reg @[\\src\\main\\scala\\Add.scala 23:8]
    reg <= mux(reset, UInt<8>("h0"), _reg_T_1) @[\\src\\main\\scala\\Add.scala 20:{20,20} 21:7]
