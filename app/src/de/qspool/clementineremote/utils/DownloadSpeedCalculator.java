/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2014, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DownloadSpeedCalculator {

    private final static int HISTORY_CYCLES = 6;

    private final static int INTERVALL = 500;

    private int index = 0;

    private List<Integer> mLastDownloadCycles = Arrays.asList(new Integer[HISTORY_CYCLES]);

    private int lastDownloaded;

    private int mDownloadSpeed;

    public DownloadSpeedCalculator(final IDownloadCalculatorSource dataSource) {

        // Get the average download speed of the past 5 seconds
        new Timer().scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                int totalBytes = dataSource.getBytesTotalDownloaded();
                mLastDownloadCycles.set(index, totalBytes - lastDownloaded);

                int sum = 0;
                int count = 0;
                for (Integer i : mLastDownloadCycles) {
                    if (i != null) {
                        sum += i;
                        count++;
                    }
                }

                mDownloadSpeed = (sum / count) * (1000 / INTERVALL);

                index = (index + 1) % mLastDownloadCycles.size();
                lastDownloaded = totalBytes;
            }
        }, 0, INTERVALL);
    }

    public int getDownloadSpeed() {
        return mDownloadSpeed;
    }
}
