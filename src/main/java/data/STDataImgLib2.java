package data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * An implementation of STData that uses ImgLib2 datastructures to hold the actual data
 *
 * @author spreibi
 *
 */
public class STDataImgLib2 extends STDataAbstract
{
	/**
	 * a 2d datastructure that holds all sequenced locations, size: [numLocations x numDimensions]
	 */
	private final RandomAccessibleInterval< DoubleType > locations;

	/**
	 * a 2d datastructure that holds all expression values, size: [numGenes x numLocations]
	 */
	private final RandomAccessibleInterval< DoubleType > exprValues;

	private final List< String > geneNames, barcodes;
	private final HashMap< String, Integer > geneLookup;

	private final Map<String, RandomAccessibleInterval<? extends NativeType< ? >>> annotations;
	private final Map<String, RandomAccessibleInterval<? extends NativeType< ? >>> geneAnnotations;

	private RealInterval realInterval;

	public static class STDataImgLib2Factory
	{
		public RandomAccessibleInterval< DoubleType > locations, exprValues;
		public List< String > geneNames, barcodes;
		public HashMap< String, Integer > geneLookup;
	}

	public STDataImgLib2( final STDataImgLib2Factory factory )
	{
		this( factory.locations, factory.exprValues, factory.geneNames, factory.barcodes, factory.geneLookup );
	}

	public STDataImgLib2(
			final RandomAccessibleInterval< DoubleType > locations,
			final RandomAccessibleInterval< DoubleType > exprValues,
			final List< String > geneNames,
			final List< String > barcodes,
			final HashMap< String, Integer > geneLookup )
	{
		super( (int)locations.dimension( 1 ), (int)locations.dimension( 0 ), (int)exprValues.dimension( 0 ) );

		this.locations = locations;
		this.exprValues = exprValues;
		this.geneNames = geneNames;
		this.barcodes = barcodes;
		this.geneLookup = geneLookup;

		this.annotations = new HashMap<>();
		this.geneAnnotations = new HashMap<>();

		this.realInterval = STDataUtils.computeRealInterval( this );
	}

	@Override
	public Map<String, RandomAccessibleInterval<? extends NativeType< ? >>> getAnnotations()
	{
		return annotations;
	}
	
	@Override
	public Map<String, RandomAccessibleInterval<? extends NativeType< ? >>> getGeneAnnotations()
	{
		return geneAnnotations;
	}

	@Override
	protected RealInterval getLocationRealInterval()
	{
		return realInterval;
	}

	@Override
	public void setLocations( final List< double[] > locations )
	{
		STDataText.setLocations( locations, this.locations );
		this.realInterval = STDataUtils.computeRealInterval( this );
	}

	@Override
	public int getIndexForGene( final String gene )
	{
		return geneLookup.get( gene );
	}

	@Override
	public List< String > getGeneNames() { return geneNames; }

	@Override
	public List< String > getBarcodes() { return barcodes; }

	@Override
	public RandomAccessibleInterval< DoubleType > getAllExprValues()
	{
		return exprValues;
	}

	@Override
	public RandomAccessibleInterval< DoubleType > getLocations()
	{
		return locations;
	}
}
