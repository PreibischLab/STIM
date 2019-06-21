package imglib2;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.Sampler;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public class ExpValueRealCursor< T > implements RealCursor< T >
{
	final RandomAccessibleInterval< DoubleType > locations;
	final RandomAccessibleInterval< T > values;

	final LocationRealCursor locationCursor;
	final Cursor< T > valueCursor;

	public ExpValueRealCursor(
			final RandomAccessibleInterval< DoubleType > locations,
			final RandomAccessibleInterval< T > values )
	{
		this.locations = locations;
		this.values = values;

		this.locationCursor = new LocationRealCursor( locations );
		this.valueCursor = Views.flatIterable( values ).cursor();
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
	public Sampler< T > copy()
	{
		return copyCursor();
	}

	@Override
	public RealCursor< T > copyCursor()
	{
		return new ExpValueRealCursor< T >( locations, values );
	}
}