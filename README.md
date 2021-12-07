# STIM - the Spatial Transcriptomics ImgLib2 Project

<img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/Projection low-res-rgb.gif" alt="Example rendering of calm-2, ptgds, mbp" width="280">

STIM is a framework for managing, storage, viewing, and processing spatial transcriptomics data, which builds on the powerful libraries [Imglib2](https://github.com/imglib/imglib2), [N5](https://github.com/saalfeldlab/n5), [BigDataViewer](https://github.com/bigdataviewer) and [Fiji](https://fiji.sc). It allows to efficiently access spatial transcriptomics data "classically" as values, or render them as images at arbitrary resolution. Latter allows to apply computer vision techniques to spatially resolved sequencing datasets. STIM highlights the potential by: 
 * efficient interactive rendering
 * image filtering framework for irregularly-spaced datasets
 * alignment of spatial dataset slides using SIFT, ICP and RANSAC combined with global optimization

A **great example** dataset is provided by the [SlideSeq paper](https://science.sciencemag.org/content/363/6434/1463.long) and can be downloaded [here](https://portals.broadinstitute.org/single_cell/study/slide-seq-study). 

A **minimal example** of a two-slice Visium dataset is available [here](https://drive.google.com/file/d/1qzzu4LmRukHBvbx_hiN2FOmIladiT7xx/view?usp=sharing). **We provide a [detailed walk-through for this dataset below to get you started](#Tutorial-on-small-example).** *Note: we highly recommend this tutorial as a starting point for using STIM. If you have any questions, feature requests or concerns please open an issue here on GitHub.*

## Contents
1. **[Tutorial on small example](#Tutorial-on-small-example)**
2. [Installation Instructions](#Installation-Instructions)
3. [Resaving](#Resaving)
4. [Normalization](#Normalization)
5. [Iteractive Viewing Application](#Iteractive-Viewing-Application)
6. [Render images and view or save as TIFF](#Render-images-and-view-or-save-as-TIFF)
7. [View selected genes for an entire N5 as 2D/3D using BigDataViewer](#View-selected-genes-for-an-entire-N5-as-2D-or-3D-using-BigDataViewer)
8. [Alignment of 2D slices](#Alignment-of-2D-slices)
   1. [Pairwise Alignment](#Pairwise-Alignment)
   2. [View Pairwise Alignment](#View-Pairwise-Alignment)
   3. [Global Optimization and ICP refinement](#Global-Optimization-and-ICP-refinement)
9. [Wrapping in Python](#Wrapping-in-Python)
10. [Java Code Examples](#Java-Code-Examples) 

## Tutorial on small example

1. To get started please follow the [Installation Instructions](#Installation-Instructions) to clone and build **STIM**. It might be easiest to **not** install into `$HOME/bin` but rather just call `./install` during the installation process.

2. Next, please download the example Visium data from [here](https://drive.google.com/file/d/1qzzu4LmRukHBvbx_hiN2FOmIladiT7xx/view?usp=sharing) and store the zip file in the same directory that contains the executables (assuming you just did `./install`).
***Note: your browser might automatically unzip the data, we cover both cases during the resaving step below.***

3. Now we resave the data into an N5 container for efficent storage and access to the dataset. Assuming the data is in the downloaded `visium.zip` file in the same directory as the executables do:
```bash
./st-resave \
   -i visium.zip/section1_locations.csv,visium.zip/section1_reads.csv,sec1 \
   -i visium.zip/section2_locations.csv,visium.zip/section2_reads.csv,sec2 \
   -o visium.n5
```
It will automatically load the `*.csv` files from within the zipped file and create a `visium.n5` folder containing the re-saved dataset. The entire resaving process should take about 10 seconds on a modern notebook with an SSD. ***Note: if your browser automatically unzipped the data, just change `visium.zip` to the respective folder name, most likely `visium`***

4. Next, we will simply take a look at the data. 
```bash
./st-explorer -i visium.n5 -c '0,110'
```
First, type `calm2` into the search gene box. Using `-c '0,110'` we already set the display range to more or less match this dataset. You can manually change it by clicking in the BigDataViewer window and press `s` to bring up the brightness dialog. As you switch between **sec1** and **sec2** you'll see that they are not aligned. Feel free to play with the **Visualization Options** in the explorer, e.g. move **Gauss Rendering** to 0.5 to get a sharper image and then play with the **Median Filter** radius to filter the data.

5. <img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/overlay calm2-mbp.png" alt="Example overlay of calm-2, mbp" width="280">Now, we will create a TIFF image for gene Calm2 and Mbp.
```bash
./st-render -i visium.n5 -g 'Calm2,Mbp' -sf 0.5
```
You can now for example overlay both images into a two-channel image using `Image > Color > Merge Channels` and select **Calm2** as magenta and **Mbp** as green. By flipping through the slices (sec1 and sec2) you will again realize that they are not aligned. You could for example convert this image to RGB `Image > Type > RGB Color` and then save it as TIFF, JPEG or AVI (e.g JPEG compression). **These can be added to your presentation or paper for example, check out my beautiful AVI** [here](https://github.com/PreibischLab/STIM/blob/master/src/main/resources/calm2-mbp.avi) (you need to click download on the right top). You could render a bigger image setting `-s 0.1`. ***Note: Please check the documentation of [ImageJ](https://imagej.net) and [Fiji](http://fiji.sc) for further help with how to further process images.***

6. Next, we will perform alignment of the two slices. We will use 15 automatically selected genes `-n` (the more the better, but it is also slower), a maximum error of 100 `--maxEpsilon` and require at least 30 inliers per gene `--minNumInliersGene` (this dataset is more robust than the SlideSeq one). **The alignment process takes around 1-2 minutes on a modern notebook.** *Note: at this point no transformations are stored within the N5 container, but only the list of corresponding points.*
```bash
./st-align-pairs -i visium.n5 -n 15 -sf 0.5 --maxEpsilon 100 --minNumInliersGene 30
```

7. <img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/align_mt-Nd4-1.gif" alt="Example alignment" width="480"> Now we will visualize before/after alignment of this pair of slices. To achieve this, we create two independent images, one using `st-render` (see above) and one using `st-align-pairs-view` on the automatically selected gene **mt-Nd4**. `st-render` will display the sections unaligned, while `st-align-pairs-view` will show them aligned. 
```bash
./st-render -i visium.n5 -sf 0.5 -g mt-Nd4
./st-align-pairs-view -i visium.n5 -sf 0.5 -g mt-Nd4
```
*Note: to create the GIF shown I saved both images independently, opened them in Fiji, cropped them, combined them, converted them to 8-bit color, set framerate to 1 fps, and saved it as one GIF.* 

8. Finally, we perform the global alignment. In this particular case, it is identical to the pairwise alignment process as we only have two sections. However, we still need to do it so the **final transformations for the sections are stored in the N5.** After that, `st-explorer`, `st-bdv-view` and `st-render` will take these transformations into account when displaying the data This final processing step usually only takes a few seconds.
```bash
./st-align-global -i visium.n5 --absoluteThreshold 100 -sf 0.5 --lambda 0.0 --skipICP
```

9. <img align="right" src="https://github.com/PreibischLab/STIM/blob/master/src/main/resources/bdv-calm2-mbp-mtnd4.png" alt="Example alignment" width="240">The final dataset can for example be visualized and interactively explored using BigDataViewer. Therefore, we specify three genes `-g Calm2,Mbp,mt-Nd4`, a crisper rendering `-sf 0.6`, and a relative z-spacing between the two planes that shows them close to each other `-z 3`. Of course, the same data can be visualized using `st-explorer` and `st-render`.
```bash
./st-bdv-view -i visium.n5 -g Calm2,Mbp,mt-Nd4 -c '0,90' -sf 0.6 -z 3
```
We encourage you to use this small dataset as a starting point for playing with and extending **STIM**. If you have any questions, feature requests or concerns please open an issue here on GitHub. Thanks so much!

## Installation Instructions

Installation requires maven and OpenJDK8 (or newer) on Ubuntu:
```bash
sudo apt-get install openjdk-8-jdk maven
```
On other platforms, please find your way and report back if interested.

Next, please check out this repository and go into the folder

```
git clone https://github.com/PreibischLab/stim.git
cd stim
```

Install into your favorite local binary `$PATH` (or leave empty for using the checked out directory):
```bash
./install $HOME/bin
```
All dependencies will be downloaded and managed by maven automatically.

This currently installs several tools, `st-resave, st-normalize, st-explorer, st-render, st-bdv-view, st-align-pairs, st-align-pairs-view, st-align-global`.

The process should finish with a message similar to this (here we only called `./install` thus installing in the code directory):
```bash
Installing 'st-explorer' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-render' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-bdv-view' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-resave' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-normalize' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-align-pairs' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-align-pairs-view' command into /Users/spreibi/Downloads/stim_test/stim
Installing 'st-align-global' command into /Users/spreibi/Downloads/stim_test/stim

Installation directory equals current directory, we are done.
Installation finished.
```

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

The _optional_ **celltypes file** should contain all `barcodes (id)` and `celltype id` (integer numbers) as a header:
```
barcodes,celltype
TCACGTAGAAACC,28
TCTCCTAGTTCGG,1
ACCGTCTGAATTC,40
...
```

## Normalization
You can run the normalization also independently after resaving if desired. The tool can resave selected or all datasets of an N5 container into the same or a new N5:
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

The images used for alignment are rendered as in the viewing programs above. The scaling of the images can be changed using `-s` (default: 0.05 or 5%), and the smoothness factor can be changed using `-sf` (default: 4.0). If a registration was run before and transformations are already stored, the application will quit. To compute anyways, previous results can be overwritten using `--overwrite`.

The alignment itself has more paramters that can be adjusted. The maximal error (default 250.0) for the RANSAC matching in SIFT can be adjusted using `-e`, the minimally required number of RANSAC inliers per tested gene can be changed using `--minNumInliersGene` (default: 5), and the minimal number of inliers over all genes can be adjusted using `--minNumInliers` (default: 30).

The results of the alignment will be shown by default using a gene (selected automatically or defined via `--renderingGene`, which can be deactivated using `--hidePairwiseRendering`. 

### View Pairwise Alignment

This command allows to manually inspect pairwise alignments between slices and to test out the effect of different transformation models (from fully rigid to fully affine). It uses all identified corresponding points to compute the respective transformation that minimizes the distance between all points.
```bash
./st-align-pairs-view \
     -i '/path/directory.n5' \
     -g Calm2 \
     [-d 'Puck_180528_20,Puck_180528_22'] \
     [-s 0.05] \
     [-sf 4.0] \
     [-l 1.0] \
```
Pairs of datasets `-d` from the selected N5 `-i` will be visualized for a gene of choice defined by `-g`. If `-d` is omitted, all pairs will be displayed. The images are rendered as explained above. The scaling of the images can be changed using `-s` (default: 0.05 or 5%), and the smoothness factor can be changed using `-sf` (default: 4.0). Importantly, `-l` allows to set the lambda of the 2D interpolated transformation model(s) (affine/rigid). Specifically, lambda defines the degree of rigidity, fully affine is 0.0, fully rigid is 1.0 (default: 1.0 - rigid). A sensible choice might be 0.1.

### Global Optimization and ICP refinement

The global optimization step minimizes the distance between all corresponding points across all pairs of slices (at least two) and includes an optional refinement step using the iterative closest point (ICP) algorithm.
```bash
./st-align-global \
     -i '/path/directory.n5' \
     [-d 'Puck_180528_20,Puck_180528_22'] \
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
By default, all datasets of the specified N5 container `-i` will be optimized, a subset of datasets can be selected using `-d`. `-l` allows to set the lambda of the 2D interpolated transformation model(s) that will be used for each slice. Lambda defines the degree of rigidity, fully affine is 0.0, fully rigid is 1.0 (default: 0.1 - 10% rigid, 90% affine). 

Prior to computing the final optimum, we try to identify if there are pairs of slices that contain wrong correspondences. To do this, we test for global consistency of the alignment and potentially remove pairs that differ significantly from the consensus of all the other pairs. There are a few parameters to adjust this process. `--ignoreQuality` ignores the amount of RANSAC inlier ratio as a way to measure their quality, otherwise it is used determine which pairwise connections to remove during global optimization (default: false). `--relativeThreshold` sets the relative threshold for dropping pairwise connections, i.e. if the pairwise error is n-times higher than the average error (default: 3.0). `--absoluteThreshold` defines the absolute error threshold for dropping pairwise connections. The errors of the pairwise matching process provide a reasonable number, the global error shouldn't be much higher than the pairwise errors, althought it is expected to be higher since it is a more constraint problem (default: 160.0 for slideseq).

`--maxAllowedError` specifies the maximally allowed error during global optimization (default: 300.0 for slideseq). The optimization will run until the maximum number of iterations `--maxIterations` if the error remains above `--maxAllowedError`. `--minAllowedError` sets the minimum number of iterations that will be performed. *Note: These parameters usually do not need to change*. 

`--skipICP` skips the more compute intense ICP refinement step. If sufficent numbers of correspondences are found in the pairwise matching (e.g. >300), this can be advisable. `--icpIterations` defines the maximum number of ICP iterations for each pair of slides (default: 100). `--icpErrorFraction` describes the distance at which sequenced locations will be assigned as correspondences in ICP, relative to median distance between all locations (default: 1.0). `--maxAllowedErrorICP` is the maximum error allowed during ICP runs (after each model fit) - here also consult the results of pairwise matching to identify a reasonable number (default: 140.0 for slideseq). 

The global optimization after ICP will run until the maximum number of iterations `--maxIterationsICP` if the error remains above `--maxAllowedError`. `--minIterationsICP` sets the minimum number of iterations that will be performed. *Note: These parameters usually do not need to change*. 

The results are displayed by default. The smoothness factor can be changed using `-sf` (default: 4.0), the gene can be selected using `-g` (default: Calm2).

## Wrapping in Python

A python wrapper, stimwrap https://github.com/rajewsky-lab/stimwrap,
provides an interface to extract datasets and their attributes from an
N5 container created by STIM.

## Java Code Examples

There example classes in this [Java package](https://github.com/PreibischLab/STIM/tree/master/src/main/java/examples) that can help you getting started with programming in STIM, specifically `VisualizeStack.java` and `TestDisplayModes.java` could be useful. Additionally, the code for all command-line tools can be found [here](https://github.com/PreibischLab/STIM/tree/master/src/main/java/cmd), which is also a good starting place in combination with the [tutorial](#Minimal-Example-Instructions). *Note that the [install.sh](https://github.com/PreibischLab/STIM/blob/master/install) shows a link between the command-line tool name and Java class name, e.g. st-bdv-view is cmd.DisplayStackedSlides.*
