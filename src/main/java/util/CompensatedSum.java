package util;

/*
 * This is an implementation of Neumaier's improvement of the Kahan summation algorithm.
 * This is intended as a drop-in replacement for RealSum and should be removed once
 * RealSum is updated in imglib2.
 */
public class CompensatedSum {
	private double sum = 0.0;
	private double compensation = 0.0;

	public void add(double value) {
		double t = sum + value;
		if (Math.abs(sum) >= Math.abs(value)) {
			compensation += (sum - t) + value;
		} else {
			compensation += (value - t) + sum;
		}
		sum = t;
	}

	public double getSum() {
		return sum + compensation;
	}
}
