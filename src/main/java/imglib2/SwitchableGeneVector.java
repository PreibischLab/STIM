package imglib2;

import java.util.Iterator;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;

public class SwitchableGeneVector<T> implements IterableInterval<T>
{
	final ExpValueRealIterable< T > iterable;
	final RandomAccessibleInterval< T > values; // [numGenes x numLocations]

	public SwitchableGeneVector( final ExpValueRealIterable< T > iterable )
	{
		this.iterable = iterable;
		this.values = iterable.values;
	}

	@Override
	public Cursor<T> localizingCursor() { return new SwitchableGeneVectorCursor< T >( iterable ); }

	@Override
	public int numDimensions() { return 1; }

	@Override
	public long size() { return values.dimension( 1 ); }

	@Override
	public T firstElement() { return localizingCursor().next(); }

	@Override
	public Object iterationOrder() { return this; }

	@Override
	public double realMin( final int d ) { return 0; }

	@Override
	public double realMax( final int d ) { return size() - 1; }

	@Override
	public Iterator<T> iterator() { return localizingCursor(); }

	@Override
	public long min( final int d ) { return 0; }

	@Override
	public long max(int d) { return size() - 1; }

	@Override
	public Cursor<T> cursor() { return localizingCursor(); }
}
