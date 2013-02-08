package com.flightstats.rest;

import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertEquals;

public class LinkedTest {

    @Test
    public void testBuilder() throws Exception {
        List<HalLink> expectedLinks = Arrays.asList(new HalLink("foo", URI.create("http://lycos.com")),
                new HalLink("bar", URI.create("http://yahoo.com")),
                new HalLink("foo", URI.create("http://hotmail.com")));

        Linked<String> buildResult = Linked.linked("hello")
                                           .withLink("foo", "http://lycos.com")
                                           .withLink("bar", "http://yahoo.com")
                                           .withLink("foo", "http://hotmail.com")
                                           .build();

        List<HalLink> linksList = buildResult.getLinks().getLinks();
        assertEquals(linksList, expectedLinks);
    }
}
