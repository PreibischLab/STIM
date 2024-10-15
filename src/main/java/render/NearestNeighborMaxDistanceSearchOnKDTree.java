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
	final SimpleSampler<T> oobSampler;
	final SimpleRealLocalizable position;
	final MaxDistanceParam param;
	final KDTree< T > tree; // is private in superclass
	final double[] pos;

	Sampler< T > value;
	RealLocalizable point;
	double newBestSquDistance;

	public NearestNeighborMaxDistanceSearchOnKDTree(final KDTree< T > tree, final Supplier<T> outOfBounds, final MaxDistanceParam param )
	{
		super( tree );

		this.pos = new double[ tree.numDimensions() ];
		this.tree = tree;
		this.oobSampler = new SimpleSampler<>(outOfBounds);
		this.position = new SimpleRealLocalizable( pos ); // last queried location
		this.outOfBounds = outOfBounds;
		this.param = param;
	}

	@Override
	public void search( final RealLocalizable p )
	{
		super.search( p );
		p.localize( pos );

		if ( super.getSquareDistance() > param.maxSqDistance() )
		{
			value = oobSampler;
			point = position;
			newBestSquDistance = 0;
		}
		else
		{
			value = super.getSampler();
			point = super.getPosition();
			newBestSquDistance = super.getSquareDistance();
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
		return newBestSquDistance;
	}

	@Override
	public double getDistance()
	{
		return Math.sqrt(newBestSquDistance);
	}

	@Override
	public NearestNeighborMaxDistanceSearchOnKDTree< T > copy()
	{
		final NearestNeighborMaxDistanceSearchOnKDTree< T > copy =
				new NearestNeighborMaxDistanceSearchOnKDTree<>( new KDTree<>( tree.treeData() ), outOfBounds, param);

		// make sure the state is preserved
		copy.search( position );

		return copy;
	}
}
