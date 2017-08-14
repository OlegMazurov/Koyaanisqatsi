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

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;

/**
 * Chaotic Life
 * <p>
 * https://github.com/OlegMazurov/Koyaanisqatsi
 */

public class NoSyncLife {

    private static final int STATE0 = 0;
    private static final int STATE1 = 1;
    private static final int MAXNGHBR = 8;
    private static final int[] COLORS = {
            0xffffff, 0xff0000, 0x00ff00, 0x0000ff,
            0xffff00, 0xff00ff, 0x00ffff, 0x80ff00,
    };

    private final int Width;
    private final int Height;
    private final int L;
    private final int[] state;
    private final int maxTime;
    private final int nThreads;
    private final boolean vis;

    private BufferedImage img;
    private int[] imgData;

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

    /**
     * Cell neighbors wrapped around a torus:
     * -------------
     * +1 | 7 | 6 | 5 |
     * -------------
     * r | 8 | 0 | 4 |
     * -------------
     * -1 | 1 | 2 | 3 |
     * -------------
     * -1   c  +1
     */
    private int getNeighbor(int idx, int i) {
        int r = idx / Width;
        int c = idx % Width;
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

//    public static int[] pixels(BufferedImage img, int x, int y, int w, int h, int[] pixels) {
//        if (w == 0 || h == 0) {
//            return new int[0];
//        }
//
//        if (pixels == null) {
//            pixels = new int[w * h];
//        } else if (pixels.length < w * h) {
//            throw new IllegalArgumentException("Pixels array must have a length >= w * h");
//        }
//
//        int imageType = img.getType();
//        if (imageType == BufferedImage.TYPE_INT_ARGB || imageType == BufferedImage.TYPE_INT_RGB) {
//            Raster raster = img.getRaster();
//            return (int[]) raster.getDataElements(x, y, w, h, pixels);
//        }
//
//        // Unmanages the image
//        return img.getRGB(x, y, w, h, pixels, 0, w);
//    }

    private void runUnsync(int id) {
        PseudoRandom rnd = new PseudoRandom(id);

        // Start apart
        int idx = L * id / nThreads;

        long threadID = Thread.currentThread().getId();

        mainLoop:
        for (; ; ) {
            int s0 = state[3 * idx];
            int s1 = state[3 * idx + 1];
            int S0 = Math.min(s0, s1);
            int S1 = Math.max(s0, s1);
            int S2 = state[3 * idx + 2];

            int TS0 = S0 >> 1;
            int TS1 = S1 >> 1;
            int TS2 = S2 >> 1;

            if (TS2 < TS1) {
                int sidx = -1;
                int off = TS1 & 0x1;
                int cnt = 0;
                int V = S1;
                for (int n = 1; n <= MAXNGHBR; ++n) {
                    int nidx = getNeighbor(idx, n);
                    int val = state[3 * nidx + off];
                    if ((val >> 1) == TS1) {
                        V ^= val;
                    } else if (rnd.nextInt(++cnt) == 0) {
                        sidx = nidx;
                    }
                }
                if (cnt == 0) {
                    state[3 * idx + 2] = V;
                    continue mainLoop;
                }

                if (TS2 < TS0) {
                    cnt = 0;
                    off = TS0 & 0x1;
                    V = S0;
                    for (int n = 1; n <= MAXNGHBR; ++n) {
                        int nidx = getNeighbor(idx, n);
                        int val = state[3 * nidx + off];
                        if ((val >> 1) == TS0) {
                            V ^= val;
                        } else if (rnd.nextInt(++cnt) == 0) {
                            sidx = nidx;
                        }
                    }
                    if (cnt == 0) {
                        state[3 * idx + 2] = V;
                        continue mainLoop;
                    }
                } else if (TS2 == TS0) {
                    cnt = 0;
                    off = TS0 & 0x1;
                    V = S0 ^ S2;
                    for (int n = 1; n <= MAXNGHBR; ++n) {
                        int nidx = getNeighbor(idx, n);
                        int val = state[3 * nidx + off];
                        if ((val >> 1) == TS0) {
                            V ^= val;
                        } else if (rnd.nextInt(++cnt) == 0) {
                            sidx = nidx;
                        }
                    }
                    if (cnt == 1) {
                        state[3 * sidx + off] = V;
                        continue mainLoop;
                    }
                } else {
                    sidx = idx;
                    cnt = 1;
                    off = TS2 & 0x1;
                    V = S2;
                    for (int n = 1; n <= MAXNGHBR; ++n) {
                        int nidx = getNeighbor(idx, n);
                        int val = state[3 * nidx + off];
                        if ((val >> 1) == TS2) {
                            V ^= val;
                        } else if (rnd.nextInt(++cnt) == 0) {
                            sidx = nidx;
                        }
                    }
                    if (cnt == 1) {
                        state[3 * sidx + off] = V;
                        continue mainLoop;
                    }
                }
                idx = sidx;
            } else if (TS2 == TS1) {
                int sidx = -1;
                int off = TS2 & 0x1;
                int cnt = 0;
                int sum = 0;

                int V = S1 ^ S2;
                for (int n = 1; n <= MAXNGHBR; ++n) {
                    int nidx = getNeighbor(idx, n);
                    int val = state[3 * nidx + off];
                    if ((val >> 1) == TS2) {
                        V ^= val;
                        sum += val & 0x1;
                    } else if (rnd.nextInt(++cnt) == 0) {
                        sidx = nidx;
                    }
                }
                if (cnt == 1) {
                    state[3 * sidx + off] = V;
                    sum += V & 0x1;
                    cnt = 0;
                }

                int cnt2 = cnt;
                int ridx = -1;
                for (int n = 1; n <= MAXNGHBR; ++n) {
                    int nidx = getNeighbor(idx, n);
                    int val = state[3 * nidx + 2];
                    if ((val >> 1) <= TS2) {
                        if ((val >> 1) < TS2) {
                            ++cnt;
                            ridx = nidx;
                        }
                        if (cnt2 > 0 && rnd.nextInt(++cnt2) == 0) {
                            sidx = nidx;
                        }
                    }
                }
                if (cnt > 0) {
                    idx = ridx != -1 ? ridx : sidx;
                    continue mainLoop;
                }

                // Are we done?
                if (TS1 == maxTime) {
                    for (int n = 0; n < L; ++n) {
                        if (++idx == L) idx = 0;
                        if (Math.max(state[3 * idx], state[3 * idx + 1]) >> 1 != maxTime) continue mainLoop;
                    }
                    return;
                }

                // Apply the rule of Life
                int nextState = sum < 2 ? STATE0 : sum == 2 ? (S1 & 0x1) : sum == 3 ? STATE1 : STATE0;
                state[3 * idx + 1 - off] = ((TS1 + 1) << 1) | nextState;

                if (vis) {
                    // Color live cells according to the current thread id

                    int color = nextState == STATE0 ? 0 : COLORS[(int) (threadID % COLORS.length)];
                    // Color all cells according to the current thread id
                    //int color = COLORS[(int)(Thread.currentThread().getId() % COLORS.length)];
                    // Color all cells according to the current generation
                    //int color = COLORS[TS1 % COLORS.length];

                    //img.setRGB(idx % Width, idx / Width, color);
                    imgData[idx] = color;
                    //        raster.setDataElements(x, y, colorModel.getDataElements(rgb, null));

                }
            } else {
                int off = TS2 & 0x1;
                int cnt = 1, sidx = idx;
                int V = S2;
                for (int n = 1; n <= MAXNGHBR; ++n) {
                    int nidx = getNeighbor(idx, n);
                    int val = state[3 * nidx + off];
                    if ((val >> 1) == TS2) {
                        V ^= val;
                    } else {
                        if (rnd.nextInt(++cnt) == 0) {
                            sidx = nidx;
                        }
                    }
                }
                if (cnt == 1) {
                    state[3 * sidx + off] = V;
                    continue mainLoop;
                }

                off = TS1 & 0x1;
                cnt = 0;
                int sum = 0;
                for (int n = 1; n <= MAXNGHBR; ++n) {
                    int nidx = getNeighbor(idx, n);
                    int val = state[3 * nidx + off];
                    if ((val >> 1) == TS1) {
                        sum += val & 0x1;
                    } else {
                        if (rnd.nextInt(++cnt) == 0) {
                            sidx = nidx;
                        }
                    }
                }
                if (cnt > 0) {
                    continue mainLoop;
                }

                // Apply the rule of Life
                int nextState = sum < 2 ? STATE0 : sum == 2 ? (S1 & 0x1) : sum == 3 ? STATE1 : STATE0;
                state[3 * idx + 1 - off] = ((TS1 + 1) << 1) | nextState;
                idx = sidx;
            }
        }
    }

    public String[] execute() {
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
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        // Produce result
        String[] result = new String[Height];
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Height; ++r) {
            sb.setLength(0);
            for (int c = 0; c < Width; ++c) {
                int idx = 3 * (r * Width + c);
                sb.append(Math.max(state[idx], state[idx + 1]) & 0x1);
            }
            result[r] = sb.toString();
        }
        return result;
    }

