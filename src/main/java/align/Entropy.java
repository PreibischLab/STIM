package align;

/**
 * Simple enum to define the different methods to compute entropy for the {@link cmd.AddEntropy} command.
 */
public enum Entropy {
	STDEV("stdev");

	private final String methodLabel;

	Entropy(String methodLabel) {
		this.methodLabel = methodLabel;
	}

	public String label() {
		return methodLabel;
	}

	public static Entropy fromLabel(String label) {
		for (Entropy e : Entropy.values()) {
			if (e.label().equals(label)) {
				return e;
			}
		}
		return null;
	}
}
