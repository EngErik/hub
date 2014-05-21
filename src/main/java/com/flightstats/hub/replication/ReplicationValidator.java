package com.flightstats.hub.replication;

import com.flightstats.hub.model.exception.ForbiddenRequestException;
import com.flightstats.hub.model.exception.InvalidRequestException;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.Collection;

public class ReplicationValidator {

    private ReplicationDao replicationDao;

    @Inject
    public ReplicationValidator(ReplicationDao replicationDao) {
        this.replicationDao = replicationDao;
    }

    public void throwExceptionIfReplicating(String channelName) {
        Collection<ReplicationDomain> domains = replicationDao.getDomains(false);
        for (ReplicationDomain domain : domains) {
            if (domain.getExcludeExcept().contains(channelName)) {
                throw new ForbiddenRequestException(channelName + " cannot modified while replicating");
            }
        }
    }

    public void validateDomain(ReplicationDomain domain) {
        if (!domain.isValid()) {
            throw new InvalidRequestException("Invalid request. excludeExcept must be populated.");
        }
        Collection<ReplicationDomain> replicationDomains = replicationDao.getDomains(true);
        for (ReplicationDomain replicationDomain : replicationDomains) {
            if (!domain.getDomain().equals(replicationDomain.getDomain())) {
                Sets.SetView<String> intersection = Sets.intersection(replicationDomain.getExcludeExcept(), domain.getExcludeExcept());
                if (!intersection.isEmpty()) {
                    throw new ForbiddenRequestException(domain.getDomain() + " has channels already being replicated " + intersection);
                }
            }
        }
    }
}
