import java.util.concurrent.RecursiveTask;

/** A divide-and-conquer Fork/Join task for a rectangular map region. */
public final class FireTask extends RecursiveTask<FireMapParallel.StepResult> {
    private final FireMapParallel map;
    private final int top;
    private final int left;
    private final int bottom;
    private final int right;
    private final int cutoff;

    public FireTask(FireMapParallel map, int top, int left, int bottom,
                    int right, int cutoff) {
        this.map = map;
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.cutoff = cutoff;
    }

    @Override
    protected FireMapParallel.StepResult compute() {
        int height = bottom - top;
        int width = right - left;
        if ((long) height * width <= cutoff || height <= 1 || width <= 1) {
            return map.updateRegion(top, left, bottom, right);
        }

        if (height >= width) {
            int middle = top + height / 2;
            FireTask first = new FireTask(map, top, left, middle, right, cutoff);
            FireTask second = new FireTask(map, middle, left, bottom, right, cutoff);
            first.fork();
            FireMapParallel.StepResult secondResult = second.compute();
            return FireMapParallel.StepResult.combine(first.join(), secondResult);
        }

        int middle = left + width / 2;
        FireTask first = new FireTask(map, top, left, bottom, middle, cutoff);
        FireTask second = new FireTask(map, top, middle, bottom, right, cutoff);
        first.fork();
        FireMapParallel.StepResult secondResult = second.compute();
        return FireMapParallel.StepResult.combine(first.join(), secondResult);
    }
}

