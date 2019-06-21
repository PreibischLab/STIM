package data;

import data.STDataUtils.DistanceStats;

public class STDataStatistics
{
	public DistanceStats ds;

	public STDataStatistics( final STData data )
	{
		this.ds = STDataUtils.distanceStats( data.getLocationKDTree() );
	}

	public double getMeanDistance() { return ds.avgDist; }
	public double getMedianDistance() { return ds.medianDist; }
	public double getMinDistance() { return ds.minDist; }
	public double getMaxDistance() { return ds.maxDist; }

	@Override
	public String toString()
	{
		return "Median Distance: " + getMeanDistance() + "\n" +
			"Mean Distance: " + getMeanDistance() + "\n" +
			"Min Distance: " + getMinDistance() + "\n" +
			"Max Distance: " + getMaxDistance();
	}
}
