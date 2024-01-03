/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package imglib2.icp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

import data.STData;
import data.STDataStatistics;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import mpicbg.models.PointMatch;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.converter.Converters;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import util.CompensatedSum;
import util.KDTreeUtil;

public class StDataPointMatchIdentification < P extends RealLocalizable > implements PointMatchIdentification< P >
{
	public static double sampling = 4.0;

	final STData stDataTarget;
	final STData stDataReference;
	final Collection< String > genes;

	NearestNeighborSearchOnKDTree< DoubleType > searchTarget, searchReference;

	//final HashMap< String, NearestNeighborSearchOnKDTree< DoubleType > > searchTarget, searchReference;

	double distanceThresold;

	public StDataPointMatchIdentification( final STData stDataTarget, final STData stDataReference, final Collection< String > genes, final double distanceThreshold, final ExecutorService service )
	{
		this.stDataTarget = stDataTarget;
		this.stDataReference = stDataReference;
		this.genes = genes;
		this.distanceThresold = distanceThreshold;

		//this.searchTarget = new HashMap<>();
		//this.searchReference = new HashMap<>();

		final double dist = Math.max( new STDataStatistics( stDataTarget ).getMedianDistance(), new STDataStatistics( stDataReference ).getMedianDistance() );

		IterableRealInterval< DoubleType > sumReference = null;
		IterableRealInterval< DoubleType > sumTarget = null;

		for ( final String gene : genes )
		{
			System.out.print(  " " + gene );

			// normalize per gene 0...1 and gaussian blur first
			IterableRealInterval<DoubleType> ref = stDataReference.getExprData( gene );
			IterableRealInterval<DoubleType> target = stDataTarget.getExprData( gene );

			ref = Filters.filter( ref, new GaussianFilterFactory<>( new DoubleType( 0 ), dist * 4, WeightType.BY_SUM_OF_WEIGHTS ), service );
			target = Filters.filter( target, new GaussianFilterFactory<>( new DoubleType( 0 ), dist * 4, WeightType.BY_SUM_OF_WEIGHTS ), service );

			if ( sumReference == null )
			{
				sumReference = new RealPointSampleList<>( stDataReference.numDimensions() );
				sumTarget = new RealPointSampleList<>( stDataTarget.numDimensions() );

				final RealCursor< DoubleType > cR = ref.localizingCursor();

				while ( cR.hasNext() )
				{
					final DoubleType value = cR.next();
					((RealPointSampleList<DoubleType>)sumReference).add( new RealPoint( cR ), value.copy() );
				}

				final RealCursor< DoubleType > cT = target.localizingCursor();

				while ( cT.hasNext() )
				{
					final DoubleType value = cT.next();
					((RealPointSampleList<DoubleType>)sumTarget).add( new RealPoint( cT ), value.copy() );
				}
			}
			else
			{
				final RealCursor< DoubleType > cR = ref.localizingCursor();
				final RealCursor< DoubleType > cRout = sumReference.localizingCursor();

				while ( cR.hasNext() )
					cRout.next().add( cR.next() );

				final RealCursor< DoubleType > cT = target.localizingCursor();
				final RealCursor< DoubleType > cTout = sumTarget.localizingCursor();

				while ( cT.hasNext() )
					cTout.next().add( cT.next() );
			}

			//ref = normalize( ref );
			//target = normalize( target );

			//searchReference.put( gene, new NearestNeighborSearchOnKDTree<>( new KDTree<>( ref ) ) );
			//searchTarget.put( gene, new NearestNeighborSearchOnKDTree<>( new KDTree<>( target ) ) );

			// regularly sample the reference dataset
			//searchTarget.put( gene, new NearestNeighborSearchOnKDTree<>( new KDTree<>( TransformCoordinates.sample( stDataTarget.getExprData( gene ), sampling ) ) ) );
		}

		CompensatedSum sumT = new CompensatedSum();
		for ( final DoubleType t : sumTarget )
			sumT.add( t.get() );

		sumTarget = normalize( sumTarget, 0, sumT.getSum() / (double)sumTarget.size() );
		this.searchTarget = new NearestNeighborSearchOnKDTree<>( KDTreeUtil.createParallelizableKDTreeFrom( sumTarget ) );

		CompensatedSum sumR = new CompensatedSum();
		for ( final DoubleType t : sumReference )
			sumR.add( t.get() );

		sumReference = normalize( sumReference, 0, sumR.getSum() / (double)sumReference.size() );
		this.searchReference = new NearestNeighborSearchOnKDTree<>( KDTreeUtil.createParallelizableKDTreeFrom( sumReference ) );

		System.out.println( "\navg target: " + sumT.getSum() / (double)sumTarget.size() + ", avg ref: " + sumR.getSum() / (double)sumReference.size() + ", maxDist: " + distanceThreshold );

		/*
		// visualize the sum of both
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( 0.05 );
		new ImageJ();
		final RealRandomAccessible< DoubleType > renderRRA =
				Render.render( sumTarget, new GaussianFilterFactory<>( new DoubleType( 0 ), 10, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		ImageJFunctions.show( Views.interval( RealViews.affine( renderRRA, tS ), ImgLib2Util.transformInterval( stDataTarget.getRenderInterval(), tS ) ), "target" );

		final RealRandomAccessible< DoubleType > renderRRA2 =
				Render.render( sumReference, new GaussianFilterFactory<>( new DoubleType( 0 ), 10, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		ImageJFunctions.show( Views.interval( RealViews.affine( renderRRA2, tS ), ImgLib2Util.transformInterval( stDataReference.getRenderInterval(), tS ) ), "ref" );

		SimpleMultiThreading.threadHaltUnClean();*/
	}

