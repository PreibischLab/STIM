# STIM - the Spatial Transcriptomics ImgLib2 Project

<img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/example-1.png" alt="Example rendering of calm-2, ptgds, mbp" width="295">

STIM is a framework for managing, storage, viewing, and processing spatial transcriptomics data, which builds on the powerful libraries Imglib2, N5, BigDataViewer and Fiji. It allows to efficiently access spatial transcriptomics data "classically" as values, or render them as images at arbitrary resolution. Latter allows to apply computer vision techniques to spatially resolved sequencing datasets. STIM highlights the potential by: 
 * efficient interactive rendering
 * image filtering framework for irregularly-spaced datasets
 * Alignment of spatial dataset slides using SIFT, RANSAC and ICP combined with global optimization.

A great example dataset is provided by the [SlideSeq paper](https://science.sciencemag.org/content/363/6434/1463.long) and can be downloaded from [here](https://portals.broadinstitute.org/single_cell/study/slide-seq-study). 


## Contents
1. [Installation Instructions](#Installation-Instructions)
2. [Resaving](#Resaving)
3. [Normalization](#Normalization)
4. [Iteractive Viewing Application](#Iteractive-Viewing-Application)
5. [Render images and view or save as TIFF](#Render-images-and-view-or-save-as-TIFF)
6. [View selected genes for an entire N5 as 2D/3D using BigDataViewer](#View-selected-genes-for-an-entire-N5-as-2D-or-3D-using-BigDataViewer)
7. [Alignment of 2D slices](#Alignment-of-2D-slices)
   1. [Pairwise Alignment](#Pairwise-Alignment)
   2. [View Pairwise Alignment](#View-Pairwise-Alignment)
   3. [Global Optimization and ICP refinement](#Global-Optimization-and-ICP-refinement)

## Installation Instructions

Installation requires maven and OpenJDK8 (or newer) on Ubuntu:
```bash
sudo apt-get install openjdk-8-jdk maven
```
On other platforms, please find your way and report back if interested.

Next, please check out this repository and go into the folder

```
git clone https://github.com/PreibischLab/imglib2-st.git
cd imglib2-st
```

Install into your favorite local binary `$PATH` (or leave empty for using the checked out directory):
```bash
./install $HOME/bin
```
All dependencies will be downloaded and managed by maven automatically.

This currently installs several tools, `st-resave, st-normalize, st-view, st-render`.

## Resaving
Resave (compressed) textfiles to the N5 format (and optionally `--normalize`) using
```bash
./st-resave \
     -o '/path/directory.n5' \
     -i '/path/locations.csv,/path/reads.csv,name' \
     -i '/Puck_180528_20.tar/BeadLocationsForR.csv,/Puck_180528_20.tar/MappedDGEForR.csv,Puck_180528_20' \
     -i ...
     [--normalize]
```
If the n5 directory exists new datasets will be added (example above:`name`, `Puck_180528_20`), otherwise a new n5 will be created. Each input consists of a `locations.csv` file, a `reads.csv` file, and a user-defined `dataset name`. The csv files can optionally be inside (zip/tar/tar.gz) files. It is tested on the slide-seq data linked above, which can be used as a blueprint for how to save one's own data for import.

_Optionally_, cell type predictions can be imported as part of the resaving step, in this case each input consists of **four entries**, `locations.csv` file, a `reads.csv` file, **a `celltypes.csv` file**,and a user-defined `dataset name`. Please note that missing barcodes in celltypes.csv will be excluded from the dataset. This way you can filter locations with bad expression values.

_Optionally_, the datasets can be directly log-normalized before resaving (recommended). The **locations file** should contain a header for `barcode (id), xcoord and ycoord`, followed by the entries:
```
barcodes,xcoord,ycoord
TCACGTAGAAACC,3091.01234567901,2471.88888888889
TCTCCTAGTTCGG,4375.91791044776,1577.52985074627
...
```
The **reads file** should contain all `barcodes (id)` as the header after a `Row` column that holds the gene identifier. It should have as many columns as there are sequenced locations (ids from above):
```
Row,TCACGTAGAAACC,TCTCCTAGTTCGG, ...
0610005C13Rik,0,0, ...
0610007P14Rik,0,0, ...
...
```
Note: if there is a mismatch between number of sequenced locations defined in the locations.csv (rows) with the locations in reads.csv (columns), the resave will stop.

The _optional_ **celltypes file** should contain all `barcodes (id)` and `celltype id` (integer numbers) as a header:
```
barcodes,celltype
TCACGTAGAAACC,28
TCTCCTAGTTCGG,1
ACCGTCTGAATTC,40
...
```

## Normalization
You can run the normalization also independently after resaving. The tool can resave selected or all datasets of an N5 container into the same or a new N5:
```bash
./st-normalize \
     -i '/path/input.n5' \
     [-o '/path/output.n5'] \
     [-d 'dataset1,dataset2'] \
     [-e 'dataset1-normed,dataset2-normed']
```
The only parameter you have to provide is the input N5 `-i`. You can optionally define an output N5 `-o` (otherwise it'll be the same), select specific input dataasets within the input N5 `-d`, and use user-defined names for the normalized datasets `-e` (by default it will be `inputname-norm`).

## Iteractive Viewing Application
Run the interactive viewer as follows
```bash
./st-explorer \
     -i '/path/directory.n5' \
     [-d 'Puck_180528_20,Puck_180528_22'] \
     [-c '0,255']
```
It allows you to browse the data in realtime for all genes and datasets. If data is registered it will automatically use the transformations that are stored in the N5 metadata to properly overlay individual datasets. The optional switch `-d` allows you to select a subset of datasets from a N5, and using `-c` allows to preset the BigDataViewer intensity range.

## Render images and view or save as TIFF
In order to render images of spatial sequencing datasets (can be saved as TIFF or displayed on screen using ImageJ) please run
```bash
./st-render \
     -i '/path/directory.n5' \
     -g 'Calm2,Hpca,Ptgds' \
     [-o '/path/exportdir'] \
     [-d 'Puck_180528_20,Puck_180528_22'] \
     [-s 0.1] \
     [-f] \
     [-m 20] \
     [-sf 2.0] \
     [-b 50]
```
If you only define the N5 path `-i` and one or more genes `-g`, the rendered image will be displayed as an ImageJ image. If a N5 contains more than one dataset, they will be rendered as 3D image. When defining an output directory `-o` images will not be displayed, but saved as TIFF (stacks) into the directory with filenames corresponding to the gene name. The optional switch `-d` allows you to select a subset of datasets from a N5, `-s` scales the rendering (default: 0.05), `-f` enables a single-spot filter (default: off), `-m` applies median filtering in locations space (not on top of the rendered image) with a certain radius (default: off), `-sf` sets the smoothness factor for rendering of the sparse dataset, and `-b` sets the size of an extra black border around the location coordinates (default: 20).

## View selected genes for an entire N5 as 2D or 3D using BigDataViewer
In order to interactively browse the 2D/3D space of one or more datasets of an N5 with BigDataViewer you can
```bash
./st-bdv-view \
     -i '/path/directory.n5' \
     -g Calm2,Hpca \
     [-md 'celltype']
     [-d 'Puck_180528_20,Puck_180528_22'] \
     [-z 5.0] \
     [-c '0,255'] \
     [-f] \
     [-m 20] \
     [-sf 2.0] \
```
Dataset(s) from the selected N5 `-i` will be interactively rendered for one or more selected gene `-g` (multiple genes will be overlaid into different colors). The switch `-md` will overlay for example celltype annotations. By default all datasets will be displayed, but they can be limited (or ordered) using `-d`. You can define the distance between sections with `-z` (as a factor of median spacing between sequenced locations), `-c` allows to preset the BigDataViewer intensity range and parameters `-f, -m, -sf` are explained above (4).

## Alignment of 2D slices

The alignment of 2D slices of a 3D volume is a two-step process. At first, using **`st-align-pairs`** slices will be aligned pairwise (e.g. 1<sup>st</sup> vs 2<sup>nd</sup>, 1<sup>st</sup> vs 3<sup>rd</sup>, and so on ...) using the Scale Invariant Feature Transform (SIFT) on a set of genes. These pairwise alignments can _optionally_ be viewed and confirmed using **`st-align-pairs-view`**. Finally, a globally optimal model for each slide will computed using **`st-align-global`**, which supports a refinement using Iterative Closest Point (ICP) matching.

### Pairwise Alignment

The pairwise alignment uses SIFT to align pairs of 2d slices. _**Important note:** the order of the datasets as they are passed into the program is crucial as it determines which slices are next to each other. If not specified, they are used in the order as stored in the JSON file inside the N5 container._ The 2d alignment can be called as follows, the resulting transformations and corresponding points are automatically stored in the N5:
```bash
./st-align-pairs \
     -i '/path/directory.n5' \
     [-d 'Puck_180528_20,Puck_180528_22'] \
     [-r 2] \
     [-g 'Calm2,Hpca'] \
     [-n 100] \
     [-s 0.05] \
     [-sf 4.0] \
     [--overwrite] \
     [-e 250.0] \
     [--minNumInliersGene 5] \
     [--minNumInliers 30] \
     [--renderingGene Calm2] \
     [--hidePairwiseRendering] \

```
Datasets from the selected N5 `-i` will be aligned in pairs. Datasets and their ordering can be optionally defined using `-d`, otherwise all datasets will be used in the order as defined in the N5 container. The comparison range (±slices to be aligned) can be defined using `-r`, by default it is set to 2. Genes to be used can be specified manually using `-g`, or a specified number of genes `-n` with the highest standard deviation in the expression signal will be used. By default, 100 genes will be automatically selected.

The images used for alignment are rendered as in the viewing programs above. The scaling of the images can be changed using `-s` (default: 0.05 or 5%), and the smoothness factor can be changed using `-sf` (default: 4.0). If a registration was run before, the application will quit. Previous results can be overwritten using `--overwrite`.

The alignment itself has more paramters that can be adjusted. The maximal error (default 250.0) for the RANSAC matching in SIFT can be adjusted using `-e`, the minimally required number of RANSAC inliers per tested gene can be changed using `--minNumInliersGene` (default: 5), and the minimal number of inliers over all genes can be adjusted using `--minNumInliers` (default: 30).

The results of the alignment will be shown by default using a gene (selected automatically or defined via `--renderingGene`, which can be deactivated using `--hidePairwiseRendering`. 



### View Pairwise Alignment

### Global Optimization and ICP refinement
