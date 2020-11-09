package imglib2;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.Sampler;

public class SwitchableGeneVectorCursor< T > implements Cursor< T >
{
	final ExpValueRealIterable< T > iterable;
	final RandomAccess< T > ra;
	final long max;

	long myIndex;

	public SwitchableGeneVectorCursor( final ExpValueRealIterable< T > iterable )
	{
		this.iterable = iterable;
		this.ra = iterable.values.randomAccess();
		this.max = iterable.values.max( 1 );

		reset();
	}

	public SwitchableGeneVectorCursor( final SwitchableGeneVectorCursor< T > cursor )
	{
		this.iterable = cursor.iterable;
		this.ra = cursor.iterable.values.randomAccess();
		this.max = cursor.max;

		this.ra.setPosition( cursor.ra );
		this.myIndex = cursor.myIndex;
	}

	@Override
	public double getDoublePosition( final int d ) { return ra.getDoublePosition( 1 ); }

	@Override
	public int numDimensions() { return 1; }

	@Override
	public T get()
	{
		if ( myIndex != iterable.getValueIndex() )
		{
			ra.setPosition( iterable.getValueIndex(), 0 );
			myIndex = iterable.getValueIndex();
		}

		return ra.get();
	}

	@Override
	public Sampler<T> copy() { return copyCursor(); }

	@Override
	public void jumpFwd( final long steps )
	{
		if ( myIndex != iterable.getValueIndex() )
		{
			ra.setPosition( iterable.getValueIndex(), 0 );
			myIndex = iterable.getValueIndex();
		}

		ra.move( steps, 1 );
	}

	@Override
	public void fwd()
	{
		if ( myIndex != iterable.getValueIndex() )
		{
			System.out.println( "happened.");
			ra.setPosition( iterable.getValueIndex(), 0 );
			myIndex = iterable.getValueIndex();
		}

		ra.fwd( 1 );
	}

	@Override
	public void reset()
	{
		ra.setPosition( myIndex = iterable.getValueIndex(), 0 );
		ra.setPosition( -1, 1 );
	}

	@Override
	public boolean hasNext() { return ra.getLongPosition( 1 ) < max; }

	@Override
	public T next()
	{
		fwd();
		return get();
	}

	@Override
	public long getLongPosition( final int d) { return ra.getLongPosition( 1 ); }

	@Override
	public SwitchableGeneVectorCursor<T> copyCursor() { return new SwitchableGeneVectorCursor<>( this ); }
}
