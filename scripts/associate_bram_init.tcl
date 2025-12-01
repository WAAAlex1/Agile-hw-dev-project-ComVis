# Associate COE files with template BRAMs
# Run this in Vivado TCL console after opening your project

# Configuration
set numDigits 10
set TPN 10
set totalTemplates [expr {$numDigits * $TPN}]

# Path to COE files
set coe_dir "../generated/coe"

puts "\n=========================================="
puts "Associating COE files with template BRAMs"
puts "COE directory: $coe_dir"
puts "Total templates: $totalTemplates"
puts "=========================================="

set success_count 0
set error_count 0

for {set i 0} {$i < $totalTemplates} {incr i} {
    # Calculate digit and template number from linear index
    set digit [expr {$i / $TPN}]
    set template [expr {$i % $TPN}]

    # Find the BRAM instance - matches actual Vivado naming
    set bram_cells [get_cells -quiet -hierarchical -filter "NAME =~ comvis/bram/templateBram_${i}/bramCore/mem_ext/Memory_reg"]

    if {[llength $bram_cells] > 0} {
        set coe_file "${coe_dir}/template_${digit}_${template}.coe"

        # Check if COE file exists
        if {[file exists $coe_file]} {
            set_property INIT_FILE $coe_file $bram_cells
            puts "  SUCCESS: templateBram_$i (digit=$digit, template=$template): [file tail $coe_file]"
            incr success_count
        } else {
            puts "  ERR: templateBram_$i: COE file not found: $coe_file"
            incr error_count
        }
    } else {
        puts "  ERR: templateBram_$i: BRAM cell not found in design"
        incr error_count
    }
}

puts "\n=========================================="
puts "Summary:"
puts "  Success: $success_count BRAMs initialized"
puts "  Errors:  $error_count"
puts "=========================================="

if {$error_count > 0} {
    puts "\nWARNING: Some BRAMs were not initialized!"
} else {
    puts "\n✓ All template BRAMs successfully associated with COE files!"
    puts "Run synthesis to initialize BRAMs with template data."
}