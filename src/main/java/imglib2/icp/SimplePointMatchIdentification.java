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
import java.util.List;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;

public class SimplePointMatchIdentification < P extends RealLocalizable > implements PointMatchIdentification< P >
{
	double distanceThreshold;

	public SimplePointMatchIdentification( final double distanceThreshold )
	{
		this.distanceThreshold = distanceThreshold;
	}

	public SimplePointMatchIdentification()
	{
		this.distanceThreshold = Double.MAX_VALUE;
	}

	public void setDistanceThreshold( final double distanceThreshold ) { this.distanceThreshold = distanceThreshold; }
	public double getDistanceThreshold() { return this.distanceThreshold; }

	@Override
	public ArrayList< PointMatch > assignPointMatches( final List< LinkedPoint< P > > target, final List< LinkedPoint< P > > reference )
	{
		final ArrayList< PointMatch > pointMatches = new ArrayList<>();

		final KDTree< LinkedPoint< P > > kdTreeTarget = new KDTree<>( target, target );
		final NearestNeighborSearchOnKDTree< LinkedPoint< P > > nnSearchTarget = new NearestNeighborSearchOnKDTree<>( kdTreeTarget );

		for ( final LinkedPoint< P > point : reference )
		{
			nnSearchTarget.search( point );
			final LinkedPoint< P > correspondingPoint = nnSearchTarget.getSampler().get();

			// world coordinates of point
			if ( Point.distance( correspondingPoint, point ) <= distanceThreshold)
				pointMatches.add( new PointMatch( correspondingPoint, point ) );
		}

		return pointMatches;
	}
}
