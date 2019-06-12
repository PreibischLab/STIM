package data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * A minimal version of STData for import/export
 * 
 * @author spreibi
 *
 */
public class STData_Minimal
{
	public List< double[] > coordinates;
	public HashMap< String, double[] > genes;

	final int n;

	public STData_Minimal( final List< double[] > coordinates, final HashMap< String, double[] > genes )
	{
		this.coordinates = coordinates;
		this.genes = genes;

		this.n = coordinates.get( 0 ).length;
	}

	public long numCoordinates()
	{
		return coordinates.size();
	}

	public long numGenes()
	{
		return genes.keySet().size();
	}

	public ArrayList< String > getGeneList()
	{
		final ArrayList< String > geneList = new ArrayList<>( genes.keySet() );
		Collections.sort( geneList );

		return geneList;
	}

	/**
	 * @return an Img with (numCoordinates x numDimensions) containing all coordinates
	 */
	public Img< DoubleType > getCoordinates()
	{
		final Img< DoubleType > img = ArrayImgs.doubles( new long[] { numCoordinates(), n } );
		final RandomAccess< DoubleType > ra = img.randomAccess();

		for ( int i = 0; i < numCoordinates(); ++i )
		{
			ra.setPosition( i, 0 );
			ra.setPosition( 0, 1 );

			final double[] coord = coordinates.get( i );

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
	 * @return an Img with (numGenes x numCoordinates) containing for each genes all values of each coordinate
	 */
	public Img< DoubleType > getValues()
	{
		final long g = numGenes();
		final long c = numCoordinates();

		final ArrayList< String > geneList = getGeneList();

		final Img< DoubleType > img = new CellImgFactory< DoubleType >( new DoubleType(), 128 ).create( new long[] { g, c } );
		final RandomAccess< DoubleType > ra = img.randomAccess();

		for ( int i = 0; i < g; ++i )
		{
			ra.setPosition( i, 0 );
			ra.setPosition( 0, 1 );

			final double[] values = genes.get( geneList.get( i ) );

			for ( int j = 0; j < c; ++j )
			{
				ra.get().set( values[ j ] );

				if ( j != c - 1 )
					ra.fwd( 1 );
			}
		}

		return img;
	}

	public static STDataMinimal createTestDataSet()
	{
		final ArrayList< double[] > coordinates = new ArrayList<>();
		coordinates.add( new double[] { -1, 1 } );
		coordinates.add( new double[] { 2.1, 2 } );
		coordinates.add( new double[] { 17.1, -5.1 } );

		final HashMap< String, double[] > geneMap = new HashMap<>();

		geneMap.put( "gene1", new double[] { 1.1, 2.2, 13.1 } );
		geneMap.put( "gene2", new double[] { 14.1, 23.12, 1.1 } );
		geneMap.put( "gene3", new double[] { 4.1, 4.15, 7.65 } );
		geneMap.put( "gene4", new double[] { 0.1, 6.12, 5.12 } );

		return new STDataMinimal( coordinates, geneMap );
	}

}
