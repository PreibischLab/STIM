package cmd;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import gui.STDataAssembly;
import io.SpatialDataIO;
import io.TextFileAccess;
import io.TextFileIO;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.IntType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "st-add-annotations", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - add annotations to slice-dataset")
public class AddAnnotations implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input dataset, e.g. -i /home/ssq.n5/Puck_180528_20")
	private String inputPath = null;

	@Option(names = {"-a", "--annotation"}, required = true, description = "comma separated list of one or more annotation info, e.g. -m '/home/celltypes_180528_20.csv,/home/clustering_180528_22.csv'")
	private String annotations = null;

	@Option(names = {"-l", "--label"}, required = false, description = "comma separated list of one or more labels for annotation, e.g. -l celltypes,clustering; default: filenames of annotation files")
	private String labels = null;

	@Override
	public Void call() throws Exception {
		if (inputPath == null) {
			System.out.println("No input path defined. Stopping.");
			return null;
		}

		final List<String> annotationList = Arrays.stream(annotations.split(",")).map(String::trim).collect(Collectors.toList());
		final List<String> labelList;
		if (labels == null) {
			System.out.println("No labels given; using filenames as labels.");
			labelList = annotationList.stream()
					.map(s -> Paths.get(new File(s).getAbsolutePath()).getFileName().toString().split("\\.")[0])
					.collect(Collectors.toList());
		}
		else
			labelList = Arrays.stream(labels.split(",")).map(String::trim).collect(Collectors.toList());

		if (annotationList.size() == 0) {
			System.out.println( "no annotation files specified. stopping.");
			return null;
		}

		if (annotationList.size() != labelList.size()) {
			System.out.println("number of annotation files (" + annotationList.size() + ") does not match number of labels (" + labelList.size() + "). stopping.");
			return null;
		}

		System.out.println("adding annotations to " + inputPath);
		final ExecutorService service = Executors.newFixedThreadPool(8);
		final SpatialDataIO sdio = SpatialDataIO.open(inputPath, service);
		final STDataAssembly stData = sdio.readData();

		for (int i = 0; i < annotationList.size(); ++i) {

			final String annotationName = annotationList.get(i);
			final String label = labelList.get(i);
			System.out.println("\n>>> Processing " + annotationName);

			final File in = new File(annotationName);
			final BufferedReader readsIn;
			if ( !in.exists() ||
					in.getAbsolutePath().toLowerCase().endsWith( ".zip" ) ||
					in.getAbsolutePath().toLowerCase().endsWith( ".gz" ) ||
					in.getAbsolutePath().toLowerCase().endsWith( ".tar" ) )
				readsIn = Resave.openCompressedFile( in ); // try opening as compressed file
			else
				readsIn = TextFileAccess.openFileRead( in );

			if (readsIn == null) {
				System.out.println( "Could not open file '" + in.getAbsolutePath() + "'. Stopping." );
				return null;
			}
			else {
				System.out.println("Loading file '" + in.getAbsolutePath() + "' as label '" + label + "'" );
			}

			final int[] ids;
			final boolean barcodesUnavailable = stData.data().getBarcodes().get(0).equals("");
			if (barcodesUnavailable)
				ids = TextFileIO.readAnnotations(readsIn, (int) stData.data().numLocations());
			else
				ids = TextFileIO.readAnnotations(readsIn, stData.data().getBarcodes());

			readsIn.close();

			final Img<IntType> img = ArrayImgs.ints(ids, (int) stData.data().numLocations());
			stData.data().getAnnotations().put(label, img);
		}

		sdio.updateStoredAnnotations(stData.data().getAnnotations());
		System.out.println( "Done." );

		service.shutdown();
		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new AddAnnotations(), args);
	}
}
