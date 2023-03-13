package filter;

import filter.GaussianFilterFactory.WeightType;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.type.numeric.RealType;

public class GaussianFilter< S extends RealType< S >, T extends RealType< T > > extends RadiusSearchFilter< S, T >
{
	final T outofbounds;
	final WeightType normalize;
	final double two_sq_sigma;

	final double thresholdMax = 0.5;
	final double thresholdMin = 0.001;

	public GaussianFilter(
			final RadiusNeighborSearch< S > search,
			final T outofbounds,
			final double radius,
			final double sigma,
			final WeightType normalize )
	{
		super( search, radius );

		this.outofbounds = outofbounds;
		this.normalize = normalize;
		this.two_sq_sigma = 2 * sigma * sigma;
	}

	@Override
	public void filter( final RealLocalizable position, final T output )
	{
		search.search( position, radius, false );

		if ( search.numNeighbors() == 0 )
		{
			output.set( outofbounds );
		}
		else
		{
			double value = 0;
			double weight = 0;

			for ( int i = 0; i < search.numNeighbors(); ++i )
			{
				final double dist = search.getDistance( i );
				final double w = Math.exp( -( dist * dist ) / two_sq_sigma );

				// hypothesis: these cursors are not copied again, so they are accessed in parallel
				value += search.getSampler( i ).get().getRealDouble() * w;

				if ( normalize == WeightType.BY_SUM_OF_WEIGHTS || normalize == WeightType.PARTIAL_BY_SUM_OF_WEIGHTS )
					weight += w;
			}

			if ( normalize == WeightType.PARTIAL_BY_SUM_OF_WEIGHTS )
			{
				if ( weight > thresholdMax )
					output.setReal( value / weight );
				else if ( weight <= thresholdMax && weight >= thresholdMin )
				{
					final double a = Math.sin( ( ( weight - thresholdMin ) / ( thresholdMax - thresholdMin ) ) * Math.PI/2 );
					final double b = 1.0 - a;

					output.setReal( a * ( value / weight ) + b * value );
				}
				else
					output.setReal( value );
			}
			else if ( normalize == WeightType.BY_SUM_OF_WEIGHTS )
				output.setReal( value / weight );
			else if ( normalize == WeightType.BY_SUM_OF_SAMPLES )
				output.setReal( value / search.numNeighbors() );
			else
				output.setReal( value );
		}
	}

	public static void main( String[] arg )
	{
		double thresholdMax = 0.8;
		double thresholdMin = 0.2;

		for ( double dist = 0.2; dist <= 0.8; dist += 0.1 )
		{

			double reWeight = ( ( dist - thresholdMin ) / ( thresholdMax - thresholdMin ) ) * Math.PI/2;
			double a = Math.sin( reWeight );
			double b = 1.0 - a;

			System.out.println( dist + " " + reWeight + " " +  a + " " + b );
		}
	}
}
