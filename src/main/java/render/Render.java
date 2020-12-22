package render;

import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import filter.RadiusSearchFilterFactory;
import filter.GaussianFilterFactory.WeightType;
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
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public class Render
{
	public static RealRandomAccessible< DoubleType > getRealRandomAccessible(
			final STDataAssembly stdata,
			final String gene )
	{
		return getRealRandomAccessible( stdata, gene, 1.0, 0, 0, 0 );
	}

	public static IterableRealInterval< DoubleType > getRealIterable(
			final STDataAssembly stdata,
			final String gene )
	{
		return getRealIterable(stdata, gene, 0, 0, 0 );
	}
	
	public static RealRandomAccessible< DoubleType > getRealRandomAccessible(
			final STDataAssembly stdata,
			final String gene,
			final double renderSigmaFactor,
			final double medianRadius,
			final double gaussRadius,
			final double avgRadius )
	{
		final IterableRealInterval< DoubleType > data = getRealIterable( stdata, gene, medianRadius, gaussRadius, avgRadius );

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
			final double medianRadius,
			final double gaussRadius,
			final double avgRadius )
	{
		IterableRealInterval< DoubleType > data;

		if ( stdata.intensityTransform().isIdentity())
		{
			data = Converters.convert(
						stdata.data().getExprData( gene ),
						(a,b) -> b.set( a.get() + 0.1 ),
						new DoubleType() );
		}
		else
		{
			final double m00 = stdata.intensityTransform().getRowPackedCopy()[ 0 ];
			final double m01 = stdata.intensityTransform().getRowPackedCopy()[ 1 ];

			data = Converters.convert(
						stdata.data().getExprData( gene ),
						(a,b) -> b.set( a.get() * m00 + m01 + 0.1 ),
						new DoubleType() );
		}

		if ( !stdata.transform().isIdentity() )
			data = new TransformedIterableRealInterval<>(
					data,
					stdata.transform() );

		// filter the iterable
		if ( medianRadius > 0 )
			data = Filters.filter( data, new MedianFilterFactory<>( new DoubleType( 0 ), medianRadius ) );

		if ( gaussRadius > 0 )
			data = Filters.filter( data, new GaussianFilterFactory<>( new DoubleType( 0 ), gaussRadius, WeightType.BY_SUM_OF_WEIGHTS ) );

		if ( avgRadius > 0 )
			data = Filters.filter( data, new MeanFilterFactory<>( new DoubleType( 0 ), avgRadius ) );

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
}
