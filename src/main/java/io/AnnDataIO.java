package io;

import java.io.IOException;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

public class AnnDataIO
{

	public static void main( String[] args ) throws IOException
	{
		final String file = "./data/test.h5ad";

		final IHDF5Reader reader = HDF5Factory.openForReading(file);

		// read out all the meta-data of the main data array
		final int[] shape = reader.getIntArrayAttribute( "X", "shape" );
		final int numVariables = shape[ 0 ];
		final int numObservations = shape[ 1 ];

		System.out.println( "numVariables = " + numVariables );
		System.out.println( "numObservations = " + numObservations );

		// observation names
		final String[] observations = reader.readStringArray( "obs/_index" );
		System.out.println( "read " + observations.length + " observations.");
		for ( final String observation : observations )
			System.out.println( observation );

		// number of variable names
		final String[] variables = reader.readStringArray( "var/_index" );
		System.out.println( "read " + variables.length + " variable names.");

		// multidimensional annotations
		final double[] multiDimAnnotation = reader.readDoubleArray( "varm/gene_stuff" );
		System.out.println( "read " + multiDimAnnotation.length + " annotations.");
	}
}
