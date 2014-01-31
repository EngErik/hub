package com.flightstats.hub.service.exceptions;

import com.flightstats.hub.model.exception.NoSuchChannelException;
import org.junit.Test;

import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;

public class NoSuchChannelExceptionMapperTest {

	@Test
	public void testMap() throws Exception {
		//GIVEN
		NoSuchChannelExceptionMapper testClass = new NoSuchChannelExceptionMapper();
		NoSuchChannelException exception = new NoSuchChannelException("No such channel: flimflam", new RuntimeException("boom"));
		//WHEN
		Response result = testClass.toResponse(exception);
		//THEN
		assertEquals("No such channel: flimflam", result.getEntity());
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result.getStatus());
	}
}
