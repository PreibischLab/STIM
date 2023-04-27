import data.STData;
import data.STDataStatistics;
import gui.STDataAssembly;
import io.AnnDataIO;
import io.N5IO;
import io.SpatialDataIO;
import io.SpatialDataIOException;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;
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

	protected String getPath() {
		return "data/tmp.h5ad";
	}

	@Test
	public void readonly_file_cannot_be_written() {
		try (N5HDF5Writer writer = new N5HDF5Writer(getPath())) {
			SpatialDataIO sdio = new AnnDataIO(getPath(), new N5HDF5Reader(getPath()));
			assertThrows(SpatialDataIOException.class, () -> sdio.writeData(null));
		}
		catch (Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("provideIOObjects")
	public void io_with_simple_data_works(IOProvider ioProvider) {
		STDataAssembly expected = new STDataAssembly(TestUtils.createTestDataSet());

		try {
			SpatialDataIO sdio = ioProvider.apply(getPath());
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			TestUtils.compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("provideIOObjects")
	public void io_works_for_transformations(IOProvider ioProvider) {
		STData data = TestUtils.createTestDataSet();
		STDataStatistics stats = new STDataStatistics(data);
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(2, 1);
		final AffineTransform2D transform = new AffineTransform2D();
		transform.rotate(3.14159 / 4);
		STDataAssembly expected = new STDataAssembly(data, stats, transform, intensityTransform);

		try {
			SpatialDataIO sdio = ioProvider.apply(getPath());
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			TestUtils.compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("provideIOObjects")
	public void io_conserves_metadata_order(IOProvider ioProvider) {
		STDataAssembly expected = new STDataAssembly(TestUtils.createTestDataSet());
		long n = expected.data().numLocations();
		List<String> labels = Arrays.asList("annotation_1", "celltypes_2", "annotation_3");
		for (String label : labels)
			expected.data().getMetaData().put(label, ArrayImgs.ints(new int[(int) n], n));

		try {
			SpatialDataIO sdio = ioProvider.apply(getPath());
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			TestUtils.compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: ", e);
		}
	}

	protected static List<Named<IOProvider>> provideIOObjects() throws IOException {
		return Arrays.asList(
				named("AnnData HDF5", (path) -> new AnnDataIO(path, new N5HDF5Writer(path))),
				// TODO: make these work!
//				named("AnnData Zarr", (path) -> new AnnDataIO(path, new N5ZarrWriter(path))),
//				named("AnnData N5", (path) -> new AnnDataIO(path, new N5FSWriter(path))),
				named("N5 HDF5", (path) -> new N5IO(path, new N5HDF5Writer(path))),
				named("N5 Zarr", (path) -> new N5IO(path, new N5ZarrWriter(path))),
				named("N5 FS", (path) -> new N5IO(path, new N5FSWriter(path)))
		);
	}

	private static interface IOProvider {
		SpatialDataIO apply(String path) throws IOException;
	}
}
