package cmd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import data.NormalizingSTData;
import data.STData;
import io.N5IO;
import io.TextFileAccess;
import io.TextFileIO;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class Resave implements Callable<Void> {

	@Option(names = {"-o", "--container"}, required = true, description = "N5 output container path to which a new dataset will be added (N5 can exist or new one will be created), e.g. -o /home/ssq.n5 ...")
	private String containerPath = null;

	@Option(names = {"-i", "--input"}, required = true, description = "list of csv input files as triple [locations.csv,reads.csv,n5groupname], e.g. -i '$HOME/Puck_180528_20/BeadLocationsForR.csv,$HOME/Puck_180528_20/MappedDGEForR.csv,Puck_180528_20' -i ...")
	private List<String> inputPaths = null;

	@Option(names = {"-n", "--normalize"}, required = false, description = "log-normalize the input data before saving (default: false)")
	private boolean normalize = false;

	@Override
	public Void call() throws Exception {

		final File n5Path = new File( containerPath );
		final N5FSWriter n5;

		if ( n5Path.exists() )
		{
			System.out.println( "N5 path '" + n5Path.getAbsolutePath() + "' exists, opening N5 container..." );
			n5 = N5IO.openN5write( n5Path );
		}
		else
		{
			System.out.println( "N5 path '" + n5Path.getAbsolutePath() + "' does not exist, creating new N5 container..." );
			n5 = N5IO.createN5( n5Path );
		}

		if ( inputPaths != null && inputPaths.size() > 0 )
		{
			for ( final String inputPath : inputPaths )
			{
				String[] elements = inputPath.trim().split( "," );
	
				if ( elements.length != 3 )
				{
					System.out.println( "input path could not parsed " + inputPath + ", stopping." );
					return null;
				}
				else
				{
					final File locationsFile = new File( elements[ 0 ].trim() );
					final File readsFile = new File( elements[ 1 ].trim() );
					final String dataset = elements[ 2 ];

					System.out.println( "\nDataset='" + dataset + "'");
					System.out.println( "Locations='" + locationsFile.getAbsolutePath() + "'");
					System.out.println( "Reads='" +readsFile.getAbsolutePath() + "'" );

					if ( n5.exists( n5.groupPath( dataset ) ) )
					{
						System.out.println( "dataset already exists, stopping." );
						return null;
					}

					final BufferedReader locationsIn;

					if ( !locationsFile.exists() )
						locationsIn = openCompressedFile( locationsFile ); // try opening as compressed file
					else
						locationsIn = TextFileAccess.openFileRead( locationsFile );

					if ( locationsIn == null )
					{
						System.out.println( "locations file does not exist and cannot be read from compressed file, stopping." );
						return null;
					}

					final BufferedReader readsIn;

					if ( !readsFile.exists() )
						readsIn = openCompressedFile( readsFile ); // try opening as compressed file
					else
						readsIn = TextFileAccess.openFileRead( readsFile );

					if ( readsIn == null )
					{
						System.out.println( "reads file does not exist and cannot be read from compressed file, stopping." );
						return null;
					}

					STData data = TextFileIO.readSlideSeq(
							locationsIn,
							readsIn );

					if ( normalize )
					{
						System.out.println( "Normalizing input ... " );
						data =  new NormalizingSTData( data );
					}

					N5IO.writeN5( n5, dataset, data );
				}
			}
		}

		System.out.println( "Done." );

		return null;
	}

	public static BufferedReader openCompressedFile( final File file ) throws IOException, ArchiveException
	{
		String path = file.getAbsolutePath();

		int index = -1;
		int length = -1;

		if ( path.contains( ".zip" ) )
		{
			index = path.indexOf( ".zip" );
			length = 4;
		}
		else if ( path.contains( ".tar.gz" ) )
		{
			index = path.indexOf( ".tar.gz" );
			length = 7;
		}
		else if ( path.contains( ".tar" ) )
		{
			index = path.indexOf( ".tar" );
			length = 4;
		}

		if ( index >= 0 )
		{
			String compressedFile = path.substring( 0, index + length );
			String pathInCompressed = path.substring( index + length + 1, path.length() );

			//System.out.println( compressedFile );
			//System.out.println( pathInCompressed );

			try
			{
				ZipFile zipFile = new ZipFile( compressedFile );
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				String baseDir = null;

				while(entries.hasMoreElements())
				{
					ZipEntry entry = entries.nextElement();
					if ( baseDir == null )
						baseDir = entry.getName();

					if ( entry.getName().equals( baseDir + pathInCompressed ) || entry.getName().equals( pathInCompressed ) )
						return new BufferedReader( new InputStreamReader( zipFile.getInputStream( entry ), "UTF-8") );
				}

				zipFile.close();
			} catch ( Exception e ) { /* not a zip file */ }

			try
			{
				final File input = new File(compressedFile);
				final InputStream is = new FileInputStream(input);
				final CompressorInputStream in = new GzipCompressorInputStream(is, true);
				final TarArchiveInputStream tin = new TarArchiveInputStream(in);
	
				TarArchiveEntry entry = tin.getNextTarEntry();
				String baseDir = entry.getName();

				while (entry != null)
				{
					if ( entry.getName().equals( baseDir + pathInCompressed ) || entry.getName().equals( pathInCompressed ) )
						return new BufferedReader( new InputStreamReader( tin, "UTF-8") );

					entry = tin.getNextTarEntry();
				}

				tin.close();
			} catch ( Exception e ) { /* not a gzipped tar file*/ }

			try
			{
				final File input = new File(compressedFile);
				final InputStream is = new FileInputStream(input);
				final TarArchiveInputStream tin = new TarArchiveInputStream(is);
	
				TarArchiveEntry entry = tin.getNextTarEntry();
				String baseDir = entry.getName();

				while (entry != null)
				{
					if ( entry.getName().equals( baseDir + pathInCompressed ) || entry.getName().equals( pathInCompressed ) )
						return new BufferedReader( new InputStreamReader( tin, "UTF-8") );

					entry = tin.getNextTarEntry();
				}

				tin.close();
			} catch ( Exception e ) { /* not a tar file */ }

			return null;
		}

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new Resave(), args);
	}
}
