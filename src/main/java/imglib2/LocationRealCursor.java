package imglib2;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.Sampler;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

/**
 * An iterator for all sequenced locations, the current index corresponds to the expression values list index
 *
 * @author spreibi
 */
public class LocationRealCursor implements RealCursor< RealLocalizable >
{
	final RandomAccess< DoubleType > ra;

	final int n;
	final long lastIndex;
	final double[] pos;

	long index;

	public LocationRealCursor( final RandomAccessibleInterval< DoubleType > locations )
	{
		this.ra = Views.extendZero( locations ).randomAccess();

		this.n = (int)locations.dimension( 1 );
		this.pos = new double[ n ];
		this.lastIndex = locations.dimension( 0 ) - 1;

		reset();
	}

	protected LocationRealCursor( final LocationRealCursor l )
	{
		this.ra = l.ra.copyRandomAccess();
		this.n = l.numDimensions();
		this.pos = l.pos.clone();
		this.lastIndex = l.lastIndex;
		this.index = l.index;
	}

	/**
	 * the current index of the locationiterator, could be used to load the corresponding expression values
	 *
	 * @return - the current index in the list of sequenced locations
	 */
	public long getIndex()
	{
		return index;
	}

	@Override
	public void jumpFwd( final long steps )
	{
		index += steps;
		ra.fwd( (int)steps );
		updatePos();
	}

	@Override
	public void fwd()
	{
		++index;
		ra.fwd( 0 );
		updatePos();
	}

	protected void updatePos()
	{
		ra.setPosition( -1, 1 );

		for ( int d = 0; d < n; ++d )
		{
			ra.fwd( 1 );
			pos[ d ] = ra.get().get();
		}
	}

	@Override
	public void reset()
	{
		this.index = -1;
		ra.setPosition( -1, 0 );
	}

	@Override
	public boolean hasNext()
	{
		return this.index < lastIndex;
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = (float)pos[ d ];
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = pos[ d ];
	}

	@Override
	public float getFloatPosition( final int d )
	{
		return (float)pos[ d ];
	}

	@Override
	public double getDoublePosition( final int d )
	{
		return pos[ d ];
	}

	@Override
	public RealLocalizable next()
	{
		fwd();
		return this;
	}

	@Override
	public RealLocalizable get()
	{
		return this;
	}

	@Override
	public Sampler< RealLocalizable > copy()
	{
		return copyCursor();
	}

	@Override
	public LocationRealCursor copyCursor()
	{
		return new LocationRealCursor( this );
	}
}
