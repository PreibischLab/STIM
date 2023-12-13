package align;

import java.util.ArrayList;
import java.util.List;

import cmd.InteractiveAlignment.AddedGene.Rendering;
import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.FilterFactory;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MeanFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import imglib2.ImgLib2Util;
import mpicbg.models.Affine2D;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import render.MaxDistanceParam;
import render.Render;

public class AlignTools
{
	// used for example visualizations
	// e.g. "Actb", "Ubb", "Hpca", "Calm2", "Mbp", "Fth1", "Pcp4", "Ptgds", "Ttr", "Calm1", "Fkbp1a"
	public static String defaultGene = "Calm2";

	// default scaling for visualization
	public static double defaultScale = 0.05;

	// default smoothness for alignment
	public static double defaultSmoothnessFactor = 4.0;

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final String gene,
			final Interval renderInterval,
			final AffineTransform2D transform,
			final AffineGet intensityTransform,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories,
			final Rendering renderType,
			final double smoothnessFactor )
	{
		//System.out.println( "Mean distance: " + stStats.getMeanDistance());
		//System.out.println( "Median distance: " + stStats.getMedianDistance() );
		//System.out.println( "Max distance: " + stStats.getMaxDistance() );

		// gauss crisp
		double medianDistance = stStats.getMedianDistance();

		final DoubleType outofbounds = new DoubleType( 0 );

		IterableRealInterval< DoubleType > data;

		if ( intensityTransform == null || intensityTransform.isIdentity())
		{
			data = stdata.getExprData( gene ); 
		}
		else
		{
			final double m00 = intensityTransform.getRowPackedCopy()[ 0 ];
			final double m01 = intensityTransform.getRowPackedCopy()[ 1 ];

			data = Converters.convert(
						stdata.getExprData( gene ),
						(a,b) -> b.set( a.get() * m00 + m01 ),
						new DoubleType() );
		}

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

		// filter the iterable
		if ( filterFactories != null )
			for ( final FilterFactory<DoubleType, DoubleType> filterFactory : filterFactories )
				data = Filters.filter( data, filterFactory );

		//data = Filters.filter( data, new DensityFilterFactory<>( new DoubleType(), medianDistance ) );
		//data = Filters.filter( data, new MeanFilterFactory<>( outofbounds, medianDistance * 10 ) );
		//data = Filters.filter( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() * 2, WeightType.BY_SUM_OF_WEIGHTS ) );
		//data = Filters.filter( data, new SingleSpotRemovingFilterFactory<>( outofbounds, stStats.getMedianDistance() * 1.5 ) );
		//data = Filters.filter( data, new MedianFilterFactory<>( outofbounds, stStats.getMedianDistance() ) );

		//final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );
		//System.out.println( "Min intensity: " + minmax.getA() );
		//System.out.println( "Max intensity: " + minmax.getB() );

		// for rendering the input pointcloud
		final RealRandomAccessible< DoubleType > renderRRA;

		if ( renderType == Rendering.Gauss )
			renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, medianDistance*smoothnessFactor, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );
		else if ( renderType == Rendering.NN )
			renderRRA = Render.renderNN( data, outofbounds, new MaxDistanceParam( medianDistance*smoothnessFactor ) );
		else if ( renderType == Rendering.Mean )
			renderRRA = Render.render( data, new MeanFilterFactory<>( outofbounds, medianDistance*smoothnessFactor  ) );
		else // LINEAR
			renderRRA = Render.renderLinear( data, 5, 3.0, new DoubleType( 0 ), new MaxDistanceParam( medianDistance*smoothnessFactor ) );

		//final RealRandomAccessible< DoubleType > renderRRA = Render.renderNN(data, outofbounds, stStats.getMedianDistance() * 10 );

		// for rendering a 16x (median distance), regular sampled pointcloud
		//final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() / 4.0, WeightType.NONE ) );
		
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( RealViews.affine( renderRRA, transform ), renderInterval );
		//ImageJFunctions.show( rendered, Threads.createFixedExecutorService() ).setTitle( stdata.toString() );
		//System.out.println( new Date(System.currentTimeMillis()) + ": Done..." );

		return rendered;
	}

	public static RandomAccessibleInterval< DoubleType > displayDensityMap(
			final STData stdata,
			final STDataStatistics stStats,
			final double smoothnessFactor,
			final Interval renderInterval,
			final AffineTransform2D transform )
	{
		// outofbounds value
		final DoubleType outofbounds = new DoubleType( 0 );

		// take any gene
		IterableRealInterval< DoubleType > data = stdata.getExprData( stdata.getGeneNames().get( 0 ) );

		// always return 1
		data = Converters.convert( data, (i,o) -> o.set( 1.0 ), new DoubleType() );

		// remove single spots
		data = Filters.filter( data, new SingleSpotRemovingFilterFactory<>( outofbounds, stStats.getMedianDistance() * 1.5 ) );

		// for rendering the input pointcloud, no weights, just add gaussians as they come along
		final double gaussRenderSigma = stStats.getMedianDistance();
		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma*smoothnessFactor, WeightType.NONE ) );

		return Views.interval( RealViews.affine( renderRRA, transform ), renderInterval );
	}

	public static ImagePlus visualizePair( final STData stDataA, final STData stDataB, final AffineTransform2D transformA, final AffineTransform2D transformB, final double smoothnessFactor )
	{
		return visualizePair(stDataA, stDataB, transformA, transformB, defaultGene, defaultScale, Rendering.Gauss, smoothnessFactor );
	}

	public static ImagePlus visualizePair( final STData stDataA, final STData stDataB, final AffineTransform2D transformA, final AffineTransform2D transformB, final String gene, final double scale, final Rendering rendering, final double smoothnessFactor )
	{
		//final AffineTransform2D pcmTransform = new AffineTransform2D();
		//pcmTransform.set( 0.43837114678907746, -0.8987940462991671, 5283.362652306015, 0.8987940462991671, 0.43837114678907746, -770.4745037840293 );

		final Interval interval = STDataUtils.getCommonInterval( stDataA, stDataB );

		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( scale );

		final AffineTransform2D tA = transformA.copy();
		tA.preConcatenate( tS );

		final AffineTransform2D tB = transformB.copy();
		tB.preConcatenate( tS );

		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		final RandomAccessibleInterval<DoubleType> visA = display( stDataA, new STDataStatistics( stDataA ), gene, finalInterval, tA, null, null, rendering, smoothnessFactor );
		final RandomAccessibleInterval<DoubleType> visB = display( stDataB, new STDataStatistics( stDataB ), gene, finalInterval, tB, null, null, rendering, smoothnessFactor );

		stack.addSlice(stDataA.toString(), ImageJFunctions.wrapFloat( visA, new RealFloatConverter<>(), stDataA.toString(), null ).getProcessor());
		stack.addSlice(stDataB.toString(), ImageJFunctions.wrapFloat( visB, new RealFloatConverter<>(), stDataB.toString(), null ).getProcessor());

		ImagePlus imp = new ImagePlus("all", stack );
		Calibration cal = imp.getCalibration();
		cal.xOrigin = finalInterval.min( 0 );
		cal.yOrigin = finalInterval.min( 1 );
		imp.resetDisplayRange();
		imp.show();

		return imp;
	}

	public static ImagePlus visualizeList( final List< Pair< STData, AffineTransform2D > > data )
	{
		return visualizeList(data, defaultScale, Rendering.Gauss, defaultSmoothnessFactor, defaultGene, true );
	}

	public static ImagePlus visualizeList(
			final List< Pair< STData, AffineTransform2D > > stdata,
			final double scale,
			final Rendering rendering,
			final double smoothnessFactor,
			final String gene,
			final boolean show )
	{
		final List< STData > data = new ArrayList<>();
		final List< AffineTransform2D > transforms = new ArrayList<>();

		for ( final Pair< STData, AffineTransform2D > stda : stdata )
		{
			data.add( stda.getA() );
			transforms.add( stda.getB() );
		}

		return visualizeList (data, transforms, null, scale, rendering, smoothnessFactor, gene, show );
	}

	public static ImagePlus visualizeList(
			final List< STData > data,
			final List< AffineTransform2D > transforms,
			final List< AffineGet > intensityTransforms,
			final double scale,
			final Rendering rendering,
			final double smoothnessFactor,
			final String gene,
			final boolean show )
	{	
		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( scale );

		final Interval interval = STDataUtils.getCommonInterval( data );
		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), Math.round( 1000 * scale ) );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		for ( int i = 0; i < data.size(); ++i )
		{
			final STData stdata = data.get( i );
			final AffineTransform2D tA = transforms.get( i ).copy();
			tA.preConcatenate( tS );
			final AffineGet iT = ( intensityTransforms == null || intensityTransforms.size() == 0 ) ? null : intensityTransforms.get( i );

			final RandomAccessibleInterval<DoubleType> vis = display( stdata, new STDataStatistics( stdata ), gene, finalInterval, tA, iT, null, rendering, smoothnessFactor );

			stack.addSlice( stdata.toString(), ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), stdata.toString(), null ).getProcessor());
		}

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();

		if ( show )
			imp.show();

		return imp;
	}

	public static AffineTransform2D modelToAffineTransform2D( final Affine2D< ? > model )
	{
		//  m00, m10, m01, m11, m02, m12
		final double[] array = new double[ 6 ];

		model.toArray(array);

		final AffineTransform2D t = new AffineTransform2D();
		t.set( array[ 0 ], array[ 2 ], array[ 4 ], array[ 1 ], array[ 3 ], array[ 5 ] );

		return t;
	}
}
