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

/**
 * Asynchronous parallel wait-free unsynchronized implementation of Life
 *
 * https://github.com/OlegMazurov/Koyaanisqatsi
 *
 */

public class NoSyncLife extends Life {

    private final Cell[] cells;

    private static class Cell {
        int idx;
        int[] state;
        Cell[] neighbors;

        public Cell(int i, int s) {
            idx = i;
            state = new int[3];
            int off = T0 & 0x1;
            state[1 - off] = (T0 - 1) << 1;
            state[off] = (T0 << 1) | s;
            neighbors = new Cell[8];
        }
    }

    protected int getState(int row, int col) {
        Cell cell = cells[row * Width + col];
        return Math.max(cell.state[0], cell.state[1]) & 0x1;
    }

    private static class PseudoRandom {
        static final int FACTOR1 = 2999;
        static final int FACTOR2 = 7901;
        int val;

        PseudoRandom(int seed) {
            val = seed;
        }

        int nextInt(int n) {
            if (n <= 1) return 0;
            val = Math.abs(val * FACTOR1 + FACTOR2);
            return val % n;
        }
    }

    private void runUnsync(int id)
    {
        PseudoRandom rnd = new PseudoRandom(id);
        Cell[] next = new Cell[16];

        // Start apart
        Cell cur = cells[cells.length * id / nThreads];

        mainLoop:
        for (;;) {
            int s0 = cur.state[0];
            int s1 = cur.state[1];
            int S0 = Math.min(s0, s1);
            int S1 = Math.max(s0, s1);
            int S2 = cur.state[2];

            int TS0 = S0 >> 1;
            int TS1 = S1 >> 1;
            int TS2 = S2 >> 1;

            if (TS2 < TS1) {
                int off = TS1 & 0x1;
                int cnt = 0;
                int V = S1;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[off];
                    if ((val >> 1) == TS1) {
                        V ^= val;
                    }
                    else {
                        next[cnt++] = neighbor;
                    }
                }
                if (cnt == 0) {
                    cur.state[2] = V;
                    continue mainLoop;
                }

                if (TS2 < TS0) {
                    cnt = 0;
                    off = TS0 & 0x1;
                    V = S0;
                    for (Cell neighbor : cur.neighbors) {
                        int val = neighbor.state[off];
                        if ((val >> 1) == TS0) {
                            V ^= val;
                        }
                        else {
                            next[cnt++] = neighbor;
                        }
                    }
                    if (cnt == 0) {
                        cur.state[2] = V;
                        continue mainLoop;
                    }
                }
                else if (TS2 == TS0) {
                    cnt = 0;
                    off = TS0 & 0x1;
                    V = S0 ^ S2;
                    for (Cell neighbor : cur.neighbors) {
                        int val = neighbor.state[off];
                        if ((val >> 1) == TS0) {
                            V ^= val;
                        }
                        else {
                            next[cnt++] = neighbor;
                        }
                    }
                    if (cnt == 1) {
                        next[0].state[off] = V;
                        continue mainLoop;
                    }
                }
                else {
                    cnt = 0;
                    next[cnt++] = cur;
                    off = TS2 & 0x1;
                    V = S2;
                    for (Cell neighbor : cur.neighbors) {
                        int val = neighbor.state[off];
                        if ((val >> 1) == TS2) {
                            V ^= val;
                        }
                        else {
                            next[cnt++] = neighbor;
                        }
                    }
                    if (cnt == 1) {
                        next[0].state[off] = V;
                        continue mainLoop;
                    }
                }
                cur = next[rnd.nextInt(cnt)];
            }
            else if (TS2 == TS1) {
                int off = TS2 & 0x1;
                int cnt = 0;
                int sum = 0;

                int V = S1 ^ S2;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[off];
                    if ((val >> 1) == TS2) {
                        V ^= val;
                        sum += val & 0x1;
                    }
                    else {
                        next[cnt++] = neighbor;
                    }
                }
                if (cnt == 1) {
                    next[0].state[off] = V;
                    sum += V & 0x1;
                    cnt = 0;
                }

                int cnt2 = cnt;
                Cell rnext = null;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[2];
                    if ((val >> 1) <= TS2) {
                        if ((val >> 1) < TS2) {
                            ++cnt2;
                            rnext = neighbor;
                        }
                        if (cnt > 0) {
                            next[cnt++] = neighbor;
                        }
                    }
                }
                if (cnt2 > 0) {
                    cur = rnext != null ? rnext : next[rnd.nextInt(cnt)];
                    continue mainLoop;
                }

