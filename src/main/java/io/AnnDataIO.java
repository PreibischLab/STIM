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

		System.out.println("The root has some meta-information.");
		printStringAttribute(dataStore,"/", "encoding-type");
		printStringAttribute(dataStore,"/", "encoding-version");

		System.out.println();
		System.out.println("The main matrix is stored in CSR/CSC format");
		printIntArrayAttribute(dataStore, "X", "shape");
		printStringAttribute(dataStore, "X", "encoding-type");
		printStringAttribute(dataStore, "X", "encoding-version");
		printDoubleArray(dataStore, "X/data");
		printIntArray(dataStore, "X/indices");
		printIntArray(dataStore, "X/indptr");
		final double[][] X = reconstructMatrixfromSparse(dataStore, "X");

		System.out.println();
		System.out.println("Information about datasets can also be inferred");
		String type = dataStore.getDataSetInformation("X/data").getTypeInformation().getRawDataClass().toString();
		long[] shape = dataStore.getDataSetInformation("X/data").getDimensions();
		System.out.println("X/data is of type " + type + " and has shape (" + shape[0] +",)");

		System.out.println();
		System.out.println("Layers are matrices with the same layout as X");
		printIntArrayAttribute(dataStore, "layers/log_transformed", "shape");
		printStringAttribute(dataStore, "layers/log_transformed", "encoding-type");
		printStringAttribute(dataStore, "layers/log_transformed", "encoding-version");
		printDoubleArray(dataStore, "layers/log_transformed/data");
		printIntArray(dataStore, "layers/log_transformed/indices");
		printIntArray(dataStore, "layers/log_transformed/indptr");
		final double[][] Y = reconstructMatrixfromSparse(dataStore, "layers/log_transformed");

		System.out.println();
		System.out.println("Variable/observation can have names and types (=1-dimensional annotations)");
		printStringArray(dataStore, "var/_index");
		printStringArray(dataStore, "obs/_index");
		printStringArray(dataStore, "obs/cell_type/categories");
		printIntArray(dataStore, "obs/cell_type/codes");

		System.out.println();
		System.out.println("Multidimensional annotations are stored as separate fields");
		printDoubleArray(dataStore, "varm/gene_stuff");
		printDoubleArray(dataStore, "obsm/X_umap");
		type = dataStore.getDataSetInformation("obsm/X_umap").getTypeInformation().getRawDataClass().toString();
		shape = dataStore.getDataSetInformation("obsm/X_umap").getDimensions();
		System.out.println("obsm/X_umap is of type " + type + " and has shape (" + shape[0] + "," + shape[1] + ")");

		System.out.println();
		System.out.println("Pairwise relationships within variables/observations are stored in varp/obsp");

		System.out.println();
		System.out.println("Everything else goes into uns");
		printDoubleArray(dataStore, "uns/random");
	}

	private static void printStringAttribute(IHDF5Reader data, String path, String attribute) {
		final String value = data.getStringAttribute(path, attribute);
		System.out.println("<" + path + " (attribute: " + attribute + ")> string: " + value);
	}

	private static void printIntArrayAttribute(IHDF5Reader data, String path, String attribute) {
		final int[] value = data.getIntArrayAttribute(path, attribute);
		int end = Math.min(5, value.length);

		StringBuilder str = createArrayPretext(path + " (attribute: " + attribute + ")", "int", value.length);
		for ( int i=0; i<end; i++) { str.append(" ").append(value[i]); }
		if ( end < value.length ) { str.append(" ..."); }

		System.out.println(str);
	}

	private static void printIntArray(IHDF5Reader data, String path) {
		final int[] value = data.readIntArray(path);
		int end = Math.min(5, value.length);

		StringBuilder str = createArrayPretext(path, "int", value.length);
		for ( int i=0; i<end; i++) { str.append(" ").append(value[i]); }
		if ( end < value.length ) { str.append(" ..."); }

		System.out.println(str);
	}

	private static void printDoubleArray(IHDF5Reader data, String path) {
		final double[] value = data.readDoubleArray(path);
		int end = Math.min(5, value.length);

		StringBuilder str = createArrayPretext(path, "double", value.length);
		for ( int i=0; i<end; i++) { str.append(" ").append(value[i]); }
		if ( end < value.length ) { str.append(" ..."); }

		System.out.println(str);
	}

	private static void printStringArray(IHDF5Reader data, String path) {
		final String[] value = data.readStringArray(path);
		int end = Math.min(5, value.length);

		StringBuilder str = createArrayPretext(path, "string", value.length);
		for ( int i=0; i<end; i++) { str.append(" ").append(value[i]); }
		if ( end < value.length ) { str.append(" ..."); }

		System.out.println(str);
	}

	private static StringBuilder createArrayPretext(String path, String type, int len) {
		StringBuilder str = new StringBuilder("<")
				.append(path)
				.append("> ")
				.append(type)
				.append(" array (")
				.append(len)
				.append("):");
		return str;
	}

	private static double[][] reconstructMatrixfromSparse(IHDF5Reader data, String path) {
		final String encoding = data.getStringAttribute(path, "encoding-type");
		final String version = data.getStringAttribute(path, "encoding-version");
		final int[] shape = data.getIntArrayAttribute(path, "shape");
		final int numVariables = shape[0];
		final int numObservations = shape[1];

		// store dense matrix in row-major order, initialized by default to 0.0d
		double[][] matrix = new double[numVariables][numObservations];

		final double[] compressedMatrix = data.readDoubleArray(path + "/data");
		final int[] indices = data.readIntArray(path + "/indices");
		final int[] indptr = data.readIntArray(path + "/indptr");
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

		return matrix;
	}
}
