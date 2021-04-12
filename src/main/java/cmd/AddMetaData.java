package cmd;

import java.io.BufferedReader;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import data.STDataN5;
import io.N5IO;
import io.TextFileAccess;
import io.TextFileIO;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class AddMetaData implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-d", "--datasets"}, required = true, description = "comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22'")
	private String datasets = null;

	@Option(names = {"-m", "--metadata"}, required = true, description = "comma separated list of one or more metadata info, e.g. -m '/home/celltypes_180528_20.csv,/home/celltypes_180528_22.csv'")
	private String metadata = null;

	@Option(names = {"-l", "--label"}, required = true, description = "label for metadata, e.g. -l celltypes")
	private String label = null;

	@Override
	public Void call() throws Exception
	{
		final File n5Path = new File( input );
		final N5FSWriter n5 = N5IO.openN5write( n5Path );

		final List< String > inputDatasets = Arrays.asList( datasets.split( "," ) );

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets specified. stopping.");
			return null;
		}

		final List< String > metadataList = Arrays.asList( metadata.split( "," ) );

		if ( metadataList.size() == 0 )
		{
			System.out.println( "no metadata files specified. stopping.");
			return null;
		}

		if ( metadataList.size() != inputDatasets.size() )
		{
			System.out.println( "number of datasets does not match number of metadata files. stopping.");
			return null;
		}

		System.out.println( "adding metadata for " + inputDatasets.size() + " datasets: " );
		for ( int i = 0; i < inputDatasets.size(); ++i )
			System.out.println( "  " + inputDatasets.get( i ) + " <<< " + metadataList.get( i ) );

		for ( int i = 0; i < inputDatasets.size(); ++i )
		{
			final String datasetName = inputDatasets.get( i );

			System.out.println( "Processing " + inputDatasets.get( i ) );

			final STDataN5 data = N5IO.readN5( n5, inputDatasets.get( i ) );

			final File in = new File( metadataList.get( i ) );
			final BufferedReader readsIn;

			if ( !in.exists() ||
					in.getAbsolutePath().toLowerCase().endsWith( ".zip" ) ||
					in.getAbsolutePath().toLowerCase().endsWith( ".gz" ) ||
					in.getAbsolutePath().toLowerCase().endsWith( ".tar" ) )
				readsIn = Resave.openCompressedFile( in ); // try opening as compressed file
			else
				readsIn = TextFileAccess.openFileRead( in );

			final int[] ids;

			if ( data.getBarcodes().get( 0 ).equals( "" ) )
				ids = TextFileIO.readMetaData( readsIn, (int)data.numLocations() ); // no barcodes available
			else
				ids = TextFileIO.readMetaData( readsIn, data.getBarcodes() );

			readsIn.close();

			final Img<IntType> img = ArrayImgs.ints( ids, (int)data.numLocations() );

			data.getMetaData().add( new ValuePair<>( label, img ) );

			// write the attribute for all existing metadata objects
			n5.setAttribute(
					n5.groupPath( datasetName ),
					"metadataList",
					data.getMetaData().stream().map( p -> p.getA() ).collect( Collectors.toList() ) );

			final ExecutorService exec = Executors.newFixedThreadPool( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
			final String metaLocation = n5.groupPath( datasetName, "meta-" + label );
			N5Utils.save( (RandomAccessibleInterval)(Object)metadata, n5, metaLocation, new int[]{ N5IO.defaultBlockLength, 1 }, N5IO.defaultCompression, exec );
			exec.shutdown();
		}

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new AddMetaData(), args);
	}
}