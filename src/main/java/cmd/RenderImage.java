package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5FSReader;

import align.AlignTools;
import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import gui.STDataAssembly;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import imglib2.ImgLib2Util;
import io.N5IO;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import render.Render;

public class RenderImage implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all)")
	private String datasets = null;

	@Option(names = {"-g", "--genes"}, required = true, description = "comma separated list of one or more genes, e.g. -g 'Calm2,Pcp4,Ptgds'")
	private String genes = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "scaling of the image, e.g. -s 0.5 (default: 0.05)")
	private double scale = 0.05;

	@Option(names = {"-f", "--singleSpotFilter"}, required = false, description = "filter single spots using the median distance between all spots as threshold (default: false)")
	private boolean singleSpotFilter = false;

	@Option(names = {"-m", "--median"}, required = false, description = "median-filter all spots using a given radius, e.g -m 20.0 (default: no filtering)")
	private Double median = null;

	@Override
	public Void call() throws Exception {

		final boolean displayAsImageInstance = true;
		//final boolean ignoreIntensityAdjustment = true;
		final boolean useTransform = true;

		if ( displayAsImageInstance )
			new ImageJ();

		final N5FSReader n5 = N5IO.openN5( new File( input ) );

		final List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		List< String > inputDatasets, geneList;

		if ( datasets == null || datasets.length() == 0 )
			inputDatasets = Arrays.asList( n5.list( "/" ) );
		else
			inputDatasets = Arrays.asList( datasets.split( "," ) );

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets available. stopping.");
			return null;
		}

		if ( genes == null || genes.length() == 0 )
			geneList = Arrays.asList( n5.list( "/" ) );
		else
			geneList = Arrays.asList( genes.split( "," ) );

		if ( geneList.size() == 0 )
		{
			System.out.println( "no genes available. stopping.");
			return null;
		}

		for ( final String dataset : inputDatasets )
		{
			final STDataAssembly stAssembly =
					N5IO.openDataset(n5, dataset);

			if ( stAssembly != null )
				data.add( new ValuePair<STData, AffineTransform2D>(
						stAssembly.data(),
						useTransform ? stAssembly.transform() : new AffineTransform2D() ) );
		}

		if ( data.size() == 0 )
		{
			System.out.println( "No datasets that contain sequencing data. stopping." );
			return null;
		}

		for ( final String gene : geneList )
		{
			System.out.println( "Rendering gene " + gene );

			ImagePlus imp = AlignTools.visualizeList( data, scale, gene, displayAsImageInstance );
			imp.setTitle( gene );
		}

		return null;
	}

	public static ImagePlus visualizeList( final List< Pair< STData, AffineTransform2D > > data, final double scale, final String gene, final boolean show )
	{
		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( scale );

		final Interval interval =
				STDataUtils.getCommonIterableInterval(
						data.stream().map( entry -> 
							ImgLib2Util.transformInterval(
									entry.getA().getRenderInterval(),
									entry.getB().copy().preConcatenate( tS ) )
						).collect( Collectors.toList() ) );

		final Interval finalInterval = Intervals.expand( interval, 100 );

		System.out.println( "Rendering interval: " + Util.printInterval( finalInterval ) );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		for ( Pair< STData, AffineTransform2D > pair : data )
		{
			final AffineTransform2D tA = pair.getB().copy();
			tA.preConcatenate( tS );

			final RandomAccessibleInterval<DoubleType> vis =
					display(
							pair.getA(),
							new STDataStatistics( pair.getA() ),
							pair.getB().copy().preConcatenate( tS ),
							null,
							gene,
							finalInterval );

			stack.addSlice(pair.getA().toString(), ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), pair.getA().toString(), null ).getProcessor());
		}

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();

		if ( show )
			imp.show();

		return imp;
	}

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final AffineGet coordinateTransform,
			final AffineGet intensityTransform,
			final String gene,
			final Interval renderInterval  )
	{
		//Render.getRealIterable( stdata, gene, filterFactorys );
		//System.out.println( "Mean distance: " + stStats.getMeanDistance());
		//System.out.println( "Median distance: " + stStats.getMedianDistance() );
		//System.out.println( "Max distance: " + stStats.getMaxDistance() );

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance();

		final DoubleType outofbounds = new DoubleType( 0 );

		IterableRealInterval< DoubleType > data = stdata.getExprData( gene );

		/*
		data = Converters.convert(
				data,
				new Converter< DoubleType, DoubleType >()
				{
					@Override
					public void convert( final DoubleType input, final DoubleType output )
					{
						output.set( input.get() + 0.1 );
						
					}
				},
				new DoubleType() );
		*/

		// TODO: this might all make more sense after normalization now, yay!

		//data = TransformCoordinates.sample( data, stStats.getMedianDistance() );

		//data = Filters.filter( data, new DensityFilterFactory<>( new DoubleType(), medianDistance ) );
		//data = Filters.filter( data, new MeanFilterFactory<>( outofbounds, medianDistance * 10 ) );
		//data = Filters.filter( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() * 2, WeightType.BY_SUM_OF_WEIGHTS ) );
		data = Filters.filter( data, new SingleSpotRemovingFilterFactory<>( outofbounds, stStats.getMedianDistance() * 1.5 ) );
		data = Filters.filter( data, new MedianFilterFactory<>( outofbounds, stStats.getMedianDistance() ) );

		//final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );
		//System.out.println( "Min intensity: " + minmax.getA() );
		//System.out.println( "Max intensity: " + minmax.getB() );

		// for rendering the input pointcloud
		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma*2, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		// for rendering a 16x (median distance), regular sampled pointcloud
		//final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() / 4.0, WeightType.NONE ) );
		
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( RealViews.affine( renderRRA, coordinateTransform ), renderInterval );
		//ImageJFunctions.show( rendered, Threads.createFixedExecutorService() ).setTitle( stdata.toString() );
		//System.out.println( new Date(System.currentTimeMillis()) + ": Done..." );

		return rendered;
	}

	public static final void main(final String... args) {
		CommandLine.call(new RenderImage(), args);
	}
}
