import gui.STDataAssembly;
import io.AnnDataIO;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import io.SpatialDataIOException;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SpatialDataContainerTest extends AbstractIOTest {

	protected String getPath() {
		return "data/test-container.n5";
	}

	@Test
	public void new_empty_container_is_empty() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPath());
		assertTrue((new File(getPath())).exists(), "File '" + getPath() + "' does not exist.");
		assertTrue(SpatialDataContainer.isCompatibleContainer(getPath()), "File '" + getPath() + "' is not a compatible container.");

		List<String> datasets = container.getDatasets();
		assertTrue(datasets.isEmpty(), "Dataset is not empty.");
	}

	@Test
	public void adding_and_deleting_dataset_works() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPath());
		final String datasetName = "tmp.h5ad";

		String fullPath = Paths.get("data", datasetName).toString();
		createAndWriteData(fullPath);
		container.addExistingDataset(fullPath);

		File datasetFile = new File(Paths.get(getPath(), datasetName).toString());
		assertTrue(container.getDatasets().contains(datasetName));
		assertTrue(datasetFile.exists());

		container.deleteDataset(datasetName);
		assertFalse(container.getDatasets().contains(datasetName));
		assertFalse(datasetFile.exists());
	}

	@Test
	public void opening_datasets_works() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPath());

		final List<String> datasetNames = Arrays.asList("tmp1.h5ad", "tmp2.zarr");
		final List<STDataAssembly> expectedData = new ArrayList<>();
		for (String dataset : datasetNames) {
			String fullPath = Paths.get("data", dataset).toString();
			expectedData.add(createAndWriteData(fullPath));
			container.addExistingDataset(fullPath);
		}

		for (int k = 0; k < datasetNames.size(); k++) {
			STDataAssembly actualData = container.openDataset(datasetNames.get(k)).readData();
			TestUtils.compareSTDataAssemblies(expectedData.get(k), actualData);
		}

		List<SpatialDataIO> allDatasets = container.openAllDatasets();
		assertEquals(2, allDatasets.size());
		assertTrue(allDatasets.stream().noneMatch(Objects::isNull));
	}

	@Test
	public void adding_existing_dataset_fails() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPath());

		final String datasetPath = "data";
		final String datasetName = "tmp.h5ad";
		String fullPath = Paths.get(datasetPath, datasetName).toString();
		SpatialDataIO sdio = new AnnDataIO(new N5HDF5Writer(fullPath));
		container.addExistingDataset(fullPath);

		assertThrows(SpatialDataIOException.class, () -> container.addExistingDataset(fullPath));

		container.deleteDataset(datasetName);
		(new File(fullPath)).delete();
	}

	protected STDataAssembly createAndWriteData(String path) throws IOException {
		SpatialDataIO sdio = SpatialDataIO.inferFromName(path);
		STDataAssembly data = new STDataAssembly(TestUtils.createTestDataSet());
		sdio.writeData(data);
		return data;
	}
}
