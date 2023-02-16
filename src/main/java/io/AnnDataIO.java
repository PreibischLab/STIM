package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import data.STDataText;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import data.STData;

public class AnnDataIO
{
	private static final String celltypePath = "/obs/cell_type";
	private static final String locationPath = "/obsm/locations";

	public static void main( String[] args ) throws IOException
	{
		final String path = "./data/test.h5ad";

		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(path);
		final N5HDF5Reader n5Reader = new N5HDF5Reader(path);

		final File file = new File(path);
		STData stData = readSlideSeq(file);
	}

	public static STData readSlideSeq(final File anndataFile) throws IOException {
		long time = System.currentTimeMillis();
		N5HDF5Reader n5Reader = new N5HDF5Reader(anndataFile.getAbsolutePath());

		final List<Pair<double[], String>> coordinateList = readSlideSeqCoordinates(n5Reader);
		System.out.println("Read " + coordinateList.size() + " coordinates from '" + locationPath + "'.");

		final HashMap<String, double[]> geneData = readSlideSeqGenes(n5Reader);
		System.out.println("Read data for " + geneData.keySet().size() + " genes from '/X'.");

		final STData data = new STDataText(coordinateList, geneData);
		if (containsCelltypes(anndataFile)) {
			int[] celltypeIds = getCelltypeIds(n5Reader);
			data.getMetaData().put("celltype", ArrayImgs.ints(celltypeIds, (int)data.numLocations()));
			System.out.println("Loading '" + celltypePath + "' as label 'celltype'.");
		}

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
		// TODO: coordinate field is not always named 'locations' or of type double!
		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(n5Reader.getFilename());
		String[] barcodes = hdf5Reader.readStringArray("obs/_index");
		int numCoordinates = barcodes.length;

		DatasetAttributes attributes = n5Reader.getDatasetAttributes(locationPath);

		if (numCoordinates != (int) attributes.getDimensions()[1])
			throw new RuntimeException("Number of barcodes does not match number of coordinates.");

		// coordinates are stored row-wise in python
		final ArrayList<Pair<double[], String>> coordinates = new ArrayList<>();
		final int dim = attributes.getNumDimensions();
		double[] block = (double[]) n5Reader.readBlock(locationPath, attributes, 0, 0).getData();

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

	public static Boolean containsCelltypes(final File anndataFile) {
		try (final N5HDF5Reader n5Reader = new N5HDF5Reader(anndataFile.getAbsolutePath())) {
			return n5Reader.exists(celltypePath);
		}
		catch (IOException e) {
			return Boolean.FALSE;
		}
	}

	public static int[] getCelltypeIds(final N5HDF5Reader n5Reader) throws IOException {
		// TODO: not all celltypes are stored as categorical
		final DatasetAttributes attributes = n5Reader.getDatasetAttributes(celltypePath + "/codes");
		final byte[] celltypeIdsByte = (byte[]) n5Reader.readBlock(celltypePath + "/codes", attributes, 0).getData();

		final int[] celltypeIds = new int[celltypeIdsByte.length];
		for (int i=0; i<celltypeIds.length; ++i)
			celltypeIds[i] = (int)celltypeIdsByte[i];

		// this assembles the actual type names
		// final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(n5Reader.getFilename());
		// final String[] categoryNames = hdf5Reader.readStringArray(celltypePath + "/categories");
		// final String[] celltypeNames = new String[celltypeIds.length];
		// for (int i=0; i<celltypeIds.length; ++i)
		// 	celltypeNames[i] = categoryNames[celltypeIds[i]];

		return celltypeIds;
	}
}
