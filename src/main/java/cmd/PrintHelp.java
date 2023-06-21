package cmd;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

public class PrintHelp implements Callable<Void> {

	@Option(names = {"-v", "--version"}, required = false, description = "print version information")
	private boolean version = false;

	@Override
	public Void call() throws Exception {

		if (version) {
			System.out.println("Spatial Transcriptomics as IMages project -- v0.2.0");
			return null;
		}

		final Map<String, Callable<Void>> commands = new HashMap<>();
		commands.put("st-explorer", new View());
		commands.put("st-render", new RenderImage());
		commands.put("st-bdv-view", new DisplayStackedSlides());
		commands.put("st-resave", new Resave());
		commands.put("st-add-slice", new AddDataset());
		commands.put("st-normalize", new Normalize());
		commands.put("st-add-annotations", new AddAnnotations());
		commands.put("st-align-pairs", new PairwiseSectionAligner());
		commands.put("st-align-pairs-view", new ViewPairwiseAlignment());
		commands.put("st-align-global", new GlobalOpt());

		for (final Entry<String, Callable<Void>> command : commands.entrySet()) {
			System.out.println();
			System.out.println("Usage for " + command.getKey() + ":");
			CommandLine.usage(command.getValue(), System.out);
		}

		return null;
	}

	public static void main(final String[] args) {
		CommandLine.call(new PrintHelp(), args);
	}
}
