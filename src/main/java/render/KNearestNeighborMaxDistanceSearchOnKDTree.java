package render;

import java.util.function.Supplier;

import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.Sampler;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import util.SimpleRealLocalizable;
import util.SimpleSampler;

public class KNearestNeighborMaxDistanceSearchOnKDTree< T > extends KNearestNeighborSearchOnKDTree< T >
{
	final Supplier<T> outofbounds;
	final SimpleSampler< T > oobsSampler;
	final SimpleRealLocalizable position;
	final double maxSqDistance, maxDistance;

	final Sampler[] values;
	final RealLocalizable[] points;
	final double[] newbestSquDistances;

	int l = 0;

	public KNearestNeighborMaxDistanceSearchOnKDTree( final KDTree< T > tree, final int k, final Supplier<T> outofbounds, final double maxDistance )
	{
		super( tree, k );

		this.oobsSampler = new SimpleSampler< T >( outofbounds );
		this.position = new SimpleRealLocalizable( pos );
		this.maxDistance = maxDistance;
		this.maxSqDistance = maxDistance * maxDistance;
		this.outofbounds = outofbounds;

		this.values = new Sampler[ k ];
		this.points = new RealLocalizable[ k ];
		this.newbestSquDistances = new double[ k ];
	}

	@Override
	public void search( final RealLocalizable p )
	{
		super.search( p );

		for ( int i = 0; i < k; ++i )
		{
			if ( bestSquDistances[ i ] > maxSqDistance )
			{
				if ( i == 0 )
				{
					values[ i ] = oobsSampler;
					points[ i ] = position;
				}
				else
				{
					values[ i ] = null;
					points[ i ] = null;
				}
				newbestSquDistances[ i ] = 0;
			}
			else
			{
				values[ i ] = bestPoints[ i ];
				points[ i ] = bestPoints[ i ];
				newbestSquDistances[ i ] = bestSquDistances[ i ];
			}
		}
	}

	@Override
	public Sampler< T > getSampler( final int i )
	{
		return values[ i ];
	}

	@Override
	public RealLocalizable getPosition( final int i )
	{
		return points[ i ];
	}

	@Override
	public double getSquareDistance( final int i )
	{
		return newbestSquDistances[ i ];
	}

	@Override
	public double getDistance( final int i )
	{
		return Math.sqrt( newbestSquDistances[ i ] );
	}

	@Override
	public Sampler< T > getSampler()
	{
		return values[ 0 ];
	}

	@Override
	public RealLocalizable getPosition()
	{
		return points[ 0 ];
	}

	@Override
	public double getSquareDistance()
	{
		return newbestSquDistances[ 0 ];
	}

	@Override
	public double getDistance()
	{
		return Math.sqrt( newbestSquDistances[ 0 ] );
	}

	@Override
	public KNearestNeighborMaxDistanceSearchOnKDTree< T > copy()
	{
		final KNearestNeighborMaxDistanceSearchOnKDTree< T > copy = new KNearestNeighborMaxDistanceSearchOnKDTree< T >( tree, k, outofbounds, maxDistance );
		System.arraycopy( pos, 0, copy.pos, 0, pos.length );

		for ( int i = 0; i < k; ++i )
		{
			copy.bestPoints[ i ] = bestPoints[ i ];
			copy.bestSquDistances[ i ] = bestSquDistances[ i ];
			copy.newbestSquDistances[ i ] = newbestSquDistances[ i ];
			copy.points[ i ] = points[ i ];
			copy.values[ i ] = values[ i ];
		}

		return copy;
	}
}
