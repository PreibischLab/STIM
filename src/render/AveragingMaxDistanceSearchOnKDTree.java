package render;

import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.Sampler;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import test.SimpleRealLocalizable;
import test.SimpleSampler;

public class AveragingMaxDistanceSearchOnKDTree< T extends RealType< T > > implements IntegratingNeighborSearch< T >
{
	protected KDTree< T > tree;

	protected final int n;

	protected final double[] pos;

	final T outofbounds, avg;
	final SimpleSampler< T > oobsSampler, avgSampler;
	SimpleSampler< T > value;

	final SimpleRealLocalizable position;
	final double maxDistance;
	final RadiusNeighborSearchOnKDTree<T> search;

	public AveragingMaxDistanceSearchOnKDTree( final KDTree< T > tree, final T outofbounds, final double maxDistance )
	{
		n = tree.numDimensions();
		pos = new double[ n ];
		this.tree = tree;

		this.avg = outofbounds.createVariable();
		this.outofbounds = outofbounds;
		this.avgSampler = new SimpleSampler<>( avg );
		this.oobsSampler = new SimpleSampler<>( outofbounds );

		this.maxDistance = maxDistance;
		this.position = new SimpleRealLocalizable( pos );
		this.search = new RadiusNeighborSearchOnKDTree<>( tree );

		// initialize with oobs value
		// this only matters if .getSampler() is incorrectly called before .search() 
		this.value = oobsSampler;
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

		search.search( position, maxDistance, false );

		if ( search.numNeighbors() == 0 )
		{
			value = oobsSampler;
		}
		else
		{
			double value = 0;

			for ( int i = 0; i < search.numNeighbors(); ++i )
				value += search.getSampler( i ).get().getRealDouble();

			avg.setReal( value / (double) search.numNeighbors() );
			this.value = avgSampler;
		}
	}

	@Override
	public Sampler< T > getSampler()
	{
		return value;
	}

	@Override
	public AveragingMaxDistanceSearchOnKDTree< T > copy()
	{
		final AveragingMaxDistanceSearchOnKDTree< T > copy = new AveragingMaxDistanceSearchOnKDTree< T >( tree, outofbounds, maxDistance );
		System.arraycopy( pos, 0, copy.pos, 0, pos.length );
		return copy;
	}
}
