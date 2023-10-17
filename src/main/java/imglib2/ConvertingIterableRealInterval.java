package imglib2;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.position.AbstractFunctionEuclideanSpace;

public class ConvertingIterableRealInterval< S, T > extends AbstractFunctionEuclideanSpace< RealLocalizable, T > implements IterableRealInterval< T >
{
	final IterableRealInterval< S > underlyingRealInterval;

	public ConvertingIterableRealInterval(
			final IterableRealInterval< S > underlyingRealInterval,
			final BiConsumer< RealLocalizable, ? super T > function,
			final Supplier< T > typeSupplier )
	{
		super( underlyingRealInterval.numDimensions(), function, typeSupplier );
		//java.util.function.BiFunction<T, U, R>
		this.underlyingRealInterval = underlyingRealInterval;
	}

	public ConvertingIterableRealInterval(
			final IterableRealInterval< S > underlyingRealInterval,
			final Supplier< BiConsumer< RealLocalizable, ? super T > > function,
			final Supplier< T > typeSupplier )
	{
		super( underlyingRealInterval.numDimensions(), function, typeSupplier );

		this.underlyingRealInterval = underlyingRealInterval;
	}

	@Override
	public Iterator<T> iterator() { return cursor(); }

	@Override
	public double realMin( final int d ) { return underlyingRealInterval.realMin( d ); }

	@Override
	public double realMax( final int d ) { return underlyingRealInterval.realMax( d ); }

	@Override
	public RealCursor<T> cursor() { return localizingCursor(); }

	@Override
	public RealCursor<T> localizingCursor() { return new ConvertingIterableRealIntervalCursor(); }

	@Override
	public long size() { return underlyingRealInterval.size(); }

	@Override
	public T firstElement() { return cursor().next(); }

	@Override
	public Object iterationOrder() { return underlyingRealInterval.iterationOrder(); }

	public class ConvertingIterableRealIntervalCursor extends RealPoint implements RealCursor< T >
	{
		private final T t = typeSupplier.get();
		private final BiConsumer< RealLocalizable, ? super T > function = functionSupplier.get();

		private final RealCursor< S > underlyingCursor = underlyingRealInterval.localizingCursor();

		public ConvertingIterableRealIntervalCursor()
		{
			super( ConvertingIterableRealInterval.this.n );
		}

		@Override
		public T get()
		{
			function.accept( this, t );
			return t;
		}

		@Override
		public RealCursor<T> copyCursor() { return copy(); }

		@Override
		public ConvertingIterableRealIntervalCursor copy() { return new ConvertingIterableRealIntervalCursor(); }

		@Override
		public void jumpFwd( final long steps ) { underlyingCursor.jumpFwd( steps ); }

		@Override
		public void fwd() { underlyingCursor.fwd(); }

		@Override
		public void reset() { underlyingCursor.reset(); }

		@Override
		public boolean hasNext() { return underlyingCursor.hasNext(); }

		@Override
		public T next()
		{
			underlyingCursor.fwd();
			return get();
		}

	}
}
