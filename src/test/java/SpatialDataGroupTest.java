import gui.STDataAssembly;
import io.AnnDataIO;
import io.SpatialDataGroup;
import io.SpatialDataIO;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SpatialDataGroupTest extends AbstractIOTest {

	protected String getPath() {
		return "data/group.n5";
	}

	@Test
	public void new_empty_group_is_empty() throws IOException {
		SpatialDataGroup group = SpatialDataGroup.createNew(getPath());
		assertTrue((new File(getPath())).exists(), "File '" + getPath() + "' does not exist.");

		List<String> datasets = group.getDatasets();
		assertTrue(datasets.isEmpty(), "Dataset is not empty.");
	}

	@Test
	public void adding_and_deleting_dataset_works() throws IOException {
		SpatialDataGroup group = SpatialDataGroup.createNew(getPath());
		STDataAssembly dataA = new STDataAssembly(TestUtils.createTestDataSet());

		final String datasetPath = "data";
		final String datasetName = "tmp.h5ad";
		Path fullPath = Paths.get(datasetPath, datasetName);
		SpatialDataIO sdio = new AnnDataIO(datasetPath, new N5HDF5Writer(fullPath.toString()));
		group.addExistingDataset(datasetPath, datasetName);

		List<String> datasets = group.getDatasets();
		assertTrue(datasets.contains(datasetName));

		File datasetFile = new File(Paths.get(getPath(), "tmp.h5ad").toString());
		assertTrue(datasetFile.exists());
		group.deleteDataset(datasetName);
		assertFalse(datasetFile.exists());
	}
}
