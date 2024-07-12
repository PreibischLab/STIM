package imglib2;

import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;

public class TransformedIterableRealIntervalRealCursor< T > implements RealCursor< T >
{
	final RealTransform transform;
	final RealCursor< T > cursor;
	final RealPoint loc;

	public TransformedIterableRealIntervalRealCursor(
			final IterableRealInterval< T > irt,
			final RealTransform transform )
	{
		if ( irt.numDimensions() != transform.numSourceDimensions() )
			throw new RuntimeException( "dimensionality of the IterableRealInterval has to be >= dimensionality of the transformation" );

		this.cursor = irt.localizingCursor();
		this.transform = transform;
		this.loc = new RealPoint( numDimensions() );
	}

	public TransformedIterableRealIntervalRealCursor( final TransformedIterableRealIntervalRealCursor< T > cursor )
	{
		this.cursor = cursor.cursor.copy();
		this.transform = cursor.transform;
		this.loc = new RealPoint( cursor.loc );
	}

	@Override
	public double getDoublePosition( final int d )
	{
		return loc.getDoublePosition( d );
	}

	protected final void updatePosition()
	{
		this.transform.apply( cursor, loc );
	}

	@Override
	public int numDimensions() { return transform.numTargetDimensions(); }

	@Override
	public T get() { return cursor.get(); }

	@Override
	public RealCursor<T> copy() {
		return new TransformedIterableRealIntervalRealCursor<>(this);
	}

	@Override
	public void jumpFwd( final long steps ) { cursor.jumpFwd( steps ); }

	@Override
	public void fwd()
	{
		cursor.fwd();
		updatePosition();
	}

	@Override
	public void reset() { cursor.reset(); }

	@Override
	public boolean hasNext() { return cursor.hasNext(); }

	@Override
	public T next()
	{
		fwd();
		return get();
	}

}
