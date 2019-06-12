package data;

import net.imglib2.FinalRealInterval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealInterval;

public class STDataStatistics
{
	public static RealInterval computeRealInterval( final IterableRealInterval< ? > coord )
	{
		if ( coord.size() == 0 )
			return null;

		final int n = coord.numDimensions();

		final RealCursor< ? > cursor = coord.localizingCursor();
		cursor.fwd();

		final double[] min = new double[ n ];
		final double[] max = min.clone();

		cursor.localize( min );
		cursor.localize( max );


		while( cursor.hasNext() )
		{
			cursor.fwd();

			for ( int d = 0; d < n; ++d )
			{
				final double pos = cursor.getDoublePosition( d );

				min[ d ] = Math.min( pos, min[ d ] );
				max[ d ] = Math.max( pos, max[ d ] );
			}
		}

		return new FinalRealInterval( min, max );
	}

}
