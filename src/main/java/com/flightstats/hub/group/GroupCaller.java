package com.flightstats.hub.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightstats.hub.cluster.CuratorLeader;
import com.flightstats.hub.cluster.Leader;
import com.flightstats.hub.cluster.LongSet;
import com.flightstats.hub.metrics.MetricsTimer;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.util.RuntimeInterruptedException;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("Convert2Lambda")
public class GroupCaller implements Leader {
    private final static Logger logger = LoggerFactory.getLogger(GroupCaller.class);

    private final CuratorFramework curator;
    private final Provider<CallbackQueue> queueProvider;
    private final GroupService groupService;
    private final MetricsTimer metricsTimer;
    private final GroupContentKey groupContentKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean deleteOnExit = new AtomicBoolean();

    private Group group;
    private CuratorLeader curatorLeader;
    private Client client;
    private ExecutorService executorService;
    private Semaphore semaphore;
    //todo - gfm - 12/3/14 -
    //private LongSet inProcess;
    private AtomicBoolean hasLeadership;
    private Retryer<ClientResponse> retryer;
    private CallbackQueue callbackQueue;

    @Inject
    public GroupCaller(CuratorFramework curator, Provider<CallbackQueue> queueProvider,
                       GroupService groupService, MetricsTimer metricsTimer, GroupContentKey groupContentKey) {
        this.curator = curator;
        this.queueProvider = queueProvider;
        this.groupService = groupService;
        this.metricsTimer = metricsTimer;
        this.groupContentKey = groupContentKey;
    }

    public boolean tryLeadership(Group group) {
        logger.debug("starting group: " + group);
        this.group = group;
        executorService = Executors.newCachedThreadPool();
        semaphore = new Semaphore(group.getParallelCalls());
        //todo - gfm - 12/3/14 -
        //inProcess = new LongSet(getInFlightPath(), curator);
        curatorLeader = new CuratorLeader(getLeaderPath(), this, curator);
        curatorLeader.start();
        return true;
    }

    @Override
    public void takeLeadership(AtomicBoolean hasLeadership) {
        this.hasLeadership = hasLeadership;
        retryer = buildRetryer();
        logger.info("taking leadership " + group);
        Optional<Group> foundGroup = groupService.getGroup(group.getName());
        if (!foundGroup.isPresent()) {
            logger.info("group is missing, exiting " + group.getName());
            return;
        }
        this.client = GroupClient.createClient();
        callbackQueue = queueProvider.get();
        try {
            ContentKey lastCompletedKey = groupContentKey.get(group.getName(), new ContentKey());
            logger.debug("last completed at {} {}", lastCompletedKey, group.getName());
            if (hasLeadership.get()) {
                //todo - gfm - 12/3/14 -
                //sendInProcess(lastCompletedKeyId);
                callbackQueue.start(group, lastCompletedKey);
                while (hasLeadership.get()) {
                    Optional<ContentKey> nextOptional = callbackQueue.next();
                    if (nextOptional.isPresent()) {
                        send(nextOptional.get());
                    }

                }
            }
        } catch (RuntimeInterruptedException | InterruptedException e) {
            logger.info("saw InterruptedException for " + group.getName());
        } finally {
            logger.info("stopping " + group);
            closeQueue();
            if (deleteOnExit.get()) {
                delete();
            }
        }
    }

