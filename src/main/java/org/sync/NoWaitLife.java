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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Asynchronous parallel wait-free implementation of Life
 *
 * https://github.com/OlegMazurov/Koyaanisqatsi
 *
 */

public class NoWaitLife extends Life {

    private final Cell[] cells;
    private CountDownLatch finished;

    private class Cell extends ForkJoinTask<Object>
    {
        private int idx;
        private int state;
        private int time;
        private Cell[] neighbors;
        private AtomicInteger count;

        public Cell(int i, int s)
        {
            idx = i;
            state = s;
            time = 0;
            neighbors = new Cell[9];
            count = new AtomicInteger(neighbors.length);
        }

        /* Not used */
        public Object getRawResult() { return null; }
        protected void setRawResult(Object value) {}

        protected boolean exec() {
            state = neighbors[0].state;
            time  = neighbors[0].time + 1;
            int sum = 0;
            for (int i = 1; i < neighbors.length; ++i) {
                if (neighbors[i].state == STATE1) {
                    sum += 1;
                }
            }

            // Apply the rule of Life
            if (sum < 2 || sum > 3) {
                state = STATE0;
            }
            else if (sum == 3) {
                state = STATE1;
            }

            // Color live cells according to the current thread id
            setColor(idx, state == STATE0 ? 0 : (int)Thread.currentThread().getId());
            // Color all cells according to the current thread id
            //setColor(idx, (int)Thread.currentThread().getId())
            // Color all cells according to the current generation
            //setColor(idx, TS1);

            reinitialize();
            count.set(neighbors.length);
            if (time == maxTime) {
                finished.countDown();
            }
            else {
                for (Cell cell : neighbors) {
                    if (cell.count.addAndGet(-1) == 0) {
                        cell.fork();
                    }
                }
            }

            return false;
        }
    }

    public String[] execute()
    {
        finished = new CountDownLatch(Width * Height);

        ForkJoinPool pool = new ForkJoinPool(
                nThreads,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                (t,e) -> e.printStackTrace(),
                false);

        for (Cell cell : cells) {
            pool.execute(cell.neighbors[0]);
        }


        try {
            finished.await();
        }
        catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        pool.shutdown();

        // Produce result
        String[] result = new String[Height];
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Height; ++r) {
            sb.setLength(0);
            for (int c = 0; c < Width; ++c) {
                int idx = r * Width + c;
                Cell cell = cells[idx];
                if (cell.time != maxTime) {
                    cell = cell.neighbors[0];
                }
                if (cell.time == maxTime) {
                    sb.append(cell.state);
                }
            }
            result[r] = sb.toString();
        }
        return result;
    }

    private int getNeighbor(int r, int c, int i)
    {
        switch (i) {
            case 1:
                if (--c < 0) c += Width;
            case 2:
                if (--r < 0) r += Height;
                break;
            case 3:
                if (--r < 0) r += Height;
            case 4:
                if (++c == Width) c = 0;
                break;
            case 5:
                if (++c == Width) c = 0;
            case 6:
                if (++r == Height) r = 0;
                break;
            case 7:
                if (++r == Height) r = 0;
            case 8:
                if (--c < 0) c += Width;
                break;
        }
        return r * Width + c;
    }


    public NoWaitLife(int w, int h, int t, int p, boolean v, int[] s)
    {
        super(w, h, t, p, v);

        // Initialize cells
        cells = new Cell[Width * Height];
        for (int r = 0; r < Height; ++r) {
            for (int c = 0; c < Width; ++c) {
                int idx = r * Width + c;
                Cell cell = new Cell(idx, s[idx] == 0 ? STATE0 : STATE1);
                Cell alt = new Cell(idx, 0);
                cell.neighbors[0] = alt;
                alt.neighbors[0] = cell;
                cells[idx] = cell;
            }
        }
        for (int r = 0; r < Height; ++r) {
            for (int c = 0; c < Width; ++c) {
                int idx = r * Width + c;
                Cell cell = cells[idx];
                for (int i = 1; i < cell.neighbors.length; ++i) {
                    Cell neighbor = cells[getNeighbor(r, c, i)].neighbors[0];
                    cell.neighbors[i] = neighbor;
                    neighbor.neighbors[i + 4 > 8 ? i - 4 : i + 4] = cell;
                }
            }
        }
    }
}
