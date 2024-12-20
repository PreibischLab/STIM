#!/bin/bash

# download and extract to local folder
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990100/suppl/GSM7990100_metastatic_lymph_node_S2.h5ad.gz | gunzip -c > GSM7990100_metastatic_lymph_node_S2.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990101/suppl/GSM7990101_metastatic_lymph_node_S3.h5ad.gz | gunzip -c > GSM7990101_metastatic_lymph_node_S3.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990102/suppl/GSM7990102_metastatic_lymph_node_S4.h5ad.gz | gunzip -c > GSM7990102_metastatic_lymph_node_S4.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990103/suppl/GSM7990103_metastatic_lymph_node_S5.h5ad.gz | gunzip -c > GSM7990103_metastatic_lymph_node_S5.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990104/suppl/GSM7990104_metastatic_lymph_node_S6.h5ad.gz | gunzip -c > GSM7990104_metastatic_lymph_node_S6.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990105/suppl/GSM7990105_metastatic_lymph_node_S7.h5ad.gz | gunzip -c > GSM7990105_metastatic_lymph_node_S7.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990106/suppl/GSM7990106_metastatic_lymph_node_S9.h5ad.gz | gunzip -c > GSM7990106_metastatic_lymph_node_S9.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990107/suppl/GSM7990107_metastatic_lymph_node_S11.h5ad.gz | gunzip -c > GSM7990107_metastatic_lymph_node_S11.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990108/suppl/GSM7990108_metastatic_lymph_node_S17.h5ad.gz | gunzip -c > GSM7990108_metastatic_lymph_node_S17.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990109/suppl/GSM7990109_metastatic_lymph_node_S18.h5ad.gz | gunzip -c > GSM7990109_metastatic_lymph_node_S18.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990110/suppl/GSM7990110_metastatic_lymph_node_S19.h5ad.gz | gunzip -c > GSM7990110_metastatic_lymph_node_S19.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990111/suppl/GSM7990111_metastatic_lymph_node_S23.h5ad.gz | gunzip -c > GSM7990111_metastatic_lymph_node_S23.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990112/suppl/GSM7990112_metastatic_lymph_node_S24.h5ad.gz | gunzip -c > GSM7990112_metastatic_lymph_node_S24.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990113/suppl/GSM7990113_metastatic_lymph_node_S25.h5ad.gz | gunzip -c > GSM7990113_metastatic_lymph_node_S25.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990114/suppl/GSM7990114_metastatic_lymph_node_S26.h5ad.gz | gunzip -c > GSM7990114_metastatic_lymph_node_S26.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990115/suppl/GSM7990115_metastatic_lymph_node_S28.h5ad.gz | gunzip -c > GSM7990115_metastatic_lymph_node_S28.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990116/suppl/GSM7990116_metastatic_lymph_node_S33.h5ad.gz | gunzip -c > GSM7990116_metastatic_lymph_node_S33.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990117/suppl/GSM7990117_metastatic_lymph_node_S34.h5ad.gz | gunzip -c > GSM7990117_metastatic_lymph_node_S34.h5ad
wget -O - ftp://ftp.ncbi.nlm.nih.gov/geo/samples/GSM7990nnn/GSM7990118/suppl/GSM7990118_metastatic_lymph_node_S36.h5ad.gz | gunzip -c > GSM7990118_metastatic_lymph_node_S36.h5ad

# add to container called open-st.n5
st-add-slice -m -i GSM7990100_metastatic_lymph_node_S2.h5ad -c open-st.n5
st-add-slice -m -i GSM7990101_metastatic_lymph_node_S3.h5ad -c open-st.n5
st-add-slice -m -i GSM7990102_metastatic_lymph_node_S4.h5ad -c open-st.n5
st-add-slice -m -i GSM7990103_metastatic_lymph_node_S5.h5ad -c open-st.n5
st-add-slice -m -i GSM7990104_metastatic_lymph_node_S6.h5ad -c open-st.n5
st-add-slice -m -i GSM7990105_metastatic_lymph_node_S7.h5ad -c open-st.n5
st-add-slice -m -i GSM7990106_metastatic_lymph_node_S9.h5ad -c open-st.n5
st-add-slice -m -i GSM7990107_metastatic_lymph_node_S11.h5ad -c open-st.n5
st-add-slice -m -i GSM7990108_metastatic_lymph_node_S17.h5ad -c open-st.n5
st-add-slice -m -i GSM7990109_metastatic_lymph_node_S18.h5ad -c open-st.n5
st-add-slice -m -i GSM7990110_metastatic_lymph_node_S19.h5ad -c open-st.n5
st-add-slice -m -i GSM7990111_metastatic_lymph_node_S23.h5ad -c open-st.n5
st-add-slice -m -i GSM7990112_metastatic_lymph_node_S24.h5ad -c open-st.n5
st-add-slice -m -i GSM7990113_metastatic_lymph_node_S25.h5ad -c open-st.n5
st-add-slice -m -i GSM7990114_metastatic_lymph_node_S26.h5ad -c open-st.n5
st-add-slice -m -i GSM7990115_metastatic_lymph_node_S28.h5ad -c open-st.n5
st-add-slice -m -i GSM7990116_metastatic_lymph_node_S33.h5ad -c open-st.n5
st-add-slice -m -i GSM7990117_metastatic_lymph_node_S34.h5ad -c open-st.n5
st-add-slice -m -i GSM7990118_metastatic_lymph_node_S36.h5ad -c open-st.n5

# automatic alignment
st-align-pairs -c open-st.n5 -n 100 --scale 0.025 -rf 2.42
st-align-global -c open-st.n5 --lambda 0.1 --skipICP --skipDisplayResults
st-extract-transformations -c open-st.n5 -o transformations-automatic.dat

# extract transformations and compute alignment quality of parameter scan
# this expects that parameter_scan.sh is run and all output was stored to 'similarity-model/'
st-extract-pairwise -c open-st.n5 -o comparison-parameter-scan.csv -i similarity-model/ -b transformations-SP.dat
