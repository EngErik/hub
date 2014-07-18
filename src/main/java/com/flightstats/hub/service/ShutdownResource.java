package com.flightstats.hub.service;

import com.flightstats.hub.app.HubServices;
import com.flightstats.hub.util.Sleeper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * ShutdownResource should only be called from the node's instance by the upstart prestop.sh script
 */
@Path("/shutdown")
public class ShutdownResource {

    private final static Logger logger = LoggerFactory.getLogger(ShutdownResource.class);

    @Inject
    HubHealthCheck healthCheck;

    @Inject
    @Named("app.shutdown_delay_seconds") Integer shutdown_delay_seconds;

    @POST
    public Response shutdown() {
        logger.warn("shutting down!");
        //this call will get the node removed from the Load Balancer
        healthCheck.shutdown();
        //wait until it's likely the node is removed from the Load Balancer
        Sleeper.sleep(shutdown_delay_seconds * 1000);
        //after the node isn't getting new requests, stop everything that needs a clean kill
        HubServices.preStopAll();

        logger.warn("completed shutdown tasks");
        return Response.ok().build();
    }
}
