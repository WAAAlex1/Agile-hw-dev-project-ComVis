#!/bin/bash

shopt -s nullglob
declare -A counters

echo "--- Starting Rename (Dry Run) ---"

for file in template*.bmp; do

    # Regex checks for: template(digits)_(digits).bmp
    if [[ "$file" =~ ^template([0-9]+)_([0-9]+)\.bmp$ ]]; then
        
        # Capture 'y' from the filename
        y="${BASH_REMATCH[2]}"
        
        # 1. Get the current count for this 'y'
        # ":-0" ensures that if it's the first time (null), it sets n to 0.
        n="${counters[$y]:-0}"
        
        # 2. Increment the counter for the NEXT time we see this 'y'
        ((counters[$y]++))
        
        # 3. Formulate the new name (e.g. template_1_0.bmp)
        new_name="template_${y}_${n}.bmp"
        
        echo "Renaming '$file' -> '$new_name'"
        
        # UNCOMMENT THE NEXT LINE TO ACTUALLY RENAME
         mv "$file" "$new_name"
        
    else
        echo "Skipping '$file' (does not match pattern)"
    fi
done
