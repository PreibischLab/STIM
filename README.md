# STIM - the Spatial Transcriptomics as Images Project

<img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/Projection low-res-rgb.gif" alt="Example rendering of calm-2, ptgds, mbp" width="280">

The **S**patial **T**ranscriptomics as **Im**ages Project (STIM) is a framework for storing, (interactively) viewing, aligning, and processing spatial transcriptomics data, which builds on the powerful libraries [Imglib2](https://github.com/imglib/imglib2), [N5](https://github.com/saalfeldlab/n5), [BigDataViewer](https://github.com/bigdataviewer) and [Fiji](https://fiji.sc). **It can be installed through [Conda](https://conda-forge.org) and uses [AnnData](https://anndata.readthedocs.io/en/latest/) and/or [N5](https://github.com/saalfeldlab/n5) as a storage backend thus allowing easy interaction with existing tools for spatial transcriptomics.** It provides efficient access to spatial transcriptomics data "classically" as values, or can render them as images at arbitrary resolution. These image representations allow to apply computer vision techniques to spatially resolved sequencing datasets. STIM highlights are: 
 * efficient interactive rendering (using [BigDataViewer](https://github.com/bigdataviewer))
 * rendering high-quality still images (using [ImageJ](https://imagej.nih.gov/ij/)/[Fiji](https://fiji.sc))
 * alignment of spatial dataset slides using [SIFT](https://en.wikipedia.org/wiki/Scale-invariant_feature_transform), [ICP](https://en.wikipedia.org/wiki/Iterative_closest_point) and [RANSAC](https://en.wikipedia.org/wiki/Random_sample_consensus) combined with global optimization
 * efficient and fast storage using [AnnData](https://anndata.readthedocs.io/en/latest/) and [N5](https://github.com/saalfeldlab/n5) for multi-slice datasets
 * [Conda](https://conda-forge.org) installable, straight-forward interoperability with other packages
 * image filtering framework for irregularly-spaced datasets

A **great example** dataset is provided by the [SlideSeq paper](https://science.sciencemag.org/content/363/6434/1463.long) and can be downloaded [here](https://portals.broadinstitute.org/single_cell/study/slide-seq-study). 

A **minimal example** of a two-slice Visium dataset is available [here](https://drive.google.com/file/d/1qzzu4LmRukHBvbx_hiN2FOmIladiT7xx/view?usp=sharing). **We provide a [detailed walk-through for this dataset below to get you started](#Tutorial-on-small-example).** *Note: we highly recommend this tutorial as a starting point for using STIM. If you have any questions, feature requests or concerns please open an issue here on GitHub.*

## Contents
1. **[Tutorials on small examples](#tutorials-on-small-examples)**
   1. [Data layout](#data-layout)
   2. [Tutorial: interactively exploring a single slice-dataset](#tutorial-interactively-exploring-a-single-slice-dataset)
   3. [Tutorial: aligning a multi-slice container-dataset](#tutorial-aligning-a-multi-slice-container-dataset)
2. [Installation instructions](#installation-instructions)
   1. [Conda](#using-conda-all-platforms)
   2. [Building from source](#building-from-source-ubuntu)
3. [Command line API documentation](#command-line-api-documentation)
   1. [Resaving](#Resaving)
   2. [Adding annotations](#adding-annotations)
   3. [Normalization](#normalization)
   4. [Iteractive viewing application](#iteractive-viewing-application)
   5. [Render images and view or save as TIFF](#render-images-and-view-or-save-as-tiff)
   6. [View selected genes for an entire N5 as 2D/3D using BigDataViewer](#view-selected-genes-for-an-entire-N5-as-2D-or-3D-using-BigDataViewer)
   7. [Alignment of 2D slices](#alignment-of-2D-slices)
      1. [Pairwise alignment](#pairwise-alignment)
      2. [View pairwise alignment](#view-pairwise-alignment)
      3. [Global optimization and ICP refinement](#global-optimization-and-ICP-refinement)
4. [Wrapping in Python](#wrapping-in-Python)
5. [Java code examples](#Java-code-examples) 

## Tutorials on small examples

To get started please follow the [Installation instructions](#Installation-instructions) to install **STIM** either through Conda or by building it from source. There are two different examples based on the storage layout, a single slice one and one with multiple slices. Therefore, we first explain the basics of our storage layout.

For the tutorials, please download the example Visium data by clicking [here](https://drive.google.com/file/d/1qzzu4LmRukHBvbx_hiN2FOmIladiT7xx/view?usp=sharing) and store the zip file in the same directory that contains the executables (assuming you just did `./install`).
***Note: your browser might automatically unzip the data, we cover both cases during the resaving step in the tutorials below.***

### Data layout

A spatial transcriptomics dataset can consist of a single 2-dimensional (2d) slice, or a container that contains several 2d slices and thereby forms a 3d volume. Note that for any 3d volume (container-dataset), each 2d slice can also be addressed as an individual dataset (slice-dataset). Most commands support both types of datasets, while some require a container (e.g. alignment).

Slice-datasets can either be saved in an [anndata](https://anndata.readthedocs.io/en/latest/)-conforming layout, where the expression values, locations and annotations are stored in `/X`, `/obsm/spatial` and `/obs`, respectively; or in a [generic hierarchical layout](https://www.biorxiv.org/content/10.1101/2021.12.07.471629), where the arrays are stored in `/expressionValues`, `/locations` and `/annotations`, respectively. The [N5 API](https://github.com/saalfeldlab/n5) is used to read and write these layouts using the N5, Zarr, or HDF5 backend. If your slice(s) are stored in `.csv` files, you can use the `st-resave` command (see below) to resave your data into one of the supported formats by specifying the extension of the output as `.h5` (generic HDF5), `.n5` (generic N5), or `.zarr` (generic Zarr); an additional suffix `ad` is used to indicate the AnnData-conforming layout (e.g. `h5ad` for HDF5-backed AnnData).

For a slice-dataset, you can:
* interactively view it using `st-explorer` (explore all genes & annotations) or `st-bdv-view` (view multiple genes in parallel)
* render the dataset in ImageJ/Fiji and save the rendering, e.g., as TIFF, using `st-render`;
* normalize the dataset using `st-normalize`;
* add annotations such as, e.g., celltypes, using `st-add-annotations`;
* create a container-dataset from one or more slice-datasets (see below).

For alignment of several slices, slices have to be grouped into an N5-container to allow additional annotations to be stored. In addition to all commands listed above for slice-datasets, the subsequent commands can be used for container-datasets:
* create a container-dataset containing one or more existing slice-datasets using `st-add-slice`;
* add a slice-dataset to a pre-existing container-dataset using `st-add-slice`;
* perform pairwise alignment of slices using `st-align-pairs` (pre-processing);
* visualize aligned pairs of slices using `st-align-pairs-view` (optional user verification);
* perform global alignment of all slices using `st-align-global` (yielding the actual transformation for each slice-dataset);
* visualize globally aligned data in BigDataViewer using `st-bdv-view`.

### Tutorial: interactively exploring a single slice-dataset

1. First, we need to convert the data we just downloaded as CSV into one of the supported formats for efficent storage and access to the dataset. We want the first slice of the data to be saved in an anndata file called `slice1.h5ad`. Assuming the data are in the downloaded `visium.zip` file in the same directory as the executables, execute the following:
```bash
./st-resave -i visium.zip/section1_locations.csv,visium.zip/section1_reads.csv,slice1.h5ad
```
This will automatically load the `*.csv` files from within the zipped file and create a `slice1.h5ad` file in the current directory *(alternatively, you could extract the `*.csv` files as well and link them)*. The entire resaving process should take about 10 seconds on a modern notebook with an SSD. **Note: if your browser automatically unzipped the data, just change `visium.zip` to the respective folder name, most likely `visium`.***

2. Next, we will simply take a look at the slice-dataset directly:
```bash
./st-explorer -i slice1.h5ad -c '0,110'
```
First, type `calm2` into the 'search gene' box. Using `-c '0,110'` we already set the display range to more or less match this dataset. You can manually change it by clicking in the BigDataViewer window and press `s` to bring up the brightness dialog. Feel free to play with the **Visualization Options** in the explorer, e.g. move **Gauss Rendering** to 0.5 to get a sharper image and then play with the **Median Filter** radius to filter the data.

3. <img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/overlay calm2-mbp.png" alt="Example overlay of calm-2, mbp" width="280">Now, we will create a TIFF image for gene Calm2 and Mbp:
```bash
./st-render -i slice1.h5ad -g 'Calm2,Mbp' -sf 0.5
```
You can now for example overlay both images into a two-channel image using `Image > Color > Merge Channels` and select **Calm2** as magenta and **Mbp** as green. You could for example convert this image to RGB `Image > Type > RGB Color` and then save it as TIFF, JPEG or AVI (e.g JPEG compression). **These can be added to your presentation or paper for example, check out our beautiful AVI** [here](https://github.com/PreibischLab/STIM/blob/master/src/main/resources/calm2-mbp.avi) (you need to click download on the right top). You could render a bigger image setting `-s 0.1`. ***Note: Please check the documentation of [ImageJ](https://imagej.net) and [Fiji](http://fiji.sc) for help how to further process images.***


### Tutorial: aligning a multi-slice container-dataset

0. Make sure you followed the previous tutorial such that you've already resaved the first slice of the visium dataset as anndata file `slice1.h5ad`.

1. In order to perform the alignment of the whole dataset (would work identically for more than two slices), we need to create a container-dataset containing the already resaved slice-dataset:
```bash
./st-add-slice -c visium.n5 -i slice1.h5ad
```
This will create an N5 container `visium.n5` and link the first slice to it. If you don't want the slice to be linked but moved instead, you can use the `-m` flag. Also, custom storage locations for the location, expression values, and annotations arrays within the slice can be given by `-l`, `-e`, and `-a`, respectively.

2. Now we resave the second slice of the data as N5 slice-dataset. Assuming the data are in the downloaded `visium.zip` file in the same directory as the executables:
```bash
./st-resave \
   -i visium.zip/section2_locations.csv,visium.zip/section2_reads.csv,slice2.n5 \
   -c visium.n5
```
It will automatically load the `*.csv` files from within the zipped file and add it to the `visium.n5` container-dataset already containing the first slice. The entire resaving process should take about 10 seconds on a modern notebook with an SSD. **Note**: if your browser automatically unzipped the data, just change `visium.zip` to the respective folder name, most likely `visium`.

3. Next, we can again take a look at the data, which now includes both slice-datasets. We can do this interactively or by rendering: 
```bash
./st-explorer -i visium.n5 -c '0,110'
./st-bdv-view ... TODO - but i thinkt it would render as 3d, so not here yet
./st-render -i visium.n5 -g 'Calm2,Mbp' -sf 0.5
```
Selecting genes and adjusting visualization options work exactly as in the first tutorial.
<img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/overlay calm2-mbp.png" alt="Example overlay of calm-2, mbp" width="280">We can now overlay both images into a two-channel image again using `Image > Color > Merge Channels` and select **Calm2** as magenta and **Mbp** as green. By flipping through the slices (slice1 and slice2) you will realize that they are not aligned.

4. To remedy this, we will perform alignment of the two slices. We will use 15 automatically selected genes `-n` (the more the better, but it is also slower), a maximum error of 100 `--maxEpsilon` (in units of the sequenced locations) and require at least 30 inliers per gene `--minNumInliersGene` (this dataset is more robust than the SlideSeq one). **The alignment process takes around 1-2 minutes on a modern notebook.** *Note: at this point no transformations are stored within the container-dataset, but only the list of corresponding points between all pairs of slices.*

TODO: how do I choose maxEpsilon??? maybe use as baseline the avg distance between sequenced points * 10?
TODO: how to choose N and inliers? or what do i do if it doesn't work on my data?

```bash
./st-align-pairs -c visium.n5 -n 15 -sf 0.5 --maxEpsilon 100 --minNumInliersGene 30
```

5. <img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/align_mt-Nd4-1.gif" alt="Example alignment" width="480"> Now we will visualize before/after alignment of this pair of slices. To this end, we create two independent images, one using `st-render` (see above) and one using `st-align-pairs-view` on the automatically selected gene **mt-Nd4**. `st-render` will display the slices unaligned, while `st-align-pairs-view` will show them aligned. 
```bash
./st-render -i visium.n5 -sf 0.5 -g mt-Nd4
./st-align-pairs-view -c visium.n5 -sf 0.5 -g mt-Nd4
```
*Note: to create the GIF shown I saved both images independently, opened them in Fiji, cropped them, combined them, converted them to 8-bit color, set framerate to 1 fps, and saved it as one GIF.* 

6. Finally, we perform the global alignment. In this particular case, it is identical to the pairwise alignment process as we only have two slices. However, we still need to do it so the **final transformations for the slices are stored in the slice-datasets.** After that, `st-explorer`, `st-bdv-view` and `st-render` will take these transformations into account when displaying the data. This final processing step usually only takes a few seconds.
```bash
./st-align-global -c visium.n5 --absoluteThreshold 100 -sf 0.5 --lambda 0.0 --skipICP
```

7. <img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/bdv-calm2-mbp-mtnd4.png" alt="Example alignment" width="240">The final dataset can for example be visualized and interactively explored using BigDataViewer. Therefore, we specify three genes `-g Calm2,Mbp,mt-Nd4`, a crisper rendering `-sf 0.6`, and a relative z-spacing between the two planes that shows them close to each other `-z 3`. Of course, the same data can be visualized using `st-explorer` and `st-render`.
```bash
./st-bdv-view -i visium.n5 -g Calm2,Mbp,mt-Nd4 -c '0,90' -sf 0.6 -z 3
```
We encourage you to use this small two slice dataset as a starting point for playing with and extending **STIM**. If you have any questions, feature requests or concerns please open an issue here on GitHub. Thanks so much!

## Installation instructions

### Using Conda (all platforms)

We recommend using Conda to install STIM. If you don't have Conda installed, please follow the instructions [here](https://docs.conda.io/projects/conda/en/latest/user-guide/install/). Once Conda is installed, you can install STIM from conda-forge by running:
```bash
conda install -c conda-forge stim
```

### Building from source (Ubuntu)

Building STIM from source requires maven and OpenJDK8 (or newer). On Ubuntu, you can install them via the default package manager:
```bash
sudo apt-get install openjdk-8-jdk maven
```

Next, please check out this repository and go into the folder
```
git clone https://github.com/PreibischLab/stim.git
cd stim
```

The recommended way is to just call the install script without any arguments to install STIM into the checked out directory:
```bash
./install
```
To install into your favorite local binary `$PATH` (e.g., `$HOME/bin`) you can call:
```bash
./install $HOME/bin
```
All dependencies will be downloaded and managed by maven automatically.
For platforms other than Ubuntu, please find your way and report back if interested.

This currently installs several tools: `st-resave, st-add-slice, st-normalize, st-explorer, st-render, st-bdv-view, st-add-annotations, st-align-pairs, st-align-pairs-view, st-align-global`.

The process should finish with a message similar to this (here we only called `./install` thus installing in the code directory):
```bash
Installing 'st-explorer' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-render' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-bdv-view' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-resave' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-add-slice' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-normalize' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-add-annotations' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-align-pairs' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-align-pairs-view' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-align-global' command into /Users/spreibi/Downloads/stim_test/stim

Installation directory equals current directory, we are done.
Installation finished.
```
The installation should take around 1 minute.

## Command line API documentation

### Resaving
Resave (compressed) textfiles to one of the layouts described above (and optionally `--normalize`) using
```bash
./st-resave \
     -i '/Puck_180528_20.tar/BeadLocationsForR.csv,/Puck_180528_20.tar/MappedDGEForR.csv,Puck_180528_20.n5' \
     -a '/path/celltypes.csv' \
     -a ...
     [-c '/path/directory.n5'] \
     [--normalize]
```
If the N5-container exists, new datasets will be added (example above:`Puck_180528_20.n5`), otherwise a new N5-container will be created. Each input consists of a `locations.csv` file, a `reads.csv` file, and a user-defined `dataset name`. The csv files can optionally be inside (zip/tar/tar.gz) files. It is tested on the slide-seq data linked above, which can be used as a blueprint for how to save one's own data for import.

_Optionally_, one or more annotations (e.g., cell types) can be imported as part of the resaving step (e.g. from `celltypes.csv`) with the `-a` flag.
Please note that missing barcodes in `celltypes.csv` will be excluded from the dataset. This way you can filter locations with bad expression values.

_Optionally_, the datasets can be directly log-normalized before resaving. The **locations file** should contain a header for `barcode (id), xcoord and ycoord`, followed by the entries:
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

The _optional_ **annotation files** should contain all `barcodes (id)` and `celltype id` (integer numbers) as a header:
```
barcodes,celltype
TCACGTAGAAACC,28
TCTCCTAGTTCGG,1
ACCGTCTGAATTC,40
...
```

### Adding annotations
You can also add CSV annotations (e.g., celltypes) to an existing dataset (within or outside some N5-container):
```bash
./st-add-annotations \
     -i '/path/input.n5' \
     -a '/path/celltypes.csv' \
     [-l 'label']
```
The annotations are stored in the dataset within the intended group as `label` if the `-l` option is given, otherwise the label is taken from the file name (in the above case, `celltypes`).
Note that this command does not act upon missing barcodes, but only warns about them.

### Normalization
You can run the normalization also independently after resaving if desired. The tool can resave datasets within or outside of an N5-container:
```bash
./st-normalize \
     -i '/path/input1.n5,/path/input2.n5' \
     [-o '/path/output1.n5,/path/output1.n5'] \
     [-c '/path/container.n5'] \
```
The only parameter you have to provide is the comma separated list of input datasets `-i` which are assumed to reside in an N5-container if additionally the `-c` option is given.
You can optionally define a comma separated list of output paths `-o` (otherwise it'll append `'-normed'` to the dataset names).

### Iteractive viewing application
Run the interactive viewer as follows
```bash
./st-explorer \
     -i '/path/directory.n5' \
     [-d 'Puck_180528_20.h5ad,Puck_180528_22.h5ad'] \
     [-c '0,255']
```
It allows you to browse the data in realtime for all genes and datasets.
If data is registered it will automatically use the transformations that are stored in the metadata to properly overlay individual datasets.
The optional switch `-d` allows you to select a subset of datasets if `-i` is an N5-container, and using `-c` allows to preset the BigDataViewer intensity range.

### Render images and view or save as TIFF
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
     [--ignoreTransforms]
```
If you only define the input path `-i` and one or more genes `-g`, the rendered image will be displayed as an ImageJ image. If the input is an N5-container, all datasets will be rendered as 3D image. When defining an output directory `-o` images will not be displayed, but saved as TIFF (stacks) into the directory with filenames corresponding to the gene name. The optional switch `-d` allows you to select a subset of datasets if `-i` is an N5-container (default: all datasets), `-s` scales the rendering (default: 0.05), `-f` enables a single-spot filter (default: off), `-m` applies median filtering in locations space (not on top of the rendered image) with a certain radius (default: off), `-sf` sets the smoothness factor for rendering of the sparse dataset, and `-b` sets the size of an extra black border around the location coordinates (default: 20). Finally, `--ignoreTransforms` lets you ignore all transforms associated with the datasets (e.g., alignment) when rendering.

### View selected genes for an entire container as 2D or 3D using BigDataViewer
In order to interactively browse the 2D/3D space of one or more datasets with BigDataViewer you can
```bash
./st-bdv-view \
     -i '/path/directory.n5' \
     -g Calm2,Hpca \
     [-a 'celltype']
     [-d 'Puck_180528_20.n5,Puck_180528_22.n5'] \
     [-z 5.0] \
     [-c '0,255'] \
     [-f] \
     [-m 20] \
     [-sf 2.0] \
```
Dataset(s) from the selected input `-i` (single dataset or N5-container) will be interactively rendered for one or more selected genes `-g` (multiple genes will be overlaid into different colors).
The switch `-a` will overlay for example celltype annotations.
By default all datasets will be displayed, but they can be limited (or ordered) using `-d`.
You can define the distance between slices with `-z` (as a factor of median spacing between sequenced locations), `-c` allows to preset the BigDataViewer intensity range and parameters `-f, -m, -sf` are explained above (4).

### Alignment of 2D slices

The alignment of 2D slices of a 3D volume is a two-step process.
At first, using **`st-align-pairs`** slices will be aligned pairwise (e.g. 1<sup>st</sup> vs 2<sup>nd</sup>, 1<sup>st</sup> vs 3<sup>rd</sup>, and so on ...) using the Scale Invariant Feature Transform (SIFT) on a set of genes.
These pairwise alignments can _optionally_ be viewed and confirmed using **`st-align-pairs-view`**.
Finally, a globally optimal model for each slide will computed using **`st-align-global`**, which supports a refinement using Iterative Closest Point (ICP) matching.
**Note:** the alignment process inherently requires multiple datasets and additional metadata to be stored. Therefore, the following commands can only be used with an N5-container.

#### Pairwise alignment

The pairwise alignment uses SIFT to align pairs of 2d slices.
_**Important note:** the order of the datasets as they are passed into the program is crucial as it determines which slices are next to each other.
If not specified, they are used in the order as stored in the JSON file inside the N5-container._
The 2d alignment can be called as follows, the resulting transformations and corresponding points are automatically stored in the N5:
```bash
./st-align-pairs \
     -c '/path/directory.n5' \
     [-d 'Puck_180528_20.n5,Puck_180528_22.n5'] \
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
Datasets from the selected N5-container `-c` will be aligned in pairs.
Datasets and their ordering can be optionally defined using `-d`, otherwise all datasets will be used in the order as defined in the N5-container.
The comparison range (±slices to be aligned) can be defined using `-r`, by default it is set to 2.
Genes to be used can be specified manually using `-g`, or a specified number of genes `-n` with the highest standard deviation in the expression signal will be used.
By default, 100 genes will be automatically selected.

The images used for alignment are rendered as in the viewing programs above. The scaling of the images can be changed using `-s` (default: 0.05 or 5%), and the smoothness factor can be changed using `-sf` (default: 4.0). If a registration was run before and transformations are already stored, the application will quit. To compute anyways, previous results can be overwritten using `--overwrite`.

The alignment itself has more paramters that can be adjusted. The maximal error (default 250.0) for the RANSAC matching in SIFT can be adjusted using `-e`, the minimally required number of RANSAC inliers per tested gene can be changed using `--minNumInliersGene` (default: 5), and the minimal number of inliers over all genes can be adjusted using `--minNumInliers` (default: 30).

The results of the alignment will be shown by default using a gene (selected automatically or defined via `--renderingGene`, which can be deactivated using `--hidePairwiseRendering`. 

#### View pairwise alignment

This command allows to manually inspect pairwise alignments between slices and to test out the effect of different transformation models (from fully rigid to fully affine). It uses all identified corresponding points to compute the respective transformation that minimizes the distance between all points.
```bash
./st-align-pairs-view \
     -c '/path/directory.n5' \
     -g Calm2 \
     [-d 'Puck_180528_20.n5,Puck_180528_22.n5'] \
     [-s 0.05] \
     [-sf 4.0] \
     [-l 1.0] \
```
Pairs of datasets `-d` from the selected N5-container `-c` will be visualized for a gene of choice defined by `-g`. If `-d` is omitted, all pairs will be displayed. The images are rendered as explained above. The scaling of the images can be changed using `-s` (default: 0.05 or 5%), and the smoothness factor can be changed using `-sf` (default: 4.0). Importantly, `-l` allows to set the lambda of the 2D interpolated transformation model(s) (affine/rigid). Specifically, lambda defines the degree of rigidity, fully affine is 0.0, fully rigid is 1.0 (default: 1.0 - rigid). A sensible choice might be 0.1.

#### Global optimization and ICP refinement

The global optimization step minimizes the distance between all corresponding points across all pairs of slices (at least two) and includes an optional refinement step using the iterative closest point (ICP) algorithm.
```bash
./st-align-global \
     -c '/path/directory.n5' \
     [-d 'Puck_180528_20.n5,Puck_180528_22.n5'] \
     [-l 0.1] \
     [--maxAllowedError] \
     [--maxIterations] \
     [--minIterations] \
     [--relativeThreshold] \
     [--absoluteThreshold] \
     [--ignoreQuality] \
     [--skipICP] \
     [--icpIterations] \
     [--icpErrorFraction] \
     [--maxAllowedErrorICP] \
     [--maxIterationsICP] \
     [--minIterationsICP] \
     [-sf 4.0] \
     [-g Calm2] \
```
By default, all datasets of the specified N5-container `-c` will be optimized, a subset of datasets can be selected using `-d`. `-l` allows to set the lambda of the 2D interpolated transformation model(s) that will be used for each slice. Lambda defines the degree of rigidity, fully affine is 0.0, fully rigid is 1.0 (default: 0.1 - 10% rigid, 90% affine). 

Prior to computing the final optimum, we try to identify if there are pairs of slices that contain wrong correspondences. To do this, we test for global consistency of the alignment and potentially remove pairs that differ significantly from the consensus of all the other pairs. There are a few parameters to adjust this process. `--ignoreQuality` ignores the amount of RANSAC inlier ratio as a way to measure their quality, otherwise it is used determine which pairwise connections to remove during global optimization (default: false). `--relativeThreshold` sets the relative threshold for dropping pairwise connections, i.e. if the pairwise error is n-times higher than the average error (default: 3.0). `--absoluteThreshold` defines the absolute error threshold for dropping pairwise connections. The errors of the pairwise matching process provide a reasonable number, the global error shouldn't be much higher than the pairwise errors, althought it is expected to be higher since it is a more constraint problem (default: 160.0 for slideseq).

`--maxAllowedError` specifies the maximally allowed error during global optimization (default: 300.0 for slideseq). The optimization will run until the maximum number of iterations `--maxIterations` if the error remains above `--maxAllowedError`. `--minAllowedError` sets the minimum number of iterations that will be performed. *Note: These parameters usually do not need to change*. 

`--skipICP` skips the more compute intense ICP refinement step. If sufficent numbers of correspondences are found in the pairwise matching (e.g. >300), this can be advisable. `--icpIterations` defines the maximum number of ICP iterations for each pair of slides (default: 100). `--icpErrorFraction` describes the distance at which sequenced locations will be assigned as correspondences in ICP, relative to median distance between all locations (default: 1.0). `--maxAllowedErrorICP` is the maximum error allowed during ICP runs (after each model fit) - here also consult the results of pairwise matching to identify a reasonable number (default: 140.0 for slideseq). 

The global optimization after ICP will run until the maximum number of iterations `--maxIterationsICP` if the error remains above `--maxAllowedError`. `--minIterationsICP` sets the minimum number of iterations that will be performed. *Note: These parameters usually do not need to change*. 

The results are displayed by default. The smoothness factor can be changed using `-sf` (default: 4.0), the gene can be selected using `-g` (default: Calm2).

## Wrapping in Python

A python wrapper, stimwrap https://github.com/rajewsky-lab/stimwrap,
provides an interface to extract datasets and their attributes from an
N5-container created by STIM.

## Java code examples

There example classes in this [Java package](https://github.com/PreibischLab/STIM/tree/master/src/main/java/examples) that can help you getting started with programming in STIM, specifically `VisualizeStack.java` and `TestDisplayModes.java` could be useful. Additionally, the code for all command-line tools can be found [here](https://github.com/PreibischLab/STIM/tree/master/src/main/java/cmd), which is also a good starting place in combination with the [tutorial](#Minimal-Example-Instructions). *Note that the [install.sh](https://github.com/PreibischLab/STIM/blob/master/install) shows a link between the command-line tool name and Java class name, e.g. st-bdv-view is cmd.DisplayStackedSlides.*
