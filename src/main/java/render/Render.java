package render;

import java.awt.Color;
import java.util.ArrayList;
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
import net.imglib2.RealCursor;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.neighborsearch.InverseDistanceWeightingInterpolatorFactory;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public class Render
{
	public static RealRandomAccessible< DoubleType > getRealRandomAccessible(
			final STDataAssembly stdata,
			final String gene )
	{
		return getRealRandomAccessible( stdata, gene, 1.0, null );
	}

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STDataAssembly stdata,
			final String gene )
	{
		return getRealIterable(stdata, gene, null );
	}
	
	public static RealRandomAccessible< DoubleType > getRealRandomAccessible(
			final STDataAssembly stdata,
			final String gene,
			final double renderSigmaFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
		final IterableRealInterval< DoubleType > data = getRealIterable( stdata, gene, filterFactories );

		// gauss crisp
		double gaussRenderSigma = stdata.statistics().getMedianDistance() * renderSigmaFactor;

		//return Render.renderNN( data );
		//return Render.renderNN( data, new DoubleType( 0 ), gaussRenderRadius );
		//return Render.render( data, new MeanFilterFactory<>( new DoubleType( 0 ), 2 * gaussRenderSigma ) );
		//return Render.render( data, new MedianFilterFactory<>( new DoubleType( 0 ), 2 * gaussRenderSigma ) );
		return Render.render( data, new GaussianFilterFactory<>( new DoubleType( 0 ), gaussRenderSigma, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );
	}

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STDataAssembly stdata,
			final String gene,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
		return getRealIterable(stdata.data(), stdata.transform(), stdata.intensityTransform(), gene, filterFactories);
	}

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STData stdata,
			final AffineGet coordinateTransform,
			final AffineGet intensityTransform,
			final String gene,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
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

		if ( coordinateTransform != null && !coordinateTransform.isIdentity() )
			data = new TransformedIterableRealInterval<>(
					data,
					coordinateTransform );

		// filter the iterable
		if ( filterFactories != null )
			for ( final FilterFactory<DoubleType, DoubleType> filterFactory : filterFactories )
				data = Filters.filter( data, filterFactory );

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
			final String meta,
			final AffineTransform2D transform,
			final List< FilterFactory< IntType, IntType > > filterFactorys, // optional
			final HashMap<Long, ARGBType> lut )
	{
		final RandomAccessibleInterval idsIn = stdata.getMetaData().get( meta );

		if ( idsIn == null )
		{
			System.out.println( "WARNING: metadata '" + meta + "' does not exist. skipping.");
			return null;
		}

		final Object type = Views.iterable( idsIn ).firstElement();
		if ( !IntegerType.class.isInstance( type ) )
		{
			System.out.println( "WARNING: metadata '" + meta + "' is not an integer type (but "+ type.getClass().getSimpleName() +". don't know how to render it. skipping.");
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

		System.out.println( "Rendering metadata '" + meta + "', type="+ type.getClass().getSimpleName() + ", min=" + min + ", max= " + max + " as integers" );

		final RandomAccessibleInterval< IntType > ids;
		if ( IntType.class.isInstance( type ) )
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
				new NearestNeighborSearchOnKDTree< T >(createParallelizableKDTreeFrom(data)),
				new NearestNeighborSearchInterpolatorFactory< T >() );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderLinear(
			final IterableRealInterval< T > data,
			final int numNeighbors,
			final double p )
	{
		return Views.interpolate(
				new KNearestNeighborSearchOnKDTree< T >(createParallelizableKDTreeFrom(data), numNeighbors),
				new InverseDistanceWeightingInterpolatorFactory< T >( p ) );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderLinear(
			final IterableRealInterval< T > data,
			final int numNeighbors,
			final double p,
			final T outofbounds,
			final double maxRadius)
	{
		return Views.interpolate(
				new KNearestNeighborMaxDistanceSearchOnKDTree< T >(
						createParallelizableKDTreeFrom(data),
						numNeighbors,
						() -> outofbounds.copy(),
						maxRadius ),
				new InverseDistanceWeightingInterpolatorFactory< T >( p ) );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderNN( final IterableRealInterval< T > data, final T outofbounds, final double maxRadius )
	{
		return Views.interpolate(
				new NearestNeighborMaxDistanceSearchOnKDTree< T >(
						createParallelizableKDTreeFrom(data),
						() -> outofbounds.copy(),
						maxRadius ),
				new NearestNeighborSearchInterpolatorFactory< T >() );
	}

	public static < S, T > RealRandomAccessible< T > render( final IterableRealInterval< S > data, final RadiusSearchFilterFactory< S, T > filterFactory )
	{
		return Views.interpolate(
				new FilteringRadiusSearchOnKDTree< S, T >( // data source (F)
						createParallelizableKDTreeFrom(data),
						filterFactory ),
				new IntegratingNeighborSearchInterpolatorFactory< T >() ); // interpolatorfactory (T,F)
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

	protected static <T> KDTree<T> createParallelizableKDTreeFrom(IterableRealInterval<T> data) {
		final List<RealCursor<T>> positions = new ArrayList<>();
		final List<T> values = new ArrayList<>();

		RealCursor<T> cursor = data.localizingCursor();
		while (cursor.hasNext()) {
			cursor.next();
			positions.add(cursor.copyCursor());
			values.add(cursor.get());
		}

		return new KDTree<T>(values, positions);
	}
}
