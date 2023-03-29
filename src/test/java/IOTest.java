import data.STData;
import data.STDataStatistics;
import data.STDataText;
import gui.STDataAssembly;
import io.AnnDataIO;
import io.SpatialDataIO;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IOTest {

	@Test
	public void n5_can_be_written() throws IOException {

		final String path = "data/tmp.h5ad";

		STDataAssembly stData = createTrivialAssembly(createTestDataSet());
		SpatialDataIO stio = new AnnDataIO(path, N5HDF5Writer::new);
		stio.writeData(stData);

		Files.delete(Paths.get(path));
	}

	public static STData createTestDataSet()
	{
		final ArrayList<Pair< double[], String >> coordinates = new ArrayList<>();
		coordinates.add( new ValuePair<>( new double[] { -1, 1 }, "ATTA" ) );
		coordinates.add( new ValuePair<>( new double[] { 2.1, 2 }, "GTTC" ) );
		coordinates.add( new ValuePair<>( new double[] { 17.1, -5.1 }, "CCCT" ) );

		final HashMap< String, double[] > geneMap = new HashMap<>();

		geneMap.put( "gene1", new double[] { 1.1, 2.2, 13.1 } );
		geneMap.put( "gene2", new double[] { 0.0, 23.12, 1.1 } );
		geneMap.put( "gene3", new double[] { 4.1, 0.0, 7.65 } );
		geneMap.put( "gene4", new double[] { 0.0, 6.12, 5.12 } );

		return new STDataText( coordinates, geneMap );
	}

	protected STDataAssembly createTrivialAssembly(STData data) {
		final STDataStatistics stat = new STDataStatistics(data);
		final AffineTransform2D transform = new AffineTransform2D();
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(1, 0);

		return new STDataAssembly(data, stat, transform, intensityTransform);
	}
}
