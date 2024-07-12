package imglib2;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;

public class ConvertingIterableRealInterval< S, T > implements IterableRealInterval< T >
{
	final IterableRealInterval< S > underlyingRealInterval;

	protected final Supplier< TriConsumer< RealLocalizable, ? super S, ? super T > > functionSupplier;
	protected final Supplier< T > typeSupplier;

	public ConvertingIterableRealInterval(
			final IterableRealInterval< S > underlyingRealInterval,
			final Supplier< TriConsumer< RealLocalizable, ? super S, ? super T > > functionSupplier,
			final Supplier< T > typeSupplier )
	{
		this.underlyingRealInterval = underlyingRealInterval;
		this.functionSupplier = functionSupplier;
		this.typeSupplier = typeSupplier;
	}

	/*
	public ConvertingIterableRealInterval(
			final IterableRealInterval< S > underlyingRealInterval,
			final TriConsumer< RealLocalizable, ? super S, ? super T > function,
			final Supplier< T > typeSupplier )
	{
		this( underlyingRealInterval, () -> function, typeSupplier );
	}*/

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

	@Override
	public int numDimensions() { return underlyingRealInterval.numDimensions(); }

	public class ConvertingIterableRealIntervalCursor /*extends RealPoint*/ implements RealCursor< T >
	{
		private final T t = typeSupplier.get();
		private final TriConsumer< RealLocalizable, ? super S, ? super T > function = functionSupplier.get();
		private final RealCursor< S > underlyingCursor = underlyingRealInterval.localizingCursor();
		private int index = -1;

		@Override
		public T get()
		{
			function.accept( underlyingCursor, underlyingCursor.get(), t );
			return t;
		}

		@Override
		public T next()
		{
			fwd();
			return get();
		}

		@Override
		public void jumpFwd( final long steps )
		{
			underlyingCursor.jumpFwd( steps );
			index += (int) steps;
		}

		@Override
		public void fwd()
		{
			underlyingCursor.fwd();
			++index;
		}

		@Override
		public void reset()
		{
			underlyingCursor.reset();
			index = -1;
		}

		@Override
		public boolean hasNext() { return underlyingCursor.hasNext(); }

		@Override
		public double getDoublePosition(int d) { return underlyingCursor.getDoublePosition(d); }

		@Override
		public int numDimensions() { return underlyingCursor.numDimensions(); }

		@Override
		public RealCursor<T> copyCursor() { return copy(); }

		@Override
		public ConvertingIterableRealIntervalCursor copy()
		{
			final ConvertingIterableRealIntervalCursor c = new ConvertingIterableRealIntervalCursor();
			c.jumpFwd( index );
			return c;
		}
	}

	@FunctionalInterface
	public interface TriConsumer<T, U, V> {

	    /**
	     * Performs this operation on the given arguments.
	     *
	     * @param t the first input argument
	     * @param u the second input argument
	     * @param v the third input argument
	     */
	    void accept(T t, U u, V v);

	    /**
	     * Returns a composed {@code TriConsumer} that performs, in sequence, this
	     * operation followed by the {@code after} operation. If performing either
	     * operation throws an exception, it is relayed to the caller of the
	     * composed operation.  If performing this operation throws an exception,
	     * the {@code after} operation will not be performed.
	     *
	     * @param after the operation to perform after this operation
	     * @return a composed {@code TriConsumer} that performs in sequence this
	     * operation followed by the {@code after} operation
	     * @throws NullPointerException if {@code after} is null
	     */
	    default TriConsumer<T, U, V> andThen(TriConsumer<? super T, ? super U, ? super V> after) {
	        Objects.requireNonNull(after);

	        return (l, r, s) -> {
	            accept(l, r, s);
	            after.accept(l, r, s);
	        };
	    }
	}
}
