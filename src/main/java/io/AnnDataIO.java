package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import data.STDataText;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import data.STData;

public class AnnDataIO
{

	public static void main( String[] args ) throws IOException
	{
		final String file = "./data/test.h5ad";

		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(file);
		final N5HDF5Reader n5Reader = new N5HDF5Reader(file);

		readSlideSeq(file);

		final double[][] X = reconstructMatrixfromSparse(hdf5Reader, "X");

		final double[][] Y = reconstructMatrixfromSparse(hdf5Reader, "layers/log_transformed");

	}

	public static STData readSlideSeq(final String anndataFile) throws IOException {
		long time = System.currentTimeMillis();
		N5HDF5Reader n5Reader = new N5HDF5Reader(anndataFile);

		final List<Pair<double[], String>> coordinateList = readSlideSeqCoordinates(n5Reader);
		System.out.println("Read " + coordinateList.size() + " coordinates.");

		final HashMap<String, double[]> geneData = readSlideSeqGenes(n5Reader);
		System.out.println("Read data for " + geneData.keySet().size() + " genes.");

		final STData data = new STDataText(coordinateList, geneData);

		System.out.println("Parsing took " + (System.currentTimeMillis() - time) + " ms.");

		return data;
	}

	private static double[][] reconstructMatrixfromSparse(IHDF5Reader hdf5Reader, String path) {
		final String encoding = hdf5Reader.getStringAttribute(path, "encoding-type");
		final String version = hdf5Reader.getStringAttribute(path, "encoding-version");
		final int[] shape = hdf5Reader.getIntArrayAttribute(path, "shape");
		final int numVariables = shape[0];
		final int numObservations = shape[1];

		// store dense matrix row-wise, initialized by default to 0.0d
		double[][] matrix = new double[numObservations][numVariables];

		final double[] compressedMatrix = hdf5Reader.readDoubleArray(path + "/data");
		final int[] indices = hdf5Reader.readIntArray(path + "/indices");
		final int[] indptr = hdf5Reader.readIntArray(path + "/indptr");
		if ( encoding.equals("csr_matrix") ) {
			for ( int i=0; i<indptr.length-1; i++ ) {
				for ( int k=indptr[i]; k<indptr[i+1]; k++ ) {
					matrix[indices[k]][i] = compressedMatrix[k];
				}
			}
		} else if ( encoding.equals("csc_matrix") ) {
			for ( int k=0; k<indptr.length-1; k++ ) {
				for ( int i=indptr[k]; i<indptr[k+1]; i++ ) {
					matrix[k][indices[i]] = compressedMatrix[k];
				}
			}
		} else {
			throw new UnsupportedOperationException( "Reconstructing sparse matrix not implemented for encoding " + encoding );
		}

		return matrix;
	}

	private static List<Pair<double[], String>> readSlideSeqCoordinates(final N5HDF5Reader n5Reader) throws IOException {
		// location data is stored in the obs-related fields in anndata:
		// obs/_index -> names; obsm/locations -> coordinates
		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(n5Reader.getFilename());
		String[] barcodes = hdf5Reader.readStringArray("obs/_index");
		int numCoordinates = barcodes.length;

		DatasetAttributes attributes = n5Reader.getDatasetAttributes("obsm/locations");

		if (numCoordinates != (int) attributes.getDimensions()[1])
			throw new RuntimeException("Number of barcodes does not match number of coordinates.");

		// coordinates are stored row-wise in python
		final ArrayList<Pair<double[], String>> coordinates = new ArrayList<>();
		final int dim = attributes.getNumDimensions();
		double[] block = (double[]) n5Reader.readBlock("obsm/locations", attributes, 0, 0).getData();

		for ( int i=0; i<numCoordinates; ++i ) {
			final double[] coordinate = new double[dim];
			for (int j = 0; j < dim; j++)
				coordinate[j] = block[i*dim + j];
			coordinates.add(new ValuePair<>(coordinate, barcodes[i]));
		}

		return coordinates;
	}

	public static HashMap<String, double[]> readSlideSeqGenes(final N5HDF5Reader n5Reader) throws IOException {

		final ArrayList<Pair<double[], String>> coordinates = new ArrayList<>();
		final HashMap<String, double[]> geneMap = new HashMap<>();

		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(n5Reader.getFilename());
		String[] geneNames = hdf5Reader.readStringArray("var/_index");
		final long[] shape = n5Reader.getAttribute("X", "shape", long[].class);

		if (geneNames.length != shape[1])
			throw new RuntimeException("Number of genes inconsistent with matrix X.");

		// X is stored row-wise
		final double[][] X = reconstructMatrixfromSparse(hdf5Reader, "X");
		for (int i=0; i<geneNames.length; ++i)
			geneMap.put(geneNames[i], X[i]);

		return geneMap;
	}

}
