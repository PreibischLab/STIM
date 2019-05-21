package importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import util.ImgLib2Util;

public class Reader
{
	public static STData read( final File locations, final File genes, final File geneNames, final double offset )
	{
		final STData data = new STData();

		data.coordinates = readCoordinates( locations );

		System.out.println( "Loaded " + data.coordinates.size() + " coordinates." );

		data.distanceStats = ImgLib2Util.distanceStats( data.coordinates );

		System.out.println( "Median Distance: " + data.distanceStats.median );
		System.out.println( "Average Distance: " + data.distanceStats.avg );
		System.out.println( "Min Distance: " + data.distanceStats.min );
		System.out.println( "Max Distance: " + data.distanceStats.max );

		final ArrayList< String > geneNameList = readGeneNames( geneNames );

		System.out.println( "Read " + geneNameList.size() + " gene names." );

		//for ( final String gene : data.genes.keySet() )
		//	System.out.println( gene );

		data.genes = Reader.readGenes( genes, geneNameList, data.coordinates.size(), 0 );

		System.out.println( "Loaded: " + data.genes.keySet().size() + " genes with " + data.coordinates.size() + " locations each." );

		data.interval = Reader.getInterval( data.coordinates );
		data.renderInterval = ImgLib2Util.roundRealInterval( data.interval );

		System.out.println( "Interval: " + ImgLib2Util.printRealInterval( data.interval ) );
		System.out.println( "RenderInterval: " + ImgLib2Util.printRealInterval( data.renderInterval ) );

		return data;
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

		System.out.println( "Loaded: " + data.genes.keySet().size() + " genes with " + data.coordinates.size() + " locations each." );

		for ( final String gene : data.genes.keySet() )
			System.out.println( gene );

		data.interval = Reader.getInterval( data.coordinates );
		data.renderInterval = ImgLib2Util.roundRealInterval( data.interval );

		System.out.println( "Interval: " + ImgLib2Util.printRealInterval( data.interval ) );
		System.out.println( "RenderInterval: " + ImgLib2Util.printRealInterval( data.renderInterval ) );

		return data;
	}

	public static HashMap< String, double[] > readGenes( final File file, final List< String > geneNameList, final int numCoordinates, final double offset )
	{
		final BufferedReader in = TextFileAccess.openFileRead( file );

		final HashMap< String, double[] > values = new HashMap<>();

		try
		{
			int location = -1;

			while ( in.ready() )
			{
				++location;
				final String[] valueList = in.readLine().trim().split( " " );

				if ( valueList.length != numCoordinates && valueList.length * 2 != numCoordinates )
				{
					System.out.println( (valueList.length) + " != numCoordinates:" + numCoordinates );
					return null;
				}

				final double[] vs = new double[ numCoordinates ];
				final boolean isDrosophilaHack = valueList.length * 2 == numCoordinates; // Drosophila symmetry hack

				for ( int i = 0; i < valueList.length; ++i )
				{
					final double v = Double.parseDouble( valueList[ i ] )*100000 + offset;
					vs[ i ] = v;

					if ( isDrosophilaHack ) 
						vs[ i + valueList.length ] = v;
				}

				values.put( geneNameList.get( location ), vs );
			}
		} 
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

		return values;
	}

	public static ArrayList< String > readGeneNames( final File geneNames )
	{
		final BufferedReader in = TextFileAccess.openFileRead( geneNames );

		final ArrayList< String > names = new ArrayList<>();

		try
		{
			while ( in.ready() )
			{
				final String name = in.readLine().trim();

				if ( name.length() > 0 )
					names.add( name );
			}
		}
		catch ( IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return names;
	}

	public static ArrayList< double[] > readCoordinates( final File file )
	{
		final ArrayList< double[] > coordinates = new ArrayList<>();

		final BufferedReader in = TextFileAccess.openFileRead( file );

		int n = -1;
		int line = 0;
		String separator = "";

		try
		{
A:			while ( in.ready() )
			{
				final String[] loc;

				++line;

				if ( n == -1 )
				{
					final String s = in.readLine().trim();

					final int comma = s.split( "," ).length;
					final int space = s.split( " " ).length;

					if ( comma >= 2 && comma <= 3 )
					{
						separator = ",";
						n = comma;
					}
					else if ( space >= 2 && space <= 3 )
					{
						separator = " ";
						n = space;
					}
					else
					{
						System.out.println( "Cannot parse file: " + file.getAbsolutePath() );
						return null;
					}

					System.out.println( "Separator: '" + separator + "', n=" + n );

					loc = s.split( separator );
				}
				else
				{
					loc = in.readLine().trim().split( separator );
				}

				final double[] pos = new double[ n ];

				for ( int d = 0; d < n; ++d )
				{
					try
					{
						pos[ d ] = Double.parseDouble( loc[ d ] );
					}
					catch ( NumberFormatException e )
					{
						System.out.println( "Error parsing line: " + line + ": " + e );
						continue A;
					}
				}

				coordinates.add( pos );
			}
		} 
		catch ( IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
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