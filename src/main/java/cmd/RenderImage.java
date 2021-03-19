package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5FSReader;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.FilterFactory;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
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

	@Option(names = {"-b", "--border"}, required = false, description = "extra empty border around spatial sequencing locations, e.g. -b 100 (default: 20)")
	private int border = 20;

	@Option(names = {"-f", "--singleSpotFilter"}, required = false, description = "filter single spots using the median distance between all spots as threshold (default: false)")
	private boolean singleSpotFilter = false;

	@Option(names = {"-m", "--medianFilter"}, required = false, description = "median-filter all spots using a given radius, e.g -m 20.0 (default: no filtering)")
	private Double median = null;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "factor for the sigma of the gaussian used for rendering, corresponds to smoothness, e.g -sf 2.0 (default: 1.5)")
	private double smoothnessFactor = 1.5;

	@Override
	public Void call() throws Exception {

		final boolean displayAsImageInstance = true;
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

		final DoubleType outofbounds = new DoubleType( 0 );
		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		if ( singleSpotFilter )
		{
			STDataStatistics stats = new STDataStatistics( data.get( 0 ).getA() );
			System.out.println( "Using single-spot filtering, radius="  + (stats.getMedianDistance() * 1.5) );
			filterFactorys.add( new SingleSpotRemovingFilterFactory<>( outofbounds,stats.getMedianDistance() * 1.5 ) );
		}

		if ( median != null && median > 0.0 )
		{
			System.out.println( "Using median filtering, radius=" + median );
			filterFactorys.add( new MedianFilterFactory<>( outofbounds, median ) );
		}

		for ( final String gene : geneList )
		{
			System.out.println( "Rendering gene " + gene );

			//ImagePlus imp = AlignTools.visualizeList( data, scale, gene, true );// filterFactorys );
			ImagePlus imp = visualizeList( data, scale, gene, smoothnessFactor, border, filterFactorys );
			imp.setTitle( gene );

			if ( displayAsImageInstance )
				imp.show();
		}

		return null;
	}

	public static ImagePlus visualizeList(
			final List< Pair< STData, AffineTransform2D > > data,
			final double scale,
			final String gene,
			final double smoothnessFactor,
			final int border,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
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

		final Interval finalInterval = Intervals.expand( interval, border );

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
							smoothnessFactor,
							filterFactories,
							gene,
							finalInterval );

			System.out.println( "rendering  " + pair.getA().toString() );

			stack.addSlice(pair.getA().toString(), ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), pair.getA().toString(), null ).getProcessor());
		}

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();
		return imp;
	}

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final AffineGet coordinateTransform,
			final AffineGet intensityTransform,
			final double smoothnessFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories,
			final String gene,
			final Interval renderInterval )
	{
		// we work at full resolution so rendering and filter parameters are independent of the scale
		final IterableRealInterval< DoubleType > data = Render.getRealIterable( stdata, null, intensityTransform, gene, filterFactories );

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( new DoubleType( 0 ), stStats.getMedianDistance()*smoothnessFactor, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		return Views.interval( RealViews.affine( renderRRA, coordinateTransform ), renderInterval );
	}

	public static final void main(final String... args) {
		CommandLine.call(new RenderImage(), args);
	}
}
