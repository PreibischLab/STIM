package cmd;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import io.AnnDataIO;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
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

	@Option(names = {"-i", "--input"}, required = true, description = "list of csv input files as triple 'locations.csv,reads.csv,n5groupname' or optionally quadruple 'locations.csv,reads.csv,celltypes.csv,n5groupname' with celltype annotations (missing barcodes in celltypes will be excluded from the datasets), e.g. -i '$HOME/Puck_180528_20/BeadLocationsForR.csv,$HOME/Puck_180528_20/MappedDGEForR.csv,Puck_180528_20' -i ...")
	private List<String> inputPaths = null;

	@Option(names = {"-n", "--normalize"}, required = false, description = "log-normalize the input data before saving (default: false)")
	private boolean normalize = false;

	@Override
	public Void call() throws Exception {

		final File n5File = new File( containerPath );

		final N5FSWriter n5 = openOrCreate(n5File);

		// TODO: can this guard be triggered at all? (-i is required)
		if ( inputPaths == null || inputPaths.size() == 0 ) {
			System.out.println("No input paths defined: " + inputPaths + ". Stopping.");
			return null;
		}

		List<Boolean> hasCelltypeAnnotations = new ArrayList<Boolean>();

		int length = -1; // with or without celltypes?
		for ( final String inputPath : inputPaths )
		{
			String[] elements = inputPath.trim().split( "," );

			if ( elements.length < 2 || elements.length > 4 ) {
				System.out.println( "Input path could not parsed, it needs to be of the form: [data.h5ad,name] or [locations.csv,reads.csv,[celltypes.csv,]name]." );
				return null;
			}

			final String dataset = elements[elements.length-1];
			if (n5.exists(n5.groupPath(dataset))) {
				System.out.println( "dataset " + dataset + " already exists, stopping." );
				return null;
			}
			System.out.println("\nDataset='" + dataset + "'");

			STData data;
			if (elements.length == 2) { // means: input is an anndata file
				final File anndataFile = new File(elements[0].trim());
				System.out.println( "Locations='" + anndataFile.getAbsolutePath() + "'");
				data = AnnDataIO.readSlideSeq(anndataFile);
				hasCelltypeAnnotations.add(AnnDataIO.containsCelltypes(anndataFile));
			}
			else { // means: input consists of csv files (with optional file for celltypes)
				final File locationsFile = new File(elements[0].trim());
				final File readsFile = new File(elements[1].trim());
				final File celltypeFile = (elements.length == 3) ? null : new File(elements[2].trim());

				System.out.println("Locations='" + locationsFile.getAbsolutePath() + "'");
				System.out.println("Reads='" + readsFile.getAbsolutePath() + "'");

				BufferedReader locationsIn, readsIn, celltypeIn = null;
				try {
					locationsIn = openCsvInput(locationsFile, "locations");
					readsIn = openCsvInput(readsFile, "reads");
					if (celltypeFile != null) {
						System.out.println("Loading file '" + celltypeFile.getAbsolutePath() + "' as label 'celltype'");
						celltypeIn = openCsvInput(celltypeFile, "cell type");
					}
				} catch (IOException e) {
					System.out.println(e.getMessage());
					return null;
				}

				if (celltypeIn == null) {
					data = TextFileIO.readSlideSeq(locationsIn, readsIn);
					hasCelltypeAnnotations.add(Boolean.FALSE);
				} else {
					data = TextFileIO.readSlideSeq(locationsIn, readsIn, celltypeIn);
					hasCelltypeAnnotations.add(Boolean.TRUE);
				}
			}

			if ( normalize ) {
				System.out.println( "Normalizing input ... " );
				data =  new NormalizingSTData( data );
			}

			N5IO.writeN5( n5, dataset, data );
		}

		Boolean celltypeAnnotationsAreConsistent = hasCelltypeAnnotations.stream().allMatch(hasCelltypeAnnotations.get(0)::equals);
		if (!celltypeAnnotationsAreConsistent)
			System.out.println( "WARNING: input path length not consistent. Some datasets with, others without cell type annotations!");
		System.out.println( "Done." );

		return null;
	}

	private static BufferedReader openCsvInput(File file, String contentDescriptor) throws IOException, ArchiveException {
		final BufferedReader reader;

		if (!file.exists()
				|| file.getAbsolutePath().toLowerCase().endsWith(".zip")
				|| file.getAbsolutePath().toLowerCase().endsWith(".gz")
				|| file.getAbsolutePath().toLowerCase().endsWith(".tar"))
			reader = openCompressedFile(file);
		else
			reader = TextFileAccess.openFileRead(file);

		if (reader == null) {
			throw new IOException(contentDescriptor + " file does not exist and cannot be read from compressed file, stopping.");
		}

		return reader;
	}

	private static N5FSWriter openOrCreate(File n5File) throws IOException {
		if (n5File.exists()) {
			System.out.println( "N5 path '" + n5File.getAbsolutePath() + "' exists, opening N5 container..." );
			return N5IO.openN5write(n5File);
		}
		else {
			System.out.println( "N5 path '" + n5File.getAbsolutePath() + "' does not exist, creating new N5 container..." );
			return N5IO.createN5(n5File);
		}
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
		else if ( path.contains( ".gz" ) )
		{
			index = path.indexOf( ".gz" );
			length = 3;
		}

		if ( index >= 0 )
		{
			String compressedFile = path.substring( 0, index + length );
			String pathInCompressed;

			if ( index + length >= path.length() )
				pathInCompressed = null; // no path inside the archive specified, open first file that comes along
			else
				pathInCompressed = path.substring( index + length + 1, path.length() );

			try
			{
				@SuppressWarnings("resource")
				ZipFile zipFile = new ZipFile( compressedFile );
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				String baseDir = null;

				while(entries.hasMoreElements())
				{
					ZipEntry entry = entries.nextElement();

					if ( pathInCompressed == null )
						return new BufferedReader( new InputStreamReader( zipFile.getInputStream( entry ), "UTF-8") );

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

				if ( pathInCompressed == null )
					return new BufferedReader( new InputStreamReader( tin, "UTF-8") );

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

				if ( pathInCompressed == null )
					return new BufferedReader( new InputStreamReader( tin, "UTF-8") );

				while (entry != null)
				{
					if ( entry.getName().equals( baseDir + pathInCompressed ) || entry.getName().equals( pathInCompressed ) )
						return new BufferedReader( new InputStreamReader( tin, "UTF-8") );

					entry = tin.getNextTarEntry();
				}

				tin.close();
			} catch ( Exception e ) { /* not a tar file */ }

			try
			{
				final File input = new File(compressedFile);
				final InputStream is = new FileInputStream(input);
				final CompressorInputStream gzip = new CompressorStreamFactory().createCompressorInputStream(new BufferedInputStream(is));
				//final GzipCompressorInputStream gzip = new GzipCompressorInputStream( is, true );

				//final GzipParameters metaData = gzip.getMetaData();
				//System.out.println( metaData.getFilename() );

				return new BufferedReader( new InputStreamReader( gzip, "UTF-8") );
			} catch ( Exception e ) { /* not a gzipped file*/ }

			System.out.println( "ERROR: File '" + compressedFile + "' could not be read as archive." );

			return null;
		}

		return null;
	}

	public static final void main(final String... args) throws ZipException, IOException, ArchiveException {
		CommandLine.call(new Resave(), args);
	}
}
