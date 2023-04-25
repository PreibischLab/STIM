package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.imglib2.RandomAccess;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineSet;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import data.STData;
import data.STDataN5;
import data.STDataStatistics;
import gui.STDataAssembly;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;

public class N5IO extends SpatialDataIO {

	public static Compression defaultCompression = new GzipCompression( 3 );
	public static int[] defaultBlockSize = new int[] { 512, 512 };
	public static int defaultBlockLength = 1024;

	protected static String exprValuesGroup = "/expressionValues";
	protected static String locationsGroup = "/locations";

	public N5IO(String path, N5Reader reader) {
		super(path, reader);
	}

	public static STDataAssembly openDataset( final File n5Path, final String dataset ) throws IOException
	{
		return openDataset(N5IO.openN5( n5Path ), dataset );
	}

	public static STDataAssembly openDataset( final N5FSReader n5, final String dataset ) throws IOException
	{
		final STData slide = /*new NormalizingSTData*/( N5IO.readN5( n5, dataset ) );//.copy();

		if ( slide == null )
			return null;

		final STDataStatistics stat = new STDataStatistics( slide );

		final String groupPath = n5.groupPath( dataset );
		final Set< String > attributes = n5.listAttributes( groupPath ).keySet();

		final AffineTransform2D t = new AffineTransform2D();

		if ( attributes.contains( "transform" ))
			t.set( n5.getAttribute( n5.groupPath( dataset ), "transform", double[].class ) );

		final AffineTransform i = new AffineTransform( 1 );

		if ( attributes.contains( "intensity_transform" ))
		{
			double[] values = n5.getAttribute( n5.groupPath( dataset ), "intensity_transform", double[].class );
			i.set( values[ 0 ], values[ 1 ] );
		}
		else
		{
			i.set( 1, 0 );
		}

		return new STDataAssembly( slide, stat, t, i );
	}

