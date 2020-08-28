package align;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import data.STData;
import data.STDataStatistics;
import imglib2.icp.ICP;
import imglib2.icp.PointMatchIdentification;
import imglib2.icp.StDataPointMatchIdentification;
import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.Model;
import mpicbg.models.PointMatch;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class ICPAlign
{
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
		// regularly sample the reference dataset
		final STDataStatistics stStatsDataA = new STDataStatistics( stdataA );
		final STDataStatistics stStatsDataB = new STDataStatistics( stdataB );

		final ArrayList< RealPoint > listA = new ArrayList<>(); // reference
		final ArrayList< RealPoint > listB = new ArrayList<>(); // target

		for ( final RealLocalizable p : stdataA )
			listA.add( new RealPoint( p ) );

		//System.out.println( "listA (reference): " + listA.size() );

		/*
		// tmp
		System.out.println( "listB (target) sampling: " + StDataPointMatchIdentification.sampling );
		StDataPointMatchIdentification.sampling = 4.0;//Math.min( stStatsDataA.getMedianDistance(), stStatsDataB.getMedianDistance() ) / 2.0;
		IterableRealInterval< DoubleType > dataB = TransformCoordinates.sample( stdataB.getExprData( genesToUse.get( 0 ) ), StDataPointMatchIdentification.sampling );
		final RealCursor< DoubleType > c = dataB.localizingCursor();

		while ( c.hasNext() )
		{
			c.fwd();
			listB.add( new RealPoint( c ) );
		}
		*/
		for ( final RealLocalizable p : stdataB )
			listB.add( new RealPoint( p ) );

		//System.out.println( "listB (target): " + listB.size() );

		/*
		final double[] m = initialModel.getRowPackedCopy(); //a.m00, a.m01, a.m02, a.m10, a.m11, a.m12
		final AffineModel2D model = new AffineModel2D();
		//model.set( m00, m10, m01, m11, m02, m12 );
		model.set( m[ 0 ], m[ 3 ], m[ 1 ], m[ 4 ], m[ 2 ], m[ 5 ] );
		*/
		
		final M model = initialModel.copy();

		final PointMatchIdentification< RealPoint > pmi = new StDataPointMatchIdentification<>( stdataB, stdataA, genesToUse, maxDistance );
		final ICP< RealPoint > icp = new ICP<>( listB, listA, pmi );

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
		{
			return null;
		}
		else
		{
			/*
			model.toArray( m ); // The order is: m00, m10, m01, m11, m02, m12
			final AffineTransform2D result = new AffineTransform2D();
			// a.m00, a.m01, a.m02, a.m10, a.m11, a.m12
			result.set( m[ 0 ], m[ 2 ], m[ 4 ], m[ 1 ], m[ 3 ], m[ 5 ] );
			*/
			return new ValuePair<>( model, new ArrayList<>( PointMatch.flip( icp.getPointMatches() ) ) );
		}
	}

}
