package render;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import data.STData;
import filter.FilterFactory;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.RadiusSearchFilterFactory;
import gui.STDataAssembly;
import imglib2.ExpValueRealIterable;
import imglib2.TransformedIterableRealInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.neighborsearch.InverseDistanceWeightingInterpolatorFactory;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import util.KDTreeUtil;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class Render
{
	private static final Logger logger = LoggerUtil.getLogger();

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STDataAssembly stdata,
			final String gene )
	{
		return getRealIterable(stdata, gene, null );
	}

	public static RealRandomAccessible< DoubleType > getRealRandomAccessible(
			final STDataAssembly stdata,
			final String gene )
	{
		return getRealRandomAccessible( stdata, gene, 1.0, null );
	}

	public static Pair< RealRandomAccessible< DoubleType >, GaussianFilterFactory< DoubleType, DoubleType > > getRealRandomAccessible2(
			final STDataAssembly stdata,
			final String gene )
	{
		return getRealRandomAccessible2( stdata, gene, 1.0, null );
	}

	public static RealRandomAccessible< DoubleType > getRealRandomAccessible(
			final STDataAssembly stdata,
			final String gene,
			final double renderSigmaFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
		return getRealRandomAccessible2( stdata, gene, renderSigmaFactor, filterFactories ).getA();
	}

	public static Pair< RealRandomAccessible< DoubleType >, GaussianFilterFactory< DoubleType, DoubleType > > getRealRandomAccessible2(
			final STDataAssembly stdata,
			final String gene,
			final double renderSigmaFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
		final IterableRealInterval< DoubleType > data = getRealIterable( stdata, gene, filterFactories );

		// gauss crisp
		final double gaussRenderSigmaBase = stdata.statistics().getMedianDistance();

		//return Render.renderNN( data );
		//return Render.renderNN( data, new DoubleType( 0 ), gaussRenderRadius );
		//return Render.render( data, new MeanFilterFactory<>( new DoubleType( 0 ), 2 * gaussRenderSigma ) );
		//return Render.render( data, new MedianFilterFactory<>( new DoubleType( 0 ), 2 * gaussRenderSigma ) );
		final GaussianFilterFactory< DoubleType, DoubleType > factory =
				new GaussianFilterFactory<>( new DoubleType( 0 ), gaussRenderSigmaBase * renderSigmaFactor, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS );

		return new ValuePair<>( Render.render( data, factory ), factory );
	}

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STDataAssembly stdata,
			final String gene,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
		return getRealIterable(stdata.data(), stdata.transform(), gene, filterFactories);
	}

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STData stdata,
			final AffineGet coordinateTransform,
			final String gene,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
		IterableRealInterval< DoubleType > data = stdata.getExprData( gene ); 

		/*
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
		*/

		if ( coordinateTransform != null && !coordinateTransform.isIdentity() )
			data = new TransformedIterableRealInterval<>(
					data,
					coordinateTransform );

		// filter the iterable
		if ( filterFactories != null )
			for ( final FilterFactory<DoubleType, DoubleType> filterFactory : filterFactories )
				data = Filters.filter( data, filterFactory );
				// data = Filters.filterVirtual( data, filterFactory, DoubleType::new );

		/*
		if ( medianRadius > 0 )
			data = Filters.filter( data, new MedianFilterFactory<>( new DoubleType( 0 ), medianRadius ) );

		if ( gaussRadius > 0 )
			data = Filters.filter( data, new GaussianFilterFactory<>( new DoubleType( 0 ), gaussRadius, WeightType.BY_SUM_OF_WEIGHTS ) );

		if ( avgRadius > 0 )
			data = Filters.filter( data, new MeanFilterFactory<>( new DoubleType( 0 ), avgRadius ) );
		*/
		return data;
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< T > raster( final RealRandomAccessible< T > realRandomAccessible, final Interval interval )
	{
		return Views.interval(
				Views.raster( realRandomAccessible ),
				interval );
	}

	public static IterableRealInterval< IntType > getRealIterable(
			final STData stdata,
			final String annotation,
			final AffineTransform2D transform,
			final List< FilterFactory< IntType, IntType > > filterFactorys, // optional
			final HashMap<Long, ARGBType> lut )
	{
		final RandomAccessibleInterval idsIn = stdata.getAnnotations().get(annotation);

		if ( idsIn == null )
		{
			logger.warn("annotation '{}' does not exist. skipping.", annotation);
			return null;
		}

		final Object type = Views.iterable( idsIn ).firstElement();
		if (!(type instanceof IntegerType))
		{
			logger.warn("annotation '{}' is not an integer type (but {}. don't know how to render it. skipping.", annotation, type.getClass().getSimpleName());
			return null;
		}

		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		Random rnd = new Random( 243 );

		for ( final IntegerType<?> t : Views.iterable( (RandomAccessibleInterval<IntegerType<?>>)idsIn ) )
		{
			final long l = t.getIntegerLong();

			min = Math.min( min, l );
			max = Math.max( max, l );

			if ( lut != null )
			{
				if ( !lut.containsKey( l ) )
					lut.put( l, Render.randomColor( rnd ) );
			}
		}

		logger.debug("Rendering annotation '{}', type={}, min={}, max= {} as integers", annotation, type.getClass().getSimpleName(), min, max);

		final RandomAccessibleInterval< IntType > ids;
		if (type instanceof IntType)
			ids = (RandomAccessibleInterval< IntType >)idsIn;
		else
			ids = Converters.convert( idsIn, (i,o) -> o.setInt( ((IntegerType)i).getInteger() ), new IntType() );

		IterableRealInterval< IntType > data = new ExpValueRealIterable(
				stdata.getLocations(),
				ids,
				new FinalRealInterval( stdata ));

		if ( transform != null && !transform.isIdentity() )
			data = new TransformedIterableRealInterval<>(
					data,
					transform );

		// filter the iterable
		if ( filterFactorys != null )
			for ( final FilterFactory<IntType, IntType> filterFactory : filterFactorys )
				data = Filters.filter( data, filterFactory );

		return data;
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderNN( final IterableRealInterval< T > data )
	{
		return Views.interpolate(
				new NearestNeighborSearchOnKDTree<>(KDTreeUtil.createParallelizableKDTreeFrom(data)),
				new NearestNeighborSearchInterpolatorFactory<>() );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderLinear(
			final IterableRealInterval< T > data,
			final int numNeighbors,
			final double p )
	{
		return Views.interpolate(
				new KNearestNeighborSearchOnKDTree<>(KDTreeUtil.createParallelizableKDTreeFrom(data), numNeighbors),
				new InverseDistanceWeightingInterpolatorFactory<>(p) );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderLinear(
			final IterableRealInterval< T > data,
			final int numNeighbors,
			final double p,
			final T outofbounds,
			final MaxDistanceParam param )
	{
		return renderLinear2(data, numNeighbors, p, outofbounds, param).getA();
	}

	public static < T extends RealType< T > > Pair< RealRandomAccessible< T >, KDTree< T > > renderLinear2(
			final IterableRealInterval< T > data,
			final int numNeighbors,
			final double p,
			final T outofbounds,
			final MaxDistanceParam param )
	{
		final KDTree< T > tree = KDTreeUtil.createParallelizableKDTreeFrom(data);

		return new ValuePair<>(Views.interpolate(
				new KNearestNeighborMaxDistanceSearchOnKDTree<>(
						tree,
						numNeighbors,
						outofbounds::copy,
						param),
				new InverseDistanceWeightingInterpolatorFactory<>(p) ), tree );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderNN( final IterableRealInterval< T > data, final T outofbounds, final MaxDistanceParam maxRadius )
	{
		return renderNN2(data, outofbounds, maxRadius).getA();
	}

	public static < T extends RealType< T > > Pair< RealRandomAccessible< T >, KDTree< T > > renderNN2( final IterableRealInterval< T > data, final T outofbounds, final MaxDistanceParam maxRadius )
	{
		final KDTree< T > tree = KDTreeUtil.createParallelizableKDTreeFrom(data);
		return new ValuePair<>(Views.interpolate(
				new NearestNeighborMaxDistanceSearchOnKDTree<>(
						tree,
						outofbounds::copy,
						maxRadius),
				new NearestNeighborSearchInterpolatorFactory<>() ), tree );
	}

	public static < S extends Type<S>, T > RealRandomAccessible< T > render( final IterableRealInterval< S > data, final RadiusSearchFilterFactory< S, T > filterFactory )
	{
		return render2( data, filterFactory ).getA();
	}

	public static < S extends Type<S>, T > Pair< RealRandomAccessible< T >, KDTree< S > > render2( final IterableRealInterval< S > data, final RadiusSearchFilterFactory< S, T > filterFactory )
	{
		final KDTree< S > tree = KDTreeUtil.createParallelizableKDTreeFrom(data);
		return new ValuePair<>(Views.interpolate(
				new FilteringRadiusSearchOnKDTree<>( // data source (F)
													 tree,
													 filterFactory),
				new IntegratingNeighborSearchInterpolatorFactory<>() ), tree ); // interpolatorfactory (T,F)
	}

	public static < T extends IntegerType< T > > RealRandomAccessible< ARGBType > convertToRGB( final RealRandomAccessible< T > rra, final T outofbounds, final ARGBType background, final HashMap<Long, ARGBType> lut )
	{
		return Converters.convert(
				rra,
				(i,o) -> {
					final long v = i.getIntegerLong();
					if ( v == outofbounds.getIntegerLong() )
						o.set( background );
					else
						o.set( lut.get( v ) ); },
				new ARGBType() );
	}

	public static < T extends IntegerType< T > > RealRandomAccessible< ARGBType > switchableConvertToRGB(
			final RealRandomAccessible< T > rra,
			final T outofbounds,
			final ARGBType background,
			final HashMap<Long, ARGBType> lut,
			final Function<Long, Boolean> visible )
	{
		return Converters.convert(
				rra,
				(i,o) -> {
					final long v = i.getIntegerLong();
					if ( v == outofbounds.getIntegerLong() || !visible.apply( v ) )
						o.set( background );
					else
						o.set( lut.get( v ) ); },
				new ARGBType() );
	}

	public static ARGBType randomColor( Random rnd )
	{
		final float h = rnd.nextFloat();
		final float s = rnd.nextFloat();
		final float b = 0.9f + 0.1f * rnd.nextFloat();
		final Color c = Color.getHSBColor(h, s, b);

		return new ARGBType( ARGBType.rgba(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
	}
}
