package io;

import java.io.IOException;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5SimpleReader;

public class HDF510xImporter
{

	public static void main( String[] args ) throws IOException
	{
		final String file = "./data/10x-HDF5/V1_Mouse_Brain_Sagittal_Anterior_filtered_feature_bc_matrix.h5";

		final IHDF5SimpleReader reader = HDF5Factory.openForReading(file);

		final int[] shape = reader.readIntArray( "matrix/shape" );
		final int numGenes = shape[ 0 ];
		final int numBarcodes = shape[ 1 ];

		System.out.println( "numGenes = " + numGenes );
		System.out.println( "numBarcodes = " + numBarcodes );

		final String[] barcodes = reader.readStringArray( "matrix/barcodes" );
		System.out.println( "read " + barcodes.length + " barcodes.");
		//for ( final String barcode : barcodes )
		//	System.out.println( barcode );

		// gene names
		final String[] geneNames = reader.readStringArray( "matrix/features/name" );
		System.out.println( "read " + geneNames.length + " gene names.");
		//for ( final String name : geneNames )
		//	System.out.println( name );

		// pointers for indicies and data (|barcodes|+1) > from (inclusive), to (exclusive)
		final long[] indptrs = reader.readLongArray( "matrix/indptr" );
		System.out.println( "read " + indptrs.length + " pointers.");
		//for ( final long indptr : indptrs )
		//	System.out.println( indptr );

		// loading "data" (reads?)
		final int[] data = reader.readIntArray( "matrix/data" );
		System.out.println( "read " + data.length + " data entries.");

		// loading "indicies" (gene?)
		final int[] indices = reader.readIntArray( "matrix/indices" );
		System.out.println( "read " + data.length + " indices entries.");

		for ( long i = indptrs[ 1 ]; i < indptrs[ 2 ]; ++i )
			System.out.println( i + " (no meaning - many per location): " + data[ (int)i ] + " (reads), " + indices[ (int)i ] + " (gene id)");

		// says "genome"
		//final String allTags = reader.readString( "matrix/features/_all_tag_keys" );

		// says "Gene Expression" for all
		// final String[] featureTypes = reader.readStringArray( "matrix/features/feature_type" );

		// says "mm10" for all
		//final String[] genomes = reader.readStringArray( "matrix/features/genome" );

		// says "ENSMUSG00000058881", different for each
		//final String[] ids = reader.readStringArray( "matrix/features/id" );


		/*
		final N5Reader reader = new N5HDF5Reader( file, 32 );
		final Map<String, Class<?>> att = reader.listAttributes( "" );

		System.out.println( "Base attributes: ");
		for ( final Entry<String, Class<?>> e : att.entrySet() )
			System.out.println( "  " + e.getKey() +" [" + e.getValue().getName() + "] = " + reader.getAttribute("", e.getKey(), e.getValue() ) );

		System.out.println( "\nBase datasets: ");
		String[] datasets = reader.list( "" );

		for ( final String e : datasets )
			System.out.println( "  " + e );

		

		System.out.println( "Loading barcodes ... ");
		DatasetAttributes da = reader.getDatasetAttributes( barcodes );
		DataBlock<?> block = reader.readBlock( barcodes, da, 0 );*/
	}
}
