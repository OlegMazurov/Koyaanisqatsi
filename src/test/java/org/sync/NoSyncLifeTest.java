/*
 * Copyright 2017 Oleg Mazurov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sync;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class NoSyncLifeTest {

    @Test(timeout = 60000)
    public void testFast() {
        // Test 8-thread parallel execution against serial
        RLE acorn = RLE.getAcorn();
        NoSyncLife sample = NoSyncLife.fromRLE(acorn, 100, 1, false);
        String[] golden = sample.execute();

        sample = NoSyncLife.fromRLE(acorn, 100, 4, false);
        String[] state = sample.execute();

        Assert.assertArrayEquals(golden, state);
    }

    @Test(timeout = 50000)
    public void testLong100() {
        testLong(100, 16);
    }
    @Test(timeout = 50000)
    public void testLong200() {
        testLong(200, 16);
    }

    @Ignore
    @Test(timeout = 300000)
    public void testLong2000() {
        testLong(2000, 256);
    }

    private void testLong(int generations, int maxParallelism) {
        RLE acorn = RLE.getAcorn();

        System.out.print("Running test for " + generations +" generations with 1 thread");
        NoSyncLife sample = NoSyncLife.fromRLE(acorn, generations, 1, false);
        long start = System.currentTimeMillis();
        String[] golden = sample.execute();
        long time1 = System.currentTimeMillis() - start;
        System.out.println(", base time: " + time1 + " ms");

        for (int p = 2; p <= maxParallelism; p *= 2) {
            System.out.print("Running test for " + generations +" generations with " + p + " threads");
            sample = NoSyncLife.fromRLE(acorn, generations, p, false);
            start = System.currentTimeMillis();
            String[] state = sample.execute();
            long time = System.currentTimeMillis() - start;

            Assert.assertArrayEquals(golden, state);

            System.out.println(", time: " + time + " ms, speedup: " + (100 * time1 / time)/100d);
        }
    }

    //    @Test
    public void testInfinite() {
        int generations = 10000;
        int threads = 32;
        RLE acorn = RLE.getAcorn();

        System.out.print("Running test for " + generations +" generations with 1 thread");
        NoSyncLife sample = NoSyncLife.fromRLE(acorn, generations, 1, false);
        long start = System.currentTimeMillis();
        String[] golden = sample.execute();
        long time1 = System.currentTimeMillis() - start;
        System.out.println(", base time: " + time1 + " ms");

        for (;;) {
            System.out.print("Running test for " + generations +" generations with " + threads + " threads");
            sample = NoSyncLife.fromRLE(acorn, generations, threads, false);
            start = System.currentTimeMillis();
            String[] state = sample.execute();
            long time = System.currentTimeMillis() - start;

            Assert.assertArrayEquals(golden, state);

            System.out.println(", time: " + time + " ms");
        }
    }

}