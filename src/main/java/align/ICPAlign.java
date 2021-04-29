package align;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import data.STData;
import imglib2.icp.ICP;
import imglib2.icp.LinkedPoint;
import imglib2.icp.PointMatchIdentification;
import imglib2.icp.StDataPointMatchIdentification;
import mpicbg.models.Model;
import mpicbg.models.PointMatch;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class ICPAlign
{
	public static double computeSum( final RealLocalizable q, final HashMap< String, NearestNeighborSearchOnKDTree< DoubleType > > searchReference )
	{
		double sum = 0;
		for ( final NearestNeighborSearchOnKDTree< DoubleType > search : searchReference.values() )
		{
			search.search( q );
			sum += search.getSampler().get().get();
		}
		return sum;
	}

	/**
	 * @param stdataA - data for A
	 * @param stdataB - data for B
	 * @param genesToUse - list of genes
	 * @param initialModel - maps B to A
	 * @param maxDistance - max search radius for corresponding point
	 * @param maxIterations - max num of ICP iterations
	 */
	public static < M extends Model< M >> Pair< M, List< PointMatch > > alignICP(
			final STData stdataA,
			final STData stdataB,
			final Collection< String > genesToUse,
			final M initialModel,
			final double maxDistance,
			final int maxIterations )
	{
		final ArrayList< RealPoint > listA = new ArrayList<>(); // reference
		final ArrayList< RealPoint > listB = new ArrayList<>(); // target

		for ( final RealLocalizable p : stdataA )
			listA.add( new RealPoint( p ) );

		for ( final RealLocalizable p : stdataB )
			listB.add( new RealPoint( p ) );

		//
		// filter only the brightest local points
		// this way we make sure it is a relative measure, map brightest point in the local neighborhood to the brightest point in the corresponding area
		//
		//final ArrayList< RealPoint > listAFiltered = new ArrayList<>(); // reference filtered

		final HashMap< String, NearestNeighborSearchOnKDTree< DoubleType > > searchReference = new HashMap<>();
		
		for ( final String gene : genesToUse )
			searchReference.put( gene, new NearestNeighborSearchOnKDTree<>( new KDTree<>( stdataA.getExprData( gene ) ) ) );

		final KDTree< RealPoint > kdTreeRef = new KDTree< RealPoint >( listA, listA );
		final RadiusNeighborSearchOnKDTree< RealPoint > radiusSearchRef = new RadiusNeighborSearchOnKDTree<>( kdTreeRef );

		// TODO: Gaussian blur first?? and/or relative brightness of each spot or gradient instead of selecting them? -- checkout 0<>2

		/*
		for ( final RealPoint p : listA )
		{
			radiusSearchRef.search( p, maxDistance, true );

			// we ignore isolated points
			if ( radiusSearchRef.numNeighbors() == 1 )
				continue;

			// sum across all genes
			final double myBrightness = computeSum( radiusSearchRef.getSampler( 0 ).get(), searchReference );
			boolean isBrightest = true;

			for ( int i = 1; i < radiusSearchRef.numNeighbors(); ++i )
			{
				// sum across all genes
				final double otherBrightness = computeSum( radiusSearchRef.getSampler( i ).get(), searchReference );

				if ( otherBrightness > myBrightness )
				{
					isBrightest = false;
					break;
				}
			}

			if ( isBrightest && myBrightness > 0 )
				listAFiltered.add( p );
		}

		System.out.println( "Remaining: " + listAFiltered.size()+ "/" + listA.size() + ", maxdist=" + maxDistance );
		*/
		final M model = initialModel.copy();

		final PointMatchIdentification< RealPoint > pmi = new StDataPointMatchIdentification<>( stdataB, stdataA, genesToUse, maxDistance );
		final ICP< RealPoint > icp = new ICP<>( listB, listA /* listAFiltered */, pmi, maxDistance / 2.0 );

		int i = 0;
		double lastAvgError = 0;
		int lastNumCorresponding = 0;

		boolean converged = false;

		do
		{
			try
			{
				icp.runICPIteration( model, model );
			}
			catch ( Exception e )
			{
				//System.out.println( "Failed with e: " + e );
				e.printStackTrace();
				return null;
			}

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();
			
			System.out.println( i + ": " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + ", max error [px] " + icp.getMaximalError() );
		}
		while ( !converged && ++i < maxIterations );

		if ( icp.getPointMatches() == null )
			return null;
		else
			return new ValuePair<>( model, new ArrayList<>( PointMatch.flip( icp.getPointMatches() ) ) );
	}

}
