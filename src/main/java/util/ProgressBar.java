package util;
import java.util.function.Consumer;

public class ProgressBar implements Consumer<Double> {
    private final double total;
    private double progress;

    // Constructor to initialize the total value
    public ProgressBar(double total) {
        this.total = total;
        this.progress = 0;
    }

    // Method to update the progress bar (for example purposes)
    public void updateProgress(double progress) {
        this.progress += progress;
        printProgress();
    }

    // Implement the accept method from Consumer<Double> interface
    @Override
    public void accept(Double progress) {
        updateProgress(progress);
    }

    // Method to print the progress bar (example implementation)
    private void printProgress() {
        int width = 50; // Progress bar width
        int completed = (int) (width * (progress / total));
        int remaining = width - completed;
        if (remaining < 0) {
            remaining = 0;
            completed = width;
        }
        String bar = "[" + String.format("%" + completed + "s", "").replace(' ', '=') + String.format("%" + remaining + "s", "") + "]";
        System.out.printf("\r%s %.2f%%", bar, (progress / total) * 100);
        if (progress >= total) {
            System.out.println();
        }
    }

    // Example main method to demonstrate usage
    public static void main(String[] args) {
        ProgressBar progressBar = new ProgressBar(100);
        for (int i = 0; i <= 100; i++) {
            progressBar.accept((double) i);
            try {
                Thread.sleep(100); // Simulate work
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}