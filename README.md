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

This currently installs one tool, `st-view`.

Run the viewer
```bash
./st-view -i '/path/directory.n5'
```
