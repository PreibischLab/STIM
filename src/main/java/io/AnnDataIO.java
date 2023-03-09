package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STDataUtils;
import filter.FilterFactory;
import filter.MedianFilterFactory;
import gui.STDataExplorer;
import imglib2.TransformedIterableRealInterval;
import net.imglib2.*;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import anndata.AnnDataUtils;
import data.STData;
import data.STDataImgLib2;
import data.STDataStatistics;
import gui.STDataAssembly;
import ij.ImageJ;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.Scale2D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import render.Render;
import util.Grid;

import static gui.RenderThread.*;

public class AnnDataIO
{
	private static final String celltypePath = "/obs/cell_type";
	private static final String locationPath = "/obsm/spatial";

	public static void main( String[] args ) throws IOException
	{
		final String path = System.getProperty("user.dir") + "/data/human-lymph-node.h5ad";
		final File file = new File(path);

		//STData stData = readSlideSeq(file);

//		ImageJFunctions.show(stData.getAllExprValues());
		//final ArrayList< STDataAssembly > data;
		STDataAssembly data;
		String gene;

		data = AnnDataIO.openAllDatasets(new File(path));
		gene = "IGKC";

		RandomAccessibleInterval<DoubleType> loc = data.data().getLocations();
		RandomAccessibleInterval<DoubleType> values = data.data().getAllExprValues();

		/*
		RandomAccessibleInterval<DoubleType> loc2 = ArrayImgs.doubles( loc.dimensionsAsLongArray() );
		RandomAccessibleInterval<DoubleType> values2 = ArrayImgs.doubles( values.dimensionsAsLongArray() );

		Cursor<DoubleType> in = Views.flatIterable( loc ).cursor();
		Cursor<DoubleType> out = Views.flatIterable( loc2 ).cursor();

		while ( in.hasNext() )
			out.next().set( in.next() );

		in = Views.flatIterable( values ).cursor();
		out = Views.flatIterable( values2 ).cursor();

		while ( in.hasNext() )
			out.next().set( in.next() );
		*/
		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < data.data().getGeneNames().size(); ++i )
			geneLookup.put(data.data().getGeneNames().get(i), i);

		AffineTransform2D t = new AffineTransform2D();
		//t.scale( 0.25, 0.25 );
		//t.translate( 1000, 1000 );
		data = new STDataAssembly( new STDataImgLib2(loc, values, data.data().getGeneNames(), data.data().getBarcodes(), geneLookup ), data.statistics(), t, new AffineTransform( 1 ) );

		//data = N5IO.openAllDatasets( new File( System.getProperty("user.dir") + "/data/10x-Visium.n5" ) ).get( 0 );
		//gene = "Calm2";

		//new ImageJ();
		//ImageJFunctions.show( multiThreadedDisplay( data ) ).setDisplayRange( 0 , 1 );
		//SimpleMultiThreading.threadHaltUnClean();

		Interval interval = data.data().getRenderInterval();
		System.out.println( "render interval: " + Util.printInterval( interval ) );
		System.out.println( "exp value base matrix: " + Util.printInterval( data.data().getAllExprValues() ) );
		System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());


		// todo: if filter factory list is not empty, the picture is visualized correctly, since the filter copies data
		List<FilterFactory<DoubleType, DoubleType>> filterFactories = new ArrayList<>();
//		filterFactories.add( new MedianFilterFactory<>( new DoubleType( 0 ), 0 ) );
		BdvOptions options = BdvOptions.options().is2D().numRenderingThreads(Runtime.getRuntime().availableProcessors());

		final RealRandomAccessible<DoubleType> renderRRA = Render.getRealRandomAccessible( data, gene, 0.1, filterFactories );
		BdvStackSource<DoubleType> bdv = BdvFunctions.show(renderRRA, interval, "", options);
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.setDisplayRange( min, 1 );


		// TODO: I would copy the Decoded RAI, and see if errors are still there
		//final RealRandomAccessible< DoubleType > renderRRA = Render.getRealRandomAccessible( data, gene, 0.1, new ArrayList<>() );
		//final RandomAccessibleInterval<DoubleType> img = Views.interval( Views.raster( renderRRA ), data.data().getRenderInterval() );
		//new ImageJ(); ImageJFunctions.show( img );

		// Gene to look at: IGKC, mean filter > 0, Gauß filter ~ 0.1
