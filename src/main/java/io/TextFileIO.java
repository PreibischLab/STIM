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

		final BufferedReader beadLocationsIn = TextFileAccess.openFileRead( beadLocations );
		final BufferedReader readsIn = TextFileAccess.openFileRead( reads );

		final STData data = readSlideSeq( beadLocationsIn, readsIn );

		try {
			beadLocationsIn.close();
			readsIn.close();
		} catch (IOException e) {}

		System.out.println( data );

		return data;
	}

	public static STData readSlideSeq( final BufferedReader beadLocations, final BufferedReader reads )
	{
		long time = System.currentTimeMillis();

		final HashMap< String, double[] > coordinateMap = readSlideSeqCoordinates( beadLocations );
		System.out.println( "Read " + coordinateMap.keySet().size() + " coordinates." );

		final Pair< List< Pair< double[], String > >, HashMap< String, double[] > > geneData = readSlideSeqGenes( reads, coordinateMap );
		System.out.println( "Read data for " + geneData.getB().keySet().size() + " genes." );

		final STData data = new STDataText( geneData.getA(), geneData.getB() );
		//System.out.println( data );

		System.out.println( "Parsing took " + ( System.currentTimeMillis() - time ) + " ms." );

		return data;
	}

	public static Pair< List< Pair< double[], String > >, HashMap< String, double[] > > readSlideSeqGenes(
			final BufferedReader in,
			final HashMap< String, double[] > coordinateMap )
	{
		final ArrayList< Pair< double[], String > > coordinates = new ArrayList<>();
		final HashMap< String, double[] > geneMap = new HashMap<>();

		//final BufferedReader in = TextFileAccess.openFileRead( file );

		try
		{
			int line = 0;

			while ( in.ready() )
			{
				final String[] values = in.readLine().trim().split( "," );

				if ( values.length - 1 != coordinateMap.keySet().size() )
					throw new RuntimeException( "length of header inconsistent with number of locations: " + (values.length - 1) + " != " + coordinateMap.keySet().size() + "\n" +
							"You defined " + coordinateMap.keySet().size() + " locations (ids), but the reads file only contains data for " + (values.length - 1) + " locations.");

				if ( line++ == 0 ) 
				{
					// header: Row,GACGCAAGAAACA,TTGGGAGAAAACT,GGTCTCAGAAACG, ...
					for ( int i = 1; i < values.length; ++i )
					{
						final double[] coordinate = coordinateMap.get( values[ i ] );
						if ( coordinate == null )
							throw new RuntimeException( "barcode " + values[ i ] + " not present in file." );

						coordinates.add( new ValuePair<>( coordinate, values[ i ] ) );
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

	public static HashMap< String, double[] > readSlideSeqCoordinates( final BufferedReader in )
	{
		final HashMap< String, double[] > coordinates = new HashMap<>();

		//final BufferedReader in = TextFileAccess.openFileRead( file );

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
}
