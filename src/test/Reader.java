package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import mpicbg.spim.io.TextFileAccess;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;

public class Reader
{
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

	public static HashMap< String, double[] > readGenes( final File file, final int numCoordinates )
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
					v[ i ] = Double.parseDouble( loc[ i + 1 ] );

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
