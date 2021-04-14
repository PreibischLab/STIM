package render;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import data.STData;
import data.STDataStatistics;
import filter.FilterFactory;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.RadiusSearchFilterFactory;
import gui.STDataAssembly;
import imglib2.TransformedIterableRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
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
			final List< FilterFactory< DoubleType, DoubleType > > filterFactorys )
	{
		final IterableRealInterval< DoubleType > data = getRealIterable( stdata, gene, filterFactorys );

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
			final List< FilterFactory< DoubleType, DoubleType > > filterFactorys )
	{
		return getRealIterable(stdata.data(), stdata.transform(), stdata.intensityTransform(), gene, filterFactorys);
	}

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STData stdata,
			final AffineGet coordinateTransform,
			final AffineGet intensityTransform,
			final String gene,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactorys )
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
		if ( filterFactorys != null )
			for ( final FilterFactory<DoubleType, DoubleType> filterFactory : filterFactorys )
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

	public static < T extends RealType< T > > RealRandomAccessible< T > renderNN( final IterableRealInterval< T > data )
	{
		return Views.interpolate(
				new NearestNeighborSearchOnKDTree< T >( new KDTree< T > ( data ) ),
				new NearestNeighborSearchInterpolatorFactory< T >() );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderNN( final IterableRealInterval< T > data, final T outofbounds, final double maxRadius )
	{
		return Views.interpolate(
				new NearestNeighborMaxDistanceSearchOnKDTree< T >(
						new KDTree< T > ( data ),
						outofbounds,
						maxRadius ),
				new NearestNeighborSearchInterpolatorFactory< T >() );
	}

	public static < S, T > RealRandomAccessible< T > render( final IterableRealInterval< S > data, final RadiusSearchFilterFactory< S, T > filterFactory )
	{
		return Views.interpolate(
				new FilteringRadiusSearchOnKDTree< S, T >(
						new KDTree<> ( data ),
						filterFactory ),
				new IntegratingNeighborSearchInterpolatorFactory< T >() );
	}

	public static < T extends IntegerType< T > > RealRandomAccessible< ARGBType > convertToRGB( final RealRandomAccessible< T > rra, final T outofbounds, final HashMap<Long, ARGBType> lut )
	{
		final Random rnd = new Random( 455 );
		final ARGBType black = new ARGBType( 0 );

		return Converters.convert( rra, (i,o) -> {
				final long v = i.getIntegerLong();
				if ( v == outofbounds.getIntegerLong() )
				{
					o.set( black );
				}
				else
				{
					ARGBType t = lut.get( v );
					if ( t == null )
					{
						synchronized ( lut ) {
							t = randomColor( rnd );
							lut.put( v,  t );
						}
					}

					o.set( t );
				}
			} , new ARGBType() );
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
