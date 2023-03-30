import data.STData;
import data.STDataStatistics;
import gui.STDataAssembly;
import io.AnnDataIO;
import io.SpatialDataIO;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IOTest {

	protected String path;
	protected File file;

	@BeforeEach
	public void setupFile() {
		path = "data/tmp.h5ad";
		file = new File(path);
	}

	@AfterEach
	public void cleanUp() {
		if (file.exists())
			file.delete();
	}

	@Test
	public void readonly_file_cannot_be_written() {
		try (N5HDF5Writer writer = new N5HDF5Writer(path)) {
			SpatialDataIO sdio = new AnnDataIO(path, new N5HDF5Reader(path));
			assertThrows(SpatialDataIO.SpatialDataIOException.class, () -> sdio.writeData(null));
		}
		catch (Exception e) {
			fail("Could not write / read file: " + e.getMessage());
		}
	}

	@Test
	public void io_with_simple_n5_works() {
		STDataAssembly expected = TestUtils.createTrivialAssembly(TestUtils.createTestDataSet());

		try {
			SpatialDataIO sdio = new AnnDataIO(path, new N5HDF5Writer(path));
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: " + e.getMessage());
		}
	}

	@Test
	public void io_works_for_transformations() throws IOException {
		STData data = TestUtils.createTestDataSet();
		STDataStatistics stats = new STDataStatistics(data);
		final AffineTransform2D transform = new AffineTransform2D();
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(2, 1);
		transform.rotate(3.14159 / 4);
		STDataAssembly expected = new STDataAssembly(data, stats, transform, intensityTransform);

		try {
			SpatialDataIO sdio = new AnnDataIO(path, new N5HDF5Writer(path));
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			compareSTDataAssemblies(actual, expected);
		}
		catch (IOException e) {
			fail("Could not write / read file: " + e.getMessage());
		}
	}

	protected static void compareSTDataAssemblies(STDataAssembly actual, STDataAssembly expected) {
		TestUtils.assertRaiEquals(actual.data().getAllExprValues(), expected.data().getAllExprValues());
		TestUtils.assertRaiEquals(actual.data().getLocations(), expected.data().getLocations());
		assertLinesMatch(actual.data().getGeneNames(), expected.data().getGeneNames(), "Gene names not equal.");
		assertLinesMatch(actual.data().getBarcodes(), expected.data().getBarcodes(), "Barcodes not equal.");
		assertArrayEquals(actual.transform().getRowPackedCopy(), expected.transform().getRowPackedCopy(), "2D transforms not equal.");
		assertArrayEquals(actual.intensityTransform().getRowPackedCopy(), expected.intensityTransform().getRowPackedCopy(), "Intensity transforms not equal.");
	}
}
