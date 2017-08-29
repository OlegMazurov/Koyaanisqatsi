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

import java.util.concurrent.CyclicBarrier;

/**
 * Synchronous parallel implementation of Life
 *
 * https://github.com/OlegMazurov/Koyaanisqatsi
 *
 */

public class OrdinaryLife extends Life {

    private final Cell[] cells;
    private CyclicBarrier barrier;
    private boolean useAlt;

    protected int getState(int row, int col) {
        Cell cell = cells[row * Width + col];
        if (useAlt) {
            cell = cell.neighbors[0];
        }
        return cell.state;
    }

    private static class Cell {
        int state;
        Cell[] neighbors;

        public Cell(int i, int s)
        {
            state = s;
            neighbors = new Cell[9];
        }

        void updateState() {
            state = neighbors[0].state;
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
        }
    }

    private void runStaticSchedule(int id) {

        int minIdx = (int)((long)id * cells.length / nThreads);
        int maxIdx = (int)((long)(id + 1) * cells.length / nThreads);

        for (int time = 1; time <= maxTime; ++time) {

            for (int idx = minIdx; idx < maxIdx; ++idx) {
                Cell cell = useAlt ? cells[idx] : cells[idx].neighbors[0];
                cell.updateState();

                // Color live cells according to the current thread id
                setColor(idx, cell.state == STATE0 ? 0 : id + 1);
                // Color all cells according to the current thread id
                //setColor(idx, (int)Thread.currentThread().getId())
                // Color all cells according to the current generation
                //setColor(idx, TS1);
            }

            try {
                barrier.await();
            }
            catch (Exception ex) {
                System.err.println("ERROR in thread " + id);
                ex.printStackTrace();
                return;
            }
        }
    }

    public void execute()
    {
        // Run concurrently
        Thread[] threads = new Thread[nThreads];
        for (int t = 0; t < threads.length; ++t) {
            final int id = t;
            Thread thread = new Thread(() -> runStaticSchedule(id));
            threads[t] = thread;
            thread.start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        }
        catch (InterruptedException ie) {
            ie.printStackTrace();
        }
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

    public OrdinaryLife(int w, int h, int t, int p, boolean v, int[] s) {
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
        useAlt = false;

        barrier = new CyclicBarrier(nThreads, () -> { useAlt = !useAlt; });
    }

    public static void main(String[] args) {
        type = Type.ORDINARY;
        Life.main(args);
    }
}
