package com.flightstats.datahub.model.serialize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightstats.datahub.app.config.DataHubObjectMapperFactory;
import com.flightstats.datahub.model.DataHubKey;
import com.flightstats.datahub.model.SequenceDataHubKey;
import com.flightstats.datahub.model.ValueInsertionResult;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ValueInsertionResultMixInTest {

	@Test
	public void testSerialize() throws Exception {
		ObjectMapper objectMapper = DataHubObjectMapperFactory.construct();
		DataHubKey key = new SequenceDataHubKey( 1033);
        ValueInsertionResult valueInsertionResult = new ValueInsertionResult(key, null, new Date(1384305309087L));
		OutputStream out = new ByteArrayOutputStream();
		objectMapper.writeValue(out, valueInsertionResult);
		String result = out.toString();
        assertEquals("{\n  \"timestamp\" : \"2013-11-13T01:15:09.087Z\"\n}", result);
    }

    @Test
    public void testRowKeyIgnored() throws Exception {
        ObjectMapper objectMapper = DataHubObjectMapperFactory.construct();
        DataHubKey key = new SequenceDataHubKey( 1033);
        ValueInsertionResult valueInsertionResult = new ValueInsertionResult(key, "rowKeyIsAwesome", new Date(1384305309087L));
        OutputStream out = new ByteArrayOutputStream();
        objectMapper.writeValue(out, valueInsertionResult);
        String result = out.toString();
        assertFalse(result.contains("rowKey"));
    }
}
