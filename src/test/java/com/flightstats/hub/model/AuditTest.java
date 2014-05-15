package com.flightstats.hub.model;

import org.junit.Before;
import org.junit.Test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class AuditTest {

    Audit audit;

    @Before
    public void setUp() throws Exception {
        audit = Audit.builder().date(new Date()).user("somebody").uri("http://hub/channel/blah/1").build();
    }

    @Test
    public void testJsonCycle() throws Exception {
        Audit cycled = Audit.fromJson(audit.toJson());
        assertEquals(audit.toJson(), cycled.toJson());
    }

    @Test
    public void testJson() throws Exception {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertEquals("{\"user\":\"somebody\",\"date\":\"" + formatter.format(audit.getDate()) +
                "\",\"uri\":\"http://hub/channel/blah/1\"}", audit.toJson());
    }

}