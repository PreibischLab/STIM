package render;

import filter.MeanFilter;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.Sampler;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import test.SimpleRealLocalizable;
import test.SimpleSampler;

public class MeanMaxDistanceSearchOnKDTree< T extends RealType< T > > implements IntegratingNeighborSearch< T >
{
	protected KDTree< T > tree;
	protected final int n;
	protected final double[] pos;

	final SimpleSampler< T > value;
	final SimpleRealLocalizable position;
	final T outofbounds;
	final double maxDistance;
	final MeanFilter< T, T > meanFilter;

	public MeanMaxDistanceSearchOnKDTree( final KDTree< T > tree, final T outofbounds, final double maxDistance )
	{
		n = tree.numDimensions();
		pos = new double[ n ];
		this.tree = tree;

		this.outofbounds = outofbounds;
		this.value = new SimpleSampler< T >( outofbounds.createVariable() );

		this.maxDistance = maxDistance;
		this.position = new SimpleRealLocalizable( pos );
		this.meanFilter = new MeanFilter< T, T >(
				new RadiusNeighborSearchOnKDTree<>( tree ),
				maxDistance,
				outofbounds );
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public void search( final RealLocalizable p )
	{
		p.localize( pos );
		meanFilter.filter( p, value.get() );
	}

	@Override
	public Sampler< T > getSampler()
	{
		return value;
	}

	@Override
	public MeanMaxDistanceSearchOnKDTree< T > copy()
	{
		final MeanMaxDistanceSearchOnKDTree< T > copy = new MeanMaxDistanceSearchOnKDTree< T >( tree, outofbounds, maxDistance );
		System.arraycopy( pos, 0, copy.pos, 0, pos.length );
		return copy;
	}
}
