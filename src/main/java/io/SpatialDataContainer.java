package io;

import mpicbg.models.PointMatch;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import align.PairwiseSIFT.PairwiseSiftResults;

public class SpatialDataContainer {

	final private String rootPath;
	final private boolean readOnly;
	final private N5FSReader n5;
	private List<String> datasets = new ArrayList<>();
	private List<String> matches = new ArrayList<>();
	final private static String version = "0.1.0";
	final private static String versionKey = "spatial_data_container";
	final private static String numDatasetsKey = "num_datasets";
	final private static String datasetsKey = "datasets";

	protected SpatialDataContainer(String path, boolean readOnly) throws IOException {
		this.rootPath = path;
		this.readOnly = readOnly;

		this.n5 = readOnly ? new N5FSReader(path) : new N5FSWriter(path);
	}

	public static SpatialDataContainer openExisting(String path) throws IOException {
		if (!(new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' does not exist.");
		SpatialDataContainer container = new SpatialDataContainer(path, false);
		container.readFromDisk();
		return container;
	}

	public static SpatialDataContainer openForReading(String path) throws IOException {
		if (!(new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' does not exist.");
		SpatialDataContainer container = new SpatialDataContainer(path, true);
		container.readFromDisk();
		return container;
	}

	public static SpatialDataContainer createNew(String path) throws IOException {
		if ((new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' already exists.");
		SpatialDataContainer container = new SpatialDataContainer(path, false);
		container.initializeContainer();
		return container;
	}

	protected void initializeContainer() throws IOException {
		N5FSWriter writer = (N5FSWriter) n5;
		writer.setAttribute("/", versionKey, version);
		writer.createGroup("/matches");
		updateDatasetMetadata();
	}

	protected void readFromDisk() throws IOException {
		String actualVersion = n5.getAttribute("/", versionKey, String.class);
		if (!version.equals(actualVersion))
			throw new SpatialDataIOException("Incompatible spatial data container version: expected " + version + ", got " + actualVersion + ".");

		int numDatasets = n5.getAttribute("/", numDatasetsKey, int.class);
		datasets = n5.getAttribute("/", datasetsKey, List.class);
		if (numDatasets != datasets.size())
			throw new SpatialDataIOException("Incompatible number of datasets: expected " + numDatasets + ", found " + datasets.size() + ".");

		matches = Arrays.asList(n5.list(n5.groupPath("matches")));
	}

	protected void updateDatasetMetadata() throws IOException {
		N5FSWriter writer = (N5FSWriter) n5;
		writer.setAttribute("/", numDatasetsKey, datasets.size());
		writer.setAttribute("/", datasetsKey, datasets.toArray());
	}

	public void addExistingDataset(String path) throws IOException {
		Path oldPath = Paths.get(path);
		String datasetName = oldPath.getFileName().toString();

		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data container.");
		if (datasets.contains(datasetName))
			throw new SpatialDataIOException("Dataset '" + datasetName + "' already exists.");

		Files.move(oldPath, Paths.get(rootPath, datasetName));
		datasets.add(datasetName);
		updateDatasetMetadata();
	}

	public void deleteDataset(String datasetName) throws IOException {
		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data container.");
		if (datasets.remove(datasetName)) {
			deleteFileOrDirectory(Paths.get(rootPath, datasetName));
			updateDatasetMetadata();
		}
	}

	public SpatialDataIO openDataset(String datasetName) throws IOException {
		if (!datasets.contains(datasetName))
			throw new SpatialDataIOException("Container does not contain dataset '" + datasetName + "'.");
		return SpatialDataIO.inferFromName(Paths.get(rootPath, datasetName).toString());
	}

	public List<SpatialDataIO> openAllDatasets() throws IOException {
		List<SpatialDataIO> datasetIOs = new ArrayList<>();
		for (final String datasetName : datasets)
			datasetIOs.add(SpatialDataIO.inferFromName(Paths.get(rootPath, datasetName).toString()));
		return datasetIOs;
	}

	public static boolean isCompatibleContainer(String path) {
		try {
			N5FSReader reader = new N5FSReader(path);
			String actualVersion = reader.getAttribute("/", versionKey, String.class);
			return (actualVersion.equals(version));
		} catch (Exception e) {
			return false;
		}
	}

	public List<String> getDatasets() {
		return new ArrayList<>(datasets);
	}

	public List<String> getMatches() {
		return new ArrayList<>(matches);
	}

	public String getVersion() {
		return version;
	}

	public void deleteMatch(String matchName) throws IOException {
		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data container.");
		if (matches.remove(matchName))
			deleteFileOrDirectory(Paths.get(rootPath, "matches", matchName));
	}

	public void deleteFileOrDirectory(Path path) throws IOException {
		File file = new File(path.toString());
		if (file.exists()) {
			if (file.isFile())
				file.delete();
			else
				Files.walkFileTree(Paths.get(file.getAbsolutePath()), new TreeDeleter());
		}
	}

	public void savePairwiseMatch(final PairwiseSiftResults results) throws IOException {
		N5FSWriter writer = (N5FSWriter) n5;
		final String matchName = results.getStDataAName() + "-" + results.getStDataBName();
		final String pairwiseGroupName = writer.groupPath("/", "matches", matchName);

		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data container.");
		if (matches.contains(matchName))
			throw new SpatialDataIOException("Match '" + matchName + "' already exists.");
		if (writer.exists(pairwiseGroupName))
			writer.remove(pairwiseGroupName);

		writer.createDataset(
				pairwiseGroupName,
				new long[] {1},
				new int[] {1},
				DataType.OBJECT,
				new GzipCompression());

		writer.setAttribute(pairwiseGroupName, "stDataAname", results.getStDataAName());
		writer.setAttribute(pairwiseGroupName, "stDataBname", results.getStDataBName());
		writer.setAttribute(pairwiseGroupName, "inliers", results.getInliers().size());
		writer.setAttribute(pairwiseGroupName, "candidates", results.getNumCandidates());
		writer.setAttribute(pairwiseGroupName, "genes", results.getGenes());

		writer.writeSerializedBlock(
				results.getInliers(),
				pairwiseGroupName,
				n5.getDatasetAttributes( pairwiseGroupName ),
				0);

		matches.add(matchName);
	}

	public PairwiseSiftResults loadPairwiseMatch(final String stDataAName, final String stDataBName) throws IOException, ClassNotFoundException {
		final String pairwiseGroupName = n5.groupPath("/", "matches", stDataAName + "-" + stDataBName);
		final String matchName = stDataAName + "-" + stDataBName;

		if (!matches.contains(matchName))
			throw new SpatialDataIOException("Match '" + matchName + "' does not exist.");

		final String loadedNameA = n5.getAttribute(pairwiseGroupName, "stDataAname", String.class);
		final String loadedNameB = n5.getAttribute(pairwiseGroupName, "stDataBname", String.class);
		final int numCandidates = n5.getAttribute(pairwiseGroupName, "candidates", int.class);
		final int numInliers = n5.getAttribute(pairwiseGroupName, "inliers", int.class);

		final ArrayList<PointMatch> inliers =
				n5.readSerializedBlock(pairwiseGroupName, n5.getDatasetAttributes(pairwiseGroupName), new long[]{0L});

		if (!loadedNameA.equals(stDataAName) || !loadedNameB.equals(stDataBName) || numInliers != inliers.size())
			throw new SpatialDataIOException("Loaded data for match '" + matchName + "' not consistent.");

		return new PairwiseSiftResults(stDataAName, stDataBName, numCandidates, inliers);
	}

	private static class TreeDeleter extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.delete(file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
			if (e == null) {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			} else {
				// directory iteration failed
				throw e;
			}
		}
	}
}
