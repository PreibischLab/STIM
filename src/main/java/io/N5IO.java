package io;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import data.STData;
import importer.TextFileAccess;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;

public class N5IO
{
	public static void main( String[] args ) throws IOException, InterruptedException, ExecutionException
	{
		final File n5path = new File( "test.n5" );

		System.out.println( "n5-path: " + n5path.getAbsolutePath() );

		if ( n5path.exists() )
			TextFileAccess.recursiveDelete( n5path );

		STData data = 
				//STData.createTestDataSet();
				JsonIO.readJSON( new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/full.json.zip" ) );

		N5FSWriter n5 = new N5FSWriter( n5path.getAbsolutePath() );

		n5.createGroup("/coordinates");
		n5.createGroup("/expression");

		// save general parameters and genelist
		n5.setAttribute("/", "dim", data.numDimensions() );
		n5.setAttribute("/", "numCoordinates", data.numCoordinates() );
		n5.setAttribute("/", "numGenes", data.numGenes() );
		n5.setAttribute("/", "geneList", data.getGeneList() );

		final ExecutorService exec = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );

		final Img< DoubleType > coord = data.getCoordinates();
		final Img< DoubleType > expr = data.getValues();

		System.out.println( "Saving N5 ... " );

		long time = System.currentTimeMillis();

		final Compression compression = new GzipCompression( 6 ); // new RawCompression();

		// save the coordinates
		N5Utils.save( coord, n5, "/coordinates", new int[]{ 1024, data.numDimensions() }, compression, exec );

		// save the values
		// numGenes x numCoordinates, 1 block for 1 genes
		N5Utils.save( expr, n5, "/expression", new int[]{ 16, (int)data.numCoordinates() }, compression, exec );

		System.out.println( "Saving N5 took " + ( System.currentTimeMillis() - time ) + " ms." );

		exec.shutdown();
	}
}
