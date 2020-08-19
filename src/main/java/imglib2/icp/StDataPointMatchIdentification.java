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
import java.util.HashMap;
import java.util.List;

import data.STData;
import mpicbg.models.PointMatch;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.RealSum;
import transform.TransformCoordinates;

public class StDataPointMatchIdentification < P extends RealLocalizable > implements PointMatchIdentification< P >
{
	public static double sampling = 4.0;

	final STData stDataTarget;
	final STData stDataReference;
	final Collection< String > genes;

	final HashMap< String, NearestNeighborSearchOnKDTree< DoubleType > > searchTarget, searchReference;

	double distanceThresold;

	public StDataPointMatchIdentification( final STData stDataTarget, final STData stDataReference, final Collection< String > genes, final double distanceThreshold )
	{
		this.stDataTarget = stDataTarget;
		this.stDataReference = stDataReference;
		this.genes = genes;
		this.distanceThresold = distanceThreshold;

		this.searchTarget = new HashMap<>();
		this.searchReference = new HashMap<>();

		for ( final String gene : genes )
		{
			searchReference.put( gene, new NearestNeighborSearchOnKDTree<>( stDataReference.getExpValueKDTree( gene ) ) );

			searchTarget.put( gene, new NearestNeighborSearchOnKDTree<>( stDataTarget.getExpValueKDTree( gene ) ) );
			// regularly sample the reference dataset
			//searchTarget.put( gene, new NearestNeighborSearchOnKDTree<>( new KDTree<>( TransformCoordinates.sample( stDataTarget.getExprData( gene ), sampling ) ) ) );
		}
	}

	public void setDistanceThreshold( final double distanceThreshold ) { this.distanceThresold = distanceThreshold; }
	public double getDistanceThreshold() { return this.distanceThresold; }

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

	@Override
	public ArrayList< PointMatch > assignPointMatches( final List< LinkedPoint< P > > target, final List< LinkedPoint< P > > reference )
	{
		//
		// we want to select the location within a certain radius that is most similar in terms of gene expression levels
		//

		final ArrayList< PointMatch > pointMatches = new ArrayList<>();

		final KDTree< LinkedPoint< P > > kdTreeTarget = new KDTree<>( target, target );
		final RadiusNeighborSearchOnKDTree< LinkedPoint< P > > nnSearchTarget = new RadiusNeighborSearchOnKDTree<>( kdTreeTarget );

		final RealSum sumDiff = new RealSum();
		long numMatches = 0;

		for ( final LinkedPoint< P > referencePoint : reference )
		{
			nnSearchTarget.search( referencePoint, distanceThresold, false );

			double minDiff = Double.MAX_VALUE;
			LinkedPoint< P > bestTargetPoint = null;
			
			for ( int i = 0; i < nnSearchTarget.numNeighbors(); ++i )
			{
				final RealLocalizable targetLocation = nnSearchTarget.getPosition( i );
				final LinkedPoint< P > targetPoint = nnSearchTarget.getSampler( i ).get();

				final double expDiff = difference( targetLocation, referencePoint );

				if ( expDiff < minDiff )
				{
					minDiff = expDiff;
					bestTargetPoint = targetPoint;
				}
			}

			if ( bestTargetPoint != null )
			{
				pointMatches.add( new PointMatch( bestTargetPoint, referencePoint ) );
				sumDiff.add( minDiff );
				++numMatches;
			}
		}

		//System.out.println("Assigned " + numMatches + " with avg error = " + (sumDiff.getSum() / numMatches ) );

		return pointMatches;
	}
}