                // Are we done?
                if (TS1 == maxTime) {
                    int idx = cur.idx;
                    for (int n = 0; n < cells.length; ++n) {
                        if (++idx == cells.length) idx = 0;
                        cur = cells[idx];
                        if (Math.max(cur.state[0], cur.state[1]) >> 1 != maxTime) continue mainLoop;
                    }
                    return;
                }

                // Apply the rule of Life
                int nextState = sum < 2 ? STATE0 : sum == 2 ? (S1 & 0x1) : sum == 3 ? STATE1 : STATE0;
                cur.state[1 - off] = ((TS1 + 1) << 1) | nextState;

                // Color live cells according to the current thread id
                setColor(cur.idx, nextState == STATE0 ? 0 : id + 1);
                // Color all cells according to the current thread id
                //setColor(cur.idx, id + 1);
                // Color all cells according to the current generation
                //setColor(cur.idx, TS1);
            }
            else {
                int off = TS2 & 0x1;
                int cnt = 0;
                int V = S2;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[off];
                    if ((val >> 1) == TS2) {
                        V ^= val;
                    }
                    else {
                        cnt++;
                        break;
                    }
                }
                if (cnt == 0) {
                    cur.state[off] = V;
                    continue mainLoop;
                }

                off = TS1 & 0x1;
                int sum = 0;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[off];
                    if ((val >> 1) == TS1) {
                        sum += val & 0x1;
                    }
                    else {
                        continue mainLoop;
                    }
                }

                // Apply the rule of Life
                int nextState = sum < 2 ? STATE0 : sum == 2 ? (S1 & 0x1) : sum == 3 ? STATE1 : STATE0;
                cur.state[1 - off] = ((TS1 + 1) << 1) | nextState;
            }
        }
    }

    public void execute()
    {
        // Run concurrently
        Thread[] threads = new Thread[nThreads];
        for (int t = 0; t < threads.length; ++t) {
            final int id = t;
            Thread thread = new Thread(() -> runUnsync(id));
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

    /**
     * Cell neighbors wrapped around a torus:
     *       -------------
     *    +1 | 6 | 5 | 4 |
     *       -------------
     *     r | 7 |   | 3 |
     *       -------------
     *    -1 | 0 | 1 | 2 |
     *       -------------
     *        -1   c  +1
     */
    private int getNeighbor(int idx, int i)
    {
        int r = idx / Width;
        int c = idx % Width;
        switch (i) {
            case 0:
                if (--c < 0) c += Width;
            case 1:
                if (--r < 0) r += Height;
                break;
            case 2:
                if (--r < 0) r += Height;
            case 3:
                if (++c == Width) c = 0;
                break;
            case 4:
                if (++c == Width) c = 0;
            case 5:
                if (++r == Height) r = 0;
                break;
            case 6:
                if (++r == Height) r = 0;
            case 7:
                if (--c < 0) c += Width;
                break;
        }
        return r * Width + c;
    }

    public NoSyncLife(int w, int h, int t, int p, boolean v, int[] s)
    {
        super(w, h, t, p, v);

        // Initialize cells
        cells = new Cell[Width * Height];
        for (int idx = 0; idx < cells.length; ++idx) {
            Cell cell = new Cell(idx, s[idx] == 0 ? STATE0 : STATE1);
            cells[idx] = cell;
        }
        for (Cell cell : cells) {
            int S = cell.state[T0 & 0x1];
            cell.state[2] ^= S;
            for (int n = 0; n < cell.neighbors.length; ++n) {
                int nidx = getNeighbor(cell.idx, n);
                Cell neighbor = cells[nidx];
                cell.neighbors[n] = neighbor;
                neighbor.state[2] ^= S;
            }
        }
    }

    public static void main(String[] args) {
        type = Type.NOSYNC;
        Life.main(args);
    }
}
