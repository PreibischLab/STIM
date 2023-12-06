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
	/** 
	 * A common parameter class so maxDistance can be changed externally
	 */
	public static class NNParams
	{
		private double maxSqDistance, maxDistance;

		public NNParams( final double maxDistance ) { setMaxDistance( maxDistance ); }

		public void setMaxDistance( final double maxDistance )
		{
			this.maxDistance = maxDistance;
			this.maxSqDistance = maxDistance * maxDistance;
		}

		public double maxDistance() { return maxDistance; }
		public double maxSqDistance() { return maxSqDistance; }
	}

	final Supplier<T> outofbounds;
	final SimpleSampler< T > oobsSampler;
	final SimpleRealLocalizable position;
	final NNParams params;

	Sampler< T > value;
	RealLocalizable point;
	double newbestSquDistance;

	public NearestNeighborMaxDistanceSearchOnKDTree( final KDTree< T > tree, final Supplier<T> outofbounds, final NNParams params )
	{
		super( tree );

		this.oobsSampler = new SimpleSampler< T >( outofbounds );
		this.position = new SimpleRealLocalizable( pos );
		this.outofbounds = outofbounds;
		this.params = params;
	}

	@Override
	public void search( final RealLocalizable p )
	{
		super.search( p );

		if ( bestSquDistance > params.maxSqDistance() )
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
		final NearestNeighborMaxDistanceSearchOnKDTree< T > copy = new NearestNeighborMaxDistanceSearchOnKDTree< T >( tree, outofbounds, params );
		System.arraycopy( pos, 0, copy.pos, 0, pos.length );
		copy.bestPoint = bestPoint;
		copy.bestSquDistance = bestSquDistance;
		copy.newbestSquDistance = newbestSquDistance;
		copy.point = point;
		copy.value = value;
		return copy;
	}
}
