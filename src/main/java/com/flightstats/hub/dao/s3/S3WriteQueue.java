package com.flightstats.hub.dao.s3;


import com.flightstats.hub.dao.ContentDao;
import com.flightstats.hub.model.ChannelContentKey;
import com.flightstats.hub.model.Content;
import com.flightstats.hub.util.RuntimeInterruptedException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

@SuppressWarnings("Convert2Lambda")
public class S3WriteQueue {

    private final static Logger logger = LoggerFactory.getLogger(S3WriteQueue.class);

    private ExecutorService executorService;
    private BlockingQueue<ChannelContentKey> keys = new LinkedBlockingQueue<>(2000);

    @Inject
    public S3WriteQueue(@Named(ContentDao.CACHE) ContentDao cacheContentDao,
                        @Named(ContentDao.LONG_TERM) ContentDao longTermContentDao) throws InterruptedException {
        int threads = 20;
        executorService = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    //noinspection InfiniteLoopStatement
                    while (true) {
                        ChannelContentKey key = keys.take();
                        if (key != null) {
                            logger.trace("writing {}", key);
                            Content content = cacheContentDao.read(key.getChannel(), key.getContentKey());
                            longTermContentDao.write(key.getChannel(), content);
                            //todo - gfm - 11/21/14 - should this do something else to verify?
                        }
                    }
                }
            });
        }
    }

    public void add(ChannelContentKey key) {
        try {
            keys.put(key);
        } catch (InterruptedException e) {
            logger.warn("interupted " + e.getMessage());
            throw new RuntimeInterruptedException(e);
        }
    }

    public void close() {
        try {
            logger.info("awaited " + executorService.awaitTermination(1, TimeUnit.MINUTES));
        } catch (InterruptedException e) {
            logger.warn("unable to close", e);
        }
    }
}
