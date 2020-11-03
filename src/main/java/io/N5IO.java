package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import data.STDataN5;
import data.STDataStatistics;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
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
				//STDataUtils.createTestDataSet();
				JsonIO.readJSON( new File( Path.getPath() + "patterns_examples_2d/small.json.zip" ) );

		System.out.println( "Loding Json took " + ( System.currentTimeMillis() - time ) + " ms." );

		final File n5path = new File( Path.getPath() + "patterns_examples_2d/small.n5" );
		System.out.println( "n5-path: " + n5path.getAbsolutePath() );

		// write N5
		writeN5( "data", stdata, n5path );

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

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.NONE ) ), stdata.getRenderInterval(), "Pcp4_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );
	}

	public static void writeN5( final String name, final STData data, final File n5path ) throws IOException, InterruptedException, ExecutionException
	{
		final HashMap< String, STData > datasets = new HashMap<String, STData>();

		datasets.put( name, data );

		writeN5( datasets, n5path, new GzipCompression( 6 ) );
	}

	public static void writeN5(
			final HashMap< String, STData > datasets,
			final File n5path,
			final Compression compression ) throws IOException, InterruptedException, ExecutionException
	{
		writeN5( datasets, n5path, 1024, new int[]{ 512, 512 }, compression, null );
	}

	public static void writeN5(
			final HashMap< String, STData > datasets,
			final File n5path,
			final int blockSizeLocations, // 1024
			final int[] blockSizeExpression, // new int[]{ 512, 512 }
			final Compression compression, // new GzipCompression( 6 ); // new RawCompression();
			final ExecutorService service ) throws IOException, InterruptedException, ExecutionException
	{
		if ( datasets == null || datasets.size() == 0 )
			throw new IOException( "no data to save. stopping." );

		if ( n5path == null )
			throw new IOException( "no n5path. stopping." );

		if ( n5path.exists() )
			throw new IOException( n5path.getAbsolutePath() + " exists, cannot save." );

		final N5FSWriter n5 = new N5FSWriter( n5path.getAbsolutePath() );

		final ArrayList< String > datasetNames = new ArrayList<String>( datasets.keySet() );
		
		n5.setAttribute("/", "numDatasets", datasetNames.size() );
		n5.setAttribute("/", "datasets", datasetNames );

		final ExecutorService exec;

		if ( service == null )
			exec = Executors.newFixedThreadPool( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		else
			exec = service;

		for ( final String datasetName : datasetNames )
		{
			final STData data = datasets.get( datasetName );
			final String groupName = n5.groupPath( datasetName );
			
			if ( n5.datasetExists( groupName ) )
				throw new IOException( groupName + " exists. stopping." );
			
			n5.createGroup( groupName );

			final String groupLocations = n5.groupPath( datasetName, "locations" );
			final String groupExpression = n5.groupPath( datasetName, "expression" );

			n5.createGroup( groupLocations );
			n5.createGroup( groupExpression );
	
			// save general parameters and genelist
			n5.setAttribute( groupName, "dim", data.numDimensions() );
			n5.setAttribute( groupName, "numLocations", data.numLocations() );
			n5.setAttribute( groupName, "numGenes", data.numGenes() );
			n5.setAttribute( groupName, "geneList", data.getGeneNames() );
		
			final RandomAccessibleInterval< DoubleType > locations = data.getLocations();
			final RandomAccessibleInterval< DoubleType > expr = data.getAllExprValues();
	
			System.out.println( "Saving N5 '" + n5path.getAbsolutePath() + "', group '" + groupName + "' ... " );
			long time = System.currentTimeMillis();

			// save the coordinates
			// numLocations x numDimensions
			N5Utils.save( locations, n5, groupLocations, new int[]{ blockSizeLocations, data.numDimensions() }, compression, exec );
	
			// save the values
			// numGenes x numCoordinates, 1 block for 1 genes
			N5Utils.save( expr, n5, groupExpression, blockSizeExpression, compression, exec );

			System.out.println( "Saving N5 '" + n5path.getName() + "' group '" + groupName + "' took " + ( System.currentTimeMillis() - time ) + " ms." );
		}

		if ( service == null )
			exec.shutdown();
	}

	public static STDataN5 readN5( final File n5path, final String datasetName ) throws IOException
	{
		if ( !n5path.exists() )
			throw new RuntimeException( "n5-path '" + n5path.getAbsolutePath() + "' does not exist." );

		System.out.println( "Loading N5 '" + n5path.getName() + "'... " );

		long time = System.currentTimeMillis();

		final N5FSReader n5 = new N5FSReader( n5path.getAbsolutePath() );
		
		final String groupName = n5.groupPath( datasetName );
		
		if ( n5.datasetExists( groupName ) )
			throw new IOException( groupName + " does not exist. stopping." );

		final int n = n5.getAttribute( groupName, "dim", Integer.class );
		final long numLocations = n5.getAttribute( groupName, "numLocations", Long.class );
		final long numGenes = n5.getAttribute( groupName, "numGenes", Long.class );

		@SuppressWarnings("unchecked")
		final List< String > geneNameList = n5.getAttribute( groupName, "geneList", List.class );

		final RandomAccessibleInterval< DoubleType > locations = N5Utils.open( n5, n5.groupPath( datasetName, "locations" ) );//.openWithBoundedSoftRefCache( n5, "/locations", 100000000 );
		final RandomAccessibleInterval< DoubleType > exprValues = N5Utils.open( n5, n5.groupPath( datasetName, "expression" ) );

		long[] dim1 = new long[ locations.numDimensions() ];
		long[] dim2 = new long[ exprValues.numDimensions() ];

		locations.dimensions( dim1 );
		exprValues.dimensions( dim2 );

		System.out.println( "N5 '" + n5path.getName() + "', group '" + groupName + "': dims=" + n + ", numLocations=" + numLocations + ", numGenes=" + numGenes + ", size(locations)=" + Util.printCoordinates( dim1 ) + ", size(exprValues)=" + Util.printCoordinates( dim2 ) );

		if ( locations.dimension( 1 ) != n )
			throw new IOException( "n (dimensionality) stored in the metadata does not match size of locations datastructure" );

		if ( locations.dimension( 0 ) != numLocations )
			throw new IOException( "numLoctions stored in the metadata does not match size of locations datastructure" );

		if ( exprValues.dimension( 1 ) != numLocations )
			throw new IOException( "numLoctions stored in the metadata does not match size of exprValues datastructure" );

		if ( exprValues.dimension( 0 ) != numGenes )
			throw new IOException( "numLoctions stored in the metadata does not match size of exprValues datastructure" );

		System.out.println( "Loading N5 '" + n5path.getName() + "' took " + ( System.currentTimeMillis() - time ) + " ms." );

		final HashMap< String, Integer > geneLookup = new HashMap<>();

		for ( int i = 0; i < geneNameList.size(); ++i )
			geneLookup.put( geneNameList.get( i ), i );

		return new STDataN5( locations, exprValues, geneNameList, geneLookup, n5, n5path );

	}

	public static ArrayList< STDataN5 > readN5( final File n5path ) throws IOException
	{
		if ( !n5path.exists() )
			throw new RuntimeException( "n5-path '" + n5path.getAbsolutePath() + "' does not exist." );

		System.out.println( "Loading N5 '" + n5path.getName() + "'... " );

		long time = System.currentTimeMillis();

		final N5FSReader n5 = new N5FSReader( n5path.getAbsolutePath() );

		final int numDatasets = n5.getAttribute( "/", "numDatasets", Integer.class );

		@SuppressWarnings("unchecked")
		final List< String > datasets = n5.getAttribute( "/", "datasets", List.class );
	}
}
