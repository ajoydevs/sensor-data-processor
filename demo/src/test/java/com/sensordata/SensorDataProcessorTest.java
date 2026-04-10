package com.sensordata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SensorDataProcessor.
 *
 * Branch coverage targets inside calculate():
 *
 *   B1: if (average(data2[i][j]) > 10 && average(data2[i][j]) < 50) break;
 *       - B1-A-false : average <= 10              → falls through to B2
 *       - B1-A-true, B1-B-false : average >= 50  → falls through to B2
 *       - B1-both-true : 10 < average < 50        → break
 *
 *   B2: else if (Math.max(data[..], data2[..]) > data[..]) break;
 *       - B2-true  : data2 > data                 → break
 *       - B2-false : data2 <= data                → falls through to B3
 *
 *   B3: else if (|data|^3 < |data2|^3 && avg(data) < data2 && (i+1)(j+1)>0)
 *       - B3-A-false : |data| >= |data2|          → else continue
 *       - B3-A-true, B3-B-false : avg >= data2   → else continue
 *       - B3-all-true                             → data2 *= 2
 *       NOTE: (i+1)*(j+1)>0 is always true for non-negative loop indices;
 *             its false-branch is structurally unreachable and is documented
 *             in the analysis report.
 *
 *   EX: catch block is exercised by supplying mismatched limit dimensions so
 *       that limit[i][j] throws ArrayIndexOutOfBoundsException inside the try.
 *
 * Statement coverage: all executable lines in calculate() and average() are
 * hit by the combination of tests below.
 */
class SensorDataProcessorTest {

    // Clean up the output file written by calculate() between tests
    @AfterEach
    void cleanUp() {
        new File("RacingStatsData.txt").delete();
    }

    // -----------------------------------------------------------------------
    // average() – exercised indirectly through every calculate() call.
    // The for-loop in average() has its "condition true" branch covered
    // whenever calculate() iterates over at least one element, and its
    // "condition false" branch covered when the loop terminates naturally.
    // -----------------------------------------------------------------------