//		new STDataExplorer( data );

	}

	protected static RandomAccessibleInterval< DoubleType > multiThreadedDisplay( final STDataAssembly data )
	{
		final Interval interval =  data.data().getRenderInterval();
		final List<long[][]> grid =
				Grid.create(
						interval.dimensionsAsLongArray(),
						new int[] { 512, 512 } );
		final long[] min = interval.minAsLongArray();

		System.out.println( "Computing " + grid.size() + " blocks ... " );

		final RandomAccessibleInterval< DoubleType > rendered =
				Views.translate( ArrayImgs.doubles( interval.dimensionsAsLongArray() ), min );

		final long time = System.currentTimeMillis();

		final ExecutorService ex = Executors.newFixedThreadPool( 16 );

		ex.submit(() ->
			grid.parallelStream().forEach(
					gridBlock -> {
						try {
							final long[] offset = new long[ gridBlock[ 0 ].length ];
							for ( int d = 0; d < offset.length; ++d )
								offset[ d ] = gridBlock[ 0 ][ d ] + min[ d ];
	
							final Interval block =
									Intervals.translate(
											new FinalInterval( gridBlock[1] ), // blocksize
											offset ); // block offset + min of the rendered image
	
							System.out.println( "computing block: " + Util.printInterval( block ) );
	
							final RealRandomAccessible< DoubleType > renderRRA = Render.getRealRandomAccessible( data, "IGKC", 0.1, new ArrayList<>() );
							final RandomAccess< DoubleType > ra = Views.raster( renderRRA ).randomAccess();
							final Cursor< DoubleType> cursor = Views.flatIterable( Views.interval( rendered, block ) ).localizingCursor();
	
							while ( cursor.hasNext() )
							{
								final DoubleType t = cursor.next();
								ra.setPosition( cursor );
								t.set( ra.get().get() );
							}
						}
						catch (Exception e) 
						{
							System.out.println( "Error computing block offset=" + Util.printCoordinates( gridBlock[0] ) + "' ... " );
							e.printStackTrace();
						}
					} )
			);

		try
		{
			ex.shutdown();
			ex.awaitTermination( 10000000, TimeUnit.HOURS);
			System.out.println( "done computing ... " + ( System.currentTimeMillis() - time ) + " ms.");
		}
		catch (InterruptedException e)
		{
			System.out.println( "Failed to compute. Error: " + e );
			e.printStackTrace();
			return null ;
		}

		return rendered;
	}

	// TODO: ALL? opens just one ...
	public static STDataAssembly openAllDatasets(final File anndataFile) throws IOException {
		final N5HDF5Reader reader = openAnnData(anndataFile);

		STData stData = readSlideSeq(anndataFile);

		if (stData == null)
			return null;

		final STDataStatistics stat = new STDataStatistics(stData);

		final AffineTransform2D transform = new AffineTransform2D();
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(1, 0);

		return new STDataAssembly(stData, stat, transform, intensityTransform);
	}

	public static N5HDF5Reader openAnnData(final File anndataFile) throws IOException {
		if (!anndataFile.exists())
			throw new RuntimeException("AnnData-path '" + anndataFile.getAbsolutePath() + "' does not exist." );

		return new N5HDF5Reader(anndataFile.getAbsolutePath());
	}

	public static STData readSlideSeq(final File anndataFile) throws IOException {
		long time = System.currentTimeMillis();
		N5Reader reader = new N5HDF5Reader(anndataFile.getAbsolutePath());

		List<String> geneNames = AnnDataUtils.readAnnotation(reader, "/var/_index");
		List<String> barcodes = AnnDataUtils.readAnnotation(reader, "/obs/_index");

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i ) {
			geneLookup.put(geneNames.get(i), i);
		}

		// permute locations, since this is required by STData
		RandomAccessibleInterval<LongType> locations =
				Views.permute((RandomAccessibleInterval<LongType>) AnnDataUtils.readData(reader, locationPath), 0, 1);
		final RandomAccessibleInterval<DoubleType> convertedLocations = Converters.convert(
				locations, (i, o) -> o.set(i.getRealDouble()), new DoubleType());

		RandomAccessibleInterval<RealType> expressionVals = AnnDataUtils.readData(reader, "/X");

		final RandomAccessibleInterval<DoubleType> convertedexpressionVals = Converters.convert(
				expressionVals, (i, o) -> o.set(i.getRealDouble()), new DoubleType());

		STData stdata = new STDataImgLib2(convertedLocations, convertedexpressionVals, geneNames, barcodes, geneLookup);

		if (containsCelltypes(anndataFile)) {
			Img<IntType> celltypeIds = getCelltypeIds(reader);
			stdata.getMetaData().put("celltype", celltypeIds);
			System.out.println("Loading '" + celltypePath + "' as label 'celltype'.");
		}

		System.out.println("Parsing took " + (System.currentTimeMillis() - time) + " ms.");

		return stdata;
	}

	public static Boolean containsCelltypes(final File anndataFile) {
		try (final N5HDF5Reader n5Reader = new N5HDF5Reader(anndataFile.getAbsolutePath())) {
			return n5Reader.exists(celltypePath);
		}
		catch (IOException e) {
			return Boolean.FALSE;
		}
	}

	public static ArrayImg<IntType, IntArray> getCelltypeIds(final N5Reader reader) throws IOException {
		final List<String> cellTypes = AnnDataUtils.readAnnotation(reader, celltypePath);
		final HashMap<String, Integer> typeToIdMap = new HashMap<>();
		final int[] celltypeIds = new int[cellTypes.size()];

		// for categorical arrays, this is redundant -> use codes directly?
		for (int k = 0; k < cellTypes.size(); ++k) {
			final String type = cellTypes.get(k);
			Integer id = typeToIdMap.get(type);
			if (null == id) {
				id = typeToIdMap.size() + 1;
				typeToIdMap.put(type, id);
			}
			celltypeIds[k] = id;
		}

		return ArrayImgs.ints(celltypeIds, celltypeIds.length);
	}
}
