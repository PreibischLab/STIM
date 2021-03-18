package align;

import java.util.List;
import java.util.stream.Collectors;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
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
	// used for example visualizations
	// e.g. "Actb", "Ubb", "Hpca", "Calm2", "Mbp", "Fth1", "Pcp4", "Ptgds", "Ttr", "Calm1", "Fkbp1a"
	public static String defaultGene = "Calm2";

	// default scaling for visualization
	public static double defaultScale = 0.05;

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
		tS.scale( defaultScale );

		final AffineTransform2D tA = transformA.copy();
		tA.preConcatenate( tS );

		final AffineTransform2D tB = transformB.copy();
		tB.preConcatenate( tS );

		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		final RandomAccessibleInterval<DoubleType> visA = display( stDataA, new STDataStatistics( stDataA ), defaultGene, finalInterval, tA );
		final RandomAccessibleInterval<DoubleType> visB = display( stDataB, new STDataStatistics( stDataB ), defaultGene, finalInterval, tB );

		stack.addSlice(stDataA.toString(), ImageJFunctions.wrapFloat( visA, new RealFloatConverter<>(), stDataA.toString(), null ).getProcessor());
		stack.addSlice(stDataB.toString(), ImageJFunctions.wrapFloat( visB, new RealFloatConverter<>(), stDataB.toString(), null ).getProcessor());

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();
		imp.show();

		return imp;
	}

	public static ImagePlus visualizeList( final List< Pair< STData, AffineTransform2D > > data )
	{
		return visualizeList(data, defaultScale, defaultGene, true );
	}

	public static ImagePlus visualizeList( final List< Pair< STData, AffineTransform2D > > data, final double scale, final String gene, final boolean show )
	{
		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( scale );

		final Interval interval = STDataUtils.getCommonInterval( data.stream().map( entry -> entry.getA() ).collect( Collectors.toList() ) );
		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		for ( Pair< STData, AffineTransform2D > pair : data )
		{
			final AffineTransform2D tA = pair.getB().copy();
			tA.preConcatenate( tS );

			final RandomAccessibleInterval<DoubleType> vis = display( pair.getA(), new STDataStatistics( pair.getA() ), gene, finalInterval, tA );

			stack.addSlice(pair.getA().toString(), ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), pair.getA().toString(), null ).getProcessor());
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
