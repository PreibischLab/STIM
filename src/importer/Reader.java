package importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import mpicbg.spim.io.TextFileAccess;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import util.ImgLib2Util;
import util.ImgLib2Util.SimpleStats;

public class Reader
{
	public static class STData
	{
		public ArrayList< double[] > coordinates;
		public HashMap< String, double[] > genes;
		public SimpleStats distanceStats;
		public RealInterval interval;
		public Interval renderInterval;
	}

	public static STData read( final File locations, final File genes, final double offset )
	{
		final STData data = new STData();

		data.coordinates = Reader.readCoordinates( locations );

		System.out.println( "Loaded " + data.coordinates.size() + " coordinates." );

		data.distanceStats = ImgLib2Util.distanceStats( data.coordinates );

		System.out.println( "Median Distance: " + data.distanceStats.median );
		System.out.println( "Average Distance: " + data.distanceStats.avg );
		System.out.println( "Min Distance: " + data.distanceStats.min );
		System.out.println( "Max Distance: " + data.distanceStats.max );

		data.genes = Reader.readGenes( genes, data.coordinates.size(), offset );

		System.out.println( "Loaded: " + data.genes.keySet().size() + " genes with " + data.coordinates.size() + " values each." );

		for ( final String gene : data.genes.keySet() )
			System.out.println( gene );

		data.interval = Reader.getInterval( data.coordinates );
		data.renderInterval = ImgLib2Util.roundRealInterval( data.interval );

		System.out.println( "Interval: " + ImgLib2Util.printRealInterval( data.interval ) );
		System.out.println( "RenderInterval: " + ImgLib2Util.printRealInterval( data.renderInterval ) );

		return data;
	}

	public static ArrayList< double[] > readCoordinates( final File file )
	{
		final ArrayList< double[] > coordinates = new ArrayList<>();

		final BufferedReader in = TextFileAccess.openFileRead( file );

		try
		{
			while ( in.ready() )
			{
				final String[] loc = in.readLine().trim().split( "," );
				coordinates.add( new double[] { Double.parseDouble( loc[ 0 ] ), Double.parseDouble( loc[ 1 ] ) } );
			}
		} 
		catch ( IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return coordinates;
	}

	public static RealInterval getInterval( final ArrayList< double[] > coord )
	{
		final double[] min = coord.get( 0 ).clone();
		final double[] max = coord.get( 0 ).clone();

		final int n = min.length;

		for ( final double[] c : coord )
		{
			for ( int d = 0; d < n; ++d )
			{
				min[ d ] = Math.min( c[d], min[ d ] );
				max[ d ] = Math.max( c[d], max[ d ] );
			}
		}

		return new FinalRealInterval( min, max );
	}

	public static HashMap< String, double[] > readGenes( final File file, final int numCoordinates, final double offset )
	{
		final BufferedReader in = TextFileAccess.openFileRead( file );

		final HashMap< String, double[] > values = new HashMap<>();

		try
		{
			while ( in.ready() )
			{
				final String[] loc = in.readLine().trim().split( "," );

				if ( loc.length - 1 != numCoordinates )
				{
					System.out.println( (loc.length - 1) + " != numCoordinates:" + numCoordinates );
					return null;
				}

				final String geneName = loc[ 0 ].trim();

				final double[] v = new double[ numCoordinates ];

				for ( int i = 0; i < numCoordinates; ++i )
					v[ i ] = Double.parseDouble( loc[ i + 1 ] ) + offset;

				values.put( geneName, v );
			}
		} 
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

		return values;
	}
}
