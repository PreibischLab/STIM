package align;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import data.STData;
import imglib2.icp.ICP;
import imglib2.icp.PointMatchIdentification;
import imglib2.icp.StDataRelativePointMatchIdentification;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class ICPAlign
{
	private static final Logger logger = LoggerUtil.getLogger();

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
	 * @param transformA - loaded transform for A
	 * @param stdataB - data for B
	 * @param transformB - loaded transform for B
	 * @param genesToUse - list of genes
	 * @param initialModel - maps B to A
	 * @param maxDistance - max search radius for corresponding point
	 * @param ransacDistance - distance for ransac, Double.NaN means no ransac
	 * @param maxIterations - max num of ICP iterations
	 * @param service - ExecutorService
	 */
	public static < M extends Model< M >> Pair< M, List< PointMatch > > alignICP(
			final STData stdataA,
			final AffineTransform2D transformA,
			final STData stdataB,
			final AffineTransform2D transformB,
			final Collection< String > genesToUse,
			final M initialModel,
			final double maxDistance,
			final double ransacDistance,
			final AtomicInteger maxIterations,
			final Double ffSingleSpot,
			final Double ffMedian,
			final Double ffGauss,
			final Double ffMean,
			final ExecutorService service )
	{
		return alignICP(stdataA, transformA, stdataB, transformB, genesToUse, initialModel, maxDistance, ransacDistance, maxIterations, ffSingleSpot, ffMedian, ffGauss, ffMean, v -> {}, m -> {}, service);
	}
	
	/**
	 * @param stdataA - data for A
	 * @param transformA - loaded transform for A
	 * @param stdataB - data for B
	 * @param transformB - loaded transform for B
	 * @param genesToUse - list of genes
	 * @param initialModel - maps B to A
	 * @param maxDistance - max search radius for corresponding point
	 * @param ransacDistance - distance for ransac, Double.NaN means no ransac
	 * @param maxIterations - max num of ICP iterations
	 * @param progressBar - set the current progress
	 * @param updateBDV - send over the current model so the preview can be updated
	 * @param service - ExecutorService
	 */
	public static < M extends Model< M >> Pair< M, List< PointMatch > > alignICP(
			final STData stdataA,
			final AffineTransform2D transformA,
			final STData stdataB,
			final AffineTransform2D transformB,
			final Collection< String > genesToUse,
			final M initialModel,
			final double maxDistance,
			final double ransacDistance,
			final AtomicInteger maxIterations,
			final Double ffSingleSpot,
			final Double ffMedian,
			final Double ffGauss,
			final Double ffMean,
			final Consumer< Double > progressBar,
			final Consumer< M > updateBDV,
			final ExecutorService service )
	{
		logger.debug( "Setting up Pointmatch identification: " );

		final PointMatchIdentification<RealPoint> pmi;
		try
		{
			pmi = new StDataRelativePointMatchIdentification<>( stdataB, transformB, stdataA, transformA, genesToUse, maxDistance, 0.25, ffSingleSpot, ffMedian, ffGauss, ffMean, service );

			/*
			new ImageJ();
			Pair<RandomAccessibleInterval<DoubleType>, RandomAccessibleInterval<DoubleType>> imgs = ((StDataRelativePointMatchIdentification<RealPoint>)pmi).renderRankImages();
			ImageJFunctions.show( imgs.getA() );
			ImageJFunctions.show( imgs.getB() );
			*/
		}
		catch (NotEnoughDataPointsException e)
		{
			logger.error(e);
			return null;
		}

		progressBar.accept( 3.0 );

		final ArrayList< RealPoint > listA = new ArrayList<>(); // reference
		final ArrayList< RealPoint > listB = new ArrayList<>(); // target

		final int n = stdataA.numDimensions();
		final double[] l = new double[ n ];

		for ( final RealLocalizable p : stdataA )
		{
			p.localize( l );
			transformA.apply(l, l);
			listA.add( new RealPoint( l ) ); // copies the location array
		}

		for ( final RealLocalizable p : stdataB )
		{
			p.localize( l );
			transformB.apply(l, l);
			listB.add( new RealPoint( l ) ); // copies the location array
		}

		final M model = initialModel.copy();

		logger.debug( "Setting up ICP" );
		final ICP< RealPoint > icp = new ICP<>( listB, listA, pmi, ransacDistance );

		progressBar.accept( 2.0 );

		int i = 0;
		double lastAvgError = 0;
		int lastNumCorresponding = 0;

		final double progressPerIteration = 98.0 / maxIterations.get();

		boolean converged = false;

		do
		{
			logger.info("Iteration: {}", i);
			try
			{
				icp.runICPIteration( model, model );
				updateBDV.accept( model );
			}
			catch ( Exception e )
			{
				//System.out.println( "Failed with e: " + e );
				logger.error("Error during ICP alignment", e);
				return null;
			}

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();

			progressBar.accept( progressPerIteration );

			logger.info("{}: {} matches found by ICP.", i, icp.getNumPointMatches());
		}
		while ( !converged && ++i < maxIterations.get() );

		if ( icp.getPointMatches() == null )
			return null;
		else
			return new ValuePair<>( model, new ArrayList<>( PointMatch.flip( icp.getPointMatches() ) ) );
	}

}
