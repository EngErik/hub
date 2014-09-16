package com.flightstats.hub.service;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.model.ChannelConfiguration;
import com.flightstats.hub.model.exception.ConflictException;
import com.flightstats.hub.model.exception.InvalidRequestException;
import com.flightstats.rest.Linked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

/**
 * This resource represents the collection of all channels in the Hub.
 */
@Path("/channel")
public class ChannelResource {

    private final static Logger logger = LoggerFactory.getLogger(ChannelResource.class);

	private final ChannelService channelService;
	private final ChannelLinkBuilder linkBuilder;
	private final UriInfo uriInfo;

	@Inject
	public ChannelResource(ChannelService channelService, ChannelLinkBuilder linkBuilder, UriInfo uriInfo) {
		this.channelService = channelService;
		this.linkBuilder = linkBuilder;
		this.uriInfo = uriInfo;
    }

	@GET
	@Timed(name = "channel.get", absolute = true)
    @ExceptionMetered
	@Produces(MediaType.APPLICATION_JSON)
	public Response getChannels() {
		Iterable<ChannelConfiguration> channels = channelService.getChannels();
		Linked<?> result = linkBuilder.build(channels, uriInfo);
		return Response.ok(result).build();
	}

	@POST
    @Timed(name = "channel.post", absolute = true)
    @ExceptionMetered
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response createChannel(String json) throws InvalidRequestException, ConflictException {
        ChannelConfiguration channelConfiguration = ChannelConfiguration.fromJson(json);
        channelConfiguration = channelService.createChannel(channelConfiguration);
		URI channelUri = linkBuilder.buildChannelUri(channelConfiguration, uriInfo);
		return Response.created(channelUri).entity(
			linkBuilder.buildChannelLinks(channelConfiguration, channelUri))
			.build();
	}
}
