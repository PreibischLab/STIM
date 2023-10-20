package util;
/*-
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2021 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import bdv.util.Bounds;

/**
 * A bounded value with {@code minBound <= value <= maxBound}.
 * <p>
 * {@code BoundedValue} is immutable.
 * <p>
 * {@link #withValue(double)}, {@link #withMinBound(double)} etc derive a new
 * {@code BoundedValue} with the given {@code value}, {@code minBound} etc, while
 * maintaining the {@code minBound <= value <= maxBound} property. For
 * example, {@code this.withMin(x)}, will also update {@code value}, if
 * {@code x > this.getValue()}.
 *
 * @author Tobias Pietzsch
 */
// TODO: move to bdv-core
public final class BoundedValue
{
	private final double minBound;
	private final double maxBound;
	private final double value;

	public BoundedValue( final double minBound, final double maxBound, final double value )
	{
		if ( ( minBound > value ) || ( maxBound < value ) )
			throw new IllegalArgumentException();

		this.minBound = minBound;
		this.maxBound = maxBound;
		this.value = value;
	}

	public double getMinBound()
	{
		return minBound;
	}

	public double getMaxBound()
	{
		return maxBound;
	}

	public Bounds getBounds()
	{
		return new Bounds( minBound, maxBound );
	}

	public double getValue()
	{
		return value;
	}

	public BoundedValue withValue( final double newValue )
	{
		final double newMinBound = Math.min( minBound, newValue );
		final double newMaxBound = Math.max( maxBound, newValue );
		return new BoundedValue( newMinBound, newMaxBound, newValue );
	}

	public BoundedValue withMaxBound( final double newMaxBound )
	{
		final double newMinBound = Math.min( minBound, newMaxBound );
		final double newValue = Math.min( Math.max( value, newMinBound ), newMaxBound );
		return new BoundedValue( newMinBound, newMaxBound, newValue );
	}

	public BoundedValue withMinBound( final double newMinBound )
	{
		final double newMaxBound = Math.max( maxBound, newMinBound );
		final double newValue = Math.min( Math.max( value, newMinBound ), newMaxBound );
		return new BoundedValue( newMinBound, newMaxBound, newValue );
	}

	@Override
	public String toString()
	{
		return "BoundedValue[ (" + minBound + ") " + value + " (" + maxBound + ") ]";
	}

	@Override
	public boolean equals( final Object o )
	{
		if ( this == o )
			return true;
		if ( o == null || getClass() != o.getClass() )
			return false;

		final BoundedValue that = (BoundedValue) o;

		if ( Double.compare( that.minBound, minBound ) != 0 )
			return false;
		if ( Double.compare( that.maxBound, maxBound ) != 0 )
			return false;
		return Double.compare( that.value, value ) == 0;
	}

	@Override
	public int hashCode()
	{
		int result;
		long temp;
		temp = Double.doubleToLongBits( minBound );
		result = ( int ) ( temp ^ ( temp >>> 32 ) );
		temp = Double.doubleToLongBits( maxBound );
		result = 31 * result + ( int ) ( temp ^ ( temp >>> 32 ) );
		temp = Double.doubleToLongBits( value );
		result = 31 * result + ( int ) ( temp ^ ( temp >>> 32 ) );
		return result;
	}
}
