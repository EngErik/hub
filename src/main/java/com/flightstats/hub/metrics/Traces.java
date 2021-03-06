package com.flightstats.hub.metrics;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightstats.hub.model.SingleTrace;
import com.flightstats.hub.model.Trace;
import com.flightstats.hub.util.ObjectRing;
import org.joda.time.DateTime;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;

public class Traces {

    private static final int LIMIT = 50;
    private long start = System.currentTimeMillis();
    private long end;
    private final String id = UUID.randomUUID().toString();
    private final List<Trace> traces = Collections.synchronizedList(new ArrayList<>());
    private final ObjectRing<Trace> lastTraces = new ObjectRing<>(LIMIT);

    public Traces(Object... objects) {
        add(objects);
    }

    public void end() {
        end = System.currentTimeMillis();
        add("end");
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public long getTime() {
        if (end > 0) {
            return end - start;
        } else {
            return System.currentTimeMillis() - start;
        }
    }

    public void add(Trace trace) {
        if (traces.size() > LIMIT) {
            lastTraces.put(trace);
        } else {
            traces.add(trace);
        }
    }

    public void add(Object... objects) {
        add(new SingleTrace(objects));
    }

    public void add(String string, SortedSet sortedSet) {
        if (sortedSet.isEmpty()) {
            add(string, "empty set");
        } else {
            add(string, sortedSet.size(), sortedSet.first(), sortedSet.last());
        }
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getStart() {
        return start;
    }

    public String getId() {
        return id;
    }

    public void log(long slowLogThresholdMillis, boolean trace, Logger logger) {
        long processingTime = System.currentTimeMillis() - start;
        if (processingTime >= slowLogThresholdMillis) {
            logger.info("slow processing of {} millis. trace: {}", processingTime, getOutput(logger));
        } else if (trace) {
            logger.info("requested trace: {}", getOutput(logger));
        }
    }

    public void log(Logger logger) {
        logger.info("trace: {}", getOutput(logger));
    }

    private String getOutput(Logger logger) {
        try {
            StringBuilder builder = new StringBuilder("\n\t");
            outputTraces((trace) -> builder.append(trace).append("\n\t"));
            return builder.toString();
        } catch (Exception e) {
            logger.warn("unable to log {} traces {}", traces);
            return "unable to output";
        }
    }

    public void output(ObjectNode root) {
        root.put("first", traces.get(0).context());
        root.put("id", id);
        root.put("start", new DateTime(this.start).toString());
        root.put("millis", getTime());
        ArrayNode traceRoot = root.putArray("trace");
        outputTraces(traceRoot::add);
    }

    void outputTraces(Consumer<String> consumer) {
        synchronized (traces) {
            for (Trace trace : traces) {
                consumer.accept(trace.toString());
            }
            if (lastTraces.getTotalSize() > LIMIT) {
                consumer.accept("   ...cut " + (lastTraces.getTotalSize() - LIMIT) + " lines...");
            }
            List<Trace> lastItems = lastTraces.getItems();
            for (Trace trace : lastItems) {
                consumer.accept(trace.toString());
            }
        }
    }


}
