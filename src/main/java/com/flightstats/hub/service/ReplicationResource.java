package com.flightstats.hub.service;

import com.flightstats.hub.replication.ReplicationDomain;
import com.flightstats.hub.replication.ReplicationService;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 *
 */
@Path("/replication")
public class ReplicationResource {
    private final static Logger logger = LoggerFactory.getLogger(ReplicationResource.class);

    private final ReplicationService replicationService;

    private UriInfo uriInfo;

    @Inject
    public ReplicationResource(ReplicationService replicationService, UriInfo uriInfo) {
        this.replicationService = replicationService;
        this.uriInfo = uriInfo;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() throws Exception {
        return Response.ok(replicationService.getReplicationBean()).build();
    }

    @PUT
    @Path("/{domain}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putDomain(@PathParam("domain") String domain, ReplicationDomain replicationDomain,
                              @HeaderParam("Host") String host) {
        logger.info("creating domain " + domain + " replicationConfig " + replicationDomain + " host " + host);
        if (domain.equalsIgnoreCase(host)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("The domain must be different than the host").build();
        }
        replicationService.create(domain, replicationDomain);
        return Response.created(uriInfo.getRequestUri()).entity(replicationDomain).build();
    }

    @GET
    @Path("/{domain}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDomain(@PathParam("domain") String domain) {
        Optional<ReplicationDomain> replicationConfig = replicationService.get(domain);
        if (!replicationConfig.isPresent()) {
            Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(replicationConfig.get()).build();
    }

    @DELETE
    @Path("/{domain}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteDomain(@PathParam("domain") String domain) {
        replicationService.delete(domain);
        return Response.ok().build();
    }

}
