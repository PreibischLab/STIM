package imglib2;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.Sampler;

public class StackedIterableRealIntervalRealCursor< T > implements RealCursor< T >
{
	final ArrayList< RealCursor< T > > cursors = new ArrayList<>();
	final int n, lastPos;
	final double spacing;

	RealCursor< T > currentCursor;
	int pos = -1;

	public StackedIterableRealIntervalRealCursor(
			final List< IterableRealInterval< T > > slices,
			final double spacing,
			final int n )
	{
		for ( final IterableRealInterval< T > slice : slices )
			this.cursors.add( slice.localizingCursor() );

		this.currentCursor = null;
		this.spacing = spacing;
		this.n = n;
		this.lastPos = cursors.size() - 1;
	}

	public StackedIterableRealIntervalRealCursor( final StackedIterableRealIntervalRealCursor< T > cursor )
	{
		this.currentCursor = null;
		this.pos = cursor.pos;

		for ( final RealCursor< T > sliceCursor : cursor.cursors )
		{
			this.cursors.add( sliceCursor.copyCursor() );

			if ( sliceCursor == cursor.currentCursor )
				currentCursor = this.cursors.get( this.cursors.size() - 1 );
		}

		this.spacing = cursor.spacing;
		this.n = cursor.n;
		this.lastPos = cursor.lastPos;
	}

	@Override
	public double getDoublePosition( final int d )
	{
		try
		{
		if ( d == n - 1)
			return pos * spacing;
		else
			return currentCursor.getDoublePosition( d );
		}
		catch ( Exception e ) {
			e.printStackTrace();
			System.out.println( pos );
			System.out.println( currentCursor );
			System.exit( 0 );
			return 0;
		}
	}

	@Override
	public int numDimensions() { return n; }

	@Override
	public T get()
	{
		return currentCursor.get();
	}

	@Override
	public void jumpFwd( final long steps )
	{
		for ( long s = 0; s < steps; ++s )
			fwd();
	}

	@Override
	public void fwd()
	{
		if ( currentCursor == null || !currentCursor.hasNext() )
		{
			++pos;
			currentCursor = cursors.get( pos );
		}

		currentCursor.fwd();
	}

	@Override
	public void reset()
	{
		for ( final RealCursor< T > cursor : cursors )
			cursor.reset();
		currentCursor = null;
		pos = -1;
	}

	@Override
	public boolean hasNext()
	{
		return pos < lastPos || currentCursor.hasNext();
	}

	@Override
	public T next()
	{
		if ( currentCursor == null || !currentCursor.hasNext() )
		{
			++pos;
			currentCursor = cursors.get( pos );
		}

		return currentCursor.next();
	}

	@Override
	public Sampler<T> copy() { return copyCursor(); }

	@Override
	public RealCursor<T> copyCursor() { return new StackedIterableRealIntervalRealCursor< T >( this ); }
}
