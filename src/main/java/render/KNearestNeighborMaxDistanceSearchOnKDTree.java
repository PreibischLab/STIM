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
	final Supplier<T> outOfBounds;
	final SimpleSampler< T > oobsSampler;
	final SimpleRealLocalizable position;
	final MaxDistanceParam param;

	final Sampler[] values;
	final RealLocalizable[] points;
	final double[] newBestSquDistances;
	final double[] pos;
	final KDTree< T > tree; // is private in superclass

	public KNearestNeighborMaxDistanceSearchOnKDTree(
			final KDTree< T > tree,
			final int k,
			final Supplier<T> outOfBounds,
			final MaxDistanceParam param )
	{
		super( tree, k );

		this.pos = new double[ tree.numDimensions() ];
		this.oobsSampler = new SimpleSampler<>(outOfBounds);
		this.position = new SimpleRealLocalizable( pos );
		this.param = param;
		this.outOfBounds = outOfBounds;
		this.tree = tree;

		this.values = new Sampler[ k ];
		this.points = new RealLocalizable[ k ];
		this.newBestSquDistances = new double[ k ];
	}

	@Override
	public void search( final RealLocalizable p )
	{
		super.search( p );
		p.localize( pos );

		for ( int i = 0; i < getK(); ++i )
		{
			if ( getSquareDistance( i ) > param.maxSqDistance() )
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
				newBestSquDistances[ i ] = 0;
			}
			else
			{
				values[ i ] = getSampler( i );
				points[ i ] = getPosition( i );
				newBestSquDistances[ i ] = getSquareDistance( i );
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
		return newBestSquDistances[ i ];
	}

	@Override
	public double getDistance( final int i )
	{
		return Math.sqrt(newBestSquDistances[ i ] );
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
		return newBestSquDistances[ 0 ];
	}

	@Override
	public double getDistance()
	{
		return Math.sqrt(newBestSquDistances[ 0 ] );
	}

	@Override
	public KNearestNeighborMaxDistanceSearchOnKDTree< T > copy()
	{
		final KNearestNeighborMaxDistanceSearchOnKDTree< T > copy =
				new KNearestNeighborMaxDistanceSearchOnKDTree<>(tree, getK(), outOfBounds, param);

		// make sure the state is preserved
		copy.search( position );

		return copy;
	}
}
