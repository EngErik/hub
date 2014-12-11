package com.flightstats.hub.spoke;

import com.flightstats.hub.app.HubServices;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CuratorSpokeCluster implements SpokeCluster {
    public static final String CLUSTER_PATH = "/SpokeCluster";
    private final static Logger logger = LoggerFactory.getLogger(CuratorSpokeCluster.class);
    private final CuratorFramework curator;
    private final int port;
    private final PathChildrenCache clusterCache;

    @Inject
    public CuratorSpokeCluster(CuratorFramework curator, @Named("http.bind_port") int port) throws Exception {
        this.curator = curator;
        this.port = port;
        //todo - gfm - 11/18/14 - should spoke run on different port from the Hub, that starts first?
        clusterCache = new PathChildrenCache(curator, CLUSTER_PATH, true);
        clusterCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
        clusterCache.getListenable().addListener(new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                logger.info("event {}", event);
            }
        });
        HubServices.register(new CuratorSpokeClusterHook(), HubServices.TYPE.POST_START, HubServices.TYPE.PRE_STOP);
    }

    public void register() {
        try {
            logger.info("adding host {}", getHost());
            curator.create().withMode(CreateMode.EPHEMERAL).forPath(getFullPath(), getHost().getBytes());
        } catch (Exception e) {
            logger.error("unable to register, should die", e);
            throw new RuntimeException(e);
        }
    }

    private String getFullPath() throws UnknownHostException {
        return CLUSTER_PATH + "/" + getHost();
    }

    private String getHost() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostAddress() + ":" + port;
    }

    @Override
    public List<String> getServers() {
        List<String> servers = new ArrayList<>();
        List<ChildData> currentData = clusterCache.getCurrentData();
        //noinspection Convert2streamapi
        for (ChildData childData : currentData) {
            servers.add(new String(childData.getData()));
        }
        return servers;
    }

    @Override
    public List<String> getRandomServers() {
        List<String> servers = getServers();
        Collections.shuffle(servers);
        return servers;
    }

    private class CuratorSpokeClusterHook extends AbstractIdleService {
        @Override
        protected void startUp() throws Exception {
            register();
        }

        @Override
        protected void shutDown() throws Exception {
            logger.info("removing host from cluster " + getHost());
            curator.delete().forPath(getFullPath());
        }
    }
}
