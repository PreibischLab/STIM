package transform;

import data.STData;
import imglib2.ImgLib2Util;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class TransformIntensities
{
	@Deprecated
	public static void add( final STData data, final double value )
	{
		for ( final DoubleType t : Views.iterable( data.getAllExprValues() ) )
			t.set( t.get() + value );
	}

	@Deprecated
	public static void add( final STData data, final String geneName, final double value )
	{
		for ( final DoubleType t : Views.iterable( data.getExprValues( geneName ) ) )
			t.set( t.get() + value );
	}

	@Deprecated
	public static void mul( final STData data, final double value )
	{
		for ( final DoubleType t : Views.iterable( data.getAllExprValues() ) )
			t.set( t.get() * value );
	}

	@Deprecated
	public static void mul( final STData data, final String geneName, final double value )
	{
		for ( final DoubleType t : Views.iterable( data.getExprValues( geneName ) ) )
			t.set( t.get() * value );
	}

	public static Pair< Double, Double > minmax( final STData data )
	{
		final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( Views.iterable( data.getAllExprValues() ) );

		return new ValuePair<>(minmax.getA().get(), minmax.getB().get());
	}

	public static Pair< Double, Double > minmax( final STData data, final String geneName )
	{
		final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( Views.iterable( data.getExprValues( geneName ) ) );

		return new ValuePair<>(minmax.getA().get(), minmax.getB().get());
	}

}
