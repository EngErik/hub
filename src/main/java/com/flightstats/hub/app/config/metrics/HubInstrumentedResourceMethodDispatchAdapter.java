package com.flightstats.hub.app.config.metrics;

import com.flightstats.hub.metrics.HostedGraphiteSender;
import com.sun.jersey.spi.container.ResourceMethodDispatchAdapter;
import com.sun.jersey.spi.container.ResourceMethodDispatchProvider;

import javax.ws.rs.ext.Provider;

@Provider
public class HubInstrumentedResourceMethodDispatchAdapter implements ResourceMethodDispatchAdapter {
    private final HostedGraphiteSender graphiteSender;

    public HubInstrumentedResourceMethodDispatchAdapter(HostedGraphiteSender graphiteSender) {
        this.graphiteSender = graphiteSender;
    }

    @Override
    public ResourceMethodDispatchProvider adapt(ResourceMethodDispatchProvider provider) {
        return new HubInstrumentedResourceMethodDispatchProvider(provider, graphiteSender);
    }
}
