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

**Please see the [wiki](https://github.com/PreibischLab/STIM/wiki) for additional tutorials, detailed installation instructions, and more.**

## Wrapping in Python

A python wrapper, stimwrap https://github.com/rajewsky-lab/stimwrap,
provides an interface to extract datasets and their attributes from an
N5-container created by STIM.


## Java code examples

There example classes in this [Java package](https://github.com/PreibischLab/STIM/tree/master/src/main/java/examples) that can help you getting started with programming in STIM, specifically `VisualizeStack.java` and `TestDisplayModes.java` could be useful. Additionally, the code for all command-line tools can be found [here](https://github.com/PreibischLab/STIM/tree/master/src/main/java/cmd), which is also a good starting place in combination with the [tutorial](#Minimal-Example-Instructions). *Note that the [install.sh](https://github.com/PreibischLab/STIM/blob/master/install) shows a link between the command-line tool name and Java class name, e.g. st-bdv-view is cmd.DisplayStackedSlides.*
