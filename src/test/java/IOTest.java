import data.STData;
import data.STDataStatistics;
import gui.STDataAssembly;
import io.AnnDataIO;
import io.SpatialDataIO;
import io.SpatialDataIOException;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Named.named;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IOTest extends AbstractIOTest {

	@Test
	public void readonly_file_cannot_be_written() {
		try (N5HDF5Writer writer = new N5HDF5Writer(getPlaygroundPath("data.h5ad"))) {
			SpatialDataIO sdio = new AnnDataIO(() -> writer, null);
			assertThrows(SpatialDataIOException.class, () -> sdio.writeData(null));
		}
		catch (Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("provideDatasetPaths")
	public void io_with_simple_data_works(String path) {
		STDataAssembly expected = new STDataAssembly(TestUtils.createTestDataSet());

		try {
			SpatialDataIO sdio = SpatialDataIO.inferFromName(getPlaygroundPath(path));
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			TestUtils.compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("provideDatasetPaths")
	public void io_works_for_transformations(String path) {
		STData data = TestUtils.createTestDataSet();
		STDataStatistics stats = new STDataStatistics(data);
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(2, 1);
		final AffineTransform2D transform = new AffineTransform2D();
		transform.rotate(3.14159 / 4);
		STDataAssembly expected = new STDataAssembly(data, stats, transform, intensityTransform);

		try {
			SpatialDataIO sdio = SpatialDataIO.inferFromName(getPlaygroundPath(path));
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			TestUtils.compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("provideDatasetPaths")
	public void io_conserves_metadata_order(String path) {
		STDataAssembly expected = new STDataAssembly(TestUtils.createTestDataSet());
		long n = expected.data().numLocations();
		List<String> labels = Arrays.asList("annotation_1", "celltypes_2", "annotation_3");
		for (String label : labels)
			expected.data().getMetaData().put(label, ArrayImgs.ints(new int[(int) n], n));

		try {
			SpatialDataIO sdio = SpatialDataIO.inferFromName(getPlaygroundPath(path));
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			TestUtils.compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: ", e);
		}
	}

	protected static List<Named<String>> provideDatasetPaths() throws IOException {
		return Arrays.asList(
				named("AnnData HDF5", "data.h5ad"),
				// TODO: make these work!
//				named("AnnData Zarr", "data.zarrad"),
//				named("AnnData N5", "data.n5ad"),
				named("N5 HDF5", "data.h5"),
				named("N5 Zarr", "data.zarr"),
				named("N5 FS", "data.n5")
		);
	}
}
