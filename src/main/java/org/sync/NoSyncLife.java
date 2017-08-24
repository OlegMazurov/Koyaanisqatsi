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

import javax.swing.JFrame;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Chaotic Life
 *
 * https://github.com/OlegMazurov/Koyaanisqatsi
 *
 */

public class NoSyncLife {

    private static final int STATE0 = 0;
    private static final int STATE1 = 1;
    private static final int T0 = 0;
    private static final int[] COLORS = {
            0xffffff, 0xff0000, 0x00ff00, 0x0000ff,
            0xffff00, 0xff00ff, 0x00ffff, 0x80ff00,
    };

    private final int Width;
    private final int Height;
    private final Cell[] cells;
    private final int maxTime;
    private final int nThreads;
    private final boolean vis;
    private int[] imgData;

    private class Cell {
        private int idx;
        private int[] state;
        private Cell[] neighbors;

        public Cell(int i, int s) {
            idx = i;
            state = new int[3];
            int off = T0 & 0x1;
            state[1 - off] = (T0 - 1) << 1;
            state[off] = (T0 << 1) | s;
            neighbors = new Cell[8];
        }
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
            val = val * FACTOR1 + FACTOR2;
            return val % n;
        }
    }

    private void runUnsync(int id)
    {
        PseudoRandom rnd = new PseudoRandom(id);

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
                Cell next = null;
                int off = TS1 & 0x1;
                int cnt = 0;
                int V = S1;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[off];
                    if ((val >> 1) == TS1) {
                        V ^= val;
                    }
                    else if (rnd.nextInt(++cnt) == 0) {
                        next = neighbor;
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
                        else if (rnd.nextInt(++cnt) == 0) {
                            next = neighbor;
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
                        else if (rnd.nextInt(++cnt) == 0) {
                            next = neighbor;
                        }
                    }
                    if (cnt == 1) {
                        next.state[off] = V;
                        continue mainLoop;
                    }
                }
                else {
                    next = cur;
                    cnt = 1;
                    off = TS2 & 0x1;
                    V = S2;
                    for (Cell neighbor : cur.neighbors) {
                        int val = neighbor.state[off];
                        if ((val >> 1) == TS2) {
                            V ^= val;
                        }
                        else if (rnd.nextInt(++cnt) == 0) {
                            next = neighbor;
                        }
                    }
                    if (cnt == 1) {
                        next.state[off] = V;
                        continue mainLoop;
                    }
                }
                cur = next;
            }
            else if (TS2 == TS1) {
                Cell next = null;
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
                    else if (rnd.nextInt(++cnt) == 0) {
                        next = neighbor;
                    }
                }
                if (cnt == 1) {
                    next.state[off] = V;
                    sum += V & 0x1;
                    cnt = 0;
                }

                int cnt2 = cnt;
                Cell rnext = null;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[2];
                    if ((val >> 1) <= TS2) {
                        if ((val >> 1) < TS2) {
                            ++cnt;
                            rnext = neighbor;
                        }
                        if (cnt2 > 0 && rnd.nextInt(++cnt2) == 0) {
                            next = neighbor;
                        }
                    }
                }
                if (cnt > 0) {
                    cur = rnext != null ? rnext : next;
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

                if (vis) {
                    // Color live cells according to the current thread id
                    int color = nextState == STATE0 ? 0 : COLORS[id % COLORS.length];
                    // Color all cells according to the current thread id
                    //int color = COLORS[id % COLORS.length];
                    // Color all cells according to the current generation
                    //int color = COLORS[TS1 % COLORS.length];
                    imgData[cur.idx] = color;
                }
            }
            else {
                int off = TS2 & 0x1;
                int cnt = 1;
                Cell next = cur;
                int V = S2;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[off];
                    if ((val >> 1) == TS2) {
                        V ^= val;
                    }
                    else {
                        if (rnd.nextInt(++cnt) == 0) {
                            next = neighbor;
                        }
                    }
                }
                if (cnt == 1) {
                    next.state[off] = V;
                }

                off = TS1 & 0x1;
                cnt = 0;
                int sum = 0;
                for (Cell neighbor : cur.neighbors) {
                    int val = neighbor.state[off];
                    if ((val >> 1) == TS1) {
                        sum += val & 0x1;
                    }
                    else {
                        if (rnd.nextInt(++cnt) == 0) {
                            next = neighbor;
                        }
                    }
                }
                if (cnt > 0) {
                    continue mainLoop;
                }

                // Apply the rule of Life
                int nextState = sum < 2 ? STATE0 : sum == 2 ? (S1 & 0x1) : sum == 3 ? STATE1 : STATE0;
                cur.state[1 - off] = ((TS1 + 1) << 1) | nextState;
                cur = next;
            }
        }
    }

    public String[] execute()
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

        // Produce result
        String[] result = new String[Height];
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Height; ++r) {
            sb.setLength(0);
            for (int c = 0; c < Width; ++c) {
                Cell cell = cells[r * Width + c];
                sb.append(Math.max(cell.state[0], cell.state[1]) & 0x1);
            }
            result[r] = sb.toString();
        }
        return result;
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
        Width = w;
        Height = h;
        maxTime = T0 + t;
        nThreads = p;
        vis = v;

        // Initialize visualization
        if (vis) {
            BufferedImage img = new BufferedImage(Width, Height, BufferedImage.TYPE_INT_RGB);
            imgData = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

            JFrame frame = new JFrame() {
                public void paint(Graphics g) {
                    g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                }
            };
            frame.setSize(Width, Height);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
            frame.setVisible(true);

            Timer timer = new Timer(40, (e) -> frame.repaint());
            timer.start();
        }

        // Initialize the state
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

    public static NoSyncLife fromRLE(RLE rle, int width, int height, int time, int par, boolean vis)
    {
        // Re-center
        width = Math.max(width, rle.getW());
        height = Math.max(height, rle.getH());
        int[] state = new int[width * height];
        int x0 = (width - rle.getW()) / 2;
        int y0 = (height - rle.getH()) / 2;
        for (int x = 0; x < rle.getW(); ++x) {
            for (int y = 0; y < rle.getH(); ++y) {
                state[(y + y0) * width + x + x0] = rle.getState(x, y);
            }
        }
        return new NoSyncLife(width, height, time, par, vis, state);
    }

    public static NoSyncLife fromRLE(RLE rle, int time, int par, boolean vis)
    {
        return fromRLE(rle, rle.getW(), rle.getH(), time, par, vis);
    }

    public static void main(String[] args)
    {
        int width = 0;
        int height = 0;
        int time = 1000;
        int parallelism = 1;
        boolean vis = true;
        RLE rle = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-w")) {
                width = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-h")) {
                height = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-p")) {
                parallelism = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-t")) {
                time = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-novis")) {
                vis = false;
            }
            else {
                rle = RLE.fromFile(args[i]);
                if (rle == null) {
                    return;
                }
            }
        }

        if (rle == null) {
            rle = RLE.getAcorn();
        }

        NoSyncLife lf = fromRLE(rle, width, height, time, parallelism, vis);
        long start = System.currentTimeMillis();
        String[] state = lf.execute();
        long end = System.currentTimeMillis();

        for (String str : state) {
            System.out.println(str);
        }
        System.out.println("Score: " + (1000l * time * lf.Width * lf.Height / (end-start)) + " ops/sec");
    }
}
