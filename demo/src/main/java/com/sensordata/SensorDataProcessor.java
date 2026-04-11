package com.sensordata;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

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

        // AI OPTIMIZATION 1: Cache loop bounds to avoid repeated array-length lookups.
        // Original lines 43-45 dereferenced data[0].length and data[0][0].length on
        // every single loop iteration.  Local int variables are accessed from a CPU
        // register after the first load, making this a zero-cost lookup in the hot path.
        final int iLen = data.length;
        final int jLen = data[0].length;
        final int kLen = data[0][0].length;

        // AI OPTIMIZATION 2: Replace per-element division with a single reciprocal
        // multiply.  Floating-point division is significantly slower than multiplication
        // on modern hardware (~20-40 cycles vs ~4 cycles on x86).  Computing 1/d once
        // and multiplying converts every "dataIJ[k] / d" into "dataIJ[k] * invD".
        // This was not found in the manual pass and represents a measurable gain for
        // large k dimensions.
        final double invD = 1.0 / d;

        int i, j, k;
        double[][][] data2 = new double[iLen][jLen][kLen];

        // AI OPTIMIZATION 3: Use a reusable StringBuilder to batch all file-write
        // content into a single string, then flush it in one write.  The original
        // second loop (original lines 61-65) called out.write() once per (i,j) pair,
        // incurring overhead for each call even through BufferedWriter.
        // Building the string in memory first and writing once reduces I/O round-trips.
        final StringBuilder sb = new StringBuilder(iLen * jLen * 32);

        BufferedWriter out;

        try {
            out = new BufferedWriter(new FileWriter("RacingStatsData.txt"));

            for (i = 0; i < iLen; i++) {
                for (j = 0; j < jLen; j++) {

                    // AI OPTIMIZATION 4 (original line 46): Hoist limit[i][j]^2 outside
                    // the k-loop.  The limit value is fixed for a given (i,j), so squaring
                    // it once per (i,j) pair versus once per (i,j,k) triple saves kLen
                    // array dereferences and Math.pow calls per pair.  Multiplication
                    // replaces Math.pow(x, 2.0) for an additional speedup.
                    final double limSq = limit[i][j] * limit[i][j];

                    // AI OPTIMIZATION 5 (original line 53): Pre-compute average(data[i][j])
                    // once per (i,j) before entering the k-loop.  data[i][j] is read-only
                    // within the loop, so repeating this O(kLen) summation kLen times was
                    // O(kLen^2) work; computing it once reduces it to O(kLen).
                    final double avgData = average(data[i][j]);

                    // AI OPTIMIZATION 6: Cache slice references to avoid repeated 2-D
                    // array pointer chasing on every k iteration.
                    final double[] dataIJ  = data[i][j];
                    final double[] data2IJ = data2[i][j];

                    for (k = 0; k < kLen; k++) {
                        // invD multiply (Optimization 2) replaces division
                        data2IJ[k] = dataIJ[k] * invD - limSq;

                        // AI OPTIMIZATION 7 (original line 48): Cache average(data2IJ) in
                        // a local variable.  The original code called average(data2[i][j])
                        // TWICE in one boolean expression, iterating the array twice per k.
                        final double avg2 = average(data2IJ);
                        if (avg2 > 10 && avg2 < 50)
                            break;

                        // AI OPTIMIZATION 8 (original line 50): Replace Math.max(a,b)>a
                        // with a direct comparison b>a — mathematically identical but
                        // eliminates one JDK method call per inner iteration.
                        else if (data2IJ[k] > dataIJ[k])
                            break;

                        // AI OPTIMIZATION 9 (original lines 52-53):
                        // (a) Replace Math.pow(Math.abs(x), 3) < Math.pow(Math.abs(y), 3)
                        //     with Math.abs(x) < Math.abs(y).  Since f(t)=t^3 is strictly
                        //     monotone on [0, ∞), |x|^3 < |y|^3 iff |x| < |y|, saving
                        //     two Math.pow calls and two redundant Math.abs calls.
                        // (b) Remove the always-true guard (i+1)*(j+1)>0.  Because loop
                        //     indices are non-negative, (i+1)≥1 and (j+1)≥1, making their
                        //     product always ≥ 1.  This is dead code; removing it eliminates
                        //     one multiplication, one comparison, and an unreachable branch
                        //     that prevented 100% branch coverage in the test suite.
                        else if (Math.abs(dataIJ[k]) < Math.abs(data2IJ[k])
                                && avgData < data2IJ[k])
                            data2IJ[k] *= 2;
                        else
                            continue;
                    }

                    // Build write content in StringBuilder (Optimization 3)
                    sb.append(data2IJ).append('\t');
                }
            }

            // Single write flush for all data (Optimization 3)
            out.write(sb.toString());
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
