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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SpatialDataGroupTest extends AbstractIOTest {

	protected String getPath() {
		return "data/test-container.n5";
	}

	@Test
	public void new_empty_container_is_empty() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPath());
		assertTrue((new File(getPath())).exists(), "File '" + getPath() + "' does not exist.");

		List<String> datasets = container.getDatasets();
		assertTrue(datasets.isEmpty(), "Dataset is not empty.");
	}

	@Test
	public void adding_and_deleting_dataset_works() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPath());

		final String datasetPath = "data";
		final String datasetName = "tmp.h5ad";
		String fullPath = Paths.get(datasetPath, datasetName).toString();
		SpatialDataIO sdio = new AnnDataIO(fullPath, new N5HDF5Writer(fullPath));
		container.addExistingDataset(fullPath);

		File datasetFile = new File(Paths.get(getPath(), "tmp.h5ad").toString());
		assertTrue(container.getDatasets().contains(datasetName));
		assertTrue(datasetFile.exists());

		container.deleteDataset(datasetName);
		assertFalse(container.getDatasets().contains(datasetName));
		assertFalse(datasetFile.exists());
	}

	@Test
	public void adding_existing_dataset_fails() throws IOException {
		SpatialDataContainer container = SpatialDataContainer.createNew(getPath());

		final String datasetPath = "data";
		final String datasetName = "tmp.h5ad";
		String fullPath = Paths.get(datasetPath, datasetName).toString();
		SpatialDataIO sdio = new AnnDataIO(fullPath, new N5HDF5Writer(fullPath));
		container.addExistingDataset(fullPath);

		assertThrows(SpatialDataIOException.class, () -> container.addExistingDataset(fullPath));

		container.deleteDataset(datasetName);
		(new File(fullPath)).delete();
	}
}
