/*
 * Copyright 2013 Jive Software, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.jivesoftware.os.mlogger.core;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author jonathan
 */
public class Timer implements TimerMXBean {

    final DescriptiveStatistics stats;
    private volatile long sampleCount;
    private long lastSample;

    public Timer(int sampleWindowSize) {
        this.stats = new DescriptiveStatistics(sampleWindowSize);
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public long getLastSample() {
        return lastSample;
    }

    public void sample(long sample) {
        sampleCount++;
        lastSample = sample;
        stats.addValue(sample);
    }

    public void reset() {
        lastSample = 0;
        sampleCount = 0;
        stats.clear();
    }

    @Override
    public double getMin() {
        return stats.getMin();
    }

    @Override
    public double getMax() {
        return stats.getMax();
    }

    @Override
    public double getMean() {
        return stats.getMean();
    }

    @Override
    public double getVariance() {
        return stats.getVariance();
    }

    @Override
    public double get50ThPercentile() {
        return stats.getPercentile(50);
    }

    @Override
    public double get75ThPercentile() {
        return stats.getPercentile(50);
    }

    @Override
    public double get90ThPercentile() {
        return stats.getPercentile(90);
    }

    @Override
    public double get95ThPercentile() {
        return stats.getPercentile(95);
    }

    @Override
    public double get99ThPercentile() {
        return stats.getPercentile(99);
    }

    /**

    @param percentile 0 - 100;
    @return
    */
    public double getPercentile(double percentile) {
        return stats.getPercentile(percentile);
    }
}
