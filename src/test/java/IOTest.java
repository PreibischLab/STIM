import gui.STDataAssembly;
import io.AnnDataIO;
import io.SpatialDataIO;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IOTest {

	@Test
	public void n5_can_be_written() throws IOException {

		final String path = "data/tmp.h5ad";
		STDataAssembly expected = TestUtils.createTrivialAssembly(TestUtils.createTestDataSet());

		try {
			SpatialDataIO sdio = new AnnDataIO(path, N5HDF5Writer::new);
			sdio.writeData(expected);
			STDataAssembly actual = sdio.readData();

			TestUtils.assertRaiEquals(actual.data().getAllExprValues(), expected.data().getAllExprValues());
			TestUtils.assertRaiEquals(actual.data().getLocations(), expected.data().getLocations());
			assertIterableEquals(actual.data().getGeneNames(), expected.data().getGeneNames(), "Gene names not equal.");
			assertIterableEquals(actual.data().getBarcodes(), expected.data().getBarcodes(), "Barcodes not equal.");
		}
		catch (IOException e) {
			fail("Could not write / read file: " + e.getMessage());
		}
		finally {
			Files.delete(Paths.get(path));
		}
	}

}
