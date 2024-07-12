package analyze;

import net.imglib2.IterableRealInterval;
import net.imglib2.type.numeric.real.DoubleType;
import util.CompensatedSum;

import java.util.function.Function;

/**
 * Simple enum to define the different methods to compute entropy for the {@link cmd.AddEntropy} command.
 */
public enum Entropy {
	/**
	 * Standard deviation of intensity values.
	 */
	STDEV(expressionValues -> {
		final CompensatedSum sum = new CompensatedSum();
		expressionValues.stream().mapToDouble(DoubleType::get).forEach(sum::add);
		final double avg = sum.getSum() / expressionValues.size();

		final CompensatedSum totalDev = new CompensatedSum();
		expressionValues.stream().mapToDouble(t -> t.get() - avg).forEach(t -> totalDev.add(t * t));
		return Math.sqrt(totalDev.getSum() / expressionValues.size());
	}),

	/**
	 * Average intensity value.
	 */
	AVG(expressionValues -> {
		final CompensatedSum sum = new CompensatedSum();
		expressionValues.stream().mapToDouble(DoubleType::get).forEach(sum::add);
		return sum.getSum() / expressionValues.size();
	});


	private final Function<IterableRealInterval<DoubleType>, Double> computeFunction;

	Entropy(Function<IterableRealInterval<DoubleType>, Double> computeFunction) {
		this.computeFunction = computeFunction;
	}

	/**
	 * @return the label usually used to describe the method (e.g. as a dataset in a container)
	 */
	public String label() {
		return toString().toLowerCase();
	}

	/**
	 * @param expressionValues the expression values to compute the entropy for
	 * @return the computed entropy
	 */
	public double computeFor(IterableRealInterval<DoubleType> expressionValues) {
		return computeFunction.apply(expressionValues);
	}

	/**
	 * @param label the label to search for
	 * @return the {@link Entropy} method corresponding to the label
	 */
	public static Entropy fromLabel(String label) {
		for (Entropy e : Entropy.values()) {
			if (e.label().equals(label)) {
				return e;
			}
		}
		return null;
	}
}
