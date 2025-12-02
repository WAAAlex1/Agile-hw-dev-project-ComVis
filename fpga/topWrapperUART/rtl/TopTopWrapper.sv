module TopTopWrapper (
    // Raw clock input (for XDC legacy reasons)
    input  wire       clock,
    
    // Reset and control
    input  wire       reset,
    input  wire       io_start,
    
    // Outputs
    output wire       io_done,
    output wire [7:0] io_anodes,
    output wire [7:0] io_cathodes,
    
    //uart
    input wire       io_rx,
    output wire [7:0] io_led
);

    // Internal signals from clocking wizard
    wire clk_out;      // Generated clock from wizard
    wire locked;       // PLL lock indicator
    
    // Synchronized reset
    wire system_reset;
    
    // ========================================
    // Clock Wizard Instantiation
    // ========================================
    clk_wiz_0 clock_wizard (
        // Clock in ports
        .clk_in1  (clock),      // Raw input clock
        
        // Clock out ports  
        .clk_out1 (clk_out),    // Generated system clock
        
        // Status and control signals
        .reset    (reset),      // Input reset
        .locked   (locked)      // PLL locked indicator
    );
    
    // ========================================
    // Reset Synchronization
    // ========================================
    // Hold system in reset until PLL is locked
    // and synchronize reset to generated clock domain
    reg [2:0] reset_sync;
    
    always @(posedge clk_out or negedge locked) begin
        if (!locked) begin
            reset_sync <= 3'b111;
        end else begin
            reset_sync <= {reset_sync[1:0], reset};
        end
    end
    
    assign system_reset = reset_sync[2];
    
    // ========================================
    // TopWrapper Instantiation
    // ========================================
    TopWrapperUART top_wrapper (
        // Clock and reset (using generated clock)
        .clock        (clk_out),
        .reset        (system_reset),
        .io_rx        (io_rx),
        // Control inputs
        .io_start     (io_start),
        
        // Outputs
        .io_done      (io_done),
        .io_anodes    (io_anodes),
        .io_cathodes  (io_cathodes),
        .io_led       (io_led)
    );

endmodule