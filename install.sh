#!/bin/bash

# This script is shamelessly adapted from https://github.com/saalfeldlab/n5-utils, thanks @axtimwalde & co!

display_usage () {
  echo "Usage: install.sh [options]"
  echo ""
  echo "OPTIONS"
  echo "  -h                    Display this help message"
  echo "  -i <install_dir>      Install commands into <install_dir>"
  echo "                        (default: current directory)"
  echo "  -r <repository_dir>   Download dependencies into <repository_dir>"
  echo "                        (default: standard maven repository, most"
  echo "                        likely \$HOME/.m2/repository)"
  exit
}

VERSION="0.3.2-SNAPSHOT"

while getopts :hi:r: flag
do
  case "${flag}" in
    h) display_usage;;
    i) INSTALL_DIR=${OPTARG};;
    r) REPO_DIR=${OPTARG};;
	?) display_usage;;
  esac
done
INSTALL_DIR=${INSTALL_DIR:-$(pwd)}
REPO_DIR=${REPO_DIR:-$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)}

echo ""
echo "Downloading dependencies into ${REPO_DIR}"
echo "Installing into ${INSTALL_DIR}"

# check for operating system
if [[ "${OSTYPE}" == "linux-gnu" ]]; then
  echo "Assuming on Linux operating system"
  MEM=$(cat /proc/meminfo | grep MemTotal | sed s/^MemTotal:\\\s*\\\|\\\s\\+[^\\\s]*$//g)
  MEMGB=$((${MEM}/1024/1024))
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  echo "Assuming on MacOS X operating system"
  # sysctl returns total hardware memory size in bytes
  MEM=$(sysctl hw.memsize | grep hw.memsize | sed s/hw.memsize://g)
  MEMGB=$((${MEM}/1024/1024/1024))
else
  echo "ERROR - Operating system must be either Linux or MacOS X - EXITING"
  echo "(on Windows, please run the Windows specific install script)"
  exit
fi

MEM_LIMIT=$(((${MEMGB}/5)*4))
echo "Available memory:" ${MEMGB} "GB, setting Java memory limit to" ${MEM_LIMIT} "GB"
echo ""

mvn clean install -Dmaven.repo.local=${REPO_DIR}
mvn -Dmdep.outputFile=cp.txt -Dmdep.includeScope=runtime -Dmaven.repo.local=${REPO_DIR} dependency:build-classpath

echo ""

# function that installs one command
# $1 - command name
# $2 - java class containing the functionality
install_command () {
	echo "Installing '$1' command into" $INSTALL_DIR

	echo '#!/bin/bash' > $1
	echo '' >> $1
	echo "JAR=${REPO_DIR}/net/preibisch/imglib2-st/${VERSION}/imglib2-st-${VERSION}.jar" >> $1
	echo 'java \' >> $1
	echo "  -Xmx${MEM_LIMIT}g \\" >> $1
	echo -n '  -cp $JAR:' >> $1
	echo -n $(cat cp.txt) >> $1
	echo ' \' >> $1
	echo '  '$2' "$@"' >> $1

	chmod a+x $1
}


install_command st-explorer "cmd.View"
install_command st-render "cmd.RenderImage"
install_command st-bdv-view "cmd.BigDataViewerDisplay"
install_command st-bdv-view3d "cmd.BigDataViewerStackDisplay"
install_command st-resave "cmd.Resave"
install_command st-add-slice "cmd.AddSlice"
install_command st-normalize "cmd.Normalize"
install_command st-add-annotations "cmd.AddAnnotations"
install_command st-add-entropy "cmd.AddEntropy"
install_command st-align-pairs "cmd.PairwiseSectionAligner"
install_command st-align-pairs-add "cmd.AddPairwiseMatch"
install_command st-align-pairs-view "cmd.ViewPairwiseAlignment"
install_command st-align-global "cmd.GlobalOpt"
install_command st-align-interactive "cmd.InteractiveAlignment"
install_command st-help "cmd.PrintHelp"
install_command st-extract-transformations "cmd.ExtractTransformations"

install_command st-test-stdev "cmd.ComputeVariance"

if [ $(pwd) == "${INSTALL_DIR}" ]; then
    echo "Installation directory equals current directory, we are done."
else
	echo "Creating directory ${INSTALL_DIR} and moving files..."
    mkdir -p ${INSTALL_DIR}
    mv st-explorer ${INSTALL_DIR}/
    mv st-bdv-view ${INSTALL_DIR}/
    mv st-bdv-view3d ${INSTALL_DIR}/
    mv st-render ${INSTALL_DIR}/
    mv st-resave ${INSTALL_DIR}/
    mv st-add-slice ${INSTALL_DIR}/
    mv st-normalize ${INSTALL_DIR}/
    mv st-add-annotations ${INSTALL_DIR}/
    mv st-add-entropy ${INSTALL_DIR}/
    mv st-align-pairs ${INSTALL_DIR}/
	  mv st-align-pairs-add ${INSTALL_DIR}/
    mv st-align-pairs-view ${INSTALL_DIR}/
    mv st-align-global ${INSTALL_DIR}/
    mv st-align-interactive ${INSTALL_DIR}/
    mv st-help ${INSTALL_DIR}/
    mv st-test-stdev ${INSTALL_DIR}/
    mv st-extract-transformations ${INSTALL_DIR}/
fi

rm cp.txt

echo "Installation finished."
