package transform;

import java.util.Collection;

import data.STData;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class TransformIntensities
{
	public static void add( final STData data, final double value )
	{
		add( data.genes.values(), value );
	}

	public static void add( final Collection< double[] > data, final double value )
	{
		for ( final double[] values : data )
			for ( int i = 0; i < values.length; ++i )
				values[ i ] += value;
	}

	public static void mul( final STData data, final double value )
	{
		add( data.genes.values(), value );
	}

	public static void mul( final Collection< double[] > data, final double value )
	{
		for ( final double[] values : data )
			for ( int i = 0; i < values.length; ++i )
				values[ i ] *= value;
	}

	public static Pair< Double, Double > minmax( final STData data )
	{
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( final double[] values : data.genes.values() )
		{
			final Pair< Double, Double > minmax = minmax( values );

			min = Math.min( min, minmax.getA() );
			max = Math.max( max, minmax.getB() );
		}

		return new ValuePair< Double, Double >( min, max );
	}

	public static Pair< Double, Double > minmax( final double[] values )
	{
		double min = values[ 0 ];
		double max = values[ 0 ];

		for ( final double v : values )
		{
			min = Math.min( min, v );
			max = Math.max( max, v );
		}

		return new ValuePair< Double, Double >( min, max );
	}

}
