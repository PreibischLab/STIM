#!/bin/bash

# Compare transformations in a pairwise way

for A in automatic SP NK DLP MI; do
	for B in automatic SP NK DLP MI; do
		st-compare -c openst.n5 -b transformations-${A}.dat -t transformations-${B}.dat -o compare-${A}-${B}.csv
	done
done

