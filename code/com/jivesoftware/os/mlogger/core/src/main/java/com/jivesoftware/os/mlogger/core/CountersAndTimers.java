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

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author jonathan
 */
public final class CountersAndTimers {

    private final static Logger logger = LogManager.getLogger(CountersAndTimers.class.getName());
    private final static ConcurrentHashMap<String, CountersAndTimers> loggers = new ConcurrentHashMap<>();

    public static CountersAndTimers getOrCreate(Class _class) {
        return getOrCreate(classToKey(_class));
    }

    public static CountersAndTimers getOrCreate(String name) {
        CountersAndTimers got = loggers.get(name);
        if (got == null) {
            got = new CountersAndTimers(name);
            CountersAndTimers had = loggers.putIfAbsent(name, got);
            if (had != null) {
                got = had;
            }
        }
        return got;
    }

    public static Collection<CountersAndTimers> getAll() {
        return loggers.values();
    }

    public static void resetAll() {
        for (CountersAndTimers cat : loggers.values()) {
            cat.resetAllCounterAndTimers();
        }
    }

    public static String classToKey(Class _class) {
        final String path = _class.getCanonicalName();
        if (path == null) {
            throw new IllegalArgumentException("Doesn't work with anonymous classes");
        }
        return path;
    }
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicCounter> atomicCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final String name;
    private final ConcurrentHashMap<String, CountersAndTimers> tenantSpecifcMetric = new ConcurrentHashMap<>();

    private CountersAndTimers(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Collection<CountersAndTimers> getAllTenantSpecficMetrics() {
        return tenantSpecifcMetric.values();
    }

    public CountersAndTimers getTenantMetric(String tenant) {
        CountersAndTimers got = tenantSpecifcMetric.get(tenant);
        if (got == null) {
            got = new CountersAndTimers(name + ">tenant>" + tenant);
            CountersAndTimers had = tenantSpecifcMetric.putIfAbsent(tenant, got);
            if (had != null) {
                got = had;
            }
        }
        return got;
    }

    public Set<Entry<String, Counter>> getCounters() {
        return counters.entrySet();
    }

    public Set<Entry<String, AtomicCounter>> getAtomicCounters() {
        return atomicCounters.entrySet();
    }

    public Set<Entry<String, Timer>> getTimers() {
        return timers.entrySet();
    }

    public Counter counter(ValueType type, String key) {
        Counter counter = counters.get(key);

        if (counter == null) {
            counter = new Counter(type);
            Counter originalCounter = counters.putIfAbsent(key, counter);
            if (originalCounter != null) {
                return originalCounter;
            }
            register(name + ">" + key, counter);
        }
        return counter;
    }

    public AtomicCounter atomicCounter(ValueType type, String key) {
        AtomicCounter counter = atomicCounters.get(key);

        if (counter == null) {
            counter = new AtomicCounter(type);
            AtomicCounter originalCounter = atomicCounters.putIfAbsent(key, counter);
            if (originalCounter != null) {
                return originalCounter;
            }
            register(name + ">" + key, counter);
        }
        return counter;
    }

    public void startNanoTimer(String key) {

        String threadKey = key + Thread.currentThread().getId();
        Long startTime = startTimes.get(threadKey);

        if (startTime == null) {
            startTime = System.nanoTime();
            startTimes.put(threadKey, startTime);
        }
    }

    public long stopNanoTimer(String key, String recordedKey) {

        String threadKey = key + Thread.currentThread().getId();
        Long startTime = startTimes.remove(threadKey);
        if (startTime == null) {
            logger.warn("Trying to stop a timer you never called start on: TimerId:" + key);
            return -1;
        }

        Timer timer = timers.get(recordedKey);
        if (timer == null) {
            timer = new Timer(5000);
            Timer exisitingTimer = timers.putIfAbsent(recordedKey, timer);
            if (exisitingTimer == null) {
                register(name + ">" + recordedKey, timer);
            } else {
                timer = exisitingTimer;
            }
        }
        long elapseInNanos = System.nanoTime() - startTime;
        timer.sample(elapseInNanos);
        return elapseInNanos;
    }

    public void startTimer(String key) {

        String threadKey = key + Thread.currentThread().getId();
        Long startTime = startTimes.get(threadKey);

        if (startTime == null) {
            startTime = System.currentTimeMillis();
            startTimes.put(threadKey, startTime);
        }
    }

    /**
     *
     * @param key
     * @param recordedKey
     * @return
     * @deprecated Suggested you use stopAndGetTimer.
     */
    @Deprecated
    public long stopTimer(String key, String recordedKey) {
        return stopAndGetTimer(key, recordedKey, 5000).getLastSample();
    }

    public Timer stopAndGetTimer(String key, String recordedKey, int sampleWindowSize) {

        String threadKey = key + Thread.currentThread().getId();
        Long startTime = startTimes.remove(threadKey);
        if (startTime == null) {
            logger.warn("Trying to stop a timer you never called start on: TimerId:" + key);
            return new Timer(2);
        }

        Timer timer = timers.get(recordedKey);
        if (timer == null) {
            timer = new Timer(sampleWindowSize);
            Timer exisitingTimer = timers.putIfAbsent(recordedKey, timer);
            if (exisitingTimer == null) {
                register(name + ">" + recordedKey, timer);
            } else {
                timer = exisitingTimer;
            }
        }
        long elapseInMillis = System.currentTimeMillis() - startTime;
        timer.sample(elapseInMillis);
        return timer;
    }

    /**
     *
     * @param key
     * @return will return null if key doesn't exist.
     */
    public Counter getCounterIfAvailable(final String key) {
        return counters.get(key);
    }

    public AtomicCounter getAtomicCounterIfAvailable(final String key) {
        return atomicCounters.get(key);
    }

    public Timer getTimerIfAvailable(final String key) {
        return timers.get(key);
    }

    private static void register(String name, Object mbean) {
        name = name.replace(':', '_');

        String[] parts = name.split(">");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append("leaf");
            sb.append(i);
            sb.append("=");
            sb.append(parts[i]);
        }

        Class clazz = mbean.getClass();
        String objectName = "service.metrics:type=" + clazz.getSimpleName() + "," + sb.toString();

        logger.debug("registering bean: " + objectName);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        try {
            ObjectName mbeanName = new ObjectName(objectName);

            // note: unregister any previous, as this may be a replacement
            if (mbs.isRegistered(mbeanName)) {
                mbs.unregisterMBean(mbeanName);
            }

            mbs.registerMBean(mbean, mbeanName);

            logger.debug("registered bean: " + objectName);
        } catch (MalformedObjectNameException | NotCompliantMBeanException |
            InstanceAlreadyExistsException | InstanceNotFoundException | MBeanRegistrationException e) {
            logger.warn("unable to register bean: " + objectName + "cause: " + e.getMessage(), e);
        }
    }

    public void resetAllCounterAndTimers() {
        for (Counter v : counters.values()) {
            v.reset();
        }
        for (AtomicCounter v : atomicCounters.values()) {
            v.reset();
        }
        for (Timer v : timers.values()) {
            v.reset();
        }
    }
}
