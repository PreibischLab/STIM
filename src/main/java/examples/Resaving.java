package examples;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gui.STDataAssembly;
import io.SpatialDataContainer;
import io.SpatialDataIO;

import data.NormalizingSTData;
import data.STData;
import io.Path;
import io.TextFileIO;

public class Resaving
{
	protected static void resaveNormalizedSlideSeq() throws IOException {
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.openExisting(path + "slide-seq.n5", service);

		for ( final String puck : pucks ) {
			final STData stData = container.openDataset(puck + ".n5").readData().data();
			final STData normalizedData = new NormalizingSTData( stData );
			final SpatialDataIO sdout = SpatialDataIO.open(puck + "-normalized.n5", service);
			sdout.writeData(new STDataAssembly(normalizedData));
		}
		service.shutdown();
	}

	protected static void resaveSlideSeq() throws IOException {
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.createNew(path + "slide-seq.n5", service);

		for ( final String puck : pucks ) {
			final STData slideSeqOriginal = TextFileIO.readSlideSeq(
					new File( path + "/slide-seq/" + puck + "/BeadLocationsForR.csv" ),
					new File( path + "/slide-seq/" + puck + "/MappedDGEForR.csv" ) );
			SpatialDataIO sdout = SpatialDataIO.open(puck + ".n5", service);
			sdout.writeData(new STDataAssembly(slideSeqOriginal));
		}

		System.out.println( "done" );
		service.shutdown();
	}

	public static void main( String[] args ) throws IOException, InterruptedException, ExecutionException
	{
		resaveSlideSeq();
		resaveNormalizedSlideSeq();
	}
}