    //todo - gfm - 12/3/14 -
    /*private long sendInProcess(long lastCompletedKeyId) throws InterruptedException {
        Set<Long> inProcessSet = inProcess.getSet();
        logger.trace("sending in process {} to {}", inProcessSet, group.getName());
        for (Long toSend : inProcessSet) {
            if (toSend < lastCompletedKeyId) {
                send(toSend);
            } else {
                inProcess.remove(toSend);
            }
        }
        return lastCompletedKeyId;
    }
*/
    private void send(ContentKey key) throws InterruptedException {
        logger.trace("sending {} to {}", key, group.getName());
        semaphore.acquire();
        executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //todo - gfm - 12/3/14 -
                //inProcess.add(next);
                try {
                    makeTimedCall(createResponse(key));
                    groupContentKey.updateIncrease(key, group.getName());
                    //todo - gfm - 12/3/14 -
                    //inProcess.remove(next);
                    logger.trace("completed {} call to {} ", key, group.getName());
                } catch (Exception e) {
                    logger.warn("exception sending " + key + " to " + group.getName(), e);
                } finally {
                    semaphore.release();
                }
                return null;
            }
        });
    }

    private ObjectNode createResponse(ContentKey key) {
        ObjectNode response = mapper.createObjectNode();
        response.put("name", group.getName());
        response.put("id", UUID.randomUUID().toString());
        ArrayNode uris = response.putArray("uris");
        uris.add(group.getChannelUrl() + "/" + key.toUrl());
        return response;
    }

    private void makeTimedCall(final ObjectNode response) throws Exception {
        metricsTimer.time("group." + group.getName() + ".post", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return metricsTimer.time("group.ALL.post", new Callable<Object>() {
                    @Override
                    public Object call() throws ExecutionException, RetryException {
                        makeCall(response);
                        return null;
                    }
                });
            }
        });
    }

    private void makeCall(final ObjectNode response) throws ExecutionException, RetryException {
        retryer.call(new Callable<ClientResponse>() {
            @Override
            public ClientResponse call() throws Exception {
                if (!hasLeadership.get()) {
                    logger.debug("not leader {} {} {}", group.getCallbackUrl(), group.getName(), response);
                    return null;
                }
                logger.debug("calling {} {}", group.getCallbackUrl(), response);
                return client.resource(group.getCallbackUrl())
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .post(ClientResponse.class, response.toString());
            }
        });
    }

    public void exit(boolean delete) {
        String name = group.getName();
        logger.info("exiting group " + name + " deleting " + delete);
        deleteOnExit.set(delete);
        curatorLeader.close();
        closeQueue();
        try {
            executorService.shutdown();
            logger.info("awating termination " + name);
            executorService.awaitTermination(1, TimeUnit.SECONDS);
            logger.info("terminated " + name);
        } catch (InterruptedException e) {
            logger.warn("unable to stop?", e);
        }
    }

    private void closeQueue() {
        try {
            if (callbackQueue != null) {
                callbackQueue.close();
            }
        } catch (Exception e) {
            logger.warn("unable to close callbackQueue", e);
        }
    }

    private String getLeaderPath() {
        return "/GroupLeader/" + group.getName();
    }

    private String getInFlightPath() {
        return "/GroupInFlight/" + group.getName();
    }

    public ContentKey getLastCompleted() {
        return groupContentKey.get(group.getName(), ContentKey.NONE);
    }

    private void delete() {
        logger.info("deleting " + group.getName());
        LongSet.delete(getInFlightPath(), curator);
        groupContentKey.delete(group.getName());
        logger.info("deleted " + group.getName());
    }

    public boolean deleteIfReady() {
        if (isReadyToDelete()) {
            deleteAnyway();
            return true;
        }
        return false;
    }

    void deleteAnyway() {
        try {
            curator.delete().deletingChildrenIfNeeded().forPath(getLeaderPath());
        } catch (Exception e) {
            logger.warn("unable to delete leader path " + group.getName(), e);
        }
        delete();
    }

    private boolean isReadyToDelete() {
        try {
            return curator.getChildren().forPath(getLeaderPath()).isEmpty();
        } catch (KeeperException.NoNodeException ignore) {
            return true;
        } catch (Exception e) {
            logger.warn("unexpected exception " + group.getName(), e);
            return true;
        }
    }

    private Retryer<ClientResponse> buildRetryer() {
        return RetryerBuilder.<ClientResponse>newBuilder()
                .retryIfException(new Predicate<Throwable>() {
                    @Override
                    public boolean apply(@Nullable Throwable throwable) {
                        if (throwable != null) {
                            if (throwable.getClass().isAssignableFrom(ClientHandlerException.class)) {
                                logger.info("got ClientHandlerException trying to call client back " + throwable.getMessage());
                            } else {
                                logger.info("got throwable trying to call client back ", throwable);
                            }
                        }
                        return throwable != null;
                    }
                })
                .retryIfResult(new Predicate<ClientResponse>() {
                    @Override
                    public boolean apply(@Nullable ClientResponse response) {
                        if (response == null) return true;
                        boolean failure = response.getStatus() != 200;
                        if (failure) {
                            logger.info("unable to send to " + response);
                        }
                        return failure;
                    }
                })
                .withWaitStrategy(WaitStrategies.exponentialWait(1000, 1, TimeUnit.MINUTES))
                .withStopStrategy(new GroupStopStrategy(hasLeadership))
                .build();
    }
}
