package com.flightstats.rest;

import org.codehaus.jackson.JsonGenerator;
import org.junit.Test;

import java.util.Date;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class Rfc3339DateSerializerTest {

    @Test
    public void testSerialize() throws Exception {
        Rfc3339DateSerializer testClass = new Rfc3339DateSerializer();
        Date date = new Date(493959282725L);
        JsonGenerator jgen = mock(JsonGenerator.class);
        testClass.serialize(date, jgen, null);
        verify(jgen).writeString("1985-08-27T02:54:42.725");
    }
}
