package io;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import anndata.AnnDataUtils;
import data.STDataImgLib2;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.integer.IntType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import data.STData;

public class AnnDataIO
{
	private static final String celltypePath = "/obs/cell_type";
	private static final String locationPath = "/obsm/locations";

	public static void main( String[] args ) throws IOException
	{
		final String path = "./data/test.h5ad";
		final File file = new File(path);

		STData stData = readSlideSeq(file);
	}

	public static STData readSlideSeq(final File anndataFile) throws IOException {
		long time = System.currentTimeMillis();
		N5Reader reader = new N5HDF5Reader(anndataFile.getAbsolutePath());

		List<String> barcodes = AnnDataUtils.readAnnotation(reader, "/var/_index");
		List<String> geneNames = AnnDataUtils.readAnnotation(reader, "/obs/_index");

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i ) {
			geneLookup.put(geneNames.get(i), i);
		}

		RandomAccessibleInterval locations = AnnDataUtils.readData(reader, locationPath);
		RandomAccessibleInterval expressionVals = AnnDataUtils.readData(reader, "/X");

		STData stdata = new STDataImgLib2(locations, expressionVals, geneNames, barcodes, geneLookup);

		if (containsCelltypes(anndataFile)) {
			Img<IntType> celltypeIds = getCelltypeIds(reader);
			stdata.getMetaData().put("celltype", celltypeIds);
			System.out.println("Loading '" + celltypePath + "' as label 'celltype'.");
		}

		System.out.println("Parsing took " + (System.currentTimeMillis() - time) + " ms.");

		return stdata;
	}

	public static Boolean containsCelltypes(final File anndataFile) {
		try (final N5HDF5Reader n5Reader = new N5HDF5Reader(anndataFile.getAbsolutePath())) {
			return n5Reader.exists(celltypePath);
		}
		catch (IOException e) {
			return Boolean.FALSE;
		}
	}

	public static ArrayImg<IntType, IntArray> getCelltypeIds(final N5Reader reader) throws IOException {
		final List<String> cellTypes = AnnDataUtils.readAnnotation(reader, celltypePath);
		final HashMap<String, Integer> typeToIdMap = new HashMap<>();
		final int[] celltypeIds = new int[cellTypes.size()];

		// for categorical arrays, this is redundant -> use codes directly?
		for (int k = 0; k < cellTypes.size(); ++k) {
			final String type = cellTypes.get(k);
			Integer id = typeToIdMap.get(type);
			if (null == id) {
				id = typeToIdMap.size() + 1;
				typeToIdMap.put(type, id);
			}
			celltypeIds[k] = id;
		}

		return ArrayImgs.ints(celltypeIds, celltypeIds.length);
	}
}
