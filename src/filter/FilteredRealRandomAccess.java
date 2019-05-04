package filter;

import net.imglib2.IterableRealInterval;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.Sampler;

public abstract class FilteredRealRandomAccess< T > implements RealRandomAccess< T >
{
	final IterableRealInterval< T > data;
	final double[] pos;
	final int n;

	public FilteredRealRandomAccess( final IterableRealInterval< T > data )
	{
		this.data = data;
		this.n = data.numDimensions();
		this.pos = new double[ n ];
	}

	@Override
	public double getDoublePosition( final int d )
	{
		return pos[ d ];
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public void move( final float distance, final int d )
	{
		pos[ d ] += distance;
	}

	@Override
	public void move( final double distance, final int d )
	{
		pos[ d ] += distance;
	}

	@Override
	public void move( final RealLocalizable distance )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] += distance.getDoublePosition( d );
	}

	@Override
	public void move( final float[] distance )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] += distance[ d ];
	}

	@Override
	public void move( final double[] distance )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] += distance[ d ];
	}

	@Override
	public void setPosition( final RealLocalizable position )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = position.getDoublePosition( d );
	}

	@Override
	public void setPosition( final float[] position )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final double[] position )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final float position, final int d )
	{
		pos[ d ] = position;
	}

	@Override
	public void setPosition( final double position, final int d )
	{
		pos[ d ] = position;
	}

	@Override
	public void fwd( final int d )
	{
		++pos[ d ];
	}

	@Override
	public void bck( final int d )
	{
		--pos[ d ];
	}

	@Override
	public void move( final int distance, final int d )
	{
		pos[ d ] += distance;
	}

	@Override
	public void move( final long distance, final int d )
	{
		pos[ d ] += distance;
	}

	@Override
	public void move( final Localizable distance )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] += distance.getDoublePosition( d );
	}

	@Override
	public void move( final int[] distance )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] += distance[ d ];
	}

	@Override
	public void move( final long[] distance )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] += distance[ d ];
	}

	@Override
	public void setPosition( final Localizable position )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = position.getDoublePosition( d );
	}

	@Override
	public void setPosition( final int[] position )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final long[] position )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final int position, final int d )
	{
		pos[ d ] = position;
	}

	@Override
	public void setPosition( final long position, final int d )
	{
		pos[ d ] = position;
	}

	@Override
	public Sampler< T > copy()
	{
		return copyRealRandomAccess();
	}
}
