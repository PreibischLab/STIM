package io;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import data.STData;
import data.STDataText;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class TextFileIO
{
	public static STData readSlideSeq( final File beadLocations, final File reads )
	{
		System.out.println( "Parsing " + beadLocations.getName() + ", " + reads.getName() );

		long time = System.currentTimeMillis();

		final HashMap< String, double[] > coordinateMap = readSlideSeqCoordinates( beadLocations );
		System.out.println( "Read " + coordinateMap.keySet().size() + " coordinates." );

		final Pair< List< double[] >, HashMap< String, double[] > > geneData = readSlideSeqGenes( reads, coordinateMap );
		System.out.println( "Read data for " + geneData.getB().keySet().size() + " genes." );

		final STData data = new STDataText( geneData.getA(), geneData.getB() );
		System.out.println( data );

		System.out.println( "Parsing took " + ( System.currentTimeMillis() - time ) + " ms." );

		return data;
	}

	public static STData read( final File locations, final File genes, final File geneNames )
	{
		System.out.println( "Parsing " + locations.getName() + ", " + genes.getName() + ", " + geneNames.getName() );
		long time = System.currentTimeMillis();

		final ArrayList< double[] > coordinates = readCoordinates( locations );
		System.out.println( "Read " + coordinates.size() + " coordinates." );

		final ArrayList< String > geneNameList = readGeneNames( geneNames );
		System.out.println( "Read " + geneNameList.size() + " gene names." );

		//for ( final String gene : data.genes.keySet() )
		//	System.out.println( gene );

		final HashMap< String, double[] > geneMap = TextFileIO.readGenes( genes, geneNameList, coordinates.size() );
		System.out.println( "Read " + geneMap.keySet().size() + " genes with " + coordinates.size() + " locations each." );

		final STData data = new STDataText( coordinates, geneMap );

		System.out.println( data );

		System.out.println( "Parsing took " + ( System.currentTimeMillis() - time ) + " ms." );

		return data;
	}

	public static STData read( final File locations, final File genes )
	{
		System.out.println( "Parsing " + locations.getName() + ", " + genes.getName() );
		long time = System.currentTimeMillis();

		final ArrayList< double[] > coordinates = TextFileIO.readCoordinates( locations );
		System.out.println( "Read " + coordinates.size() + " coordinates." );

		final HashMap< String, double[] > geneMap = TextFileIO.readGenes( genes, coordinates.size() );
		System.out.println( "Read " + geneMap.keySet().size() + " genes with " + coordinates.size() + " locations each." );

		//for ( final String gene : data.genes.keySet() )
		//	System.out.println( gene );

		final STData data = new STDataText( coordinates, geneMap );

		System.out.println( data );

		System.out.println( "Parsing took " + ( System.currentTimeMillis() - time ) + " ms." );

		return data;
	}

	public static HashMap< String, double[] > readGenes( final File file, final List< String > geneNameList, final int numCoordinates )
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
					final double v = Double.parseDouble( valueList[ i ] );
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

	public static Pair< List< double[] >, HashMap< String, double[] > > readSlideSeqGenes(
			final File file,
			final HashMap< String, double[] > coordinateMap )
	{
		final ArrayList< double[] > coordinates = new ArrayList<>();
		final HashMap< String, double[] > geneMap = new HashMap<>();

		final BufferedReader in = TextFileAccess.openFileRead( file );

		try
		{
			int line = 0;

			while ( in.ready() )
			{
				final String[] values = in.readLine().trim().split( "," );

				if ( values.length - 1 != coordinateMap.keySet().size() )
					throw new RuntimeException( "length of header inconsistent with number of coordinates: " + (values.length - 1) + " != " + coordinateMap.keySet().size() );

				if ( line++ == 0 ) 
				{
					// header: Row,GACGCAAGAAACA,TTGGGAGAAAACT,GGTCTCAGAAACG, ...
					for ( int i = 1; i < values.length; ++i )
					{
						final double[] coordinate = coordinateMap.get( values[ i ] );
						if ( coordinate == null )
							throw new RuntimeException( "barcode " + values[ i ] + " not present in file " + file.getName() );

						coordinates.add( coordinate );
					}
				}
				else
				{
					final String geneName = values[ 0 ];
					final double[] exprValues = new double[ values.length - 1 ];

					for ( int i = 1; i < values.length; ++i )
						exprValues[ i - 1 ] = Double.parseDouble( values[ i ] );

					geneMap.put( geneName, exprValues );
				}
			}

			in.close();
		}
		catch (Exception e )
		{
			e.printStackTrace();
			return null;
		}

		return new ValuePair<>( coordinates, geneMap );
	}

	public static HashMap< String, double[] > readSlideSeqCoordinates( final File file )
	{
		final HashMap< String, double[] > coordinates = new HashMap<>();

		final BufferedReader in = TextFileAccess.openFileRead( file );

		try
		{
			int line = 0;
			int dim = -1;

			while ( in.ready() )
			{
				final String s = in.readLine().trim();
				
				if ( line++ == 0 ) // header: barcodes,xcoord,ycoord
					continue;

				final String[] values = s.split( "," );

				final String barcode = values[ 0 ];

				if ( dim == -1 )
					dim = values.length - 1;
				else if ( dim != values.length - 1 )
					throw new RuntimeException( "inconsistent dimensionality: " + s );

				final double[] coordinate = new double[ dim ];
				for ( int d = 0; d < coordinate.length; ++d )
					coordinate[ d ] = Double.parseDouble( values[ d + 1 ] );

				coordinates.put( barcode, coordinate );
			}

			in.close();
		}
		catch ( Exception e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return coordinates;
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
