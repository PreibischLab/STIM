package data;

import java.util.HashMap;
import java.util.List;

import importer.Parser;
import net.imglib2.EuclideanSpace;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.util.Util;
import util.ImgLib2Util;
import util.ImgLib2Util.SimpleStats;

public class STData extends STDataMinimal implements EuclideanSpace
{
	public SimpleStats distanceStats;
	public RealInterval interval;
	public Interval renderInterval;

	public STData( final List< double[] > coordinates, final HashMap< String, double[] > genes )
	{
		super( coordinates, genes );

		computeStatistics();
		computeIntervals();
	}

	public STData( final STDataMinimal stdata )
	{
		this( stdata.coordinates, stdata.genes );
	}

	public void expandInterval( final double border )
	{
		expandInterval( Util.getArrayFromValue( border, numDimensions() ) );
	}

	public void expandInterval( final double[] border )
	{
		final double min[] = new double[ numDimensions() ];
		final double max[] = new double[ numDimensions() ];

		interval.realMin( min );
		interval.realMax( max );

		for ( int d = 0; d < numDimensions(); ++d )
		{
			min[ d ] -= border[ d ];
			max[ d ] += border[ d ];
		}

		this.interval = new FinalRealInterval( min, max );
		this.renderInterval = ImgLib2Util.roundRealInterval( this.interval );
	}

	public void computeStatistics()
	{
		this.distanceStats = ImgLib2Util.distanceStats( this.coordinates );
	}

	public void computeIntervals()
	{
		this.interval = Parser.getInterval( this.coordinates );
		this.renderInterval = ImgLib2Util.roundRealInterval( this.interval );
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	public void printInfo()
	{
		System.out.println( "Median Distance: " + this.distanceStats.median );
		System.out.println( "Average Distance: " + this.distanceStats.avg );
		System.out.println( "Min Distance: " + this.distanceStats.min );
		System.out.println( "Max Distance: " + this.distanceStats.max );
		System.out.println( "Interval: " + ImgLib2Util.printRealInterval( this.interval ) );
		System.out.println( "RenderInterval: " + ImgLib2Util.printRealInterval( this.renderInterval ) );
	}

	public static STData createTestDataSet()
	{
		return new STData( STDataMinimal.createTestDataSet() );
	}
}
