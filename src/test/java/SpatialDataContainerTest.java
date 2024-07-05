import gui.STDataAssembly;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import io.SpatialDataException;
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
import static org.junit.jupiter.api.Assertions.fail;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SpatialDataContainerTest extends AbstractIOTest {

	@Test
	public void new_empty_container_is_empty() throws IOException {
		final String path = getPlaygroundPath("container.n5");
		SpatialDataContainer container = SpatialDataContainer.createNew(path, executorService);
		assertTrue((new File(path)).exists(), "File '" + path + "' does not exist.");
		assertTrue(SpatialDataContainer.isCompatibleContainer(path), "File '" + path + "' is not a compatible container.");

		List<String> datasets = container.getDatasets();
		assertTrue(datasets.isEmpty(), "Dataset is not empty.");
	}

	@Test
	public void adding_and_deleting_dataset_works() throws IOException {
		final String path = getPlaygroundPath("container.n5");
		SpatialDataContainer container = SpatialDataContainer.createNew(path, executorService);
		final String datasetName = "tmp.h5ad";

		String fullPath = getPlaygroundPath(datasetName);
		createAndWriteData(fullPath);
		container.addExistingDataset(fullPath);

		File datasetFile = new File(Paths.get(path, datasetName).toString());
		assertTrue(container.getDatasets().contains(datasetName));
		assertTrue(datasetFile.exists());

		container.deleteDataset(datasetName);
		assertFalse(container.getDatasets().contains(datasetName));
		assertFalse(datasetFile.exists());
	}

	@Test
	public void linking_does_not_move_dataset() throws IOException {
		final String path = getPlaygroundPath("container.n5");
		SpatialDataContainer container = SpatialDataContainer.createNew(path, executorService);
		final String datasetName = "tmp.h5ad";

		String fullPath = getPlaygroundPath(datasetName);
		createAndWriteData(fullPath);
		container.linkExistingDataset(fullPath);

		File symbolicLink = new File(Paths.get(path, datasetName).toString());
		File physicalFile = new File(fullPath);
		assertTrue(container.getDatasets().contains(datasetName));
		assertTrue(symbolicLink.exists());
		assertTrue(physicalFile.exists());

		container.deleteDataset(datasetName);
		assertFalse(container.getDatasets().contains(datasetName));
		assertFalse(symbolicLink.exists());
		assertTrue(physicalFile.exists());

		physicalFile.delete();
		assertFalse(physicalFile.exists());
	}

	@Test
	public void datasets_can_have_custom_storage_locations() throws IOException {
		final String path = getPlaygroundPath("container.n5");
		SpatialDataContainer container = SpatialDataContainer.createNew(path, executorService);
		final String datasetName = "tmp.h5ad";

		SpatialDataIO sdio = SpatialDataIO.open(getPlaygroundPath(datasetName), executorService);
		sdio.setDataPaths("/test", null, "/anotherTest", "/anotherGeneTest");
		STDataAssembly expected = new STDataAssembly(TestUtils.createTestDataSet());
		sdio.writeData(expected);

		String fullPath = getPlaygroundPath(datasetName);
		container.addExistingDataset(fullPath, "/test", null, "/anotherTest", "/anotherGeneTest");

		STDataAssembly actual = container.openDatasetReadOnly(datasetName).readData();
		TestUtils.compareSTDataAssemblies(actual, expected);
	}

	@Test
	public void opening_datasets_works() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPlaygroundPath("container.n5"), executorService);

		final List<String> datasetNames = Arrays.asList("tmp1.h5ad", "tmp2.zarr");
		final List<STDataAssembly> expectedData = new ArrayList<>();
		for (String dataset : datasetNames) {
			String fullPath = getPlaygroundPath(dataset);
			expectedData.add(createAndWriteData(fullPath));
			container.addExistingDataset(fullPath);
		}

		for (int k = 0; k < datasetNames.size(); k++) {
			STDataAssembly actualData = container.openDatasetReadOnly(datasetNames.get(k)).readData();
			TestUtils.compareSTDataAssemblies(expectedData.get(k), actualData);
		}

		List<SpatialDataIO> allDatasets = container.openAllDatasets();
		assertEquals(2, allDatasets.size());
		assertTrue(allDatasets.stream().noneMatch(Objects::isNull));
	}

	@Test
	public void adding_existing_dataset_fails() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPlaygroundPath("container.n5"), executorService);

		String datasetName = "tmp.h5ad";
		String fullPath = getPlaygroundPath(datasetName);

		try (N5HDF5Writer writer = new N5HDF5Writer(fullPath)) {
			container.addExistingDataset(fullPath);
			assertThrows(SpatialDataException.class, () -> container.addExistingDataset(fullPath));

			container.deleteDataset(datasetName);
			(new File(fullPath)).delete();
		}
		catch (Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	protected STDataAssembly createAndWriteData(String path) throws IOException {
		SpatialDataIO sdio = SpatialDataIO.open(getPlaygroundPath(path), executorService);
		STDataAssembly data = new STDataAssembly(TestUtils.createTestDataSet());
		sdio.writeData(data);
		return data;
	}
}
