package io;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import data.NormalizingSTData;
import data.STData;
import data.STDataN5;

public class Resaving
{
	public static STDataN5 saveAndOpenSTDataN5( final STData dataIn, final File n5path ) throws IOException, InterruptedException, ExecutionException
	{
		return saveSTDataN5( dataIn, n5path, true );
	}

	public static void saveSTDataN5( final STData dataIn, final File n5path ) throws IOException, InterruptedException, ExecutionException
	{
		saveSTDataN5( dataIn, n5path, false );
	}

	protected static STDataN5 saveSTDataN5( final STData dataIn, final File n5path, final boolean open ) throws IOException, InterruptedException, ExecutionException
	{
		N5IO.writeN5( dataIn, n5path );

		if ( open )
			return N5IO.readN5( n5path );
		else
			return null;
	}

	protected static void resaveNormalizedSlideSeq() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };
		final String[] pucks = new String[] { "Puck_180531_23" };

		for ( final String puck : pucks )
		{
			final STData stData = N5IO.readN5( new File( path + "slide-seq/" + puck + ".n5" ) );
			final STData normalizedData = new NormalizingSTData( stData );

			saveSTDataN5( normalizedData, new File( path + "slide-seq/" + puck + "-normalized.n5" ) );
		}
	}

	protected static void resaveTextFileExamples() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();
		final String dir = "patterns_examples_2d";

		final STData slideSeq = TextFileIO.read(
				new File( path + "/" + dir + "/full_locations.txt" ),
				new File( path + "/" + dir + "/full_dge_normalized.txt" ) );

		N5IO.writeN5( slideSeq, new File( Path.getPath() + "/" + dir + "/slideSeq.n5" ) );

		final STData slideSeqSmall = TextFileIO.read(
				new File( path + "/" + dir + "/locations.txt" ),
				new File( path + "/" + dir + "/dge_normalized.txt" ) );

		N5IO.writeN5( slideSeqSmall, new File( Path.getPath() + "/" + dir + "/slideSeqSmall.n5" ) );

		final STData slideSeqSmallCut = TextFileIO.read(
				new File( path + "/" + dir + "/locations.txt" ),
				new File( path + "/" + dir + "/dge_normalized_cut.txt" ) );

		N5IO.writeN5( slideSeqSmallCut, new File( Path.getPath() + "/" + dir + "/slideSeqSmallCut.n5" ) );
	}

	protected static void resaveFly3d() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();
		final String dir = "fly_3d_data";

		final STData fly3d = TextFileIO.read(
				new File( path + "/" + dir + "/geometry.txt" ),
				new File( path + "/" + dir + "/sdge_1297_cells_3039_locations_84_markers.txt" ),
				new File( path + "/" + dir + "/gene_names.txt" ) );

		N5IO.writeN5( fly3d, new File( Path.getPath() + "/" + dir + "/fly3d.n5" ) );
	}

	protected static void resaveSlideSeq() throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		for ( final String puck : pucks )
		{
			final STData slideSeqOriginal = TextFileIO.readSlideSeq(
					new File( path + "/slide-seq/" + puck + "/BeadLocationsForR.csv" ),
					new File( path + "/slide-seq/" + puck + "/MappedDGEForR.csv" ) );
	
			N5IO.writeN5( slideSeqOriginal, new File( Path.getPath() + "/slide-seq/" + puck + ".n5" ) );
		}

		System.out.println( "done" );
	}

	public static void main( String[] args ) throws IOException, InterruptedException, ExecutionException
	{
		resaveNormalizedSlideSeq();
		//resaveSlideSeq();
		//resaveFly3d();
		//resaveTextFileExamples();
	}
}
