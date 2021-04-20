package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5FSReader;

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
import io.N5IO;
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

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-g", "--genes"}, required = true, description = "comma separated list of one or more gene to visualize, e.g. -g Calm2,Ubb")
	private String genes = null;

	@Option(names = {"-md", "--metadata"}, required = false, description = "comma separated list of metadata to visualize, e.g. -md celltype")
	private String metadata = null;

	@Option(names = {"-mdr", "--metadataRadius"}, required = false, description = "radius of metadata spots as a factor of their median distance, e.g. -mdr 2.0 (default: 0.75; in 3d: zSpacing*0.75)")
	private double metadataRadius = 0.75;

	@Option(names = {"-z", "--zSpacingFactor"}, required = false, description = "define the z-spacing between differnt sections (as a factor of median spacing between sequenced locations), e.g. -z 10.0 (default: 5.0)")
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

		final N5FSReader n5 = N5IO.openN5( new File( input ) );

		List< String > inputDatasets;

		if ( datasets == null || datasets.length() == 0 )
			inputDatasets = N5IO.listAllDatasets( n5 );//Arrays.asList( n5.list( "/" ) );
		else
			inputDatasets = Arrays.asList( datasets.split( "," ) );

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets available. stopping.");
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

		final ArrayList< STDataAssembly > slides = new ArrayList<>();

		for ( final String dataset : inputDatasets )
		{
			final STDataAssembly st = N5IO.openDataset(n5, dataset );

			if ( !useTransform )
				st.transform().set( new AffineTransform2D() );

			if ( !useIntensityTransform )
				st.intensityTransform().set( 1, 0 );

			slides.add( st );
		}

		final DoubleType outofbounds = new DoubleType( 0 );

		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		if ( singleSpotFilter )
		{
			System.out.println( "Using single-spot filtering, radius="  + (slides.get( 0 ).statistics().getMedianDistance() * 1.5) );
			filterFactorys.add( new SingleSpotRemovingFilterFactory<>( outofbounds, slides.get( 0 ).statistics().getMedianDistance() * 1.5 ) );
		}

		if ( median != null && median > 0.0 )
		{
			System.out.println( "Using median filtering, radius=" + median );
			filterFactorys.add( new MedianFilterFactory<>( outofbounds, median ) );
		}

		List< String > genesToShow;

		if ( genes == null || genes.length() == 0 )
		{
			System.out.println( "no genes defined. stopping.");
			return null;
		}
		else
			genesToShow = Arrays.asList( genes.split( "," ) );

		if ( genesToShow.size() == 0 )
		{
			System.out.println( "no genes defined. stopping.");
			return null;
		}

		BdvStackSource< ? > source = null;

		//
		// Display metadata
		//
		for ( final String meta : metadataList )
		{
			final IntType outofboundsInt = new IntType( -1 );
			final double spotSize = slides.get( 0 ).statistics().getMedianDistance() * metadataRadius;
			final HashMap<Long, ARGBType > lut = new HashMap<>();

			final List< FilterFactory< IntType, IntType > > filterFactorysInt = new ArrayList<>();
			
			if ( singleSpotFilter )
			{
				System.out.println( "Using single-spot filtering, radius="  + (slides.get( 0 ).statistics().getMedianDistance() * 1.5) );
				filterFactorysInt.add( new SingleSpotRemovingFilterFactory<>( outofboundsInt, slides.get( 0 ).statistics().getMedianDistance() * 1.5 ) );
			}

			final RealRandomAccessible< IntType > rra;
			final Interval interval;

			if ( slides.size() > 1 )
			{
				final Pair< RealRandomAccessible< IntType >, Interval > stack =
						VisualizeMetadata.createStack( slides, meta, zSpacingFactor * 0.75 * spotSize, zSpacingFactor, outofboundsInt, filterFactorysInt, lut );
				rra = stack.getA();
				interval = stack.getB();
			}
			else
			{
				final STDataAssembly slide = slides.get( 0 );

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
			if ( slides.size() == 1 )
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

			if ( slides.size() > 1 )
			{
				final Pair< RealRandomAccessible< DoubleType >, Interval > stack =
						VisualizeStack.createStack( slides, gene, outofbounds, zSpacingFactor, smoothnessFactor, filterFactorys );
				rra = stack.getA();
				interval = stack.getB();
			}
			else
			{
				rra = Render.getRealRandomAccessible( slides.get( 0 ), gene, smoothnessFactor, filterFactorys );

				interval =
						STDataUtils.getIterableInterval(
								new TransformedIterableRealInterval<>(
										slides.get( 0 ).data(),
										slides.get( 0 ).transform() ) );
			}

			BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).addTo( source );
			if ( slides.size() == 1 )
				options = options.is2D();
			source = BdvFunctions.show( rra, interval, gene, options );
			source.setDisplayRange( minI, maxI );
			source.setDisplayRangeBounds( 0, 200 );
			source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.FUSED );
			source.setCurrent();

			if ( genesToShow.size() > 1 )
				source.setColor( Render.randomColor( rnd ) );
		}

		return null;
	}


	public static final void main(final String... args) {
		CommandLine.call(new DisplayStackedSlides(), args);
	}

}
