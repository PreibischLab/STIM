package filter.realrandomaccess;

import filter.Filter;
import filter.FilterFactory;
import net.imglib2.IterableRealInterval;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.Sampler;
import util.SimpleRealLocalizable;

public class FilteredRealRandomAccess< S, T > implements RealRandomAccess< T >
{
	final IterableRealInterval< S > data;
	final FilterFactory< S, T > filterFactory;
	final Filter< T > filter;
	final T output;
	final double[] pos;
	final SimpleRealLocalizable loc;
	final int n;

	public FilteredRealRandomAccess( final IterableRealInterval< S > data, final FilterFactory< S, T > filterFactory )
	{
		this.data = data;
		this.filterFactory = filterFactory;
		this.n = data.numDimensions();
		this.pos = new double[ n ];
		this.filter = filterFactory.createFilter( data );
		this.output = filterFactory.create();
		this.loc = new SimpleRealLocalizable( pos );
	}

	@Override
	public T get()
	{
		filter.filter( loc, output );

		return output;
	}

	@Override
	public RealRandomAccess< T > copyRealRandomAccess()
	{
		final FilteredRealRandomAccess< S, T > mrr = new FilteredRealRandomAccess< S, T >( data, filterFactory );
		mrr.setPosition( this );

		return mrr;
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
