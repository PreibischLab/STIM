package data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;

/**
 * A text-input based implementation that can be used to convert to e.g. N5.
 * <p>
 * Computes the ImgLib2 datastructures on demand from a list of locations and a HashMap of geneName to expression values
 * 
 * 
 * @author spreibi
 *
 */
public class STDataText extends STDataImgLib2
{
	public STDataText( final List< Pair< double[], String > > locations, final HashMap< String, double[] > exprValues )
	{
		super( create( locations, exprValues ) );
	}

	protected static STDataImgLib2Factory create( final List< Pair< double[], String > > locations, final HashMap< String, double[] > exprValues )
	{
		final STDataImgLib2Factory factory = new STDataImgLib2Factory();

		factory.geneNames = new ArrayList<>( exprValues.keySet() );
		Collections.sort( factory.geneNames );

		factory.geneLookup = new HashMap<>();
		for ( int i = 0; i < factory.geneNames.size(); ++i )
			factory.geneLookup.put( factory.geneNames.get( i ), i );

		factory.barcodes = locations.stream().map(Pair::getB).collect( Collectors.toList() );
		factory.locations = locationsToImgLib2( locations );
		factory.exprValues = exprValuesToImgLib2( factory.geneNames, exprValues );

		return factory;
	}

	/**
	 * @param locations - the list of sequenced locations
	 * @return - a 2d datastructure that holds all sequenced locations, size: [numLocations x numDimensions]
	 */
	public static Img< DoubleType > locationsToImgLib2( final List< Pair< double[], String > > locations )
	{
		final int n = locations.get( 0 ).getA().length;
		final int numLocations = locations.size();

		final Img< DoubleType > img = ArrayImgs.doubles(numLocations, n);

		setLocations( locations.stream().map(Pair::getA).collect( Collectors.toList() ), img );

		return img;
	}

	public static void setLocations( final List< double[] > locations, final RandomAccessibleInterval< DoubleType > img )
	{
		final int numLocations = (int)img.dimension( 0 );
		final int n = (int)img.dimension( 1 );

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
	}

	/**
	 * @param geneList - the order of the genes defines the order in the imglib2 img
	 * @param exprValues - a map that links geneNames to their values
	 *
	 * @return a 2d datastructure that holds all expression values, size: [numGenes x numLocations]
	 */
	public static Img< DoubleType > exprValuesToImgLib2( final List< String > geneList, final Map< String, double[] > exprValues )
	{
		final long numGenes = geneList.size();
		final long numLocations = exprValues.values().iterator().next().length;

		final Img< DoubleType > img = new CellImgFactory<>( new DoubleType(), 128 ).create(numGenes, numLocations);
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
