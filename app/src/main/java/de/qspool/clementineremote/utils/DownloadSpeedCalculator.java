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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

/** Computes speed on demand from timestamped byte samples over a sliding window; no background Timer thread. */
public class DownloadSpeedCalculator {

    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(3);

    private static final long SAMPLE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private static final long MIN_ELAPSED_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private static final class Sample {
        final long timeNanos;
        final int totalBytes;

        Sample(long timeNanos, int totalBytes) {
            this.timeNanos = timeNanos;
            this.totalBytes = totalBytes;
        }
    }

    private final IDownloadCalculatorSource mDataSource;

    private final Deque<Sample> mSamples = new ArrayDeque<>();

    public DownloadSpeedCalculator(final IDownloadCalculatorSource dataSource) {
        mDataSource = dataSource;
        long now = System.nanoTime();
        int initialBytes = dataSource != null ? dataSource.getBytesTotalDownloaded() : 0;
        mSamples.addLast(new Sample(now, initialBytes));
    }

    public synchronized int getDownloadSpeed() {
        if (mDataSource == null) {
            return 0;
        }
        long now = System.nanoTime();
        int totalBytes = mDataSource.getBytesTotalDownloaded();

        Sample last = mSamples.peekLast();
        if (last == null || (now - last.timeNanos) >= SAMPLE_INTERVAL_NANOS) {
            mSamples.addLast(new Sample(now, totalBytes));
        }

        // Prune expired samples, keeping at least two so an anchor stays available across pauses.
        while (mSamples.size() > 2 && (now - mSamples.peekFirst().timeNanos) > WINDOW_NANOS) {
            mSamples.removeFirst();
        }

        Sample anchor = mSamples.peekFirst();
        if (anchor == null) {
            return 0;
        }
        long elapsedNanos = now - anchor.timeNanos;
        // Widen before subtracting to prevent 32-bit int wraparound on large byte counts.
        long bytesDelta = (long) totalBytes - anchor.totalBytes;
        if (elapsedNanos < MIN_ELAPSED_NANOS || bytesDelta <= 0) {
            return 0;
        }
        long bytesPerSecond = (bytesDelta * TimeUnit.SECONDS.toNanos(1)) / elapsedNanos;
        return (int) Math.min(Integer.MAX_VALUE, bytesPerSecond);
    }
}
