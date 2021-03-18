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

This currently installs two tools, `st-resave, st-view`.

### 1.	Resave
Resave a (compressed) textfile to the N5 format using
```bash
./st-resave \ 
     -o '/path/directory.n5' \
     -i '/path/locations1.csv,/path/reads2.csv,name1' \
     -i '/path.zip/locations2.csv,/path.zip/reads2.csv,name2' ...
```
If the n5 directory exists new datasets will be added (name1, name2), otherwise a new n5 will be created. Each input consists of a locations.csv file, a reads.csv file, and a user-chosen name. The locations file should contain a header for `barcode (id), xcoord and ycoord`, followed by the entries:
```
barcodes,xcoord,ycoord
TCACGTAGAAACC,3091.01234567901,2471.88888888889
TCTCCTAGTTCGG,4375.91791044776,1577.52985074627
...
```
The reads file should contain all `barcodes (id)` as the header after a `Row` column that holds the gene identifier. It should have as many rows as there are sequenced locations:
```
Row,TCACGTAGAAACC,TCTCCTAGTTCGG, ...
0610005C13Rik,0,0, ...
0610007P14Rik,0,0, ...
...
```

### 2. Viewing
Run the viewer
```bash
./st-view -i '/path/directory.n5'
```
