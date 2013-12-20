package com.flightstats.datahub.service.eventing;

import com.flightstats.datahub.service.ChannelHypermediaLinkBuilder;
import com.flightstats.datahub.util.DataHubKeyRenderer;
import com.google.inject.Inject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

@WebSocket(maxMessageSize = 1024 * 10)    //10k
public class DataHubWebSocket {

	private final static Logger logger = LoggerFactory.getLogger(DataHubWebSocket.class);
	private final Runnable afterDisconnectCallback;
	private final WebSocketChannelNameExtractor channelNameExtractor;
	private final SubscriptionRoster subscriptions;
	private final ChannelHypermediaLinkBuilder linkBuilder;
	private String remoteAddress;
	private JettyWebSocketEndpointSender endpointSender;
	private String channelName;

	@Inject
	public DataHubWebSocket(SubscriptionRoster subscriptions, WebSocketChannelNameExtractor channelNameExtractor, ChannelHypermediaLinkBuilder linkBuilder, DataHubKeyRenderer keyRenderer) {
		this(subscriptions, channelNameExtractor, linkBuilder, keyRenderer, new Runnable() {
			@Override
			public void run() {
				//nop
			}
		});
	}

	DataHubWebSocket(SubscriptionRoster subscriptions, WebSocketChannelNameExtractor channelNameExtractor, ChannelHypermediaLinkBuilder linkBuilder,
					 DataHubKeyRenderer keyRenderer, Runnable afterDisconnectCallback) {
		this.linkBuilder = linkBuilder;
		this.afterDisconnectCallback = afterDisconnectCallback;
		this.channelNameExtractor = channelNameExtractor;
		this.subscriptions = subscriptions;
	}

	@OnWebSocketConnect
	public void onConnect(final Session session) {
		UpgradeRequest upgradeRequest = session.getUpgradeRequest();
		URI requestUri = upgradeRequest.getRequestURI();
        remoteAddress = session.getRemoteAddress().toString();
        logger.info("New client connection: " + remoteAddress + " for " + requestUri);
		String host = upgradeRequest.getHeader("Host");
        channelName = channelNameExtractor.extractChannelName(requestUri);
		//todo this is totally hacky. Is there no way to get the full request URI?
        String channelUri = "http://" + host + "/channel/" + channelName;
		try {
			endpointSender = new JettyWebSocketEndpointSender(remoteAddress, session.getRemote(), linkBuilder, new URI(channelUri));
		} catch (URISyntaxException e) {
			//this should really never happen.  stupid checked exceptions!
			throw new RuntimeException(e);
		}
		subscriptions.subscribe(channelName, endpointSender);
	}

	@OnWebSocketClose
	public void onDisconnect(int statusCode, String reason) {
		logger.info("Client disconnect: " + remoteAddress + " (status = " + statusCode + ", reason = " + reason + ")");
		afterDisconnectCallback.run();
		subscriptions.unsubscribe(channelName, endpointSender);
	}
}
