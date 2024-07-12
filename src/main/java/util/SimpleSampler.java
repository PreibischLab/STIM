package util;

import java.util.function.Supplier;

import net.imglib2.Sampler;

public class SimpleSampler< T > implements Sampler< T >
{
	final Supplier< T > supplier;
	final T type;

	public SimpleSampler( final Supplier< T > supplier )
 	{
		this.supplier = supplier;
		this.type = supplier.get();
	}

	@Override
	public T get()
	{
		return type;
	}

	@Override
	public Sampler< T > copy()
	{
		return new SimpleSampler<>(supplier);
	}
}
