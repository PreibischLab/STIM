package data;

import java.util.Iterator;
import java.util.List;

import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.type.numeric.real.DoubleType;

public class NormalizingSTData implements STData
{
	final STData data;
	final RandomAccessibleInterval< DoubleType > allExprValues;

	/**
	 * Replaces the underlying data with a normalized version of it
	 * 
	 * @param data - an STData instance
	 */
	public NormalizingSTData( final STData data )
	{
		this.data = data;
		this.allExprValues = new NormalizingRandomAccessibleInterval( data.getAllExprValues() );
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getAllExprValues()
	{
		return allExprValues;
	}

	@Override
	public RealCursor< RealLocalizable > cursor()
	{
		return data.cursor();
	}

	@Override
	public RealCursor< RealLocalizable > localizingCursor()
	{
		return data.localizingCursor();
	}

	@Override
	public long size()
	{
		return data.size();
	}

	@Override
	public RealLocalizable firstElement()
	{
		return data.firstElement();
	}

	@Override
	public Object iterationOrder()
	{
		return data.iterationOrder();
	}

	@Override
	public double realMin( int d )
	{
		return data.realMin( d );
	}

	@Override
	public double realMax( int d )
	{
		return data.realMax( d );
	}

	@Override
	public int numDimensions()
	{
		return data.numDimensions();
	}

	@Override
	public Iterator< RealLocalizable > iterator()
	{
		return data.iterator();
	}

	@Override
	public Interval getRenderInterval()
	{
		return data.getRenderInterval();
	}

	@Override
	public Interval getRenderInterval( long border )
	{
		return data.getRenderInterval( border );
	}

	@Override
	public Interval getRenderInterval( long[] border )
	{
		return data.getRenderInterval( border );
	}

	@Override
	public KDTree< RealLocalizable > getLocationKDTree()
	{
		return data.getLocationKDTree();
	}

	@Override
	public KDTree< DoubleType > getExpValueKDTree( String geneName )
	{
		return data.getExpValueKDTree( geneName );
	}

	@Override
	public IterableRealInterval< DoubleType > getExprData( String geneName )
	{
		return data.getExprData( geneName );
	}

	@Override
	public long numLocations()
	{
		return data.numLocations();
	}

	@Override
	public long numGenes()
	{
		return data.numGenes();
	}

	@Override
	public List< String > getGeneNames()
	{
		return data.getGeneNames();
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getLocations()
	{
		return data.getLocations();
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getExprValues( String gene )
	{
		return data.getExprValues( gene );
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getExprValues( long locationIndex )
	{
		return data.getExprValues( locationIndex );
	}

	@Override
	public List< double[] > getLocationsCopy()
	{
		return data.getLocationsCopy();
	}

	@Override
	public void setLocations( List< double[] > locations )
	{
		data.setLocations( locations );
	}

	@Override
	public double[] getExpValuesCopy( String geneName )
	{
		return data.getExpValuesCopy( geneName );
	}

	@Override
	public void setExpValues( String geneName, double[] values )
	{
		data.setExpValues( geneName, values );
	}
}
