package data;

import java.util.ArrayList;
import java.util.List;

import imglib2.ExpValueRealIterable;
import imglib2.LocationRealCursor;
import net.imglib2.Cursor;
import net.imglib2.FinalRealInterval;
import net.imglib2.IterableInterval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public abstract class STDataAbstract implements STData
{
	final int n, numLocations, numGenes;

	public STDataAbstract(
			final int n,
			final int numLocations,
			final int numGenes )
	{
		this.n = n;
		this.numLocations = numLocations;
		this.numGenes = numGenes;
	}

	@Override
	public LocationRealCursor iterator()
	{
		return new LocationRealCursor( getLocations() );
	}

	@Override
	public IterableRealInterval< DoubleType > getExprData( final String geneName )
	{
		return new ExpValueRealIterable(
				getLocations(),
				getExprValues( geneName ),
				new FinalRealInterval( this ) );
	}

	@Override
	public int numDimensions() { return n; }

	@Override
	public long numLocations() { return numLocations; }

	@Override
	public long numGenes() { return numGenes; }

	protected abstract RealInterval getLocationRealInterval();
	protected abstract int getIndexForGene( final String geneName );

	@Override
	public RandomAccessibleInterval< DoubleType > getExprValues( final String geneName )
	{
		return Views.hyperSlice( getAllExprValues(), 1, getIndexForGene( geneName ) );
	}

	@Override
	public List< double[] > getLocationsCopy()
	{
		final ArrayList< double[] > locations = new ArrayList<>();

		final int n = numDimensions();

		for ( final RealLocalizable l : this )
		{
			final double[] loc = new double[ n ];
			l.localize( loc );
			locations.add( loc );
		}

		return locations;
	}

	@Override
	public double[] getExpValuesCopy( final String geneName )
	{
		final IterableInterval< DoubleType > exprValues = Views.flatIterable( getExprValues( geneName ) );

		final double[] exprValuesCopy = new double[ (int)exprValues.size() ];

		final Cursor< DoubleType > cursor = exprValues.localizingCursor();

		while ( cursor.hasNext() )
		{
			final DoubleType t = cursor.next();
			exprValuesCopy[ cursor.getIntPosition( 0 ) ] = t.get();
		}

		return exprValuesCopy;
	}

	@Override
	public void setExpValues( final String geneName, final double[] exprValuesCopy )
	{
		final IterableInterval< DoubleType > exprValues = Views.flatIterable( getExprValues( geneName ) );
		final Cursor< DoubleType > cursor = exprValues.localizingCursor();

		while ( cursor.hasNext() )
		{
			final DoubleType t = cursor.next();
			t.set( exprValuesCopy[ cursor.getIntPosition( 0 ) ] );
		}
	}

	@Override
	public RealCursor< RealLocalizable > cursor()
	{
		return iterator();
	}

	@Override
	public RealCursor< RealLocalizable > localizingCursor()
	{
		return iterator();
	}

	@Override
	public long size()
	{
		return numLocations();
	}

	@Override
	public RealLocalizable firstElement()
	{
		return iterator().next();
	}

	@Override
	public Object iterationOrder()
	{
		return this;
	}


	@Override
	public double realMin( final int d )
	{
		return getLocationRealInterval().realMin( d );
	}

	@Override
	public void realMin( final double[] min )
	{
		getLocationRealInterval().realMin( min );
	}

	@Override
	public void realMin( final RealPositionable min )
	{
		getLocationRealInterval().realMin( min );
	}

	@Override
	public double realMax( final int d )
	{
		return getLocationRealInterval().realMax( d );
	}

	@Override
	public void realMax( final double[] max )
	{
		getLocationRealInterval().realMax( max );
	}

	@Override
	public void realMax( final RealPositionable max )
	{
		getLocationRealInterval().realMax( max );
	}
}
