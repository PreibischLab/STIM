package cmd;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import gui.STDataAssembly;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import data.NormalizingSTData;
import data.STData;
import io.TextFileAccess;
import io.TextFileIO;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

@Command(name = "st-resave", mixinStandardHelpOptions = true, version = "0.2.0", description = "Spatial Transcriptomics as IMages project - resave a slice-dataset to N5/AnnData")
public class Resave implements Callable<Void> {

	@Option(names = {"-c", "--container"}, required = false, description = "N5 output container path to which a new dataset will be added (N5 can exist or new one will be created), e.g. -o /home/ssq.n5. If omitted, a single slice-dataset will be stored in the current path.")
	private String containerPath = null;

	@Option(names = {"-i", "--input"}, required = true, description = "list of csv input files as triple 'locations.csv,reads.csv,datasetName' or optionally quadruple 'locations.csv,reads.csv,celltypes.csv,datasetName' with celltype annotations , e.g. -i '$HOME/Puck_180528_20/BeadLocationsForR.csv,$HOME/Puck_180528_20/MappedDGEForR.csv,Puck_180528_20'")
	private String inputPaths = null;

	@Option(names = {"-a", "--annotation"}, required = false, description = "location of csv file that contains annotations of locations, e.g., cell types (missing barcodes in annotations will be excluded from the datasets)")
	private List<String> annotations = new ArrayList<>();

	@Option(names = {"-n", "--normalize"}, required = false, description = "log-normalize the input data before saving (default: false)")
	private boolean normalize = false;

	@Override
	public Void call() throws Exception {
		if (inputPaths == null) {
			System.out.println("No input paths defined: " + inputPaths + ". Stopping.");
			return null;
		}

		String[] elements = inputPaths.trim().split( "," );
		final File outputFile = new File(elements[elements.length-1].trim());

		if (elements.length != 3) {
			System.out.println("Input path could not parsed, it needs to be of the form [locations.csv,reads.csv,name].");
			return null;
		}
		if (outputFile.exists()) {
			System.out.println("File " + outputFile.getAbsolutePath() + " already exists, stopping." );
			return null;
		}

		final File locationsFile = new File(elements[0].trim());
		final File readsFile = new File(elements[1].trim());

		System.out.println("Locations='" + locationsFile.getAbsolutePath() + "'");
		System.out.println("Reads='" + readsFile.getAbsolutePath() + "'");

		BufferedReader locationsIn, readsIn = null;
		Map<String, BufferedReader> annotationsInMap = new HashMap<>();
		try {
			locationsIn = openCsvInput(locationsFile, "locations");
			readsIn = openCsvInput(readsFile, "reads");
			for (String annotationPath : annotations) {
				final File annotationFile = new File(annotationPath.trim());
				final String annotationLabel = Paths.get(annotationFile.getAbsolutePath()).getFileName().toString().split("\\.")[0];
				System.out.println("Loading annotation file '" + annotationPath + "' as label '" + annotationLabel + "'.");
				annotationsInMap.put(annotationLabel, openCsvInput(annotationFile, annotationLabel));
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return null;
		}

		STData data;
		if (annotations.isEmpty())
			data = TextFileIO.readSlideSeq(locationsIn, readsIn);
		else
			data = TextFileIO.readSlideSeq(locationsIn, readsIn, annotationsInMap);

		if (normalize) {
			System.out.println("Normalizing input ... ");
			data =  new NormalizingSTData(data);
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		SpatialDataIO sdio = SpatialDataIO.inferFromName(outputFile.getAbsolutePath(), service);
		System.out.println("\nSaving in file='" + outputFile.getPath() + "'");
		sdio.writeData(new STDataAssembly(data));

		if (containerPath != null) {
			final File n5File = new File(containerPath);
			SpatialDataContainer container;
			if (n5File.exists())
				container = SpatialDataContainer.openExisting(containerPath, service);
			else
				container = SpatialDataContainer.createNew(containerPath, service);

			System.out.println("\nMoving file to '" + containerPath + "'");
			container.addExistingDataset(outputFile.getAbsolutePath());
		}

		System.out.println("Done.");
		service.shutdown();
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
