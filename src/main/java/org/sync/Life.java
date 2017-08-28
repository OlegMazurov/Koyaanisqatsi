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
import java.awt.image.DataBufferInt;

/**
 * Chaotic Life
 *
 * https://github.com/OlegMazurov/Koyaanisqatsi
 *
 */

public abstract class Life {

    public enum Type { ORDINARY, NOSYNC, NOWAIT };
    protected static final int STATE0 = 0;
    protected static final int STATE1 = 1;
    protected static final int T0 = 0;
    private static final int[] COLORS = {
            0xff8000, 0xffffff, 0xff0000, 0x00ff00,
            0x0000ff, 0xffff00, 0xff00ff, 0x00ffff,
    };

    protected static Type type = Type.NOSYNC;

    protected final int Width;
    protected final int Height;
    protected final int maxTime;
    protected final int nThreads;

    private final boolean vis;
    private int[] imgData;

    public abstract void execute();

    protected abstract int getState(int row, int col);

    protected void setColor(int idx, int color) {
        if (vis) {
            imgData[idx] = color == 0 ? 0 : COLORS[color % COLORS.length];
        }
    }

    protected Life(int w, int h, int t, int p, boolean v) {
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
    }

    public static Life fromRLE(RLE rle, Type type, int width, int height, int time, int par, boolean vis)
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

        Life res = null;
        switch (type) {
            case ORDINARY:
                res = new OrdinaryLife(width, height, time, par, vis, state);
                break;
            case NOSYNC:
                res = new NoSyncLife(width, height, time, par, vis, state);
                break;
            case NOWAIT:
                res = new NoWaitLife(width, height, time, par, vis, state);
                break;
            default:
                System.err.println("ERROR: unknown type: " + type);
                System.exit(1);
        }
        return res;
    }

    public static Life fromRLE(RLE rle, Type type, int time, int par, boolean vis)
    {
        return fromRLE(rle, type, rle.getW(), rle.getH(), time, par, vis);
    }

    public String[] getResult() {
        String[] result = new String[Height];
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Height; ++r) {
            sb.setLength(0);
            for (int c = 0; c < Width; ++c) {
                sb.append(getState(r, c));
            }
            result[r] = sb.toString();
        }
        return result;
    }

    public static void main(String[] args)
    {
        int width = 0;
        int height = 0;
        int time = 10000;
        int parallelism = Runtime.getRuntime().availableProcessors();
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
            else if (args[i].equals("-T")) {
                type = Type.valueOf(args[++i]);
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

        Life lf = fromRLE(rle, type, width, height, time, parallelism, vis);
        long start = System.currentTimeMillis();
        lf.execute();
        long end = System.currentTimeMillis();

        String[] state = lf.getResult();
        for (String str : state) {
            System.out.println(str);
        }
        System.out.println("Score: " + (1000l * time * lf.Width * lf.Height / (end-start)) + " ops/sec");
    }
}
