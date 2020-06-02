package imglib2;

import net.imglib2.Interval;
import net.imglib2.iterator.IntervalIterator;

public class SteppingIntervalIterator extends IntervalIterator
{
	final Interval fullInterval;
	final int[] steps;
	final long[] min;

	public SteppingIntervalIterator( final Interval interval, final int[] steps )
	{
		super( computeBaseDimensions( interval, steps ) );

		this.fullInterval = interval;
		this.steps = steps;

		this.min = new long[ interval.numDimensions() ];
		interval.min( min );
	}

	protected static long[] computeBaseDimensions( final Interval interval, final int[] steps )
	{
		final long[] dim = new long[ interval.numDimensions() ];

		for ( int d = 0; d < dim.length; ++d )
			dim[ d ] = Math.max( 1, interval.dimension( d ) / steps[ d ] );

		return dim;
	}

	/* Localizable */

	@Override
	public long getLongPosition( final int dim )
	{
		return super.getLongPosition( dim ) * steps[ dim ] + min[ dim ];
	}

	@Override
	public void localize( final long[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = getLongPosition( d );
	}

	@Override
	public int getIntPosition( final int dim )
	{
		return ( int ) getLongPosition( dim );
	}

	@Override
	public void localize( final int[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = ( int ) getLongPosition( d );
	}

	/* RealLocalizable */

	@Override
	public double getDoublePosition( final int dim )
	{
		return getLongPosition( dim );
	}

	@Override
	public float getFloatPosition( final int dim )
	{
		return getLongPosition( dim );
	}

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = getLongPosition( d );
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = getLongPosition( d );
	}

	/* Interval */

	@Override
	public long dimension( final int d )
	{
		return fullInterval.dimension( d );
	}

	@Override
	public void dimensions( final long[] dim )
	{
		fullInterval.dimensions( dim );
	}
}
