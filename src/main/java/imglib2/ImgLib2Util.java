package imglib2;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.type.Type;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class ImgLib2Util
{
	public static Interval transformInterval( final Interval interval, final AffineGet affine )
	{
		if ( affine == null )
			return interval;

		final double[] min = new double[ interval.numDimensions() ];
		final double[] max = new double[ interval.numDimensions() ];

		for ( int d = 0; d < min.length; ++d )
		{
			min[ d ] = interval.min( d );
			max[ d ] = interval.max( d );
		}

		affine.apply( min, min );
		affine.apply( max, max );

		final long[] minL = new long[ min.length ];
		final long[] maxL = new long[ max.length ];

		for ( int d = 0; d < min.length; ++d )
		{
			minL[ d ] = Math.round( Math.floor( min[ d ] ) );
			maxL[ d ] = Math.round( Math.ceil( max[ d ] ) );
		}

		return new FinalInterval( minL, maxL );
	}

	public static < T extends Comparable< T > & Type< T > > Pair< T, T > minmax( final Iterable< T > img )
	{
		final T min = img.iterator().next().copy();
		final T max = min.copy();

		for ( final T value : img )
		{
			if ( value.compareTo( min ) < 0 )
				min.set( value );

			if ( value.compareTo( max ) > 0 )
				max.set( value );
		}

		return new ValuePair< T, T >( min, max );
	}

	public static String printRealInterval( final RealInterval interval )
	{
		String out = "(Interval empty)";

		if ( interval == null || interval.numDimensions() == 0 )
			return out;

		out = "[" + interval.realMin( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMin( i );

		out += "] -> [" + interval.realMax( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMax( i );

		out += "], size (" + (interval.realMax( 0 ) - interval.realMin( 0 ));

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + (interval.realMax( i ) - interval.realMin( i ));

		out += ")";

		return out;
	}

	public static long[] dimensions( final Interval ri )
	{
		final long[] dim = new long[ ri.numDimensions() ];
		ri.dimensions( dim );
		return dim;
	}

	public static long[] min( final Interval ri )
	{
		final long[] min = new long[ ri.numDimensions() ];
		ri.min( min );
		return min;
	}

	public static FinalInterval roundRealInterval( final RealInterval ri )
	{
		final long[] min = new long[ ri.numDimensions() ];
		final long[] max = new long[ ri.numDimensions() ];

		for ( int d = 0; d < ri.numDimensions(); ++d )
		{
			min[ d ] = Math.round( Math.floor( ri.realMin( d ) ) );
			max[ d ] = Math.round( Math.ceil( ri.realMax( d ) ) );
		}

		return new FinalInterval( min, max );
	}

	
	public static < T extends Type< T > > RealPointSampleList< T > copyIterableRealInterval(
			final IterableRealInterval< T > data )
	{
		final RealPointSampleList< T > list =
				new RealPointSampleList<>( data.numDimensions() );

		final RealCursor< T > cursor = data.localizingCursor();

		while ( cursor.hasNext() )
		{
			final T value = cursor.next();

			list.add( new RealPoint( cursor ), value.copy() );
		}

		return list;
	}

	/*
	public static RealPointSampleList< DoubleType > wrapDouble(
			final List< double[] > coordinates,
			final double[] values )
	{
		final RealPointSampleList< DoubleType > list =
				new RealPointSampleList<>( coordinates.get( 0 ).length );

		for ( int i = 0; i < coordinates.size(); ++i )
			list.add( new RealPoint( coordinates.get( i ) ), new DoubleType( values[ i ] ) );

		return list;
	}

	public static RealPointSampleList< FloatType > wrapFloat(
			final List< double[] > coordinates,
			final double[] values )
	{
		final RealPointSampleList< FloatType > list =
				new RealPointSampleList<>( coordinates.get( 0 ).length );

		for ( int i = 0; i < coordinates.size(); ++i )
			list.add( new RealPoint( coordinates.get( i ) ), new FloatType( (float)values[ i ] ) );

		return list;
	}

	public static RealPointSampleList< UnsignedShortType > wrapUnsignedShort(
			final List< double[] > coordinates,
			final double[] values,
			final double offset )
	{
		final Pair< Double, Double > minmax = TransformIntensities.minmax( values );

		return wrapUnsignedShort( coordinates, values, minmax.getA(), minmax.getB(), offset );
	}

	public static RealPointSampleList< UnsignedShortType > wrapUnsignedShort(
			final List< double[] > coordinates,
			final double[] values,
			final double min,
			final double max )
	{
		return wrapUnsignedShort( coordinates, values, min, max, 0 );
	}

	public static RealPointSampleList< UnsignedShortType > wrapUnsignedShort(
			final List< double[] > coordinates,
			final double[] values,
			final double minIn,
			final double max,
			final double offset )
	{
		final RealPointSampleList< UnsignedShortType > list =
				new RealPointSampleList<>( coordinates.get( 0 ).length );

		final double min = minIn - offset;

		for ( int i = 0; i < coordinates.size(); ++i )
		{
			final int value = Math.min( 65535, Math.max( 0, (int)Math.round( 65535 * ( ( values[ i ] - min ) / ( max - min ) ) ) ) );
			list.add( new RealPoint( coordinates.get( i ) ), new UnsignedShortType( value ) );
		}

		return list;
	}

	public static RealPointSampleList< UnsignedByteType > wrapUnsignedByte(
			final List< double[] > coordinates,
			final double[] values,
			final double offset )
	{
		final Pair< Double, Double > minmax = TransformIntensities.minmax( values );

		return wrapUnsignedByte( coordinates, values, minmax.getA(), minmax.getB(), offset );
	}

	public static RealPointSampleList< UnsignedByteType > wrapUnsignedByte(
			final List< double[] > coordinates,
			final double[] values,
			final double min,
			final double max )
	{
		return wrapUnsignedByte( coordinates, values, min, max, 0 );
	}

	public static RealPointSampleList< UnsignedByteType > wrapUnsignedByte(
			final List< double[] > coordinates,
			final double[] values,
			final double minIn,
			final double max,
			final double offset )
	{
		final RealPointSampleList< UnsignedByteType > list =
				new RealPointSampleList<>( coordinates.get( 0 ).length );

		final double min = minIn - offset;

		for ( int i = 0; i < coordinates.size(); ++i )
		{
			final int value = Math.min( 255, Math.max( 0, (int)Math.round( 255 * ( ( values[ i ] - min ) / ( max - min ) ) ) ) );
			list.add( new RealPoint( coordinates.get( i ) ), new UnsignedByteType( value ) );
		}

		return list;
	}*/
}
