package io;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import data.STData;
import data.STDataText;
import net.imglib2.img.array.ArrayImgs;
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

	public static STData readSlideSeq( final BufferedReader beadLocations, final BufferedReader reads, final BufferedReader cellTypes ) throws IOException
	{
		long time = System.currentTimeMillis();

		final HashMap< String, double[] > coordinateMap = readSlideSeqCoordinates( beadLocations );
		System.out.println( "Read " + coordinateMap.keySet().size() + " coordinates." );

		// coordinates, geneMap
		final Pair< List< Pair< double[], String > >, HashMap< String, double[] > > geneData = readSlideSeqGenes( reads, coordinateMap );
		System.out.println( "Read data for " + geneData.getB().keySet().size() + " genes." );

		final HashSet< Integer > notAssigned = new HashSet<>();
		final List< String > barcodes = geneData.getA().stream().map( p -> p.getB() ).collect( Collectors.toList() );

		int[] celltypeIds = readMetaData(
				cellTypes,
				barcodes,
				notAssigned );

		final STData data;

		if ( notAssigned.size() > 0 )
		{
			// reduce celltypes
			int[] celltypeIdsRed = new int[ celltypeIds.length  - notAssigned.size() ];

			// reduce coordinates
			List< Pair< double[], String > > coordinatesRed = new ArrayList<>();

			int i = 0;

			for ( int j = 0; j < celltypeIds.length; ++j )
			{
				if ( !notAssigned.contains( j ) )
				{
					celltypeIdsRed[ i ] = celltypeIds[ j ];
					coordinatesRed.add( geneData.getA().get( j ) );
					++i;
				}
			}

			// reduce geneMap
			HashMap< String, double[] > geneMapRed = new HashMap<>();

			for ( final Entry< String, double[] > entry : geneData.getB().entrySet() )
			{
				double[] exp = entry.getValue();
				double[] expRed = new double[ celltypeIds.length  - notAssigned.size() ];

				i = 0;
				for ( int j = 0; j < exp.length; ++j )
					if ( !notAssigned.contains( j ) )
						expRed[ i++ ] = exp[ j ];

				geneMapRed.put( entry.getKey(),  expRed );
			}

			data = new STDataText( coordinatesRed, geneMapRed );
			data.getMetaData().put( "celltype", ArrayImgs.ints( celltypeIdsRed, (int)data.numLocations() ) );
		}
		else
		{
			data = new STDataText( geneData.getA(), geneData.getB() );
			data.getMetaData().put( "celltype", ArrayImgs.ints( celltypeIds, (int)data.numLocations() ) );
		}

		System.out.println( "Parsing took " + ( System.currentTimeMillis() - time ) + " ms." );

		return data;
	}

	public static int[] readMetaData( final BufferedReader metaFile, final List< String > barcodes ) throws IOException
	{
		return readMetaData(metaFile, barcodes, null);
	}

	/*
	 * Important: the barcodes list defines the order of the returned int[] array. Not all barcodes might be listed in the metaFile,
	 * 			  those id's that were not assigned will be listed in the notAssigned map
	 */
	public static int[] readMetaData( final BufferedReader metaFile, final List< String > barcodes, final Set< Integer > notAssigned ) throws IOException
	{
		final HashMap< String, Integer > barcodeMap = new HashMap<>();

		int i = 0;
		for ( final String barcode : barcodes )
			barcodeMap.put( barcode, i++ );

		int[] ids = new int[ barcodes.size() ];
		boolean[] assigned = new boolean[ barcodes.size() ];

		i = 0;
		String nextLine = null;

		while ( (nextLine = metaFile.readLine()) != null ) 
		{
			String[] val = nextLine.split( "," );

			if ( val.length != 2 || val[ 0 ].trim().length() == 0 )
				continue;

			final Integer index = barcodeMap.get( val[ 0 ] );

			if ( index == null )
			{
				System.out.println( "barcode '" + val[ 0 ] + "' defined in celltypes not found in the location/expression data. Ignoring.");
				continue;
			}
	
			ids[ index ] = Integer.parseInt( val[ 1 ].trim() );
			assigned[ index ] = true;
			++i;
		}

		if ( i != barcodes.size() )
			System.out.println( "WARNING: Not all ids could be assigned (only " + i + " out of " + barcodes.size() + ")." );

		if ( notAssigned != null )
		{
			for ( int j = 0; j < barcodes.size(); ++j )
				if ( !assigned[ j ] )
					notAssigned.add( j );

			System.out.println( "not assigned: " + notAssigned.size() );
		}

		return ids;
	}

	/**
	 * Assumes that the order of the entries in the file are the same (no barcode-check)
	 * 
	 * @param metaFile
	 * @param numLocations
	 * @return
	 * @throws IOException
	 */
	public static int[] readMetaData( final BufferedReader metaFile, final int numLocations ) throws IOException
	{
		int[] ids = new int[ numLocations ];

		int i = 0;
		String nextLine = null;

		while ( (nextLine = metaFile.readLine()) != null ) 
		{
			String[] val = nextLine.split( "," );

			if ( val.length != 2 || val[ 0 ].trim().length() == 0 )
				continue;

			ids[ i++ ] = Integer.parseInt( val[ 1 ].trim() );
		}

		if ( i != numLocations )
			System.out.println( "WARNING: Not all ids could be assigned (only " + i + " out of " + numLocations + "). Assigning label 0 to all other locations." );

		return ids;
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

			String nextLine = null;

			while ( (nextLine = in.readLine()) != null ) 
			{
				final String[] values = nextLine.trim().split( "," );

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

			String nextLine = null;

			while ( (nextLine = in.readLine()) != null ) 
			{
				final String s = nextLine.trim();
				
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

	public static void saveGenes( final File file, final Collection< String > genes ) throws IOException
	{
		PrintWriter out = TextFileAccess.openFileWrite( file );

		for ( final String gene : genes )
			out.println( gene );

		out.close();
	}

	public static ArrayList< String > loadGenes( final File file, final HashSet< String > existingGenes1, final HashSet< String > existingGenes2 ) throws IOException
	{
		BufferedReader in = TextFileAccess.openFileRead( file );

		ArrayList< String > genes = new ArrayList<>();

		String nextLine = null;

		while ( (nextLine = in.readLine()) != null ) 
		{
			String trimmed = nextLine.trim();

			if ( existingGenes1.contains( trimmed ) && existingGenes2.contains( trimmed ) )
				genes.add( trimmed );
			else
				System.out.println( "Gene '" + trimmed + "' does not exist in both datasets, skipping.");
		}

		in.close();

		return genes;
	}
}
