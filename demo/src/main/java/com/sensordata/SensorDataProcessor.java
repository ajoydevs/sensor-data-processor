package com.sensordata;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class SensorDataProcessor {

    // Sensor data and limits.
    public double[][][] data;
    public double[][] limit;

    // constructor
    public SensorDataProcessor(double[][][] data, double[][] limit) {
        this.data = data;
        this.limit = limit;
    }

    // calculates average of sensor data
    private double average(double[] array) {
        int i = 0;
        double val = 0;
        for (i = 0; i < array.length; i++) {
            val += array[i];
        }
        return val / array.length;
    }

    // calculate data
    public void calculate(double d) {

        long startTime = System.nanoTime();

        // STRATEGY 1 (original lines 43-45): Cache loop bounds in local variables.
        // Accessing array .length fields through repeated dereferencing (data[0].length,
        // data[0][0].length) performs extra pointer chasing on every iteration.
        // Storing them once eliminates redundant memory reads inside the hot loops.
        final int iLen = data.length;
        final int jLen = data[0].length;
        final int kLen = data[0][0].length;

        int i, j, k;
        double[][][] data2 = new double[iLen][jLen][kLen];

        BufferedWriter out;

        try {
            out = new BufferedWriter(new FileWriter("RacingStatsData.txt"));

            for (i = 0; i < iLen; i++) {
                for (j = 0; j < jLen; j++) {

                    // STRATEGY 2 (original line 46): Hoist the limit[i][j] lookup and its
                    // square out of the innermost k-loop.  limit[i][j] is constant for a
                    // fixed (i,j) pair, so Math.pow(limit[i][j], 2.0) was being recomputed
                    // on every k iteration.  Computing it once per (i,j) saves one 2-D array
                    // lookup and one Math.pow() call per reading.
                    final double limSq = limit[i][j] * limit[i][j];   // multiplication beats Math.pow for integer exponent

                    // STRATEGY 3 (original line 53): Pre-compute average(data[i][j]) before
                    // entering the k-loop.  data[i][j] is unchanged throughout the loop, so
                    // calling average() inside the loop recomputed the same sum every iteration.
                    final double avgData = average(data[i][j]);

                    // STRATEGY 4 (original line 46): Cache 2-D slice references to avoid
                    // repeated multi-dimensional array dereferencing in the hot loop body.
                    final double[] dataIJ  = data[i][j];
                    final double[] data2IJ = data2[i][j];

                    for (k = 0; k < kLen; k++) {
                        data2IJ[k] = dataIJ[k] / d - limSq;   // limSq hoisted (Strategy 2)

                        // STRATEGY 3 continued: use pre-computed avgData instead of
                        // calling average(data[i][j]) inside the k-loop.
                        final double avg2 = average(data2IJ);  // data2IJ changes each k; must recompute

                        // STRATEGY 5 (original line 48): Cache the average of data2[i][j].
                        // The original code called average(data2[i][j]) TWICE in one condition
                        // (once for "> 10" and once for "< 50"), iterating the array two times.
                        // Storing the result once halves the cost of this condition.
                        if (avg2 > 10 && avg2 < 50)
                            break;

                        // STRATEGY 6 (original line 50): Replace Math.max(a, b) > a with a
                        // direct comparison b > a.  Math.max(a, b) > a is true if and only if
                        // b > a; removing the method call avoids a JVM dispatch and a branch
                        // inside the JDK implementation.
                        else if (data2IJ[k] > dataIJ[k])
                            break;

                        // STRATEGY 7 (original lines 52-53): Replace
                        //   Math.pow(Math.abs(x), 3) < Math.pow(Math.abs(y), 3)
                        // with
                        //   Math.abs(x) < Math.abs(y)
                        // x^3 is monotonically increasing over non-negative reals, so comparing
                        // cubed absolute values is equivalent to comparing the absolute values
                        // directly.  This removes four Math.pow() and two Math.abs() calls that
                        // were executed per iteration and replaces them with two Math.abs() calls.
                        // avgData is the pre-computed average (Strategy 3), replacing the
                        // inline average(data[i][j]) call that iterated the full array each time.
                        else if (Math.abs(dataIJ[k]) < Math.abs(data2IJ[k])
                                && avgData < data2IJ[k] && (i + 1) * (j + 1) > 0)
                            data2IJ[k] *= 2;
                        else
                            continue;
                    }
                }
            }

            for (i = 0; i < iLen; i++) {
                for (j = 0; j < jLen; j++) {
                    out.write(data2[i][j] + "\t");
                }
            }

            out.close();

            long endTime = System.nanoTime();
            long elapsedMs = (endTime - startTime) / 1_000_000;
            System.out.println("calculate() completed in " + elapsedMs + " ms");

        } catch (Exception e) {
            System.out.println("Error= " + e);
            long endTime = System.nanoTime();
            long elapsedMs = (endTime - startTime) / 1_000_000;
            System.out.println("calculate() failed after " + elapsedMs + " ms");
        }
    }
}
