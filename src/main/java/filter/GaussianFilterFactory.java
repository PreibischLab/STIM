package filter;

import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;

public class GaussianFilterFactory< S extends RealType< S >, T extends RealType< T > > extends RadiusSearchFilterFactory< S, T >
{
	public enum WeightType { NONE, BY_SUM_OF_WEIGHTS, BY_SUM_OF_SAMPLES, PARTIAL_BY_SUM_OF_WEIGHTS }

	final T outofbounds;
	double sigma, two_sq_sigma;
	final WeightType normalize;

	public GaussianFilterFactory(
			final T outofbounds,
			final double sigma )
	{
		this( outofbounds, sigma, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS );
	}

	public GaussianFilterFactory(
			final T outofbounds,
			final double sigma,
			final WeightType normalize )
	{
		this( outofbounds, 5 * sigma, sigma, normalize );
	}

	public GaussianFilterFactory(
			final T outofbounds,
			final double radius,
			final double sigma,
			final WeightType normalize )
	{
		super( radius );

		this.outofbounds = outofbounds;
		this.sigma = sigma;
		this.normalize = normalize;
		this.two_sq_sigma =  2 * sigma * sigma;
	}

	@Override
	public Filter< T > createFilter( final KDTree< S > tree )
	{
		return new GaussianFilter< S, T >(
				new RadiusNeighborSearchOnKDTree<>( tree ), // TODO: is this copied?
				this,
				outofbounds.copy() );
				/*radius,
				sigma,
				normalize );*/
	}

	/**
	 * sets the sigma, radius, and 2*sigma^2
	 * @param sigma - new sigma
	 */
	public void setSigma( final double sigma )
	{
		this.sigma = sigma;
		this.two_sq_sigma =  2 * sigma * sigma;
		this.radius = 5 * sigma;
	}

	public void setTwoSigmaSq( final double two_sq_sigma ) { this.two_sq_sigma =  two_sq_sigma; }

	/**
	 * @return - the current 2*sigma^2 for the search, can be changed dynamically
	 */
	public double getTwoSqSigma() { return two_sq_sigma; }

	/**
	 * @return - the current sigma for the search, can be changed dynamically
	 */
	public double getSigma() { return sigma; }

	/**
	 * @return - the weight type of the gauss filter
	 */
	public WeightType getNormalize() { return normalize; }

	@Override
	public T create()
	{
		return outofbounds.createVariable();
	}
}
