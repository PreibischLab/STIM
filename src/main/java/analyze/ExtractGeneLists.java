package analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import data.STData;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.ValuePair;

public class ExtractGeneLists
{
	public static ArrayList< Pair< String, Double > > sortByStDevIntensity( final STData data )
	{
		final ArrayList< Pair< String, Double > > stDev = new ArrayList<>();

		for ( final String gene : data.getGeneNames() )
		{
			final RealSum sum = new RealSum();
			for ( final DoubleType t : data.getExprData( gene ) )
				sum.add( t.get() );

			final double avg = sum.getSum() / data.numLocations();

			final RealSum stdev = new RealSum();
			for ( final DoubleType t : data.getExprData( gene ) )
				stdev.add( Math.pow( t.get() - avg , 2));

			stDev.add( new ValuePair<>( gene, Math.sqrt( stdev.getSum() / data.numLocations() ) ) );
		}

		Collections.sort( stDev, new Comparator< Pair< String, Double > >()
		{
			@Override
			public int compare( Pair< String, Double > o1, Pair< String, Double > o2 )
			{
				return o2.getB().compareTo( o1.getB() );
			}
		} );

		return stDev;
	}

	public static ArrayList< Pair< String, Double > > sortByAvgIntensity( final STData data )
	{
		final ArrayList< Pair< String, Double > > avgs = new ArrayList<>();

		for ( final String gene : data.getGeneNames() )
		{
			final RealSum sum = new RealSum();
			for ( final DoubleType t : data.getExprData( gene ) )
				sum.add( t.get() );

			avgs.add( new ValuePair<>( gene, sum.getSum() / data.numLocations() ) );
		}

		Collections.sort( avgs, new Comparator< Pair< String, Double > >()
		{
			@Override
			public int compare( Pair< String, Double > o1, Pair< String, Double > o2 )
			{
				return o2.getB().compareTo( o1.getB() );
			}
		} );

		return avgs;
	}
}
