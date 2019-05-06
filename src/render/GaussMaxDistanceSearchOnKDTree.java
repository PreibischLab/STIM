package render;

import filter.GaussianFilter;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.Sampler;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import test.SimpleRealLocalizable;
import test.SimpleSampler;

public class GaussMaxDistanceSearchOnKDTree< T extends RealType< T > > implements IntegratingNeighborSearch< T >
{
	protected KDTree< T > tree;
	protected final int n;
	protected final double[] pos;

	final SimpleSampler< T > value;
	final SimpleRealLocalizable position;
	final T outofbounds;
	final double maxDistance;
	final double sigma;
	final boolean normalize;

	final GaussianFilter< T, T > gaussianFilter;

	public GaussMaxDistanceSearchOnKDTree(
			final KDTree< T > tree,
			final T outofbounds,
			final double maxDistance,
			final double sigma,
			final boolean normalize )
	{
		n = tree.numDimensions();
		pos = new double[ n ];
		this.tree = tree;

		this.value = new SimpleSampler<>( outofbounds.createVariable() );
		this.maxDistance = maxDistance;
		this.position = new SimpleRealLocalizable( pos );
		this.outofbounds = outofbounds;
		this.sigma = sigma;
		this.normalize = normalize;

		this.gaussianFilter = new GaussianFilter< T, T >(
				new RadiusNeighborSearchOnKDTree<>( tree ),
				outofbounds,
				maxDistance,
				sigma,
				normalize );
	}

	@Override
	public void search( final RealLocalizable p )
	{
		p.localize( pos );
		gaussianFilter.filter( p, value.get() );
	}

	@Override
	public Sampler< T > getSampler()
	{
		return value;
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public GaussMaxDistanceSearchOnKDTree< T > copy()
	{
		final GaussMaxDistanceSearchOnKDTree< T > copy = new GaussMaxDistanceSearchOnKDTree< T >( tree, outofbounds, maxDistance, sigma, normalize );
		System.arraycopy( pos, 0, copy.pos, 0, pos.length );
		return copy;
	}
}
