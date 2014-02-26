package com.flightstats.hub.replication;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.flightstats.hub.model.Content;
import com.flightstats.hub.service.ChannelLinkBuilder;
import com.flightstats.hub.util.RuntimeInterruptedException;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SequenceIterator uses a WebSocket to keep up with the latest sequence.
 * It is designed to skip over missing sequences, should they occur.
 * SequenceIterator is not thread safe, and should only be used from a single thread.
 *
 */
@ClientEndpoint()
public class SequenceIterator implements Iterator<Content> {

    private final static Logger logger = LoggerFactory.getLogger(SequenceIterator.class);
    private final ChannelUtils channelUtils;
    private final MetricRegistry metricRegistry;
    private final Channel channel;
    private final WebSocketContainer container;
    private final String channelUrl;
    private final Object lock = new Object();

    private final AtomicLong latest = new AtomicLong(0);
    private long current;
    private AtomicBoolean shouldExit = new AtomicBoolean(false);
    private boolean connected = false;

    public SequenceIterator(long startSequence, ChannelUtils channelUtils, Channel channel,
                            WebSocketContainer container, MetricRegistry metricRegistry) {
        this.current = startSequence;
        this.channelUtils = channelUtils;
        this.channel = channel;
        this.container = container;
        this.metricRegistry = metricRegistry;
        String url = channel.getUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.channelUrl = url;
        startSocket();
        startMetrics();
    }

    @Override
    public boolean hasNext() {
        while (!shouldExit.get()) {
            if (current < latest.get()) {
                current++;
                return true;
            }
            synchronized (lock) {
                try {
                    lock.wait(TimeUnit.MINUTES.toMillis(5));
                } catch (InterruptedException e) {
                    throw new RuntimeInterruptedException(e);
                }
            }
        }
        return false;
    }

    private long getDelta() {
        return latest.get() - current;
    }

    private void startMetrics() {
        String name = "Replication." + URI.create(channelUrl).getHost() + "." + channel.getName() + ".delta";
        metricRegistry.remove(name);
        metricRegistry.register(name, new Gauge<Long>() {
            @Override
            public Long getValue() {
                long delta = getDelta();
                logger.info("delta is " + delta);
                return delta;
            }
        });
    }

    @Override
    public Content next() {
        Optional<Content> optional = channelUtils.getContent(channelUrl, current);
        while (!optional.isPresent()) {
            //todo - gfm - 1/25/14 - seems like this missing records should be logged somewhere, perhaps to a missing records channel
            logger.warn("unable to get content " + channelUrl + "/" + current);
            current++;
            optional = channelUtils.getContent(channelUrl, current);
        }
        return optional.get();
    }

    private void startSocket() {
        Optional<Long> latestSequence = channelUtils.getLatestSequence(channelUrl);
        if (!latestSequence.isPresent()) {
            logger.warn("unable to get latest for channel " + channelUrl);
            return;
        }
        URI wsUri = ChannelLinkBuilder.buildWsLinkFor(URI.create(channelUrl));
        latest.set(latestSequence.get());
        startWebSocket(wsUri);
    }

    private void startWebSocket(URI channelWsUrl) {
        try {
            container.connectToServer(this, channelWsUrl);
        } catch (Exception e) {
            logger.warn("unable to start ", e);
            throw new RuntimeException("unable to start socket", e);
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("don't call this");
    }

    @OnClose
    public void onClose(CloseReason reason) {
        logger.info("Connection closed: " + reason);
        exit();
    }

    @OnOpen
    public void onOpen() {
        connected = true;
        logger.info("connected " + channelUrl);
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            long sequence = Long.parseLong(StringUtils.substringAfterLast(message, "/"));
            logger.debug("message {}", sequence);
            if (sequence > latest.get()) {
                latest.set(sequence);
            }
            signal();
        } catch (Exception e) {
            logger.warn("unexpected error parsing " + message + " for " + channelUrl, e);
        }
    }

    @OnError
    public void onError(Throwable throwable) {
        if (throwable.getClass().isAssignableFrom(SocketTimeoutException.class)
                || throwable.getClass().isAssignableFrom(EOFException.class)) {
            logger.info("disconnected " + channelUrl + " " + throwable.getMessage());
        } else {
            logger.warn("unexpected WS error " + channelUrl, throwable);
        }
        exit();
    }

    public void exit() {
        shouldExit.set(true);
        signal();
        connected = false;
    }

    private void signal() {
        synchronized (lock) {
            lock.notify();
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