    public NoSyncLife(int w, int h, int t, int p, boolean v, int[] s) {
        Width = w;
        Height = h;
        L = Width * Height;
        maxTime = t;
        nThreads = p;
        vis = v;

        // Initialize visualization
        if (vis) {
            img = new BufferedImage(Width, Height, BufferedImage.TYPE_INT_RGB);
            Raster raster = img.getRaster();
            DataBufferInt imgData = (DataBufferInt) raster.getDataBuffer();
            this.imgData = imgData.getData();
            //System.out.println(raster.getClass() + " " + imgData + " " + imgData.getClass());


            //getDataElements(x, y, w, h, pixels);


            JFrame frame = new JFrame() {
                public void paint(Graphics g) {

                    g.drawImage(img, 0, 0,
                            getWidth(), getHeight(), //stretch scale
                            null);
                }
            };
            frame.setIgnoreRepaint(true);
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
        state = new int[L * 3];
        for (int r = 0; r < Height; ++r)
            for (int c = 0; c < Width; ++c) {
                int idx = r * Width + c;
                int st = s[idx] == 0 ? STATE0 : STATE1;
                state[3 * idx + 1] = (1 << 1) | st;
                int R = st;
                for (int n = 1; n <= MAXNGHBR; ++n) {
                    R ^= s[getNeighbor(idx, n)] == 0 ? STATE0 : STATE1;
                }
                state[3 * idx + 2] = (1 << 1) | R;
            }
    }

    public static NoSyncLife fromRLE(RLE rle, int width, int height, int time, int par, boolean vis) {
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

    public static NoSyncLife fromRLE(RLE rle, int time, int par, boolean vis) {
        return fromRLE(rle, rle.getW(), rle.getH(), time, par, vis);
    }

    public static void main(String[] args) {
        int width = 0;
        int height = 0;
        int time = 1000;
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        boolean vis = true;
        RLE rle = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-w")) {
                width = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-h")) {
                height = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-p")) {
                parallelism = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-t")) {
                time = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-novis")) {
                vis = false;
            } else {
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
        System.out.println("Time: " + (end - start) + " ms");
    }
}
