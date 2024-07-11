package imglib2;

import java.util.Iterator;

import com.sun.istack.internal.NotNull;
import net.imglib2.IterableInterval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealInterval;
import net.imglib2.RealPositionable;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public class ExpValueRealIterable< T > implements IterableRealInterval< T >
{
	final RandomAccessibleInterval< DoubleType > locations;
	//final RandomAccessibleInterval< T > values;
	final RealInterval realInterval;
	final long lastIndex, size;
	final IterableInterval< T > iterableValues;

	public ExpValueRealIterable(
			final RandomAccessibleInterval< DoubleType > locations,
			final RandomAccessibleInterval< T > values, // matrix
			final long valueIndex, //getIndexForGene( geneName )
			final RealInterval realInterval )
	{
		this.locations = locations;
		this.realInterval = realInterval;
		this.size = locations.dimension( 0 );
		this.lastIndex = locations.dimension( 0 ) - 1;
		this.iterableValues = Views.flatIterable( Views.hyperSlice( values, 0, valueIndex ) );
	}

	public ExpValueRealIterable(
			final RandomAccessibleInterval< DoubleType > locations,
			final RandomAccessibleInterval< T > values, // vector
			final RealInterval realInterval )
	{
		this.locations = locations;
		//this.values = values;
		this.realInterval = realInterval;
		this.size = locations.dimension( 0 );
		this.lastIndex = locations.dimension( 0 ) - 1;
		this.iterableValues = Views.flatIterable( values );
	}

	@Override
	public RealCursor< T > localizingCursor()
	{
		return new ExpValueRealCursor<>(
				locations,
				iterableValues);
	}

	@Override
	public double realMin( final int d )
	{
		return realInterval.realMin( d );
	}

	@Override
	public void realMin( final double[] min )
	{
		realInterval.realMin( min );
	}

	@Override
	public void realMin( final RealPositionable min )
	{
		realInterval.realMin( min );
	}

	@Override
	public double realMax( final int d )
	{
		return realInterval.realMax( d );
	}

	@Override
	public void realMax( final double[] max )
	{
		realInterval.realMax( max );
	}

	@Override
	public void realMax( final RealPositionable max )
	{
		realInterval.realMax( max );
	}

	@Override
	public int numDimensions()
	{
		return realInterval.numDimensions();
	}

	@Override
	public Iterator< T > iterator()
	{
		return localizingCursor();
	}

	@Override
	public RealCursor< T > cursor()
	{
		return localizingCursor();
	}

	@Override
	public long size()
	{
		return size;
	}

	@Override
	public T firstElement()
	{
		return cursor().next();
	}

	@Override
	public Object iterationOrder()
	{
		return this;
	}
}
