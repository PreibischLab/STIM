package data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * A text-input based implementation that can be used to convert to e.g. N5.
 * 
 * Computes the ImgLib2 datastructures on demand from a list of locations and a HashMap of genename to expression values
 * 
 * 
 * @author spreibi
 *
 */
public class STDataText extends STDataAbstract
{
	private final Img< DoubleType > locations, exprValues;

	private final ArrayList< String > geneNames;
	private final HashMap< String, Integer > geneLookup;

	private RealInterval realInterval;

	public STDataText( final List< double[] > locations, final HashMap< String, double[] > exprValues )
	{
		super( locations.get( 0 ).length, locations.size(), exprValues.keySet().size() );

		this.geneNames = new ArrayList<>( exprValues.keySet() );
		Collections.sort( this.geneNames );

		this.geneLookup = new HashMap<>();
		for ( int i = 0; i < geneNames.size(); ++i )
			this.geneLookup.put( this.geneNames.get( i ), i );

		this.locations = locationsToImgLib2( locations );
		this.exprValues = exprValuesToImgLib2( this.geneNames, exprValues );

		this.realInterval = STDataStatistics.computeRealInterval( this );
	}

	@Override
	protected RealInterval getLocationRealInterval()
	{
		return realInterval;
	}

	@Override
	protected int getIndexForGene( final String gene )
	{
		return geneLookup.get( gene );
	}

	@Override
	public ArrayList< String > getGeneNames() { return geneNames; }

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

	/**
	 * @param locations - the list of sequenced locations
	 * @return - a 2d datastructure that holds all sequenced locations, size: [numLocations x numDimensions]
	 */
	public static Img< DoubleType > locationsToImgLib2( final List< double[] > locations )
	{
		final int n = locations.get( 0 ).length;
		final int numLocations = locations.size();

		final Img< DoubleType > img = ArrayImgs.doubles( new long[] { numLocations, n } );
		// TODO: use cursor
		final RandomAccess< DoubleType > ra = img.randomAccess();

		for ( int i = 0; i < numLocations; ++i )
		{
			ra.setPosition( i, 0 );
			ra.setPosition( 0, 1 );

			final double[] coord = locations.get( i );

			for ( int d = 0; d < n; ++d )
			{
				ra.get().set( coord[ d ] );

				if ( d != n - 1 )
					ra.fwd( 1 );
			}
		}

		return img;
	}

	/**
	 * @param geneList - the order of the genes defines the order in the imglib2 img
	 * @param exprValues - a map that links genenames to their values
	 *
	 * @return a 2d datastructure that holds all expression values, size: [numGenes x numLocations]
	 */
	public static Img< DoubleType > exprValuesToImgLib2( final List< String > geneList, final Map< String, double[] > exprValues )
	{
		final long numGenes = geneList.size();
		final long numLocations = exprValues.values().iterator().next().length;

		final Img< DoubleType > img = new CellImgFactory< DoubleType >( new DoubleType(), 128 ).create( new long[] { numGenes, numLocations } );
		// TODO: use cursor
		final RandomAccess< DoubleType > ra = img.randomAccess();

		for ( int i = 0; i < numGenes; ++i )
		{
			ra.setPosition( i, 0 );
			ra.setPosition( 0, 1 );

			final double[] values = exprValues.get( geneList.get( i ) );

			for ( int j = 0; j < numLocations; ++j )
			{
				ra.get().set( values[ j ] );

				if ( j != numLocations - 1 )
					ra.fwd( 1 );
			}
		}

		return img;
	}
}
