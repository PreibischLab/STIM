package cmd;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "st-help", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - command line interface help")
public class PrintHelp implements Callable<Void> {

	@Override
	public Void call() throws Exception {

		final List<Callable<Void>> commands = Arrays.asList(
				new View(),
				new RenderImage(),
				new BigDataViewerStackDisplay(),
				new Resave(),
				new AddSlice(),
				new Normalize(),
				new AddAnnotations(),
				new PairwiseSectionAligner(),
				new ViewPairwiseAlignment(),
				new GlobalOpt()
		);

		Iterator<Callable<Void>> it = commands.iterator();
		System.out.println();
		while (it.hasNext()) {
			CommandLine.usage(it.next(), System.out);
			System.out.println();
			if (it.hasNext()) {
				System.out.println("------------------------------------------------------------------------");
				System.out.println();
			}
		}

		return null;
	}

	public static void main(final String[] args) {
		final CommandLine cmd = new CommandLine(new PrintHelp());
		cmd.execute(args);
	}
}
