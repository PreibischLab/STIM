package transform;

import java.util.Collection;

import data.STData;
import net.imglib2.util.Util;

public class TransformCoordinates
{
	public static void zeroMin( final STData data )
	{
		zeroMin( data.coordinates );
		data.computeIntervals();
	}

	public static void zeroMin( final Collection< double[] > coordinates )
	{
		if ( coordinates.isEmpty() )
			return;

		final double[] firstElement = coordinates.iterator().next();
		final int n = firstElement.length;

		final double[] min = firstElement.clone();

		for ( final double[] coor : coordinates )
			for ( int d = 0; d < n; ++d )
				min[ d ] = Math.min( coor[ d ], min[ d ] );

		for ( final double[] coor : coordinates )
			for ( int d = 0; d < n; ++d )
				coor[ d ] -= min[ d ];
	}

	public static void translate( final STData data, final double[] vector )
	{
		translate( data.coordinates, vector );
		data.computeIntervals();
	}

	public static void translate( final Collection< double[] > coordinates, final double[] vector )
	{
		if ( coordinates.isEmpty() )
			return;

		final int n = vector.length;

		for ( final double[] coor : coordinates )
			for ( int d = 0; d < n; ++d )
				coor[ d ] += vector[ d ];
	}

	public static void scale( final STData data, final double scale )
	{
		scale( data, Util.getArrayFromValue( scale, data.numDimensions() ) );
	}

	public static void scale( final STData data, final double[] scale )
	{
		scale( data.coordinates, scale );
		data.computeStatistics();
		data.computeIntervals();
	}

	public static void scale( final Collection< double[] > coordinates, final double[] scale )
	{
		if ( coordinates.isEmpty() )
			return;

		final int n = scale.length;

		for ( final double[] coor : coordinates )
			for ( int d = 0; d < n; ++d )
				coor[ d ] *= scale[ d ];
	}

}
