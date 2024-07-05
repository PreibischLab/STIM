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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import data.STData;
import data.STDataText;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class TextFileIO
{
	private static final Logger logger = LoggerUtil.getLogger();
	public static STData readSlideSeq( final File beadLocations, final File reads )
	{
		logger.debug( "Parsing " + beadLocations.getName() + ", " + reads.getName() );

		final BufferedReader beadLocationsIn = TextFileAccess.openFileRead( beadLocations );
		final BufferedReader readsIn = TextFileAccess.openFileRead( reads );

		final STData data = readSlideSeq( beadLocationsIn, readsIn );

		try {
			beadLocationsIn.close();
			readsIn.close();
		} catch (IOException e) {}

		logger.debug( data );

		return data;
	}

	public static STData readSlideSeq( final BufferedReader beadLocations, final BufferedReader reads )
	{
		long time = System.currentTimeMillis();

		final HashMap< String, double[] > coordinateMap = readSlideSeqCoordinates( beadLocations );
		logger.debug( "Read " + coordinateMap.keySet().size() + " coordinates." );

		final Pair< List< Pair< double[], String > >, HashMap< String, double[] > > geneData = readSlideSeqGenes( reads, coordinateMap );
		logger.debug( "Read data for " + geneData.getB().keySet().size() + " genes." );

		final STData data = new STDataText( geneData.getA(), geneData.getB() );
		//logger.debug( data );

		logger.debug( "Parsing took " + ( System.currentTimeMillis() - time ) + " ms." );

		return data;
	}

	public static STData readSlideSeq( final BufferedReader beadLocations, final BufferedReader reads, final Map<String, BufferedReader> annotations ) throws IOException
	{
		long time = System.currentTimeMillis();

		final HashMap< String, double[] > coordinateMap = readSlideSeqCoordinates( beadLocations );
		logger.debug( "Read " + coordinateMap.keySet().size() + " coordinates." );

		// coordinates, geneMap
		final Pair< List< Pair< double[], String > >, HashMap< String, double[] > > geneData = readSlideSeqGenes( reads, coordinateMap );
		logger.debug( "Read data for " + geneData.getB().keySet().size() + " genes." );

		final HashSet< Integer > notAssigned = new HashSet<>();
		final List< String > barcodes = geneData.getA().stream().map(Pair::getB).collect(Collectors.toList() );

		Map<String, int[]> annotationIds = new HashMap<>();
		for (Entry<String, BufferedReader> entry : annotations.entrySet()) {
			int[] ids = readAnnotations(
					entry.getValue(),
					barcodes,
					notAssigned);
			annotationIds.put(entry.getKey(), ids);
			if (!notAssigned.isEmpty())
				logger.warn( "not assigned after reading '" + entry.getKey() + "': " + notAssigned.size() );
		}

		final STData data;

		if ( notAssigned.size() > 0 )
		{
			// reduce celltypes
			final Map<String, int[]> annotationIdsRed = new HashMap<>();
			for (final String annotationName : annotationIds.keySet())
				annotationIdsRed.put(annotationName, new int[barcodes.size() - notAssigned.size()]);

			// reduce coordinates
			List< Pair< double[], String > > coordinatesRed = new ArrayList<>();

			int i = 0;

			for ( int j = 0; j < barcodes.size(); ++j )
			{
				if ( !notAssigned.contains( j ) )
				{
					for (final String annotationName : annotationIds.keySet())
						annotationIdsRed.get(annotationName)[i] = annotationIds.get(annotationName)[j];
					coordinatesRed.add( geneData.getA().get( j ) );
					++i;
				}
			}

			// reduce geneMap
			HashMap< String, double[] > geneMapRed = new HashMap<>();

			for ( final Entry< String, double[] > entry : geneData.getB().entrySet() )
			{
				double[] exp = entry.getValue();
				double[] expRed = new double[barcodes.size() - notAssigned.size()];

				i = 0;
				for ( int j = 0; j < exp.length; ++j )
					if ( !notAssigned.contains( j ) )
						expRed[ i++ ] = exp[ j ];

				geneMapRed.put( entry.getKey(),  expRed );
			}

			data = new STDataText( coordinatesRed, geneMapRed );
			for (Entry<String, int[]> entry : annotationIdsRed.entrySet())
				data.getAnnotations().put(entry.getKey(), ArrayImgs.ints(entry.getValue(), (int)data.numLocations()));
		}
		else
		{
			data = new STDataText( geneData.getA(), geneData.getB() );
			for (Entry<String, int[]> entry : annotationIds.entrySet())
				data.getAnnotations().put(entry.getKey(), ArrayImgs.ints(entry.getValue(), (int)data.numLocations()));
		}

		logger.debug( "Parsing took " + ( System.currentTimeMillis() - time ) + " ms." );

		return data;
	}

	public static int[] readAnnotations( final BufferedReader annotationFile, final List< String > barcodes ) throws IOException
	{
		return readAnnotations(annotationFile, barcodes, null);
	}

	/*
	 * Important: the barcodes list defines the order of the returned int[] array. Not all barcodes might be listed in the annotationFile,
	 * 			  those id's that were not assigned will be listed in the notAssigned map
	 */
	public static int[] readAnnotations( final BufferedReader annotationFile, final List< String > barcodes, final Set< Integer > notAssigned ) throws IOException
	{
		final HashMap< String, Integer > barcodeMap = new HashMap<>();

		int i = 0;
		for ( final String barcode : barcodes )
			barcodeMap.put( barcode, i++ );

		int[] ids = new int[ barcodes.size() ];
		boolean[] assigned = new boolean[ barcodes.size() ];

		i = 0;
		String nextLine = null;

		while ( (nextLine = annotationFile.readLine()) != null )
		{
			String[] barcodeWithId = nextLine.split( "," );

			if ( barcodeWithId.length != 2 || barcodeWithId[ 0 ].trim().length() == 0 )
				continue;

			final Integer index = barcodeMap.get( barcodeWithId[ 0 ] );

			if ( index == null )
			{
				logger.debug( "barcode '" + barcodeWithId[ 0 ] + "' defined in annotation not found in the location/expression data. Ignoring.");
				continue;
			}
	
			ids[ index ] = Integer.parseInt( barcodeWithId[ 1 ].trim() );
			assigned[ index ] = true;
			++i;
		}

		if ( i != barcodes.size() )
			logger.warn( "Not all ids could be assigned (only " + i + " out of " + barcodes.size() + ")." );

		if ( notAssigned != null )
		{
			for ( int j = 0; j < barcodes.size(); ++j )
				if ( !assigned[ j ] )
					notAssigned.add( j );
		}

		return ids;
	}

	/**
	 * Assumes that the order of the entries in the file are the same (no barcode-check)
	 * 
	 * @param annotationFile
	 * @param numLocations
	 * @return
	 * @throws IOException
	 */
	public static int[] readAnnotations( final BufferedReader annotationFile, final int numLocations ) throws IOException
	{
		int[] ids = new int[ numLocations ];

		int i = 0;
		String nextLine = null;

		while ( (nextLine = annotationFile.readLine()) != null )
		{
			String[] val = nextLine.split( "," );

			if ( val.length != 2 || val[ 0 ].trim().length() == 0 )
				continue;

			ids[ i++ ] = Integer.parseInt( val[ 1 ].trim() );
		}

		if ( i != numLocations )
			logger.warn( "Not all ids could be assigned (only " + i + " out of " + numLocations + "). Assigning label 0 to all other locations." );

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
				logger.warn( "Gene '" + trimmed + "' does not exist in both datasets, skipping.");
		}

		in.close();

		return genes;
	}
}