	public void setDistanceThreshold( final double distanceThreshold ) { this.distanceThresold = distanceThreshold; }
	public double getDistanceThreshold() { return this.distanceThresold; }

	/*
	protected double difference( final RealLocalizable target, final RealLocalizable reference )
	{
		double diff = 0;

		for ( final String gene : genes )
			diff += difference( target, reference, gene );

		return diff;
	}

	protected double difference( final RealLocalizable target, final RealLocalizable reference, final String gene )
	{
		final NearestNeighborSearchOnKDTree< DoubleType > targetSearch = searchTarget.get( gene );
		targetSearch.search( target );

		final NearestNeighborSearchOnKDTree< DoubleType > referenceSearch = searchReference.get( gene );
		referenceSearch.search( reference );

		return Math.abs( targetSearch.getSampler().get().get() - referenceSearch.getSampler().get().get() );
	}
	*/

	protected double difference( final RealLocalizable target, final RealLocalizable reference )
	{
		searchTarget.search( target );
		searchReference.search( reference );

		final double diff = Math.abs( searchTarget.getSampler().get().get() - searchReference.getSampler().get().get() );

		return diff;
	}

	@Override
	public ArrayList< PointMatch > assignPointMatches( final List< LinkedPoint< P > > target, final List< LinkedPoint< P > > reference )
	{
		//
		// we want to select the location within a certain radius that is most similar in terms of gene expression levels
		//

		final ArrayList< PointMatch > pointMatches = new ArrayList<>();

		final KDTree< LinkedPoint< P > > kdTreeTarget = new KDTree<>( target, target );
		final RadiusNeighborSearchOnKDTree< LinkedPoint< P > > nnSearchTarget = new RadiusNeighborSearchOnKDTree<>( kdTreeTarget );

		final CompensatedSum sumDiff = new CompensatedSum();
		long numMatches = 0;

		for ( final LinkedPoint< P > referencePoint : reference )
		{
			// am I the brightest dot in the local neighborhood?
			// this way we make sure it is a relative measure, map brightest point in the local neighborhood to the brightest point in the corresponding area
			
			nnSearchTarget.search( referencePoint, distanceThresold, false );

			double minDiff = Double.MAX_VALUE;
			LinkedPoint< P > bestTargetPoint = null;
			
			//double maxBrightness = -Double.MAX_VALUE;
			//LinkedPoint< P > brightestPoint = null;
			
			// find the brightest point in a certain radius (we preselected the reference point to be the brightnest in a certain region)
			for ( int i = 0; i < nnSearchTarget.numNeighbors(); ++i )
			{
				final LinkedPoint< P > targetPoint = nnSearchTarget.getSampler( i ).get();
				//wrong: transformed location - final RealLocalizable targetLocation = nnSearchTarget.getPosition( i );

				// now we need the location of the original point for the transformed target point we found, because we look up the gene expression values there
				final RealLocalizable targetLocation = targetPoint.getLinkedObject();

				final double expDiff = difference( targetLocation, referencePoint ); // TODO: shouldn't it be referencePoint.getLinkedObject() (or always identity transform?)

				if ( expDiff < minDiff )
				{
					minDiff = expDiff;
					bestTargetPoint = targetPoint;
				}

				/*
				final double sum = ICPAlign.computeSum( targetLocation, searchTarget );
				if ( sum > maxBrightness )
				{
					maxBrightness = sum;
					brightestPoint = targetPoint;
				}
				*/
			}

			if ( bestTargetPoint != null )
			{
				pointMatches.add( new PointMatch( bestTargetPoint, referencePoint ) );
				sumDiff.add( minDiff );
				++numMatches;
			}

			/*
			if ( brightestPoint != null && maxBrightness > 0 )
				pointMatches.add( new PointMatch( brightestPoint, referencePoint ) );
			*/
		}

		System.out.println("Assigned " + numMatches + " with avg error (expression value) = " + (sumDiff.getSum() / numMatches ) );

		return pointMatches;
	}

	public static < T extends RealType< T > > IterableRealInterval< T > normalize( final IterableRealInterval<T> ref )
	{
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( final T v : ref )
		{
			min = Math.min( min, v.getRealDouble() );
			max = Math.max( max, v.getRealDouble() );
		}

		return normalize( ref, min, max );
	}

	public static < T extends RealType< T > > IterableRealInterval< T > normalize( final IterableRealInterval<T> ref, final double min, final double max )
	{
		final double diff = max-min;
		return Converters.convert( ref, (i,o) -> o.setReal( (i.getRealDouble() - min)/(diff) ), ref.firstElement() );
	}

}
