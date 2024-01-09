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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import data.STData;
import data.STDataStatistics;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;
import util.CompensatedSum;
import util.KDTreeUtil;

public class StDataRelativePointMatchIdentification < P extends RealLocalizable > implements PointMatchIdentification< P >
{
	public static double sampling = 4.0;

	final STData stDataTarget;
	final STData stDataReference;
	final Collection< String > genes;

	final NearestNeighborSearchOnKDTree< DoubleType > nnSearchTarget, nnSearchReference;
	final RealPointSampleList< DoubleType > rankRef, rankTarget;
	final double rankThreshold;

	double distanceThresold;

	public StDataRelativePointMatchIdentification(
			final STData stDataTarget,
			final STData stDataReference,
			final Collection< String > genes,
			final double distanceThreshold,
			final double rankThreshold,
			final Double ffSingleSpot,
			final Double ffMedian,
			final Double ffGauss,
			final Double ffMean,
			final ExecutorService service ) throws NotEnoughDataPointsException
	{
		this.stDataTarget = stDataTarget;
		this.stDataReference = stDataReference;
		this.genes = genes;
		this.distanceThresold = distanceThreshold;
		this.rankThreshold = rankThreshold;

		RealPointSampleList< DoubleType > sumReference = null;
		RealPointSampleList< DoubleType > sumTarget = null;

		// first sum intensity over all genes, with filters applied
		for ( final String gene : genes )
		{
			System.out.print(  " " + gene );

			IterableRealInterval<DoubleType> ref = stDataReference.getExprData( gene );
			IterableRealInterval<DoubleType> target = stDataTarget.getExprData( gene );

			if ( ffSingleSpot != null )
			{
				ref = Filters.filter( ref, new SingleSpotRemovingFilterFactory<>( new DoubleType( 0 ), ffSingleSpot ), service );
				target = Filters.filter( target, new SingleSpotRemovingFilterFactory<>( new DoubleType( 0 ), ffSingleSpot ), service );
			}

			if ( ffMedian != null )
			{
				ref = Filters.filter( ref, new MedianFilterFactory<>( new DoubleType( 0 ), ffMedian ), service );
				target = Filters.filter( target, new MedianFilterFactory<>( new DoubleType( 0 ), ffMedian ), service );
			}

			if ( ffGauss != null )
			{
				ref = Filters.filter( ref, new GaussianFilterFactory<>( new DoubleType( 0 ), ffGauss, WeightType.BY_SUM_OF_WEIGHTS ), service );
				target = Filters.filter( target, new GaussianFilterFactory<>( new DoubleType( 0 ), ffGauss, WeightType.BY_SUM_OF_WEIGHTS ), service );
			}

			if ( ffMean != null )
			{
				ref = Filters.filter( ref, new MeanFilterFactory<>( new DoubleType( 0 ), ffMean ), service );
				target = Filters.filter( target, new MeanFilterFactory<>( new DoubleType( 0 ), ffMean ), service );
			}

			if ( sumReference == null )
			{
				sumReference = new RealPointSampleList<>( stDataReference.numDimensions() );
				sumTarget = new RealPointSampleList<>( stDataTarget.numDimensions() );

				final RealCursor< DoubleType > cR = ref.localizingCursor();

				while ( cR.hasNext() )
				{
					final DoubleType value = cR.next();
					sumReference.add( new RealPoint( cR ), value.copy() );
				}

				final RealCursor< DoubleType > cT = target.localizingCursor();

				while ( cT.hasNext() )
				{
					final DoubleType value = cT.next();
					sumTarget.add( new RealPoint( cT ), value.copy() );
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
		}

		System.out.println();

		// compute the rank of each spot in it's local neigborhood defined by the distance threshold
		this.rankRef = buildRank( sumReference );
		this.rankTarget = buildRank( sumTarget );

		if ( rankRef.size() < 5 || rankTarget.size() < 5 )
		{
			System.out.println( rankRef.size() );
			System.out.println( rankTarget.size() );
			throw new NotEnoughDataPointsException("not enough remaining points, please increase ICP error.");
		}

		this.nnSearchReference = new NearestNeighborSearchOnKDTree<>( KDTreeUtil.createParallelizableKDTreeFrom( rankRef ) );
		this.nnSearchTarget = new NearestNeighborSearchOnKDTree<>( KDTreeUtil.createParallelizableKDTreeFrom( rankTarget ) );
	}

	public Pair<RandomAccessibleInterval<DoubleType>, RandomAccessibleInterval<DoubleType>> renderRankImages()
	{
		// visualize the sum of both
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( 0.15 );
		new ImageJ();
		final RealRandomAccessible< DoubleType > renderRRA =
				Render.render( rankRef, new GaussianFilterFactory<>( new DoubleType( 0 ), new STDataStatistics(stDataReference).getMedianDistance() * 0.5, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		final RandomAccessibleInterval<DoubleType> imgA = Views.interval( RealViews.affine( renderRRA, tS ), ImgLib2Util.transformInterval( stDataTarget.getRenderInterval(), tS ) );

		final RealRandomAccessible< DoubleType > renderRRA2 =
				Render.render( rankTarget, new GaussianFilterFactory<>( new DoubleType( 0 ), new STDataStatistics(stDataReference).getMedianDistance() * 0.5, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		final RandomAccessibleInterval<DoubleType> imgB = Views.interval( RealViews.affine( renderRRA2, tS ), ImgLib2Util.transformInterval( stDataReference.getRenderInterval(), tS ) );

		return new ValuePair<>(imgA, imgB);
	}

	public void setDistanceThreshold( final double distanceThreshold ) { this.distanceThresold = distanceThreshold; }
	public double getDistanceThreshold() { return this.distanceThresold; }

	/*
	 * target is transformed by last model, reference is not transformed
	 */
	@Override
	public ArrayList< PointMatch > assignPointMatches( final List< LinkedPoint< P > > target, final List< LinkedPoint< P > > reference )
	{
		//
		// we want to select the location within a certain radius that is most similar in terms of gene expression levels
		//
		final ArrayList< PointMatch > pointMatches = new ArrayList<>();

		// we need to build a new tree because target is transformed
		final RadiusNeighborSearchOnKDTree< LinkedPoint< P > > radiusSearchTransformedTarget = new RadiusNeighborSearchOnKDTree<>( new KDTree<>( target, target ) );

		final CompensatedSum sumDiff = new CompensatedSum();

		// for each reference point do ...
		reference.forEach( referencePoint ->
		{
			// of all points in the search area, find the one with the most similar relative rank
			// TODO: we need a threshold for rank difference I think
			radiusSearchTransformedTarget.search( referencePoint, distanceThresold, false );

			double minDiff = Double.MAX_VALUE;
			LinkedPoint< P > bestTargetPoint = null;

			// find the brightest point in a certain radius (we preselected the reference point to be the brightnest in a certain region)
			for ( int i = 0; i < radiusSearchTransformedTarget.numNeighbors(); ++i )
			{
				// get the linked point 
				final LinkedPoint< P > targetPoint = radiusSearchTransformedTarget.getSampler( i ).get();

				// now we need the location of the original point for the transformed target point we found, because we look up the rank values there
				final RealLocalizable orginialTargetLocation = targetPoint.getLinkedObject();
				final RealLocalizable orginialReferenceLocation = referencePoint.getLinkedObject();

				// now we need the actual rank values for each point (if they exist, see buildRank() method)
				nnSearchTarget.search( orginialTargetLocation );
				nnSearchReference.search( orginialReferenceLocation );

				final double rankDiff = Math.abs( nnSearchTarget.getSampler().get().get() - nnSearchReference.getSampler().get().get() );

				if ( rankDiff > 1 )
				{
					System.out.println( rankDiff );
					System.exit( 0 );
				}

				if ( rankDiff < minDiff )
				{
					minDiff = rankDiff;
					bestTargetPoint = targetPoint;
				}
			}

			if ( bestTargetPoint != null && minDiff < rankThreshold )
			{
				pointMatches.add( new PointMatch( bestTargetPoint, referencePoint ) );
				sumDiff.add( minDiff );
			}
		});

		final long numMatches = pointMatches.size();
		System.out.println("Assigned " + numMatches + " with avg error (relative rank) = " + (sumDiff.getSum() / numMatches ) );

		return pointMatches;
	}

	public RealPointSampleList< DoubleType > buildRank( final IterableRealInterval< DoubleType > sumImg )
	{
		final RealPointSampleList< DoubleType > rankImg = new RealPointSampleList<>( sumImg.numDimensions() );
		final RadiusNeighborSearchOnKDTree< DoubleType > radiusSearch = new RadiusNeighborSearchOnKDTree<>( new KDTree<>( sumImg ) );

		final RealCursor< DoubleType > c = sumImg.localizingCursor();
		final ArrayList< Double > values = new ArrayList<>();

		while ( c.hasNext() )
		{
			final DoubleType value = c.next();

			if ( value.get() > 0 ) // we ignore 0-values
			{
				radiusSearch.search( c, distanceThresold, false );

				double v;
				values.clear();

				// the value of cR.next() will be part of the list
				for ( int i = 0; i < radiusSearch.numNeighbors(); ++i )
					if ( ( v = radiusSearch.getSampler( i ).get().get() ) > 0 )
						values.add( v );

				if ( values.size() >= 3 ) // we want at least 3 points (including the one queried)
				{
					Collections.sort( values );

					int start = -1;
					int end = -1;

					for ( int i = 0; i < values.size(); ++i )
					{
						final double d = values.get( i );

						if ( d == value.get() )
						{
							if ( start == -1 )
								start = end = i;
							else 
								end = i;
						}
					}

					final double rank = (end-start)/2.0 + start;
					final double relRank = rank / ( values.size() - 1);
					rankImg.add( new RealPoint( c ), new DoubleType( relRank ) );
				}
			}
		}

		return rankImg;
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
		return Converters.convert( ref, (i,o) -> o.setReal( Math.max(0,(i.getRealDouble() - min))/(diff) ), ref.firstElement() );
	}

}
