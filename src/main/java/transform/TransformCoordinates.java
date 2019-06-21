package transform;

import java.util.List;

import data.STData;
import net.imglib2.util.Util;

public class TransformCoordinates
{
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
