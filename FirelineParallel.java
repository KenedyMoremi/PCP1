import java.util.concurrent.ForkJoinPool;

/** Fork/Join implementation with the same command-line interface as serial. */
public final class FirelineParallel {
    private static final int DEFAULT_MAXIMUM_STEPS = 5_000;
    private static final double DEFAULT_TOLERANCE = 0.05;
    private static final int DEFAULT_CUTOFF = 20_000;

    private FirelineParallel() { }

    public static void main(String[] args) {
        if (args.length < 5 || args.length > 11
                || (args.length > 8 && args.length < 11)) {
            printUsage();
            System.exit(1);
        }

        try {
            int rows = parsePositiveInteger(args[0], "rows");
            int columns = parsePositiveInteger(args[1], "columns");
            long seed = Long.parseLong(args[2]);
            FireMapParallel.Mode mode = FireMapParallel.Mode.fromString(args[3]);
            String outputPrefix = args[4].trim();
            int maximumSteps = args.length >= 6
                    ? parsePositiveInteger(args[5], "maximum steps")
                    : DEFAULT_MAXIMUM_STEPS;
            double tolerance = args.length >= 7
                    ? parsePositiveDouble(args[6], "tolerance")
                    : DEFAULT_TOLERANCE;
            FireMapParallel.Landscape landscape = args.length >= 8
                    ? FireMapParallel.Landscape.fromString(args[7])
                    : FireMapParallel.Landscape.MIXED;
            Integer ignitionTopRow = null, ignitionLeftColumn = null,
                    ignitionPatchSize = null;
            if (args.length == 11) {
                ignitionTopRow = parseNonNegativeInteger(args[8], "ignition top row");
                ignitionLeftColumn = parseNonNegativeInteger(args[9], "ignition left column");
                ignitionPatchSize = parsePositiveInteger(args[10], "ignition patch size");
            }
            if (outputPrefix.isEmpty()) {
                throw new IllegalArgumentException("The output prefix may not be empty.");
            }

            int cutoff = Integer.getInteger("fire.cutoff", DEFAULT_CUTOFF);
            int parallelism = Integer.getInteger("fire.parallelism",
                    Runtime.getRuntime().availableProcessors());
            if (cutoff < 1 || parallelism < 1) {
                throw new IllegalArgumentException(
                        "fire.cutoff and fire.parallelism must both be greater than zero.");
            }

            FireMapParallel map = new FireMapParallel(rows, columns, seed, mode,
                    landscape, ignitionTopRow, ignitionLeftColumn, ignitionPatchSize);
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            FireMapParallel.StepResult result = null;
            int stepsCompleted = 0;
            boolean converged = false;
            long startTime = System.nanoTime();
            try {
                while (stepsCompleted < maximumSteps) {
                    result = map.parallelStep(cutoff, pool);
                    stepsCompleted++;
                    converged = mode == FireMapParallel.Mode.WILDFIRE
                            ? result.getBurningCells() == 0
                              && result.getMaximumTemperatureChange() < tolerance
                            : result.getMaximumTemperatureChange() < tolerance;
                    if (converged) break;
                }
            } finally {
                pool.shutdown();
            }
            double elapsedMilliseconds = (System.nanoTime() - startTime) / 1_000_000.0;

            map.writeImages(outputPrefix);
            System.out.println("Fireline parallel simulation");
            System.out.printf("Mode: %s%n", mode.name().toLowerCase());
            System.out.printf("Rows: %d, Columns: %d%n", rows, columns);
            System.out.printf("Random seed: %d%n", seed);
            System.out.printf("Landscape: %s%n", landscape.name().toLowerCase());
            System.out.printf("Initial source: %s%n", map.getSourceDescription());
            System.out.printf("Timesteps completed: %d%n", stepsCompleted);
            System.out.printf("Converged: %s%n", converged ? "yes" : "no");
            System.out.printf("Final burning cells: %d%n", result == null ? 0 : result.getBurningCells());
            System.out.printf("Cells burned: %d%n", map.countBurnedCells());
            System.out.printf("Maximum peak temperature: %.3f%n", map.getMaximumPeakTemperature());
            System.out.printf("Maximum change in final timestep: %.6f%n",
                    result == null ? 0.0 : result.getMaximumTemperatureChange());
            System.out.printf("Core simulation time: %.3f ms%n", elapsedMilliseconds);
            System.out.printf("Images written with prefix: %s%n", outputPrefix);
            if (!converged) System.out.println("Warning: maximum timestep limit reached before convergence.");
        } catch (NumberFormatException exception) {
            System.err.println("Invalid numeric argument: " + exception.getMessage());
            printUsage(); System.exit(1);
        } catch (IllegalArgumentException exception) {
            System.err.println("Input error: " + exception.getMessage());
            printUsage(); System.exit(1);
        } catch (Exception exception) {
            System.err.println("Simulation failed: " + exception.getMessage());
            exception.printStackTrace(); System.exit(1);
        }
    }

    private static int parsePositiveInteger(String value, String name) { int n = Integer.parseInt(value); if (n <= 0) throw new IllegalArgumentException(name + " must be greater than zero."); return n; }
    private static int parseNonNegativeInteger(String value, String name) { int n = Integer.parseInt(value); if (n < 0) throw new IllegalArgumentException(name + " must be zero or greater."); return n; }
    private static double parsePositiveDouble(String value, String name) { double n = Double.parseDouble(value); if (!Double.isFinite(n) || n <= 0.0) throw new IllegalArgumentException(name + " must be a finite value greater than zero."); return n; }
    private static void printUsage() { System.err.println("Usage: java FirelineParallel <rows> <columns> <seed> <diffusion|wildfire> <output-prefix> [max-steps] [tolerance] [mixed|grass] [ignition-top-row ignition-left-column patch-size]"); }
}

