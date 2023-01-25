package io;

import java.io.IOException;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

public class AnnDataIO
{

	public static void main( String[] args ) throws IOException
	{
		final String file = "./data/test.h5ad";

		final IHDF5Reader dataStore = HDF5Factory.openForReading(file);

		// read out main data array (may be sparse)
		final double[][] X = reconstructMatrixfromSparse(dataStore);

		// observation names
		final String[] observations = dataStore.readStringArray("obs/_index");
		System.out.println( "read " + observations.length + " observations.");
		for ( final String observation : observations )
			System.out.println(observation);

		// number of variable names
		final String[] variables = dataStore.readStringArray("var/_index");
		System.out.println("read " + variables.length + " variable names.");

		// multidimensional annotations
		final double[] multiDimAnnotation = dataStore.readDoubleArray("varm/gene_stuff");
		System.out.println("read " + multiDimAnnotation.length + " annotations.");
	}

	private static double[][] reconstructMatrixfromSparse(IHDF5Reader data) {
		final String encoding = data.getStringAttribute("X", "encoding-type");
		final String version = data.getStringAttribute("X", "encoding-version");
		final int[] shape = data.getIntArrayAttribute("X", "shape");
		final int numVariables = shape[0];
		final int numObservations = shape[1];

		// store dense matrix in row-major order, initialized by default to 0.0d
		double[][] matrix = new double[numVariables][numObservations];

		final double[] compressedMatrix = data.readDoubleArray("X/data");
		final int[] indices = data.readIntArray("X/indices");
		final int[] indptr = data.readIntArray("X/indptr");
		if ( encoding.equals("csr_matrix") ) {
			for ( int i=0; i<indptr.length-1; i++ ) {
				for ( int k=indptr[i]; k<indptr[i+1]; k++ ) {
					matrix[i][indices[k]] = compressedMatrix[k];
				}
			}
		} else if ( encoding.equals("csc_matrix") ) {
			for ( int k=0; k<indptr.length-1; k++ ) {
				for ( int i=indptr[k]; i<indptr[k+1]; i++ ) {
					matrix[indices[i]][k] = compressedMatrix[k];
				}
			}
		} else {
			throw new UnsupportedOperationException( "Reconstructing sparse matrix not implemented for encoding " + encoding );
		}

		System.out.println("read array of " + numVariables + " variables and " + numObservations +
				" observations from format " + encoding + " version " + version + ".");

		return matrix;
	}
}
