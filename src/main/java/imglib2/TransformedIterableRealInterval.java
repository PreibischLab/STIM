package imglib2;

import java.util.Iterator;

import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.realtransform.RealTransform;

public class TransformedIterableRealInterval< T > implements IterableRealInterval< T >
{
	final IterableRealInterval< T > irt;
	final RealTransform transform;
	double[][] minmax = null;

	public TransformedIterableRealInterval(
			final IterableRealInterval< T > irt,
			final RealTransform transform )
	{
		if ( irt.numDimensions() != transform.numSourceDimensions() )
			throw new RuntimeException( "dimensionality of the IterableRealInterval has to be >= dimensionality of the transformation" );

		this.transform = transform;
		this.irt = irt;
	}

	// since we do not know the type of transformation we would have to iterate over all points to find the min/max
	protected synchronized void computeMinMax()
	{
		if ( minmax == null )
		{
			final int n = numDimensions();

			final RealCursor< T > cursor = this.localizingCursor();
			while ( cursor.hasNext() )
			{
				cursor.fwd();
				
				if ( minmax == null )
				{
					minmax = new double[ n ][ 2 ];
					for ( int d = 0; d < n; ++d )
						minmax[ d ][ 0 ] = minmax[ d ][ 1 ] = cursor.getDoublePosition( d );
				}
				else
				{
					for ( int d = 0; d < n; ++d )
					{
						final double p = cursor.getDoublePosition( d );
						minmax[ d ][ 0 ] = Math.min( minmax[ d ][ 0 ], p );
						minmax[ d ][ 1 ] = Math.max( minmax[ d ][ 1 ], p );
					}
				}
			}
		}
	}

	@Override
	public double realMin( final int d )
	{
		computeMinMax();
		return minmax[ d ][ 0 ];
	}

	@Override
	public double realMax( final int d )
	{
		computeMinMax();
		return minmax[ d ][ 1 ];
	}

	@Override
	public RealCursor<T> localizingCursor() { return new TransformedIterableRealIntervalRealCursor< T >( irt, transform ); }

	@Override
	public int numDimensions() { return transform.numTargetDimensions(); }

	@Override
	public Iterator<T> iterator() { return localizingCursor(); }

	@Override
	public RealCursor<T> cursor() { return localizingCursor(); }

	@Override
	public long size() { return irt.size(); }

	@Override
	public T firstElement() { return irt.firstElement(); }

	@Override
	public Object iterationOrder() { return irt.iterationOrder(); }
}
