package filter;

import net.imglib2.neighborsearch.RadiusNeighborSearch;

public abstract class RadiusSearchFilter< S, T, F extends RadiusSearchFilterFactory<S, T> > implements Filter< T >
{
	final RadiusNeighborSearch< S > search;
	final F factory;

	public RadiusSearchFilter(
			final RadiusNeighborSearch< S > search,
			final F factory )
	{
		this.search = search;
		this.factory = factory;
	}

	public F getFactory() { return factory; }
}
