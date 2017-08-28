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

public class LifeTest {

    @Test(timeout = 60000)
    public void testFast() {
        // Test 8-thread parallel execution against serial
        RLE acorn = RLE.getAcorn();
        Life sample = Life.fromRLE(acorn, Life.Type.NOSYNC, 100, 1, false);
        sample.execute();
        String[] golden = sample.getResult();

        sample = Life.fromRLE(acorn, Life.Type.ORDINARY, 100, 8, false);
        sample.execute();
        Assert.assertArrayEquals(golden, sample.getResult());

        sample = Life.fromRLE(acorn, Life.Type.NOWAIT, 100, 8, false);
        sample.execute();
        Assert.assertArrayEquals(golden, sample.getResult());

        sample = Life.fromRLE(acorn, Life.Type.NOSYNC,100, 8, false);
        sample.execute();
        Assert.assertArrayEquals(golden, sample.getResult());
    }

    private void testLong(int generations, Life.Type type) {
        RLE acorn = RLE.getAcorn();
        System.out.print("Running " + type + " for " + generations +" generations with 1 thread");
        Life sample = Life.fromRLE(acorn, type, generations, 1, false);
        long start = System.currentTimeMillis();
        sample.execute();
        long time1 = System.currentTimeMillis() - start;
        System.out.println(", base time: " + time1 + " ms");
        String[] golden = sample.getResult();

        for (int p = 2; p <= 512; p *= 2) {
            System.out.print("Running " + type + " for " + generations +" generations with " + p + " threads");
            sample = Life.fromRLE(acorn, type, generations, p, false);
            start = System.currentTimeMillis();
            sample.execute();
            long time = System.currentTimeMillis() - start;

            Assert.assertArrayEquals(golden, sample.getResult());

            System.out.println(", time: " + time + " ms, speedup: " + (100 * time1 / time)/100d);
        }

    }

    @Test(timeout = 300000)
    public void testLongOrdinary() {
        testLong(2000, Life.Type.ORDINARY);
    }

    @Test(timeout = 300000)
    public void testLongNoSync() {
        testLong(2000, Life.Type.NOSYNC);
    }

    @Test(timeout = 300000)
    public void testLongNoWait() {
        testLong(2000, Life.Type.NOWAIT);
    }

    private void testInfinite(int generations, Life.Type type, int threads) {
        RLE acorn = RLE.getAcorn();
        System.out.print("Running " + type + " for " + generations +" generations with 1 thread");
        Life sample = Life.fromRLE(acorn, type, generations, 1, false);
        long start = System.currentTimeMillis();
        sample.execute();
        long time1 = System.currentTimeMillis() - start;
        System.out.println(", base time: " + time1 + " ms");
        String[] golden = sample.getResult();

        for (;;) {
            System.out.print("Running " + type + " for " + generations +" generations with " + threads + " threads");
            sample = Life.fromRLE(acorn, type, generations, threads, false);
            start = System.currentTimeMillis();
            sample.execute();
            long time = System.currentTimeMillis() - start;

            Assert.assertArrayEquals(golden, sample.getResult());

            System.out.println(", time: " + time + " ms");
        }
    }

    @Ignore
    @Test
    public void testInfiniteOrdinary() {
        testInfinite(10000, Life.Type.ORDINARY, 32);
    }

    @Ignore
    @Test
    public void testInfiniteNoSync() {
        testInfinite(10000, Life.Type.NOSYNC, 32);
    }

    @Ignore
    @Test
    public void testInfiniteNoWait() {
        testInfinite(10000, Life.Type.NOWAIT, 32);
    }

}