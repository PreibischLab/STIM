package imglib2;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.type.numeric.real.DoubleType;

public class ExpValueRealCursor< T > implements RealCursor< T >
{
	final LocationRealCursor locationCursor;
	final Cursor< T > valueCursor;
	final IterableInterval< T > values;

	public ExpValueRealCursor(
			final RandomAccessibleInterval< DoubleType > locations,
			final IterableInterval< T > values )
	{
		this.locationCursor = new LocationRealCursor( locations );

		// we need a special hyperslice or cursor here that returns a different part of the big 2d RAI 
		this.valueCursor = values.localizingCursor();
		this.values = values;
	}

	protected ExpValueRealCursor( final ExpValueRealCursor< T > realCursor )
	{
		this.locationCursor = new LocationRealCursor(realCursor.locationCursor);
		this.valueCursor = realCursor.valueCursor.copy();
		this.values = realCursor.values;
	}

	@Override
	public T get()
	{
		return valueCursor.get();
	}

	@Override
	public void jumpFwd( final long steps )
	{
		locationCursor.jumpFwd( steps );
		valueCursor.jumpFwd( steps );
	}

	@Override
	public void fwd()
	{
		locationCursor.fwd();
		valueCursor.fwd();
	}

	@Override
	public void reset()
	{
		locationCursor.reset();
		valueCursor.reset();
	}

	@Override
	public boolean hasNext()
	{
		return locationCursor.hasNext();
	}

	@Override
	public T next()
	{
		fwd();
		return get();
	}

	@Override
	public void localize( final float[] position )
	{
		locationCursor.localize( position );
	}

	@Override
	public void localize( final double[] position )
	{
		locationCursor.localize( position );
	}

	@Override
	public float getFloatPosition( final int d )
	{
		return locationCursor.getFloatPosition( d );
	}

	@Override
	public double getDoublePosition( final int d )
	{
		return locationCursor.getDoublePosition( d );
	}

	@Override
	public int numDimensions()
	{
		return locationCursor.numDimensions();
	}

	@Override
	public RealCursor<T> copy() {
		return new ExpValueRealCursor<>(this);
	}
}