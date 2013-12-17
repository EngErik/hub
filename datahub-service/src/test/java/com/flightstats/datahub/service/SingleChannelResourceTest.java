package com.flightstats.datahub.service;

import com.flightstats.datahub.dao.ChannelDao;
import com.flightstats.datahub.model.*;
import com.flightstats.rest.HalLink;
import com.flightstats.rest.Linked;
import com.google.common.base.Optional;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Date;
import java.util.Random;

import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class SingleChannelResourceTest {

	private String channelName;
	private String contentType;
	private String contentLanguage;
	private URI channelUri;
	private ChannelHypermediaLinkBuilder linkBuilder;
	public static final Date CREATION_DATE = new Date(12345L);
	private ChannelConfiguration channelConfig;
	private DataHubKey dataHubKey;
	private URI itemUri;
	private UriInfo urlInfo;
	private ChannelDao channelDao = mock(ChannelDao.class);

    private int DEFAULT_MAX_PAYLOAD = 1024 * 1024 * 10;

	@Before
	public void setup() throws Exception {
		channelName = "UHF";
		contentType = "text/plain";
		contentLanguage = "en";
		channelUri = URI.create("http://testification.com/channel/spoon");
		URI requestUri = URI.create("http://testification.com/channel/spoon");
		URI latestUri = URI.create("http://testification.com/channel/spoon/latest");
		itemUri = URI.create("http://testification.com/channel/spoon/888item888");
		dataHubKey = new DataHubKey((short) 1200);
		//dataHubKey = new DataHubKey(CREATION_DATE, (short) 12);
		channelConfig = new ChannelConfiguration(channelName, CREATION_DATE, null);
		linkBuilder = mock(ChannelHypermediaLinkBuilder.class);
		urlInfo = mock(UriInfo.class);

		when(urlInfo.getRequestUri()).thenReturn(requestUri);
		when(channelDao.channelExists(channelName)).thenReturn(true);
		when(linkBuilder.buildChannelUri(channelConfig, urlInfo)).thenReturn(channelUri);
		when(linkBuilder.buildChannelUri(channelName, urlInfo)).thenReturn(channelUri);
		when(linkBuilder.buildLatestUri(urlInfo)).thenReturn(latestUri);
		when(linkBuilder.buildItemUri(dataHubKey, requestUri)).thenReturn(itemUri);
	}

	@Test
	public void testGetChannelMetadataForKnownChannel() throws Exception {
		DataHubKey key = new DataHubKey((short) 1000);
        DataHubCompositeValue dataHubCompositeValue = new DataHubCompositeValue(Optional.<String>absent(), Optional.<String>absent(), "blah".getBytes(),
                0L);
        LinkedDataHubCompositeValue linkedDataHubCompositeValue = new LinkedDataHubCompositeValue(dataHubCompositeValue,
                Optional.<DataHubKey>absent(), Optional.<DataHubKey>absent());

        when(channelDao.channelExists(anyString())).thenReturn(true);
		when(channelDao.getChannelConfiguration(channelName)).thenReturn(channelConfig);
		when(channelDao.findLastUpdatedKey(channelName)).thenReturn(Optional.of(key));
		when(channelDao.getValue(channelName, key)).thenReturn(Optional.of(linkedDataHubCompositeValue));

		when(urlInfo.getRequestUri()).thenReturn(channelUri);

		SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, DEFAULT_MAX_PAYLOAD);

		Linked<MetadataResponse> result = testClass.getChannelMetadata(channelName, urlInfo);
		MetadataResponse expectedResponse = new MetadataResponse(channelConfig, new Date(dataHubCompositeValue.getMillis()));
		assertEquals(expectedResponse, result.getObject());
		HalLink selfLink = result.getHalLinks().getLinks().get(0);
		HalLink latestLink = result.getHalLinks().getLinks().get(1);
		assertEquals(new HalLink("self", channelUri), selfLink);
		assertEquals(new HalLink("latest", URI.create(channelUri.toString() + "/latest")), latestLink);
	}

	@Test
	public void testUpdateChannelMetadataWithNonNullTtl() throws Exception {

		UriInfo uriInfo = mock(UriInfo.class);
		when(channelDao.channelExists(anyString())).thenReturn(true);
		when(channelDao.getChannelConfiguration(channelName)).thenReturn(channelConfig);
		when(uriInfo.getRequestUri()).thenReturn(channelUri);

		ChannelUpdateRequest request = ChannelUpdateRequest.builder().withTtlMillis(30000L).build();
		ChannelConfiguration newConfig = ChannelConfiguration.builder().withChannelConfiguration(channelConfig).withTtlMillis(30000L).build();
		Response expectedResponse = Response.ok().entity(linkBuilder.buildLinkedChannelConfig(newConfig, channelUri, uriInfo)).build();

		SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, DEFAULT_MAX_PAYLOAD);
		Response result = testClass.updateMetadata(request, channelName, uriInfo);

		assertEquals(expectedResponse.getEntity(), result.getEntity());
	}

	@Test
	public void testUpdateChannelMetadataWithNullTtl() throws Exception {

		UriInfo uriInfo = mock(UriInfo.class);
		when(channelDao.channelExists(anyString())).thenReturn(true);
		when(channelDao.getChannelConfiguration(channelName)).thenReturn(channelConfig);
		when(uriInfo.getRequestUri()).thenReturn(channelUri);

		ChannelUpdateRequest request = ChannelUpdateRequest.builder().withTtlMillis(null).build();
		ChannelConfiguration newConfig = ChannelConfiguration.builder().withChannelConfiguration(channelConfig).withTtlMillis(null).build();
		Response expectedResponse = Response.ok().entity(linkBuilder.buildLinkedChannelConfig(newConfig, channelUri, uriInfo)).build();

		SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, DEFAULT_MAX_PAYLOAD);
		Response result = testClass.updateMetadata(request, channelName, uriInfo);

		assertEquals(expectedResponse.getEntity(), result.getEntity());
	}

	@Test(expected = WebApplicationException.class)
	public void testUpdateChannelMetadataForUnknownChannel() throws Exception {

		ChannelUpdateRequest request = ChannelUpdateRequest.builder().withTtlMillis(30000L).build();
        when(channelDao.channelExists(channelName)).thenReturn(false);
		SingleChannelResource testClass = new SingleChannelResource(channelDao, null, DEFAULT_MAX_PAYLOAD);
		testClass.updateMetadata(request, channelName, urlInfo);
	}

	@Test
	public void testGetChannelMetadataForUnknownChannel() throws Exception {

		SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, DEFAULT_MAX_PAYLOAD);
		try {
			testClass.getChannelMetadata("unknownChannel", urlInfo);
			fail("Should have thrown a 404");
		} catch (WebApplicationException e) {
			Response response = e.getResponse();
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void testInsertValue() throws Exception {
		byte[] data = new byte[]{'b', 'o', 'l', 'o', 'g', 'n', 'a'};

		HalLink selfLink = new HalLink("self", itemUri);
		HalLink channelLink = new HalLink("channel", channelUri);
		ValueInsertionResult expectedResponse = new ValueInsertionResult(dataHubKey, null, null);

		when(channelDao.insert(channelName, Optional.of(contentType), Optional.of(contentLanguage), data)).thenReturn(new ValueInsertionResult(dataHubKey, null,
                null));

		SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, DEFAULT_MAX_PAYLOAD);
		Response response = testClass.insertValue(channelName, contentType, contentLanguage, data, urlInfo);

		assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

		Linked<ValueInsertionResult> result = (Linked<ValueInsertionResult>) response.getEntity();

		assertThat(result.getHalLinks().getLinks(), hasItems(selfLink, channelLink));
		ValueInsertionResult insertionResult = result.getObject();

		assertEquals(expectedResponse, insertionResult);
		assertEquals(itemUri, response.getMetadata().getFirst("Location"));
	}

    @Test
    public void testInsert_channelExistenceNotCached() throws Exception {
        //GIVEN
        ValueInsertionResult result = new ValueInsertionResult(null, null, null);
        byte[] data = "SomeData".getBytes();
        SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, DEFAULT_MAX_PAYLOAD);

        //WHEN
        when(channelDao.insert(channelName, Optional.of(contentType), Optional.of(contentLanguage), data)).thenReturn(result);

        testClass.insertValue(channelName, contentType, contentLanguage, data, urlInfo);

        //THEN
        verify(channelDao).channelExists(channelName);
    }

    @Test(expected = WebApplicationException.class)
    public void testInsert_channelExistenceNotCached_channelDoesntExist() throws Exception {
        //GIVEN
        ValueInsertionResult result = new ValueInsertionResult(null, null, null);
        byte[] data = "SomeData".getBytes();
        when(channelDao.channelExists(channelName)).thenReturn(false);
        SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, DEFAULT_MAX_PAYLOAD);

        //WHEN
        when(channelDao.insert(channelName, Optional.of(contentType), Optional.of(contentLanguage), data)).thenReturn(result);

        testClass.insertValue(channelName, contentType, contentLanguage, data, urlInfo);
    }


    @Test
    public void testInsert_payloadSizeGreaterThanMaxSizeReturns413() throws Exception {
        //GIVEN
        byte[] data = new byte[1025];
        new Random().nextBytes(data);
        SingleChannelResource testClass = new SingleChannelResource(channelDao, linkBuilder, 1024);

        //WHEN
        Response result = testClass.insertValue(channelName, contentType, contentLanguage, data, urlInfo);

        //THEN
        assertEquals(413, result.getStatus());
        assertEquals("Max payload size is 1024 bytes.", result.getEntity().toString());
    }

}
