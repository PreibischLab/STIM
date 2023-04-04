package io;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SpatialDataGroup {

	final private String rootPath;
	final private boolean readOnly;
	final private N5FSReader n5;
	final private String version = "0.1.0";
	final private List<String> datasets = new ArrayList<>();

	protected SpatialDataGroup(String path, boolean readOnly) throws IOException {
		this.rootPath = path;
		this.readOnly = readOnly;

		this.n5 = readOnly ? new N5FSReader(path) : new N5FSWriter(path);
	}

	public static SpatialDataGroup openExisting(String path) throws IOException {
		if (!(new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' does not exist.");
		SpatialDataGroup group = new SpatialDataGroup(path, false);
		group.checkVersion();
		return group;
	}

	public static SpatialDataGroup openForReading(String path) throws IOException {
		if (!(new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' does not exist.");
		SpatialDataGroup group = new SpatialDataGroup(path, true);
		group.checkVersion();
		return group;
	}

	public static SpatialDataGroup createNew(String path) throws IOException {
		if ((new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' already exists.");
		SpatialDataGroup group = new SpatialDataGroup(path, false);
		group.initializeGroup();
		return group;
	}

	protected void initializeGroup() throws IOException {
		N5FSWriter writer = (N5FSWriter) n5;
		writer.setAttribute("/", "spatial_data_group", version);
		writer.createGroup("/matches");
		updateDatasetMetadata();
	}

	protected void checkVersion() throws IOException {
		String actualVersion = n5.getAttribute("/", "spatial-data-group", String.class);
		if (!this.version.equals(actualVersion))
			throw new SpatialDataIOException("Incompatible spatial data group version: expected " + version + ", got " + actualVersion + ".");
	}

	protected void updateDatasetMetadata() throws IOException {
		N5FSWriter writer = (N5FSWriter) n5;
		writer.setAttribute("/", "numDatasets", datasets.size());
		writer.setAttribute("/", "datasets", datasets.toArray());
	}

	public void addExistingDataset(String path) throws IOException {
		Path oldPath = Paths.get(path);
		String datasetName = oldPath.getFileName().toString();

		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data group.");
		if (datasets.contains(datasetName))
			throw new SpatialDataIOException("Dataset '" + datasetName + "' already exists.");

		Files.move(oldPath, Paths.get(rootPath, datasetName));
		datasets.add(datasetName);
		updateDatasetMetadata();
	}

	public void deleteDataset(String datasetName) throws IOException {
		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data group.");
		Files.delete(Paths.get(rootPath, datasetName));
		datasets.remove(datasetName);
		updateDatasetMetadata();
	}

	public List<String> getDatasets() {
		return new ArrayList<>(datasets);
	}

	public String getVersion() {
		return version;
	}
}
