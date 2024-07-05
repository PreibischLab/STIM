package util;
import java.util.function.Consumer;

public class ProgressBar implements Consumer<Double> {
    private final double total;
    private double progress;

    public ProgressBar(double total) {
        this.total = total;
        this.progress = 0;
    }

    public void updateProgress(double progress) {
        this.progress += progress;
        printProgress();
    }

    @Override
    public void accept(Double progress) {
        updateProgress(progress);
    }

    private void printProgress() {
        int width = 50;
        int completed = (int) (width * (progress / total));
        int remaining = width - completed;
        if (remaining < 0) {
            remaining = 0;
            completed = width;
        }
        try {
            String bar = "[" + String.format("%" + completed + "s", "").replace(' ', '=') + String.format("%" + remaining + "s", "") + "]";
            System.out.printf("\r%s %.2f%%", bar, (progress / total) * 100);
        } catch (Exception e) {
            String bar = "[" + String.format("%" + width + "s", "").replace(' ', '=') + "]";
            System.out.printf("\r%s %.2f%%", bar, 100.0);
        }
        if (progress >= total) {
            System.out.println();
        }
    }
}