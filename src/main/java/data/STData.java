package data;

import java.util.List;
import java.util.Map;

import imglib2.ImgLib2Util;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;

public interface STData extends IterableRealInterval<RealLocalizable> {
	Interval getRenderInterval();
	Interval getRenderInterval(final long border);
	Interval getRenderInterval(final long[] border);

	//public KDTree< RealLocalizable > getLocationKDTree();
	//public KDTree< DoubleType > getExpValueKDTree( final String geneName );
	IterableRealInterval<DoubleType> getExprData(final String geneName);

	/**
	 * @return additional annotations that map any location to an arbitrary value
	 */
	Map<String, RandomAccessibleInterval<? extends NativeType<?>>> getAnnotations();

	/**
	 * @return additional gene annotations that map any gene to an arbitrary value
	 */
	Map<String, RandomAccessibleInterval<? extends NativeType<?>>> getGeneAnnotations();

	int getIndexForGene(final String geneName);

	/**
	 * @return the number of sequenced locations
	 */
	long numLocations();

	/**
	 * @return the number of genes 
	 */
	long numGenes();

	/**
	 * @return a list of all gene names
	 */
	List<String> getGeneNames();

	/**
	 * @return a list of barcodes (ordered in the same fashion as the locations)
	 */
	List<String> getBarcodes();

	/**
	 * Note: usually you do not need this datastructure, rather request a RealPointSampleList or 
	 * 
	 * @return the underlying 2d datastructure that holds all sequenced locations by index, size: [numLocations x numDimensions]
	 */
	RandomAccessibleInterval<DoubleType> getLocations();

	/**
	 * @return the underlying 2d datastructure that holds all expression values by index, size: [numGenes x numLocations]
	 */
	RandomAccessibleInterval<DoubleType> getAllExprValues();

	/**
	 * @return a reference to the (modifiable) 1d vector that holds all expression values of a gene by index, size: [numLocations]
	 */
	RandomAccessibleInterval<DoubleType> getExprValues(final String gene);

	/**
	 * @return a reference to the (modifiable) 1d vector that holds all expression values of a location, size: [numGenes]
	 */
	RandomAccessibleInterval<DoubleType> getExprValues(final long locationIndex);

	/**
	 * Non-virtual way to access all sequencing locations,
	 * might copy the data in memory
	 * <p>
	 * index in the list corresponds to the getExpValues list
	 * 
	 * @return all locations, size of double[] corresponds to numDimensions()
	 */
	List<double[]> getLocationsCopy();

	/**
	 * Non-virtual way to set all sequencing locations,
	 * will overwrite existing values
	 * <p>
	 * index in the list corresponds to the getExpValues list
	 * 
	 * @param locations - list of locations
	 */
	void setLocations(final List<double[]> locations);

	/**
	 * Non-virtual way to load all expression values for a certain gene,
	 * might copy the data in memory
	 * <p>
	 * index in the list corresponds to the locations list
	 * 
	 * @param geneName - name of the gene
	 * @return all expression values of a gene
	 */
	double[] getExpValuesCopy(final String geneName);

	/**
	 * Non-virtual way to set all expression values of a gene,
	 * will overwrite existing values
	 * 
	 * @param geneName - name of the gene
	 * @param values - the values of each sequenced location, index corresponds to the location index in getLocations or getLocationsCopy
	 */
	void setExpValues(final String geneName, final double[] values);

	/**
	 * Creates a copy of the dataset that can be edited and resaved
	 *
	 * @return a copy of the STData object that holds all data in a writable form (e.g. ImgLib2 CellImg)
	 */
	default STData copy()
	{
		return ImgLib2Util.copy( this );
	}
}
