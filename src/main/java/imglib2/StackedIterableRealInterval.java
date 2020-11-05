package imglib2;

import java.util.Iterator;
import java.util.List;

import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;

public class StackedIterableRealInterval< T > implements IterableRealInterval< T >
{
	final List< IterableRealInterval< T > > slices;
	final int n;
	final double[] min, max;
	final long size;
	final double spacing;

	public StackedIterableRealInterval( final List< IterableRealInterval< T > > slices, final double spacing )
	{
		this.slices = slices;
		this.n = slices.get( 0 ).numDimensions() + 1;
		this.min = new double[ n ];
		this.max = new double[ n ];
		this.spacing = spacing;

		for ( int d = 0; d < n - 1; ++d )
		{
			min[ d ] = slices.get( 0 ).realMin( d );
			max[ d ] = slices.get( 0 ).realMax( d );
		}

		long sizeTmp = 0;

		for ( final IterableRealInterval< T > slice : slices )
		{
			for ( int d = 0; d < n - 1; ++d )
			{
				min[ d ] = Math.min( min[ d ], slice.realMin( d ) );
				max[ d ] = Math.max( max[ d ], slice.realMax( d ) );
			}

			if ( slice.size() == 0 )
				throw new RuntimeException( "Empty IterableRealIntervals not supported." );

			if ( slice.numDimensions() != slices.get( 0 ).numDimensions() )
				throw new RuntimeException( "Varying dimensionality of IterableRealIntervals not supported." );

			sizeTmp += slice.size();
		}

		this.size = sizeTmp;

		min[ n - 1 ] = 0;
		max[ n - 1 ] = ( slices.size() - 1 ) * spacing;
	}

	@Override
	public double realMin( final int d ) { return min[ d ]; }

	@Override
	public double realMax( final int d) { return max[ d ]; }

	@Override
	public int numDimensions() { return n; }

	@Override
	public Iterator<T> iterator() { return localizingCursor(); }

	@Override
	public RealCursor<T> cursor() { return localizingCursor(); }

	@Override
	public RealCursor<T> localizingCursor() { return new StackedIterableRealIntervalRealCursor<>( slices, spacing, n ); }

	@Override
	public long size() { return size; }

	@Override
	public T firstElement() { return slices.get( 0 ).firstElement(); }

	@Override
	public Object iterationOrder() { return this; }
}
