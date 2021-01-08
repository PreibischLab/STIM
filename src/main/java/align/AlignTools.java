package align;

import java.util.List;
import java.util.stream.Collectors;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import ij.ImagePlus;
import ij.ImageStack;
import imglib2.ImgLib2Util;
import mpicbg.models.Affine2D;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import render.Render;

public class AlignTools
{

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final String gene,
			final Interval renderInterval,
			final AffineTransform2D transform )
	{
		//System.out.println( "Mean distance: " + stStats.getMeanDistance());
		//System.out.println( "Median distance: " + stStats.getMedianDistance() );
		//System.out.println( "Max distance: " + stStats.getMaxDistance() );

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance();

		final DoubleType outofbounds = new DoubleType( 0 );

		IterableRealInterval< DoubleType > data = stdata.getExprData( gene );

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

		// TODO: this might all make more sense after normalization now, yay!

		//data = TransformCoordinates.sample( data, stStats.getMedianDistance() );

		//data = Filters.filter( data, new DensityFilterFactory<>( new DoubleType(), medianDistance ) );
		//data = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianDistance * 2 ) );
		//data = Filters.filter( data, new MeanFilterFactory<>( outofbounds, medianDistance * 10 ) );
		//data = Filters.filter( data, new GaussianFilterFactory<>( outofbounds, medianDistance * 2, stStats.getMedianDistance(), true ) );

		//final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );
		//System.out.println( "Min intensity: " + minmax.getA() );
		//System.out.println( "Max intensity: " + minmax.getB() );

		// for rendering the input pointcloud
		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma*4, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		// for rendering a 16x (median distance), regular sampled pointcloud
		//final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() / 4.0, WeightType.NONE ) );

		//BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		//BdvStackSource< ? > bdv = BdvFunctions.show( renderRRA, stdata.getRenderInterval(), gene, options );
		//bdv.setDisplayRange( 0.1, minmax.getB().get() * 2 );
		//bdv.setDisplayRangeBounds( 0, minmax.getB().get() * 8 );

		//System.out.println( new Date(System.currentTimeMillis()) + ": Rendering interval " + Util.printInterval( interval ) + " with " + Threads.numThreads() + " threads ... " );
		
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( RealViews.affine( renderRRA, transform ), renderInterval );
		//ImageJFunctions.show( rendered, Threads.createFixedExecutorService() ).setTitle( stdata.toString() );
		//System.out.println( new Date(System.currentTimeMillis()) + ": Done..." );

		return rendered;
	}

	public static ImagePlus visualizePair( final STData stDataA, final STData stDataB, final AffineTransform2D transformA, final AffineTransform2D transformB )
	{
		//final AffineTransform2D pcmTransform = new AffineTransform2D();
		//pcmTransform.set( 0.43837114678907746, -0.8987940462991671, 5283.362652306015, 0.8987940462991671, 0.43837114678907746, -770.4745037840293 );

		final Interval interval = STDataUtils.getCommonInterval( stDataA, stDataB );

		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( 0.05 );

		final AffineTransform2D tA = transformA.copy();
		tA.preConcatenate( tS );

		final AffineTransform2D tB = transformB.copy();
		tB.preConcatenate( tS );

		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		final RandomAccessibleInterval<DoubleType> visA = display( stDataA, new STDataStatistics( stDataA ), "Calm1", finalInterval, tA );
		final RandomAccessibleInterval<DoubleType> visB = display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB );

		stack.addSlice(stDataA.toString(), ImageJFunctions.wrapFloat( visA, new RealFloatConverter<>(), stDataA.toString(), null ).getProcessor());
		stack.addSlice(stDataB.toString(), ImageJFunctions.wrapFloat( visB, new RealFloatConverter<>(), stDataB.toString(), null ).getProcessor());

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();
		imp.show();

		return imp;
	}

	public static ImagePlus visualizeList( final List< Pair< STData, AffineTransform2D > > data )
	{
		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( 0.05 );

		final Interval interval = STDataUtils.getCommonInterval( data.stream().map( entry -> entry.getA() ).collect( Collectors.toList() ) );
		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		for ( Pair< STData, AffineTransform2D > pair : data )
		{
			final AffineTransform2D tA = pair.getB().copy();
			tA.preConcatenate( tS );

			final RandomAccessibleInterval<DoubleType> vis = display( pair.getA(), new STDataStatistics( pair.getA() ), "Ubb", finalInterval, tA );

			stack.addSlice(pair.getA().toString(), ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), pair.getA().toString(), null ).getProcessor());
		}

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();
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
