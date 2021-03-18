package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
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
import gui.STDataAssembly;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import render.Render;

public class N5IO
{
	public static Compression defaultCompression = new GzipCompression( 3 );
	public static int[] defaultBlockSize = new int[] { 512, 512 };
	public static int defaultBlockLength = 1024;

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
		writeN5( createN5( n5path ), "data", stdata );

		// load N5
		stdata = readN5( n5path, "data" );

		// display
		final IterableRealInterval< DoubleType > data = stdata.getExprData( "Pcp4" );

		final STDataStatistics stStats = new STDataStatistics( stdata );

		final double displayRadius = stStats.getMedianDistance() / 2.0;
		double gaussRenderSigma = stStats.getMedianDistance() / 4; 
		double gaussRenderRadius = displayRadius;
		final DoubleType outofbounds = new DoubleType( 0 );

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.NONE ) ), stdata.getRenderInterval(), "Pcp4_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );
	}

	public static STDataAssembly openDataset( final File n5Path, final String dataset, final boolean ignoreIntensity ) throws IOException
	{
		return openDataset(N5IO.openN5( n5Path ), dataset, ignoreIntensity );
	}

	public static STDataAssembly openDataset( final N5FSReader n5, final String dataset, final boolean ignoreIntensity ) throws IOException
	{
		final STData slide = /*new NormalizingSTData*/( N5IO.readN5( n5, dataset ) );//.copy();
		final STDataStatistics stat = new STDataStatistics( slide );

		final String groupPath = n5.groupPath( dataset );
		final Set< String > attributes = n5.listAttributes( groupPath ).keySet();

		final AffineTransform2D t = new AffineTransform2D();

		if ( attributes.contains( "transform" ))
			t.set( n5.getAttribute( n5.groupPath( dataset ), "transform", double[].class ) );

		final AffineTransform i = new AffineTransform( 1 );

		if ( ignoreIntensity )
		{
			i.set( 1, 0 );
		}
		else
		{
			double[] values = n5.getAttribute( n5.groupPath( dataset ), "intensity_transform", double[].class );
			i.set( values[ 0 ], values[ 1 ] );
		}

		return new STDataAssembly( slide, stat, t, i );
	}

	public static ArrayList< STDataAssembly > openAllDatasets( final File n5Path, final boolean ignoreIntensity ) throws IOException
	{
		final N5FSReader n5 = N5IO.openN5( n5Path );
		final List< String > datasets = N5IO.listAllDatasets( n5 );

		final ArrayList< STDataAssembly > slides = new ArrayList<>();

		for ( final String dataset : datasets )
			slides.add( openDataset(n5, dataset, ignoreIntensity) );

		return slides;
	}

	public static N5FSWriter createN5( final File n5path ) throws IOException
	{
		if ( n5path == null )
			throw new IOException( "no n5path. stopping." );

		if ( n5path.exists() )
			throw new IOException( n5path.getAbsolutePath() + " exists, cannot save." );

		final N5FSWriter n5 = new N5FSWriter( n5path.getAbsolutePath() );

		n5.setAttribute("/", "numDatasets", 0 );
		n5.setAttribute("/", "datasets", new ArrayList<>() );

		return n5;
	}

	public static N5FSWriter openN5write( final File n5path ) throws IOException
	{
		if ( n5path == null )
			throw new IOException( "no n5path. stopping." );

		if ( !n5path.exists() )
			throw new IOException( n5path.getAbsolutePath() + " does not exist, cannot open for writing." );

		final N5FSWriter n5 = new N5FSWriter( n5path.getAbsolutePath() );

		return n5;
	}

	public static void writeN5(
			final N5FSWriter n5,
			final String datasetName,
			final STData data ) throws IOException, InterruptedException, ExecutionException
	{
		writeN5( n5, datasetName, data, defaultBlockLength, defaultBlockSize, defaultCompression, null );
	}

	public static void writeN5(
			final N5FSWriter n5,
			final String datasetName,
			final STData data,
			final Compression compression ) throws IOException, InterruptedException, ExecutionException
	{
		writeN5( n5, datasetName, data, defaultBlockLength, defaultBlockSize, compression, null );
	}

	public static void writeN5(
			final N5FSWriter n5,
			final String datasetName,
			final STData data,
			final int blockSizeLocations, // 1024
			final int[] blockSizeExpression, // new int[]{ 512, 512 }
			final Compression compression, // new GzipCompression( 3 ); // new RawCompression();
			final ExecutorService service ) throws IOException, InterruptedException, ExecutionException
	{
		final String groupName = n5.groupPath( datasetName );
		
		if ( n5.datasetExists( groupName ) )
			throw new IOException( groupName + " exists. stopping." );

		// update general n5 attributes
		@SuppressWarnings("unchecked")
		final List< String > datasets = n5.getAttribute( "/", "datasets", List.class );

		datasets.add( datasetName );

		n5.setAttribute("/", "numDatasets", datasets.size() );
		n5.setAttribute("/", "datasets", datasets );

		// write the group
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

		System.out.println( "Saving N5 '" + n5.getBasePath() + "', group '" + groupName + "' ... " );
		long time = System.currentTimeMillis();

		final ExecutorService exec;

		if ( service == null )
			exec = Executors.newFixedThreadPool( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		else
			exec = service;

		// save the coordinates
		// numLocations x numDimensions
		N5Utils.save( locations, n5, groupLocations, new int[]{ blockSizeLocations, data.numDimensions() }, compression, exec );

		// save the values
		// numGenes x numCoordinates, 1 block for 1 genes
		N5Utils.save( expr, n5, groupExpression, blockSizeExpression, compression, exec );

		if ( service == null )
			exec.shutdown();

		System.out.println( "Saving N5 '" + n5.getBasePath() + "' group '" + groupName + "' took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public static N5FSReader openN5( final File n5path ) throws IOException
	{
		if ( !n5path.exists() )
			throw new RuntimeException( "n5-path '" + n5path.getAbsolutePath() + "' does not exist." );

		final N5FSReader n5 = new N5FSReader( n5path.getAbsolutePath() );

		return n5;
	}

	public static List< String > listAllDatasets( final N5FSReader n5 ) throws IOException
	{
		final int numDatasets = n5.getAttribute( "/", "numDatasets", Integer.class );

		@SuppressWarnings("unchecked")
		final List< String > datasets = n5.getAttribute( "/", "datasets", List.class );

		if ( numDatasets != datasets.size() )
			System.out.println( "n5 attributes for '" + n5.getBasePath() + "' are inconsistent, number of elements do not match size of list" );

		return datasets;
	}

	public static STDataN5 readN5( final File n5path, final String datasetName ) throws IOException
	{
		return readN5( openN5( n5path ), datasetName );
	}

	public static STDataN5 readN5( final N5FSReader n5, final String datasetName ) throws IOException
	{
		final String groupName = n5.groupPath( datasetName );
		
		if ( n5.datasetExists( groupName ) )
			throw new IOException( groupName + " does not exist. stopping." );

		long time = System.currentTimeMillis();
		System.out.println( "Loading N5 '" + n5.getBasePath() + "', dataset '" + datasetName + "' ... " );

		final int n = n5.getAttribute( groupName, "dim", Integer.class );
		final long numLocations = n5.getAttribute( groupName, "numLocations", Long.class );
		final long numGenes = n5.getAttribute( groupName, "numGenes", Long.class );

		@SuppressWarnings("unchecked")
		final List< String > geneNameList = n5.getAttribute( groupName, "geneList", List.class );

		final RandomAccessibleInterval< DoubleType > locations = N5Utils.open( n5, n5.groupPath( datasetName, "locations" ) ); // size: [numLocations x numDimensions]
		final RandomAccessibleInterval< DoubleType > exprValues = N5Utils.open( n5, n5.groupPath( datasetName, "expression" ) ); // size: [numGenes x numLocations]

		long[] dim1 = new long[ locations.numDimensions() ];
		long[] dim2 = new long[ exprValues.numDimensions() ];

		locations.dimensions( dim1 );
		exprValues.dimensions( dim2 );

		System.out.println( "N5 '" + n5.getBasePath() + "', dataset '" + datasetName + "': dims=" + n + ", numLocations=" + numLocations + ", numGenes=" + numGenes + ", size(locations)=" + Util.printCoordinates( dim1 ) + ", size(exprValues)=" + Util.printCoordinates( dim2 ) );

		if ( locations.dimension( 1 ) != n )
			throw new IOException( "n (dimensionality) stored in the metadata does not match size of locations datastructure" );

		if ( locations.dimension( 0 ) != numLocations )
			throw new IOException( "numLoctions stored in the metadata does not match size of locations datastructure" );

		if ( exprValues.dimension( 1 ) != numLocations )
			throw new IOException( "numLoctions stored in the metadata does not match size of exprValues datastructure" );

		if ( exprValues.dimension( 0 ) != numGenes )
			throw new IOException( "numLoctions stored in the metadata does not match size of exprValues datastructure" );

		System.out.println( "Loading N5 '" + n5.getBasePath() + "', dataset '" + datasetName + "' took " + ( System.currentTimeMillis() - time ) + " ms." );

		final HashMap< String, Integer > geneLookup = new HashMap<>();

		for ( int i = 0; i < geneNameList.size(); ++i )
			geneLookup.put( geneNameList.get( i ), i );

		return new STDataN5( locations, exprValues, geneNameList, geneLookup, n5, new File( n5.getBasePath() ), datasetName );
	}

	public static ArrayList< STDataN5 > readN5All( final File n5path ) throws IOException
	{
		System.out.println( "Loading N5 '" + n5path.getName() + "'... " );

		final N5FSReader n5 = openN5( n5path );

		@SuppressWarnings("unchecked")
		final List< String > datasets = n5.getAttribute( "/", "datasets", List.class );

		System.out.println( "Loading N5 '" + n5path.getName() + "' contains " + datasets.size() + " datasets." );

		final ArrayList< STDataN5 > data = new ArrayList<>();

		for ( final String dataset : datasets )
			data.add( readN5( n5, dataset ) );

		return data;
	}
}
