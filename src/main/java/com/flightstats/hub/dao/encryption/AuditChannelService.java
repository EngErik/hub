package com.flightstats.hub.dao.encryption;

import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.dao.Request;
import com.flightstats.hub.model.*;
import com.flightstats.hub.model.exception.ConflictException;
import com.flightstats.hub.model.exception.ForbiddenRequestException;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class AuditChannelService implements ChannelService {
    public static final String AUDIT = "_audit";
    private final static Logger logger = LoggerFactory.getLogger(AuditChannelService.class);
    private final ChannelService channelService;
    private final ExecutorService executorService;

    @Inject
    public AuditChannelService(@BasicChannelService ChannelService channelService,
                               @Named("audit.threads") int auditThreads, @Named("audit.queue") int auditQueue) {
        this.channelService = channelService;
        executorService = new ThreadPoolExecutor(1, auditThreads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(auditQueue));
    }

    public static boolean isAuditChannel(String name) {
        return StringUtils.endsWith(name, AUDIT);
    }

    @Override
    public boolean channelExists(String channelName) {
        return channelService.channelExists(channelName);
    }

    @Override
    public ChannelConfiguration createChannel(ChannelConfiguration configuration) {
        if (isAuditChannel(configuration.getName())) {
            throw new ForbiddenRequestException("Audit Channels can not be created");
        }

        Set<String> tags = new HashSet<>(configuration.getTags());
        tags.add("audit");
        ChannelConfiguration channel = channelService.createChannel(configuration);

        ChannelConfiguration auditConfig = ChannelConfiguration.builder()
                .withChannelConfiguration(configuration)
                .withName(configuration.getName() + AUDIT)
                .withDescription("auditing channel for " + configuration.getName())
                .withTags(tags)
                .build();

        try {
            channelService.createChannel(auditConfig);
        } catch (ConflictException e) {
            logger.info("auditing channel already exists " + auditConfig.getName());
        }

        return channel;
    }

    @Override
    public ContentKey insert(String channelName, Content content) {
        if (isAuditChannel(channelName)) {
            throw new ForbiddenRequestException("Audit Channels do not allow inserts");
        }
        return channelService.insert(channelName, content);
    }

    @Override
    public Optional<Content> getValue(Request request) {
        Optional<Content> optional = channelService.getValue(request);
        if (optional.isPresent() && !isAuditChannel(request.getChannel())) {
            audit(request);
        }
        return optional;
    }

    private void audit(Request request) {
        Audit audit = Audit.builder()
                .user(request.getUser())
                .uri(request.getUri().toString())
                .date(new Date())
                .build();
        Content content = Content.builder()
                .withContentType(MediaType.APPLICATION_JSON)
                .withData(audit.toJson().getBytes())
                .build();
        try {
            submit(request, audit, content);
        } catch (RejectedExecutionException e) {
            logger.warn("auditing queue is full " + audit);
        }
    }

    private void submit(final Request request, final Audit audit, final Content content) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    channelService.insert(request.getChannel() + AUDIT, content);
                } catch (Exception e) {
                    logger.warn("unable to audit " + audit, e);
                }
            }
        });
    }

    @Override
    public ChannelConfiguration getChannelConfiguration(String channelName) {
        return channelService.getChannelConfiguration(channelName);
    }

    @Override
    public Iterable<ChannelConfiguration> getChannels() {
        return channelService.getChannels();
    }

    @Override
    public Iterable<ChannelConfiguration> getChannels(String tag) {
        return channelService.getChannels(tag);
    }

    @Override
    public Iterable<String> getTags() {
        return channelService.getTags();
    }

    @Override
    public Optional<ContentKey> findLastUpdatedKey(String channelName) {
        return channelService.findLastUpdatedKey(channelName);
    }

    @Override
    public ChannelConfiguration updateChannel(ChannelConfiguration configuration) {
        if (isAuditChannel(configuration.getName())) {
            configuration.getTags().add("audit");
        }
        return channelService.updateChannel(configuration);
    }

    @Override
    public Collection<ContentKey> queryByTime(TimeQuery timeQuery) {
        return channelService.queryByTime(timeQuery);
    }

    @Override
    public Collection<ContentKey> getKeys(String channelName, ContentKey contentKey, int count) {
        return getKeys(channelName, contentKey, count);
    }

    @Override
    public boolean delete(String channelName) {
        if (isAuditChannel(channelName) && isNotTestChannel(channelName)) {
            throw new ForbiddenRequestException("Audit Channels can not be deleted.");
        }
        return channelService.delete(channelName);
    }

    private boolean isNotTestChannel(String channelName) {
        return !channelName.startsWith("test_");
    }
}
