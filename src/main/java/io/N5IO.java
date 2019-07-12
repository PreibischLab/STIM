package io;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import data.STData;
import data.STDataImgLib2;
import data.STDataStatistics;
import filter.GaussianFilterFactory;
import importer.TextFileAccess;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.DoubleType;
import render.Render;
import transform.TransformIntensities;

public class N5IO
{
	public static void main( String[] args ) throws IOException, InterruptedException, ExecutionException
	{
		// load from Json
		System.out.println( "Loading Json ... " );
		long time = System.currentTimeMillis();

		STData stdata = 
				//STData.createTestDataSet();
				JsonIO.readJSON( new File( Path.getPath() + "patterns_examples_2d/small.json.zip" ) );

		System.out.println( "Loding Json took " + ( System.currentTimeMillis() - time ) + " ms." );

		final File n5path = new File( Path.getPath() + "patterns_examples_2d/small.n5" );
		System.out.println( "n5-path: " + n5path.getAbsolutePath() );

		// write N5
		writeN5( stdata, n5path );

		// load N5
		stdata = readN5( n5path );

		// display
		final IterableRealInterval< DoubleType > data = stdata.getExprData( "Pcp4" );

		final STDataStatistics stStats = new STDataStatistics( stdata );
		TransformIntensities.add( stdata, 1 );

		final double displayRadius = stStats.getMedianDistance() / 2.0;
		double gaussRenderSigma = stStats.getMedianDistance() / 4; 
		double gaussRenderRadius = displayRadius;
		final DoubleType outofbounds = new DoubleType( 0 );

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.getRenderInterval(), "Pcp4_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );


	}

	public static void writeN5( final STData data, final File n5path ) throws IOException, InterruptedException, ExecutionException
	{
		if ( n5path.exists() )
			TextFileAccess.recursiveDelete( n5path );

		final N5FSWriter n5 = new N5FSWriter( n5path.getAbsolutePath() );

		n5.createGroup("/locations");
		n5.createGroup("/expression");

		// save general parameters and genelist
		n5.setAttribute("/", "dim", data.numDimensions() );
		n5.setAttribute("/", "numLocations", data.numLocations() );
		n5.setAttribute("/", "numGenes", data.numGenes() );
		n5.setAttribute("/", "geneList", data.getGeneNames() );

		final ExecutorService exec = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );

		final RandomAccessibleInterval< DoubleType > locations = data.getLocations();
		final RandomAccessibleInterval< DoubleType > expr = data.getAllExprValues();

		System.out.println( "Saving N5 ... " );
		long time = System.currentTimeMillis();

		final Compression compression = new GzipCompression( 6 ); // new RawCompression();

		// save the coordinates
		// numLocations x numDimensions
		N5Utils.save( locations, n5, "/locations", new int[]{ 1024, data.numDimensions() }, compression, exec );

		// save the values
		// numGenes x numCoordinates, 1 block for 1 genes
		N5Utils.save( expr, n5, "/expression", new int[]{ 16, 16384 }, compression, exec );

		System.out.println( "Saving N5 took " + ( System.currentTimeMillis() - time ) + " ms." );

		exec.shutdown();
	}

	public static STData readN5( final File n5path ) throws IOException
	{
		if ( !n5path.exists() )
			throw new RuntimeException( "n5-path '' does not exist." );

		System.out.println( "Loading N5 ... " );
		long time = System.currentTimeMillis();

		N5FSReader n5 = new N5FSReader( n5path.getAbsolutePath() );

		final int n = n5.getAttribute( "/", "dim", Integer.class );
		final long numLocations = n5.getAttribute( "/", "numLocations", Long.class );
		final long numGenes = n5.getAttribute( "/", "numGenes", Long.class );
		final List< String > geneNameList = n5.getAttribute( "/", "geneList", List.class );

		/*
		System.out.println( n );
		System.out.println( numLocations );
		System.out.println( numGenes );

		for ( final String s : geneNameList )
			System.out.println( s);
		*/

		final RandomAccessibleInterval< DoubleType > locations = N5Utils.open( n5, "/locations" );//.openWithBoundedSoftRefCache( n5, "/locations", 100000000 );
		final RandomAccessibleInterval< DoubleType > exprValues = N5Utils.openWithBoundedSoftRefCache( n5, "/expression", 100000000 );

		System.out.println( "Loading N5 took " + ( System.currentTimeMillis() - time ) + " ms." );

		final HashMap< String, Integer > geneLookup = new HashMap<>();

		for ( int i = 0; i < geneNameList.size(); ++i )
			geneLookup.put( geneNameList.get( i ), i );

		return new STDataImgLib2( locations, exprValues, geneNameList, geneLookup );
	}
}
