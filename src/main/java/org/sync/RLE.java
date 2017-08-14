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

import java.io.*;

/**
 * Created by olegmazurov on 7/10/17.
 */
public class RLE {
    private int w;
    private int h;
    private int[] state;

    public int getW() {
        return w;
    }

    public int getH() {
        return h;
    }

    public int[] getState() {
        return state;
    }

    public int getState(int x, int y) {
        return state[y * w + x];
    }

    /* Acorn pattern:
     *
     *     X
     *       X
     *    XX  XXX
     */
    public static RLE getAcorn() {
        int w = 200;
        int h = 200;
        int[] state = new int[w * h];
        int start = h/2*w + w/2;
        state[start] = 1;
        state[start+1] = 1;
        state[start+4] = 1;
        state[start+5] = 1;
        state[start+6] = 1;
        state[start+w+3]   = 1;
        state[start+2*w+1] = 1;

        RLE rle = new RLE();
        rle.w = w;
        rle.h = h;
        rle.state = state;
        return rle;
    }

    public static RLE fromFile(String fname)
    {
        try {
            if (fname == null || fname.length() == 0) {
                System.err.println("ERROR: empty file name");
            }
            char ch = fname.charAt(0);
            String resourceName = ch == '/' ? fname : "/" + fname;

            InputStream is = RLE.class.getResourceAsStream(resourceName);
            BufferedReader in = is != null ? new BufferedReader(new InputStreamReader(is)) :
                    new BufferedReader(new FileReader(fname));

            // Skip comments
            String line;
            for (;;) {
                line = in.readLine();
                if (line.charAt(0) != '#') {
                    break;
                }
            }

            // Parse parameters
            int w = 0;
            int h = 0;
            String[] tokens = line.split("[ ,]+");
            label:
            for (int i = 0; i < tokens.length; ++i) {
                switch (tokens[i]) {
                    case "x":
                        w = Integer.parseInt(tokens[i + 2]);
                        i += 2;
                        break;
                    case "y":
                        h = Integer.parseInt(tokens[i + 2]);
                        i += 2;
                        break;
                    default:
                        break label;
                }
            }

            // Read the state
            int[] state = new int[w * h];
            int idx = 0;
            int rcnt = 0;
            loop:       for (;;) {
                line = in.readLine();
                if (line == null) {
                    break;
                }
                for (int i=0; i<line.length(); ++i) {
                    char c = line.charAt(i);
                    switch (c) {
                        case ' ': case '\t': case '\n': case '\r':
                            break;
                        case '0': case '1': case '2': case '3': case '4':
                        case '5': case '6': case '7': case '8': case '9':
                            rcnt = rcnt*10 + (c-'0');
                            break;
                        case 'o':
                            if (rcnt == 0) rcnt = 1;
                            for (int j=0; j<rcnt; ++j) {
                                state[idx++] = 1;
                            }
                            rcnt = 0;
                            break;
                        case 'b':
                            if (rcnt == 0) rcnt = 1;
                            for (int j=0; j<rcnt; ++j) {
                                state[idx++] = 0;
                            }
                            rcnt = 0;
                            break;
                        case '$':
                            if (rcnt == 0) rcnt = 1;
                            int nidx = ((idx - 1)/w + rcnt) * w;
                            while (idx < nidx) {
                                state[idx++] = 0;
                            }
                            rcnt = 0;
                            break;
                        case '!':
                            while (idx < state.length) {
                                state[idx++] = 0;
                            }
                            break loop;
                    }
                }
            }

            RLE rle = new RLE();
            rle.w = w;
            rle.h = h;
            rle.state = state;
            return rle;
        }
        catch(IOException e) {
            System.err.println("ERROR: bad file: " + fname);
            return null;
        }
    }

}
