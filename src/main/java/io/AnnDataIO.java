package io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

public class AnnDataIO
{

	public static void main( String[] args ) throws IOException
	{
		final String file = "./data/test.h5ad";

		final IHDF5Reader dataStore = HDF5Factory.openForReading(file);
		final N5HDF5Reader n5 = new N5HDF5Reader(file);

		final String groupName = n5.groupPath("X");
		System.out.println("sucessfully loaded " + groupName);

		DatasetAttributes attributes = n5.getDatasetAttributes("X/data");
		final int nDim = attributes.getNumDimensions();
		System.out.println("Number of dimensions: " + nDim);
		final long[] dim = attributes.getDimensions();
		System.out.println("Dimensions: " + dim[0]);
		System.out.println("Data type: " + attributes.getDataType());
		System.out.println("Compression: " + attributes.getCompression());
		final int[] blkSize = attributes.getBlockSize();
		System.out.println("Blocksize: " + blkSize[0]);
		float[] data = (float[]) n5.readBlock("X/data", attributes, 0).getData();
		for ( int i=0; i<25; i++ ) {
			System.out.println(data[i]);
		}
		readLocations(n5);

		System.out.println("The root has some meta-information.");
		printStringAttribute(n5,"/", "encoding-type");
		printStringAttribute(n5,"/", "encoding-version");

		System.out.println();
		System.out.println("The main matrix is stored in CSR/CSC format");
		printIntArrayAttribute(n5, "X", "shape");
		printStringAttribute(n5, "X", "encoding-type");
		printStringAttribute(n5, "X", "encoding-version");
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
		printIntArrayAttribute(n5, "layers/log_transformed", "shape");
		printStringAttribute(n5, "layers/log_transformed", "encoding-type");
		printStringAttribute(n5, "layers/log_transformed", "encoding-version");
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

	private static void printStringAttribute(N5HDF5Reader data, String path, String attribute) throws IOException {
		final String value = data.getAttribute(path, attribute, String.class);

		System.out.println("<" + path + " (attribute: " + attribute + ")> string: " + value);
	}

	private static void printIntArrayAttribute(N5HDF5Reader data, String path, String attribute) throws IOException {
		final long[] value = data.getAttribute(path, attribute, long[].class);
		int end = Math.min(5, value.length);

		StringBuilder str = createArrayPretext(path + " (attribute: " + attribute + ")", "int", value.length);
		for ( int i=0; i<end; i++) { str.append(" ").append((int)value[i]); }
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

	private static HashMap<String, double[]> readLocations(N5HDF5Reader data) throws IOException {
		// location data is stored in the obs-related fields in anndata:
		// obs/_index -> names; obsm/locations -> coordinates
		DatasetAttributes attributes = data.getDatasetAttributes("obsm/locations");
		final int dim = attributes.getNumDimensions();
		final long[] shape = attributes.getDimensions();
		final int nLocations = (int) shape[0];
		int[] blockSize = attributes.getBlockSize();
		for ( int i=0; i<dim; i++ )
			if ( shape[i] != blockSize[i] )
				throw new RuntimeException("Blocksize in dimension " + i + " not equal: " + shape[i] + " != " + blockSize[i]);

		double[] block = (double[]) data.readBlock("obsm/locations", attributes, 0, 0).getData();
		for ( int i = 0; i < nLocations; i++ ) {
			double[] location = new double[dim];
			for (int k = 0; k < dim; k++) {
				location[k] = block[dim*i+k];
			}
		}

		attributes = data.getDatasetAttributes("obs/_index");
		ByteBuffer buffer = data.readBlock("obs/_index", attributes, 0).toByteBuffer();

		return new HashMap<>();
	}
}
