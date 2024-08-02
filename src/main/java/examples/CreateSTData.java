package examples;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.RandomStringUtils;

import analyze.Entropy;
import analyze.ExtractGeneLists;
import cmd.InteractiveAlignment;
import data.STData;
import data.STDataImgLib2;
import data.STDataImgLib2.STDataImgLib2Factory;
import gui.STDataAssembly;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import util.Threads;

public class CreateSTData
{
	/**
	 * Creates a new STData object
	 *
	 * @param rnd - random number generator for locations and expression values
	 *
	 * @return a new STData object
	 */
	public static STDataImgLib2 createRandomSTDATA( final Random rnd )
	{
		return createRandomSTDATA( rnd, null );
	}

	/**
	 * Creates a new STData object and re-uses gene-names & barcodes from an existing dataset (no checks on sizes!)
	 *
	 * @param rnd - random number generator for locations and expression values
	 * @param existing - an existing dataset (can be null)
	 *
	 * @return a new STData object
	 */
	public static STDataImgLib2 createRandomSTDATA( final Random rnd, final STData existing )
	{
		final int n = 2; // num Dimensions
		final int numLocations = 1000; // number of locations
		final int numGenes = 10; // number of genes

		final STDataImgLib2Factory factory = new STDataImgLib2Factory();

		//
		// locations
		//

		// init locations datastructure
		factory.locations = ArrayImgs.doubles( numLocations, n );

		// fill with random locations
		// first collapse the 2D array into a 1D array with variables that have two values each (x,y), then iterate all locations
		Views.iterable( Views.collapse( factory.locations ) ).forEach( values -> {
			values.get( 0 ).set( rnd.nextDouble() * 400 ); // x [0,400]
			values.get( 1 ).set( rnd.nextDouble() * 400 ); // y [0,400]
			} );

		//
		// expression values
		//

		// init gene expression datastructure
		factory.exprValues = new CellImgFactory<>( new DoubleType() ).create(numGenes, numLocations);

		// fill with random expression values
		Views.iterable( factory.exprValues ).forEach( value -> value.set( 2.0 + Math.exp( rnd.nextDouble() * 10 ) )); // expr [1,10]

		if ( existing == null )
		{
			// gene names
			factory.geneNames = new ArrayList<>();
			for ( int i = 0; i < numGenes; ++i )
				factory.geneNames.add( RandomStringUtils.randomAlphabetic( 6 ) );
	
			// mapping gene name to index in exprValues
			factory.geneLookup = new HashMap<>();
			for ( int i = 0; i < factory.geneNames.size(); ++i )
				factory.geneLookup.put( factory.geneNames.get( i ), i );
	
			// barcodes
			factory.barcodes = new ArrayList<>();
			for ( int i = 0; i < numLocations; ++i )
				factory.barcodes.add( RandomStringUtils.randomAlphabetic( 12 ) );
		}
		else
		{
			// gene names
			factory.geneNames = existing.getGeneNames();
	
			// mapping gene name to index in exprValues
			factory.geneLookup = new HashMap<>();
			for ( int i = 0; i < factory.geneNames.size(); ++i )
				factory.geneLookup.put( factory.geneNames.get( i ), i );
	
			// barcodes
			factory.barcodes = existing.getBarcodes();
		}

		return new STDataImgLib2( factory );
	}

	public static void main( String[] args ) throws IOException
	{
		// a pseudo-random number generator
		final Random rnd = new Random( 124 );

		// create random STData object
		final STData data1 = createRandomSTDATA( rnd );
		final STData data2 = createRandomSTDATA( rnd, data1 );

		// save
		final ExecutorService service = Executors.newFixedThreadPool(8);
		final String tmpDir = System.getProperty("java.io.tmpdir") + "random-dataset_" + System.currentTimeMillis() + ".n5";

		System.out.println( "saving to: " + tmpDir );

		final SpatialDataContainer container = SpatialDataContainer.createNew(tmpDir, service);
		SpatialDataIO sdout = SpatialDataIO.open( tmpDir + "/data1.n5", service);
		sdout.writeData(new STDataAssembly(data1));
		container.addExistingDataset( tmpDir + "/data1.n5" );

		sdout = SpatialDataIO.open( tmpDir + "/data2.n5", service);
		sdout.writeData(new STDataAssembly(data2));
		container.addExistingDataset( tmpDir + "/data2.n5" );

		System.out.println( "Written successfully.");

		// display in GUI
		InteractiveAlignment.main(
				"-c", tmpDir,
				"-d1", "data1.n5",
				"-d2", "data2.n5",
				"-n", "3",
				"-sk", "0" );
	}
}