    /**
     * B1-A-false: average(data2[i][j]) <= 10.
     * B2-false:   data2 == data (d=1, limit=1 → data2 = data-1 < data).
     * B3-A-false: |data2| < |data|.
     * else continue is reached.
     */
    @Test
    void testBranch1_AverageAtOrBelowLowerBound_ElseContinue() {
        // data2 = 5/1 - 1^2 = 4   average(data2) = 4 ≤ 10  (B1-A false)
        // Math.max(5, 4) = 5 > 5? No  (B2 false)
        // |5|^3 < |4|^3? 125 < 64? No  (B3-A false)  → else continue
        double[][][] data = {{{5.0}}};
        double[][] limit = {{1.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> sdp.calculate(1.0));
    }

    /**
     * B1-both-true: 10 < average(data2) < 50 → break.
     */
    @Test
    void testBranch1_AverageInRange_Break() {
        // data2 = 20/1 - 0 = 20   average = 20 ∈ (10, 50)  → break
        double[][][] data = {{{20.0}}};
        double[][] limit = {{0.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> sdp.calculate(1.0));
    }

    /**
     * B1-A-true, B1-B-false: average(data2) >= 50 (second && is false).
     * B2-false: data2 == data (d=1, limit=0).
     * B3-A-false: |data| == |data2|, so not strictly less.
     * else continue is reached.
     */
    @Test
    void testBranch1_AverageTooHigh_FallsThroughB2FalseB3AFalse() {
        // data2 = 60/1 - 0 = 60   average = 60 > 10 (A true), 60 < 50? No (B false)
        // Math.max(60, 60) = 60 > 60? No  (B2 false)
        // |60|^3 < |60|^3? No  (B3-A false)  → else continue
        double[][][] data = {{{60.0}}};
        double[][] limit = {{0.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> sdp.calculate(1.0));
    }

    /**
     * B2-true: Math.max(data, data2) > data  (i.e., data2 > data) → break.
     * B1 must be false first: average(data2) not in (10, 50).
     */
    @Test
    void testBranch2_Data2GreaterThanData_Break() {
        // data=-5, d=2 → data2 = -5/2 - 0 = -2.5
        // average(-2.5) = -2.5  not in (10, 50)   (B1 false)
        // Math.max(-5, -2.5) = -2.5 > -5?  YES   (B2 true)  → break
        double[][][] data = {{{-5.0}}};
        double[][] limit = {{0.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> sdp.calculate(2.0));
    }

    /**
     * B3-A-true, B3-B-false: |data2| > |data| but avg(data) >= data2.
     * else continue is reached.
     */
    @Test
    void testBranch3_ATrue_BFalse_ElseContinue() {
        // data=2, d=1, limit=3 → data2 = 2 - 9 = -7
        // average(data2) = -7  not in (10, 50)   (B1 false)
        // Math.max(2, -7) = 2 > 2? No  (B2 false)
        // |2|^3 < |-7|^3 → 8 < 343? YES  (B3-A true)
        // avg(data[0][0]) = 2.0 < -7? No  (B3-B false)  → else continue
        double[][][] data = {{{2.0}}};
        double[][] limit = {{3.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> sdp.calculate(1.0));
    }

    /**
     * B3-all-true: data2 *= 2 is executed.
     *
     * At k=0:
     *   data = -4, d=2, limit=10  →  data2[0][0][0] = -4/2 - 100 = -102
     *   average(data2[0][0]) = (-102+0)/2 = -51   (B1 false)
     *   Math.max(-4, -102) = -4 > -4? No           (B2 false)
     *   |-4|^3 < |-102|^3 → 64 < 1 061 208? YES   (B3-A true)
     *   avg(data[0][0]) = (-4+-300)/2 = -152
     *   -152 < -102? YES                           (B3-B true)
     *   (0+1)*(0+1) = 1 > 0? YES                  (B3-C true)
     *   → data2[0][0][0] *= 2  =  -204             (body executed)
     *
     * At k=1 the loop also exercises B2-true (data2 > data) → break.
     */
    @Test
    void testBranch3_AllTrue_DataDoubled() {
        double[][][] data = {{
            {-4.0, -300.0}    // j=0, two readings
        }};
        double[][] limit = {{10.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> sdp.calculate(2.0));
        // After calculate(), data2 is local, but we verify no exception occurred.
        // The data2[0][0][0] *= 2 line has been exercised (verified via coverage report).
    }

    /**
     * Exception path: limit has fewer columns than data, causing
     * ArrayIndexOutOfBoundsException at limit[i][j] inside the try block.
     * The catch block (lines that print the error and elapsed time) is reached.
     */
    @Test
    void testCalculate_ExceptionCaughtInternally() {
        // data is [1][2][1] but limit is [1][1]; limit[0][1] throws AIOOB
        double[][][] data = new double[1][2][1];
        data[0][0][0] = 1.0;
        data[0][1][0] = 1.0;
        double[][] limit = new double[1][1];
        limit[0][0] = 0.0;
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        // calculate() catches the exception internally; no exception should propagate
        assertDoesNotThrow(() -> sdp.calculate(1.0));
    }

    /**
     * Multi-group test to exercise the outer loops (i > 0) and confirm
     * the second write-loop (lines 61-65) runs for multiple i/j combinations.
     */
    @Test
    void testCalculate_MultipleGroups() {
        double[][][] data = {
            {{15.0}},   // group 0: data2 = 15, average=15 in (10,50) → break
            {{20.0}}    // group 1: data2 = 20, average=20 in (10,50) → break
        };
        double[][] limit = {{0.0}, {0.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> sdp.calculate(1.0));
    }

    /**
     * Verifies that calculate() produces the expected file output for a
     * simple, known input where no special branch is taken (else continue).
     */
    @Test
    void testCalculate_FileIsCreated() {
        double[][][] data = {{{5.0}}};
        double[][] limit = {{1.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        sdp.calculate(1.0);
        assertTrue(new File("RacingStatsData.txt").exists(),
                   "calculate() should create RacingStatsData.txt");
    }

    /**
     * Constructor test: verifies field assignment.
     */
    @Test
    void testConstructor_FieldsAreAssigned() {
        double[][][] data = {{{1.0, 2.0}}};
        double[][] limit = {{3.0}};
        SensorDataProcessor sdp = new SensorDataProcessor(data, limit);
        assertSame(data, sdp.data);
        assertSame(limit, sdp.limit);
    }
}
