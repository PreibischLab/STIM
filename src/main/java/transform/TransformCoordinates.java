package transform;

import java.util.List;

import data.STData;
import filter.FilterFactory;
import filter.Filters;
import filter.MeanFilterFactory;
import imglib2.SteppingIntervalIterator;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;

public class TransformCoordinates
{
	public static < T extends RealType< T > > IterableRealInterval< T > sample(
			final IterableRealInterval< T > data,
			final double distance )
	{
		return sample(
				data,
				Util.getArrayFromValue( (int)Math.round( Math.ceil( distance ) ), data.numDimensions() ),
				new MeanFilterFactory<>( data.iterator().next().createVariable(), distance ) );
	}

	public static < S extends RealType< S >, T extends RealType< T > > IterableRealInterval< T > sample(
			final IterableRealInterval< S > data,
			final int[] steps,
			final FilterFactory< S, T > filterFactory )
	{
		final long[] min = new long[ data.numDimensions() ];
		final long[] max = new long[ data.numDimensions() ];

		for ( int d = 0; d < min.length; ++d )
		{
			min[ d ] = Math.round( data.realMin( d ) );
			max[ d ] = Math.round( data.realMax( d ) );
		}

		final Interval interval = new FinalInterval( min, max );

		return Filters.filter( data, new SteppingIntervalIterator( interval, steps ), filterFactory );
	}

	public static void zeroMin( final STData data )
	{
		final int n = data.numDimensions();
		final double[] min = new double[ n ];
		data.realMin( min );

		final List< double[] > locations = data.getLocationsCopy();

		for ( int i = 0; i < locations.size(); ++i )
		{
			final double[] loc = locations.get( i );

			for ( int d = 0; d < n; ++d )
				loc[ d ] -= min[ d ];
		}

		data.setLocations( locations );
	}

	public static void translate( final STData data, final double[] vector )
	{
		final int n = data.numDimensions();

		final List< double[] > locations = data.getLocationsCopy();

		for ( int i = 0; i < locations.size(); ++i )
		{
			final double[] loc = locations.get( i );

			for ( int d = 0; d < n; ++d )
				loc[ d ] += vector[ d ];
		}

		data.setLocations( locations );
	}

	public static void scale( final STData data, final double scale )
	{
		scale( data, Util.getArrayFromValue( scale, data.numDimensions() ) );
	}

	public static void scale( final STData data, final double[] scale )
	{
		final int n = data.numDimensions();

		final List< double[] > locations = data.getLocationsCopy();

		for ( int i = 0; i < locations.size(); ++i )
		{
			final double[] loc = locations.get( i );

			for ( int d = 0; d < n; ++d )
				loc[ d ] *= scale[ d ];
		}

		data.setLocations( locations );
	}
}
