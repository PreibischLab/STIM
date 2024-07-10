package util;
import java.util.function.Consumer;

/**
 * Class to display a simple ASCII progress bar in the console.
 */
public class ProgressBar implements Consumer<Double> {
    // width of content of progress bar in characters
    private static final int WIDTH = 50;

    private final double total;
    private double progress;
    private boolean isTerminated;

    public ProgressBar(double total) {
        this.total = total;
        this.progress = 0;
        this.isTerminated = false;
    }

    /**
     * Update the progress by a certain amount. This is synchronized to ensure that updates and prints are atomic.
     * If the progress is greater than the total, the progress is set to the 100% and the printing terminates.
     *
     * @param update the amount to update the progress by
     */
    public synchronized void updateProgress(double update) {
        if (!isTerminated) {
            this.progress += update;
            printProgress();
        }
    }

    @Override
    public void accept(Double update) {
        updateProgress(update);
    }

    private void printProgress() {
        final int completed = Math.min((int) (WIDTH * (progress / total)), WIDTH);
        final int remaining = WIDTH - completed;

        final StringBuilder bar = new StringBuilder(WIDTH + 2);
        bar.append('[');
        for (int i = 0; i < completed; i++) {
            bar.append('=');
        }
        for (int i = 0; i < remaining; i++) {
            bar.append(' ');
        }
        bar.append(']');

        System.out.printf("\r%s %.2f%%", bar, Math.min((progress / total), 1) * 100);

        if (progress >= total) {
            System.out.println();
            isTerminated = true;
        }
    }
}