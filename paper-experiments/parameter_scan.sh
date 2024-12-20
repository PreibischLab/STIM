#!/bin/bash


# Array of parameter values
scales=(0.01 0.025 0.05)
render_factors=(0.5 0.75 1.0 1.5 2.0 3.0)

# Loop through each parameter
for scale in "${scales[@]}"; do
	for render_factor in "${render_factors[@]}"; do
		# Remove the decimal point and format
		scale_f=$(printf %.3f "$scale" | sed 's/\.//g')
		rf_f=$(printf %.2f "$render_factor" | sed 's/\.//g')

		# Construct the folder name
		folder_name="scale${scale_f}_rf${rf_f}"

		# Create the folder
		#mkdir -p "$folder_name"
		echo "Created folder: $folder_name"

		# Add (link) slices to folder
		st-add-slice -c "${folder_name}" -i open-st.n5/GSM7990116_metastatic_lymph_node_S33.h5ad
		st-add-slice -c "${folder_name}" -i open-st.n5/GSM7990117_metastatic_lymph_node_S34.h5ad

		# Align slices
		st-align-pairs \
			-c "${folder_name}" \
			-d GSM7990116_metastatic_lymph_node_S33.h5ad,GSM7990117_metastatic_lymph_node_S34.h5ad \
			-s "${scale}" \
			-rf "${render_factor}" \
			--rendering Gauss \
			-n 100 \
			--numThreads 64 \
			--hidePairwiseRendering
	done
done

