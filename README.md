# imglib2-st
Library for managing, storage, viewing, and working with spatial
transcriptomics data using imglib2, BigDataViewer and Fiji.

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

### 1.	Resave
Resave a (compressed) textfile to the N5 format (and optionally `--normalize`) using
```bash
./st-resave \
     -o '/path/directory.n5' \
     -i '/path/locations1.csv,/path/reads2.csv,name1' \
     -i '/path.zip/locations2.csv,/path.zip/reads2.csv,name2' ... \
     [--normalize]
```
If the n5 directory exists new datasets will be added (name1, name2), otherwise a new n5 will be created. Each input consists of a locations.csv file, a reads.csv file, and a user-chosen name. Optionally, the datasets can be directly log-normalized before resaving. The locations file should contain a header for `barcode (id), xcoord and ycoord`, followed by the entries:
```
barcodes,xcoord,ycoord
TCACGTAGAAACC,3091.01234567901,2471.88888888889
TCTCCTAGTTCGG,4375.91791044776,1577.52985074627
...
```
The reads file should contain all `barcodes (id)` as the header after a `Row` column that holds the gene identifier. It should have as many columns as there are sequenced locations (ids from above):
```
Row,TCACGTAGAAACC,TCTCCTAGTTCGG, ...
0610005C13Rik,0,0, ...
0610007P14Rik,0,0, ...
...
```
Note: if there is a mismatch between number of sequenced locations defined in the locations.csv (rows) with the locations in reads.csv (columns), the resave will stop.

### 2. Normalization
You can run the normalization also independently after resaving. The tool can resave selected or all datasets of an N5 container into the same or a new N5:
```bash
./st-normalize \
     -i '/path/input.n5' \
     [-o '/path/output.n5'] \
     [-d 'dataset1,dataset2'] \
     [-e 'dataset1-normed,dataset2-normed']
```
The only parameter you have to provide is the input N5 `-i`. You can optionally define an output N5 `-o` (otherwise it'll be the same), select specific input dataasets within the input N5 `-d`, and use user-defined names for the normalized datasets `-e` (by default it will be `inputname-norm`).

### 3. Iteractive Viewing Application
Run the interactive viewer as follows
```bash
./st-view \
     -i '/path/directory.n5' \
     [-d 'Puck_180528_20,Puck_180528_22'] \
     [-c '0,255']
```
It allows you to browse the data in realtime for all genes and datasets. If data is registered it will automatically use the transformations that are stored in the N5 metadata to properly overlay individual datasets. The optional switch `-d` allows you to select a subset of datasets from a N5, and using `-c` allows to preset the BigDataViewer intensity range.

### 4. Render images and view or save as TIFF
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

### 5. View a selected gene for an entire N5 as 2D/3D using BigDataViewer
In order to interactively browse the 2D/3D space of one or more datasets of an N5 with BigDataViewer you can
```bash
./st-3d-view \
     -i '/path/directory.n5' \
     -g Calm2 \
     [-d 'Puck_180528_20,Puck_180528_22'] \
     [-z 5.0] \
     [-c '0,255'] \
     [-f] \
     [-m 20] \
     [-sf 2.0] \
```
Dataset(s) from the selected N5 `-i` will be interactively rendered for a selected gene `-g`. By default all datasets will be displayed, but they can be limited (or ordered) using `-d`. You can define the distance between sections with `-z` (as a factor of median spacing between sequenced locations), `-c` allows to preset the BigDataViewer intensity range and parameters `-f, -m, -sf` are explained above (4).
