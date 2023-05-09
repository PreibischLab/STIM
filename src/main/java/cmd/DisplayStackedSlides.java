package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.SpatialDataContainer;
import io.SpatialDataIO;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STDataUtils;
import examples.VisualizeMetadata;
import examples.VisualizeStack;
import filter.FilterFactory;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import gui.RenderThread;
import gui.STDataAssembly;
import gui.celltype.CellTypeExplorer;
import imglib2.TransformedIterableRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import render.Render;

public class DisplayStackedSlides implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input file or N5 container, e.g. -i /home/ssq.n5")
	private String inputPath = null;

	@Option(names = {"-g", "--genes"}, required = true, description = "comma separated list of one or more gene to visualize, e.g. -g Calm2,Ubb")
	private String genes = null;

	@Option(names = {"-md", "--metadata"}, required = false, description = "comma separated list of metadata to visualize, e.g. -md celltype")
	private String metadata = null;

	@Option(names = {"-mdr", "--metadataRadius"}, required = false, description = "radius of metadata spots as a factor of their median distance, e.g. -mdr 2.0 (default: 0.75; in 3d: zSpacing*0.75)")
	private double metadataRadius = 0.75;

	@Option(names = {"-z", "--zSpacingFactor"}, required = false, description = "define the z-spacing between different sections (as a factor of median spacing between sequenced locations), e.g. -z 10.0 (default: 5.0)")
	private double zSpacingFactor = 5.0;

	@Option(names = {"-c", "--contrast"}, description = "comma separated contrast range for BigDataViewer display, e.g. -c '0,255' (default 0.1,5)" )
	private String contrastString = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all)")
	private String datasets = null;

	@Option(names = {"-f", "--singleSpotFilter"}, required = false, description = "filter single spots using the median distance between all spots as threshold (default: false)")
	private boolean singleSpotFilter = false;

	@Option(names = {"-m", "--median"}, required = false, description = "median-filter all spots using a given radius, e.g -m 20.0 (default: no filtering)")
	private Double median = null;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "factor for the sigma of the gaussian used for rendering, corresponds to smoothness, e.g -sf 2.0 (default: 1.5)")
	private double smoothnessFactor = 1.5;

	@Override
	public Void call() throws Exception {

		final boolean useIntensityTransform = false;
		final boolean useTransform = true;

		if (!(new File(inputPath)).exists()) {
			System.out.println("Container / dataset '" + inputPath + "' does not exist. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final List<SpatialDataIO> iodata = new ArrayList<>();
		if (SpatialDataContainer.isCompatibleContainer(inputPath)) {
			SpatialDataContainer container = SpatialDataContainer.openExisting(inputPath, service);

			if (datasets != null && datasets.length() != 0) {
				for (String dataset : datasets.split(",")) {
					System.out.println("Opening dataset '" + dataset + "' in '" + inputPath + "' ...");
					iodata.add(container.openDataset(dataset.trim()));
				}
			}
			else {
				System.out.println("Opening all datasets in '" + inputPath + "' ...");
				iodata.addAll(container.openAllDatasets());
			}
		}
		else {
			System.out.println("Opening dataset '" + inputPath + "' ...");
			iodata.add(SpatialDataIO.inferFromName(inputPath, service));
		}

		if (genes == null || genes.length() == 0) {
			System.out.println("No genes available. stopping.");
			return null;
		}

		List<String> genesToShow = Arrays.stream(genes.split(",")).map(String::trim).collect(Collectors.toList());
		if (genesToShow.size() == 0) {
			System.out.println("No genes available. stopping.");
			return null;
		}

		final List<STDataAssembly> dataToVisualize = new ArrayList<>();
		for (final SpatialDataIO sdio : iodata) {
			STDataAssembly data = sdio.readData();
			dataToVisualize.add(data);

			if (!useTransform)
				data.transform().set(new AffineTransform2D());
			if (!useIntensityTransform)
				data.intensityTransform().set(1, 0);
		}

		if (dataToVisualize.size() == 0) {
			System.out.println("No datasets that contain sequencing data. stopping.");
			return null;
		}

		List< String > metadataList;
		if ( metadata != null && metadata.length() > 0 )
			metadataList = Arrays.asList( metadata.split( "," ) );
		else
			metadataList = new ArrayList<>();

		double minI = RenderThread.min;
		double maxI = RenderThread.max;

		if ( contrastString != null && contrastString.length() > 0 )
		{
			String[] contrastStrings = contrastString.trim().split( "," );

			if ( contrastStrings.length != 2 )
			{
				System.out.println( "contrast string could not parsed " + Arrays.asList( contrastStrings ) + ", ignoring - setting default range (" + minI + "," + maxI + ")" );
			}
			else
			{
				minI = Double.parseDouble( contrastStrings[ 0 ] );
				maxI = Double.parseDouble( contrastStrings[ 1 ] );
	
				System.out.println( "contrast range set to (" + minI + "," + maxI + ")" );
			}
		}

		final DoubleType outofbounds = new DoubleType( 0 );

		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		if ( singleSpotFilter )
		{
			System.out.println( "Using single-spot filtering, radius="  + (dataToVisualize.get( 0 ).statistics().getMedianDistance() * 1.5) );
			filterFactorys.add( new SingleSpotRemovingFilterFactory<>( outofbounds, dataToVisualize.get( 0 ).statistics().getMedianDistance() * 1.5 ) );
		}

		if ( median != null && median > 0.0 )
		{
			System.out.println( "Using median filtering, radius=" + median );
			filterFactorys.add( new MedianFilterFactory<>( outofbounds, median ) );
		}

		BdvStackSource< ? > source = null;

		//
		// Display metadata
		//
		for ( final String meta : metadataList )
		{
			final IntType outofboundsInt = new IntType( -1 );
			final double spotSize = dataToVisualize.get( 0 ).statistics().getMedianDistance() * metadataRadius;
			final HashMap<Long, ARGBType > lut = new HashMap<>();

			final List< FilterFactory< IntType, IntType > > filterFactorysInt = new ArrayList<>();
			
			if ( singleSpotFilter )
			{
				System.out.println( "Using single-spot filtering, radius="  + (dataToVisualize.get( 0 ).statistics().getMedianDistance() * 1.5) );
				filterFactorysInt.add( new SingleSpotRemovingFilterFactory<>( outofboundsInt, dataToVisualize.get( 0 ).statistics().getMedianDistance() * 1.5 ) );
			}

			final RealRandomAccessible< IntType > rra;
			final Interval interval;

			if ( dataToVisualize.size() > 1 )
			{
				final Pair< RealRandomAccessible< IntType >, Interval > stack =
						VisualizeMetadata.createStack( dataToVisualize, meta, zSpacingFactor * 0.75 * spotSize, zSpacingFactor, outofboundsInt, filterFactorysInt, lut );
				rra = stack.getA();
				interval = stack.getB();
			}
			else
			{
				final STDataAssembly slide = dataToVisualize.get( 0 );

				rra = VisualizeMetadata.visualize2d(
						slide.data(),
						meta,
						spotSize,
						slide.transform(),
						outofboundsInt,
						filterFactorysInt,
						lut );

				interval = STDataUtils.getIterableInterval(
						new TransformedIterableRealInterval<>(
								slide.data(),
								slide.transform() ) );

			}

			CellTypeExplorer cte = new CellTypeExplorer( lut );

			final RealRandomAccessible< ARGBType > rraRGB = Render.switchableConvertToRGB( rra, outofboundsInt, new ARGBType(), lut, cte.panel() );

			BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).addTo( source );
			if ( dataToVisualize.size() == 1 )
				options = options.is2D();
			source = BdvFunctions.show( rraRGB, interval, meta, options );
			source.setDisplayRange( 0, 255 );
			source.setDisplayRangeBounds( 0, 2550 );

			cte.panel().setBDV( source.getBdvHandle().getViewerPanel() );
		}

		//
		// Display genes
		//

		// random gene coloring
		Random rnd = new Random( 343 );

		for ( final String gene : genesToShow )
		{
			System.out.println( "Rendering gene: " + gene );

			final RealRandomAccessible< DoubleType > rra;
			final Interval interval;

			if ( dataToVisualize.size() > 1 )
			{
				final Pair< RealRandomAccessible< DoubleType >, Interval > stack =
						VisualizeStack.createStack( dataToVisualize, gene, outofbounds, zSpacingFactor, smoothnessFactor, filterFactorys );
				rra = stack.getA();
				interval = stack.getB();
			}
			else
			{
				rra = Render.getRealRandomAccessible( dataToVisualize.get( 0 ), gene, smoothnessFactor, filterFactorys );

				interval =
						STDataUtils.getIterableInterval(
								new TransformedIterableRealInterval<>(
										dataToVisualize.get( 0 ).data(),
										dataToVisualize.get( 0 ).transform() ) );
			}

			BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).addTo( source );
			if ( dataToVisualize.size() == 1 )
				options = options.is2D();
			source = BdvFunctions.show( rra, interval, gene, options );
			source.setDisplayRange( minI, maxI );
			source.setDisplayRangeBounds( 0, 200 );
			source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.FUSED );
			source.setCurrent();

			if ( genesToShow.size() > 1 )
				source.setColor( Render.randomColor( rnd ) );
		}

		service.shutdown();
		return null;
	}


	public static final void main(final String... args) {
		CommandLine.call(new DisplayStackedSlides(), args);
	}

}
