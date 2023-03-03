package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import anndata.AnnDataUtils;
import data.STDataImgLib2;
import data.STDataStatistics;
import data.STDataUtils;
import gui.STDataAssembly;
import gui.STDataExplorer;
import ij.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import render.Render;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import data.STData;

public class AnnDataIO
{
	private static final String celltypePath = "/obs/cell_type";
	private static final String locationPath = "/obsm/spatial";

	public static void main( String[] args ) throws IOException
	{
		final String path = System.getProperty("user.dir") + "/data/human-lymph-node.h5ad";
		final File file = new File(path);

		STData stData = readSlideSeq(file);

//		ImageJFunctions.show(stData.getAllExprValues());
		final ArrayList< STDataAssembly > data;

		data = new ArrayList<>();
		data.add(AnnDataIO.openAllDatasets(new File(path)));

		// ignore potentially saved intensity adjustments
		for ( final STDataAssembly d : data )
			d.intensityTransform().set( 1.0, 0.0 );

		// TODO: I would copy the Decoded RAI, and see if errors are still there
		System.out.println( "Interval: " + Intervals.expand( data.get( 0 ).data().getRenderInterval(), -4000 ) );
		final RealRandomAccessible< DoubleType > renderRRA = Render.getRealRandomAccessible( data.get( 0 ), "IGKC", 0.1, new ArrayList<>() );
		final RandomAccessibleInterval<DoubleType> img =
				Views.interval( Views.raster( renderRRA ),
				Intervals.expand( data.get( 0 ).data().getRenderInterval(), -10 ) );
		new ImageJ();
		ImageJFunctions.show( img );
		
		// Gene to look at: IGKC, mean filter > 0, Gauß filter ~ 0.1
		//new STDataExplorer( data );

	}

	public static STDataAssembly openAllDatasets(final File anndataFile) throws IOException {
		final N5HDF5Reader reader = openAnnData(anndataFile);

		STData stData = readSlideSeq(anndataFile);

		if (stData == null)
			return null;

		final STDataStatistics stat = new STDataStatistics(stData);

		final AffineTransform2D transform = new AffineTransform2D();
		final AffineTransform intesityTransform = new AffineTransform(1);
		intesityTransform.set(1, 0);

		return new STDataAssembly(stData, stat, transform, intesityTransform);
	}

	public static N5HDF5Reader openAnnData(final File anndataFile) throws IOException {
		if (!anndataFile.exists())
			throw new RuntimeException("AnnData-path '" + anndataFile.getAbsolutePath() + "' does not exist." );

		return new N5HDF5Reader(anndataFile.getAbsolutePath());
	}

	public static STData readSlideSeq(final File anndataFile) throws IOException {
		long time = System.currentTimeMillis();
		N5Reader reader = new N5HDF5Reader(anndataFile.getAbsolutePath());

		List<String> geneNames = AnnDataUtils.readAnnotation(reader, "/var/_index");
		List<String> barcodes = AnnDataUtils.readAnnotation(reader, "/obs/_index");

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i ) {
			geneLookup.put(geneNames.get(i), i);
		}

		// permute locations, since this is required by STData
		RandomAccessibleInterval<LongType> locations =
				Views.permute((RandomAccessibleInterval<LongType>) AnnDataUtils.readData(reader, locationPath), 0, 1);
		RandomAccessibleInterval expressionVals = AnnDataUtils.readData(reader, "/X");
		final RandomAccessibleInterval<DoubleType> convertedLocations = Converters.convert(
				locations, (i, o) -> o.set(i.getRealDouble()), new DoubleType());

		STData stdata = new STDataImgLib2(convertedLocations, expressionVals, geneNames, barcodes, geneLookup);

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