	public static ArrayList< STDataAssembly > openAllDatasets( final File n5Path ) throws IOException
	{
		final N5FSReader n5 = N5IO.openN5( n5Path );
		final List< String > datasets = N5IO.listAllDatasets( n5 );

		final ArrayList< STDataAssembly > slides = new ArrayList<>();

		for ( final String dataset : datasets )
			slides.add( openDataset(n5, dataset) );

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

		return new N5FSWriter( n5path.getAbsolutePath() );
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

		if ( n5.exists( groupName ) )
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
		n5.setAttribute( groupName, "barcodeList", data.getBarcodes() );

		final RandomAccessibleInterval< DoubleType > locations = data.getLocations();
		final RandomAccessibleInterval< DoubleType > expr = data.getAllExprValues();

		System.out.print( "Saving N5 '" + n5.getBasePath() + "', group '" + groupName + "' ... " );
		long time = System.currentTimeMillis();

		final ExecutorService exec;

		if ( service == null )
			exec = Executors.newFixedThreadPool( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
		else
			exec = service;

		/*
		if ( data.getMetaData().size() > 0 )
		{
			n5.setAttribute(
					groupName,
					"metadataList",
					data.getMetaData().stream().map( p -> p.getA() ).collect( Collectors.toList() ) );

			for ( final Pair< String, RandomAccessibleInterval<? extends NativeType< ? >> > metadata : data.getMetaData() )
			{
				final String metaLocation = n5.groupPath( datasetName, "meta-" + metadata.getA() );
				N5Utils.save( (RandomAccessibleInterval)(Object)metadata, n5, metaLocation, new int[]{ blockSizeLocations, 1 }, compression, exec );
			}
		}
		*/
		writeMetaData( n5, datasetName, data.getMetaData(), blockSizeLocations, blockSizeExpression, compression, exec );
		
		// save the coordinates
		// numLocations x numDimensions
		N5Utils.save( locations, n5, groupLocations, new int[]{ blockSizeLocations, data.numDimensions() }, compression, exec );

		// save the values
		// numGenes x numCoordinates, 1 block for 1 genes
		N5Utils.save( expr, n5, groupExpression, blockSizeExpression, compression, exec );

		if ( service == null )
			exec.shutdown();

		System.out.println( "took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public static void writeMetaData(
			final N5Writer n5,
			final String datasetName,
			final Map< String, RandomAccessibleInterval< ? extends NativeType< ? > > > metadataMap,
			final int blockSizeLocations, // 1024
			final int[] blockSizeExpression, // new int[]{ 512, 512 }
			final Compression compression, // new GzipCompression( 3 ); // new RawCompression();
			final ExecutorService exec ) throws IOException, InterruptedException, ExecutionException
	{
		if ( metadataMap.keySet().size() > 0 )
		{
			n5.setAttribute(
					n5.groupPath( datasetName ),
					"metadataList",
					metadataMap.keySet() );

			for ( final Entry< String, RandomAccessibleInterval<? extends NativeType< ? >> > metadata : metadataMap.entrySet() )
			{
				final String metaLocation = n5.groupPath( datasetName, "meta-" + metadata.getKey() );
				N5Utils.save( (RandomAccessibleInterval)(Object)metadata.getValue(), n5, metaLocation, new int[]{ blockSizeLocations, 1 }, compression, exec );
			}
		}
	}

	public static N5FSReader openN5( final File n5path ) throws IOException
	{
		if ( !n5path.exists() )
			throw new RuntimeException( "n5-path '" + n5path.getAbsolutePath() + "' does not exist." );

		return new N5FSReader( n5path.getAbsolutePath() );
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
		
		if ( !n5.exists( groupName ) )
			throw new IOException( groupName + " does not exist. stopping." );

		// different type of dataset/group
		if ( !n5.listAttributes( groupName ).containsKey( "numLocations" ) )
			return null;

		long time = System.currentTimeMillis();
		System.out.println( "Loading N5 '" + n5.getBasePath() + "', dataset '" + datasetName + "' ... " );

		final int n = n5.getAttribute( groupName, "dim", Integer.class );
		final long numLocations = n5.getAttribute( groupName, "numLocations", Long.class );
		final long numGenes = n5.getAttribute( groupName, "numGenes", Long.class );

		@SuppressWarnings("unchecked")
		final List< String > geneNameList = n5.getAttribute( groupName, "geneList", List.class );

		@SuppressWarnings("unchecked")
		List< String > barcodeList = n5.getAttribute( groupName, "barcodeList", List.class );

		if ( barcodeList == null || barcodeList.size() == 0 || barcodeList.size() != numLocations )
		{
			System.out.println( "Error reading barcodes from N5, setting empty Strings instead");
			barcodeList = new ArrayList<>();
			for ( int i = 0; i < numLocations; ++i )
				barcodeList.add( "" );
		}

		@SuppressWarnings("unchecked")
		final List< String > metadataList = n5.getAttribute( groupName, "metadataList", List.class );

		final HashMap<String, RandomAccessibleInterval<? extends NativeType<?>>> meta = new HashMap<>();

		if ( metadataList == null )
		{
			System.out.println( "No metadata stored in N5." );
		}
		else if ( metadataList.size() > 0 )
		{
			System.out.println( "Reading " + metadataList.size() + " metadata entries from N5." );

			for ( final String metadata : metadataList )
			{
				final RandomAccessibleInterval metaRAI = N5Utils.open( n5, n5.groupPath( datasetName, "meta-" + metadata ) );
				meta.put( metadata, metaRAI );
			}
		}

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
			throw new IOException( "numLocations stored in the metadata does not match size of locations datastructure" );

		if ( exprValues.dimension( 1 ) != numLocations )
			throw new IOException( "numLocations stored in the metadata does not match size of exprValues datastructure" );

		if ( exprValues.dimension( 0 ) != numGenes )
			throw new IOException( "numLocations stored in the metadata does not match size of exprValues datastructure" );

		System.out.println( "Loading N5 '" + n5.getBasePath() + "', dataset '" + datasetName + "' took " + ( System.currentTimeMillis() - time ) + " ms." );

		final HashMap< String, Integer > geneLookup = new HashMap<>();

		for ( int i = 0; i < geneNameList.size(); ++i )
			geneLookup.put( geneNameList.get( i ), i );

		final STDataN5 data = new STDataN5( locations, exprValues, geneNameList, barcodeList, geneLookup, n5, new File( n5.getBasePath() ), datasetName );

		data.getMetaData().putAll( meta );

		return data;
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

	@Override
	protected void writeHeader(N5Writer writer, STData data) throws IOException {
		writer.createGroup(locationsGroup);
		writer.createGroup(exprValuesGroup);
		writer.setAttribute("/", "dim", data.numDimensions());
		writer.setAttribute("/", "numLocations", data.numLocations());
		writer.setAttribute("/", "numGenes", data.numGenes());
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readLocations() throws IOException {
		return N5Utils.open(n5, locationsGroup);
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readExpressionValues() throws IOException {
		return N5Utils.open(n5, exprValuesGroup);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readBarcodes() throws IOException {
		return n5.getAttribute("/", "barcodeList", List.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readGeneNames() throws IOException {
		return n5.getAttribute("/", "geneList", List.class);
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(AffineSet transform, String name) throws IOException {
		final Set<String> attributes = n5.listAttributes("/").keySet();
		if (attributes.contains(name))
			transform.set(n5.getAttribute("/", name, double[].class ) );
	}

	@Override
	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations) throws IOException {
		try {
			N5Utils.save(locations, writer, locationsGroup, new int[]{options1d.blockSize[0], (int) locations.dimension(1)}, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new SpatialDataIOException("Could not write locations.", e);
		}
	}

	@Override
	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues) throws IOException {
		try {
			N5Utils.save(exprValues, writer, exprValuesGroup, options.blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new SpatialDataIOException("Could not write expression values.", e);
		}
	}

	@Override
	protected void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException {
		writer.setAttribute("/", "barcodeList", barcodes);
	}

	@Override
	protected void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException {
		writer.setAttribute("/", "geneList", geneNames);
	}

	@Override
	protected void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException {
		writer.setAttribute("/", name, transform.getRowPackedCopy());
	}
}
