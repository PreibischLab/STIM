package cmd;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public class InteractiveAlignment implements Callable<Void> {

	@Option(names = {"-c", "--container"}, required = true, description = "input N5 container path, e.g. -i /home/ssq.n5.")
	private String containerPath = null;

	@Option(names = {"-d1", "--dataset1"}, required = false, description = "the first dataset (slice), e.g. -d1 'Puck_180528_20'")
	private String dataset1 = null;

	@Option(names = {"-d2", "--dataset2"}, required = false, description = "the second dataset (slice), e.g. -d2 'Puck_180528_22'")
	private String dataset2 = null;

	@Override
	public Void call() throws Exception
	{
		// the plan is to open two BDV windows next to each other,
		// render in parallel and overlay candidates and inliers

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new InteractiveAlignment(), args);
	}
}
