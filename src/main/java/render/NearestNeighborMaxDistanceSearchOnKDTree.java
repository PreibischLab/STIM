package render;

import java.util.function.Supplier;

import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.Sampler;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import util.SimpleRealLocalizable;
import util.SimpleSampler;

public class NearestNeighborMaxDistanceSearchOnKDTree< T > extends NearestNeighborSearchOnKDTree< T >
{
	final Supplier<T> outOfBounds;
	final SimpleSampler< T > oobsSampler;
	final SimpleRealLocalizable position;
	final MaxDistanceParam param;

	Sampler< T > value;
	RealLocalizable point;
	double newbestSquDistance;

	public NearestNeighborMaxDistanceSearchOnKDTree(final KDTree< T > tree, final Supplier<T> outOfBounds, final MaxDistanceParam param )
	{
		super( tree );

		this.oobsSampler = new SimpleSampler<>(outOfBounds);
		this.position = new SimpleRealLocalizable( pos );
		this.outOfBounds = outOfBounds;
		this.param = param;
	}

	@Override
	public void search( final RealLocalizable p )
	{
		super.search( p );

		if ( bestSquDistance > param.maxSqDistance() )
		{
			value = oobsSampler;
			point = position;
			newbestSquDistance = 0;
		}
		else
		{
			value = bestPoint;
			point = bestPoint;
			newbestSquDistance = bestSquDistance;
		}
	}

	@Override
	public Sampler< T > getSampler()
	{
		return value;
	}

	@Override
	public RealLocalizable getPosition()
	{
		return point;
	}

	@Override
	public double getSquareDistance()
	{
		return newbestSquDistance;
	}

	@Override
	public double getDistance()
	{
		return Math.sqrt( newbestSquDistance );
	}

	@Override
	public NearestNeighborMaxDistanceSearchOnKDTree< T > copy()
	{
		final NearestNeighborMaxDistanceSearchOnKDTree< T > copy = new NearestNeighborMaxDistanceSearchOnKDTree<>(tree, outOfBounds, param);
		System.arraycopy( pos, 0, copy.pos, 0, pos.length );
		copy.bestPoint = bestPoint;
		copy.bestSquDistance = bestSquDistance;
		copy.newbestSquDistance = newbestSquDistance;
		copy.point = point;
		copy.value = value;
		return copy;
	}
}
