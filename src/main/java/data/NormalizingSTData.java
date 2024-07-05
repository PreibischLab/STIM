package data;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import imglib2.ExpValueRealIterable;
import imglib2.ImgLib2Util;
import net.imglib2.Cursor;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

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

	/*
	 * overwritten methods
	 */

	@Override
	public RandomAccessibleInterval< DoubleType > getAllExprValues()
	{
		return allExprValues;
	}

	@Override
	public Map< String, RandomAccessibleInterval< ? extends NativeType< ? > > > getAnnotations()
	{
		return data.getAnnotations();
	}

	@Override
	public Map< String, RandomAccessibleInterval< ? extends NativeType< ? > > > getGeneAnnotations()
	{
		return data.getGeneAnnotations();
	}

	@Override
	public IterableRealInterval< DoubleType > getExprData( String geneName )
	{
		// TODO: use the entire values array so that the gene can be switched virtually
		return new ExpValueRealIterable< DoubleType >(
				getLocations(),
				getAllExprValues(),
				data.getIndexForGene( geneName ),
				new FinalRealInterval( this ) );
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getExprValues( String geneName )
	{
		return Views.hyperSlice( getAllExprValues(), 0, data.getIndexForGene( geneName ) );
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getExprValues( long locationIndex )
	{
		return Views.hyperSlice( getAllExprValues(), 1, locationIndex );
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
	public void setExpValues( String geneName, double[] values )
	{
		throw new RuntimeException( "setting expression values not supported on virtual constructs." );
	}

	/*
	 * delegated methods
	 */

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
	public List< String > getBarcodes()
	{
		return data.getBarcodes();
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getLocations()
	{
		return data.getLocations();
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
	public String toString()
	{
		return data.toString();
	}

	@Override
	public int getIndexForGene( String geneName )
	{
		return data.getIndexForGene( geneName );
	}

	@Override
	public STData copy()
	{
		return ImgLib2Util.copy( this );
	}
}
