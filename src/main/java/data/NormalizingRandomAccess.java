package data;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.real.DoubleType;

public class NormalizingRandomAccess implements RandomAccess< DoubleType >
{
	final protected DoubleType value = new DoubleType();

	final RandomAccess< DoubleType > inputRandomAccess;
	final RandomAccess< DoubleType > sumsPerLocationRandomAccess;

	public NormalizingRandomAccess(
			final RandomAccess< DoubleType > inputRandomAccess,
			final RandomAccess< DoubleType > sumsPerLocationRandomAccess )
	{
		this.inputRandomAccess = inputRandomAccess;
		this.sumsPerLocationRandomAccess = sumsPerLocationRandomAccess;
	}

	@Override
	public DoubleType get()
	{
//		d_mn -> d_mn = log ( 10^5 * d_mn / sum_k(d_mk) + 1 )
//
//		d_{mn} is an entry of the matrix, with {n} denoting the
//		cell/location and {m} the gene.
//
//		- cell means location, yes.
//		- {k} is a dummy index, you just sum over it
//
//		Basically you sum all raw UMIs/counts for a given cell/location,
//		then you divide those counts by that number, multiply
//		by 10*5 to get meaningful numbers, add 1 to escape infinities
//		and take a logarithm of that.

		final double d_mn = inputRandomAccess.get().get();
		final double sum_k = sumsPerLocationRandomAccess.get().get();

		if ( sum_k == 0 || d_mn == 0 )
			value.set( 0 );
		else
			value.set( Math.log( 10000 * d_mn / sum_k + 1 ) );

		return value;
	}

	@Override
	public NormalizingRandomAccess copy() {
		final NormalizingRandomAccess r = new NormalizingRandomAccess(inputRandomAccess.copy(), sumsPerLocationRandomAccess.copy());
		r.setPosition(this);
		return r;
	}

	@Override
	public void fwd( final int d )
	{
		inputRandomAccess.fwd( d );

		if ( d == 1 )
			sumsPerLocationRandomAccess.fwd( 0 );
	}

	@Override
	public void bck( final int d )
	{
		inputRandomAccess.bck( d );

		if ( d == 1 )
			sumsPerLocationRandomAccess.bck( 0 );
	}

	@Override
	public void move( final int distance, final int d )
	{
		inputRandomAccess.move( distance, d );

		if ( d == 1 )
			sumsPerLocationRandomAccess.move( distance, 0 );
	}

	@Override
	public void move( final long distance, final int d )
	{
		inputRandomAccess.move( distance, d );

		if ( d == 1 )
			sumsPerLocationRandomAccess.move( distance, 0 );
	}

	@Override
	public void move( final Localizable localizable )
	{
		inputRandomAccess.move( localizable );
		sumsPerLocationRandomAccess.move( localizable.getLongPosition( 1 ), 0 );
	}

	@Override
	public void move( final int[] distance )
	{
		inputRandomAccess.move( distance );
		sumsPerLocationRandomAccess.move( distance[ 1 ], 0 );
	}

	@Override
	public void move( final long[] distance )
	{
		inputRandomAccess.move( distance );
		sumsPerLocationRandomAccess.move( distance[ 1 ], 0 );
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		inputRandomAccess.setPosition( localizable );
		sumsPerLocationRandomAccess.setPosition( localizable.getLongPosition( 1 ), 0 );
	}

	@Override
	public void setPosition( final int[] pos )
	{
		inputRandomAccess.setPosition( pos );
		sumsPerLocationRandomAccess.setPosition( pos[ 1 ], 0 );
	}

	@Override
	public void setPosition( final long[] pos )
	{
		inputRandomAccess.setPosition( pos );
		sumsPerLocationRandomAccess.setPosition( pos[ 1 ], 0 );
	}

	@Override
	public void setPosition( final int pos, final int d )
	{
		inputRandomAccess.setPosition( pos, d );

		if ( d == 1 )
			sumsPerLocationRandomAccess.setPosition( pos, 0 );
	}

	@Override
	public void setPosition( final long pos, final int d )
	{
		inputRandomAccess.setPosition( pos, d );

		if ( d == 1 )
			sumsPerLocationRandomAccess.setPosition( pos, 0 );
	}

	@Override
	public long getLongPosition( int d )
	{
		return inputRandomAccess.getLongPosition( d );
	}

	@Override
	public int numDimensions()
	{
		return inputRandomAccess.numDimensions();
	}
}
