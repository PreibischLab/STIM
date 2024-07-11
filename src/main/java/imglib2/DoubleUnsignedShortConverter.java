package imglib2;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;

public class DoubleUnsignedShortConverter implements Converter< DoubleType, UnsignedShortType >
{
	final double min, range;

	public DoubleUnsignedShortConverter( final double min, final double max )
	{
		this.min = min;
		this.range = max - min;
	}

	@Override
	public void convert( final DoubleType input, final UnsignedShortType output )
	{
		output.set( (int)Math.round( 65535 * ( input.get() - min) / range) );
	}

}
