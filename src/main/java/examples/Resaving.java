package examples;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.RawCompression;

import data.NormalizingSTData;
import data.STData;
import data.STDataN5;
import io.N5IO;
import io.Path;
import io.TextFileIO;

public class Resaving
{
	protected static void resaveNormalizedSlideSeq() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final N5FSWriter n5 = N5IO.createN5( new File( path + "slide-seq-normalized.n5" ) );

		for ( final String puck : pucks )
		{
			final STData stData = N5IO.readN5( new File( path + "slide-seq/" + puck + ".n5" ), "/" );
			final STData normalizedData = new NormalizingSTData( stData );

			N5IO.writeN5( n5, puck, normalizedData );
		}
	}

	protected static void resaveTextFileExamples() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();

		final N5FSWriter n5 = N5IO.createN5( new File( path + "examples.n5" ) );

		String dir = "patterns_examples_2d";

		final STData slideSeq = TextFileIO.read(
				new File( path + "/" + dir + "/full_locations.txt" ),
				new File( path + "/" + dir + "/full_dge_normalized.txt" ) );

		N5IO.writeN5( n5, "slideSeq", slideSeq );

		final STData slideSeqSmall = TextFileIO.read(
				new File( path + "/" + dir + "/locations.txt" ),
				new File( path + "/" + dir + "/dge_normalized.txt" ) );

		N5IO.writeN5( n5, "slideSeqSmall", slideSeqSmall );

		final STData slideSeqSmallCut = TextFileIO.read(
				new File( path + "/" + dir + "/locations.txt" ),
				new File( path + "/" + dir + "/dge_normalized_cut.txt" ) );

		N5IO.writeN5( n5, "slideSeqSmallCut", slideSeqSmallCut );

		dir = "fly_3d_data";

		final STData fly3d = TextFileIO.read(
				new File( path + "/" + dir + "/geometry.txt" ),
				new File( path + "/" + dir + "/sdge_1297_cells_3039_locations_84_markers.txt" ),
				new File( path + "/" + dir + "/gene_names.txt" ) );

		N5IO.writeN5( n5, "fly3d", fly3d );
	}

	protected static void resaveSlideSeq() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final N5FSWriter n5 = N5IO.createN5( new File( path + "slide-seq.n5" ) );

		for ( final String puck : pucks )
		{
			final STData slideSeqOriginal = TextFileIO.readSlideSeq(
					new File( path + "/slide-seq/" + puck + "/BeadLocationsForR.csv" ),
					new File( path + "/slide-seq/" + puck + "/MappedDGEForR.csv" ) );
	
			N5IO.writeN5( n5, puck, slideSeqOriginal );
		}

		System.out.println( "done" );
	}

	public static void resaveOldN5() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();
		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		System.out.println( path );

		//final N5FSWriter n5gzip6 = N5IO.createN5( new File( path + "slide-seq-normalized-gzip6.n5" ) );
		final N5FSWriter n5gzip3 = N5IO.createN5( new File( path + "slide-seq-normalized.n5" ) );
		//final N5FSWriter n5raw = N5IO.createN5( new File( path + "slide-seq-normalized-raw.n5" ) );

		for ( final String puck : pucks )
		{
			System.out.println( new File( path + "slide-seq/" + puck + "-normalized.n5" ).getAbsolutePath() );

			final STData data = N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ), "/" );
			//final STData data = STDataUtils.createTestDataSet();
			//final STData data = JsonIO.readJSON( new File( Path.getPath() + "patterns_examples_2d/small.json.zip" ) );

			//N5IO.writeN5( n5gzip6, puck, data, new GzipCompression( 6 ) );
			N5IO.writeN5( n5gzip3, puck, data, new GzipCompression( 3 ) );
			//N5IO.writeN5( n5raw, puck, data, new RawCompression() );
		}

		System.out.println( "done" );
	}

	public static void main( String[] args ) throws IOException, InterruptedException, ExecutionException
	{
		resaveOldN5();
		//resaveNormalizedSlideSeq();
		//resaveSlideSeq();
		//resaveTextFileExamples();
	}
}
