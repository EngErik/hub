package com.flightstats.hub.spoke;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SpokePathUtilTest {
    @Test
    public void testYear() throws Exception {
        assertEquals("2014", SpokePathUtil.year("alksdjf/2014"));
        assertNotEquals("2014", SpokePathUtil.year("alksdjf2014"));
        assertEquals("10", SpokePathUtil.month("alksdjf/2014/10"));
        assertEquals("05", SpokePathUtil.day("alksdjf/2014/10/05"));
        assertEquals("13", SpokePathUtil.hour("alksdjf/2014/10/05/13"));
        assertEquals("09", SpokePathUtil.minute("alksdjf/2014/10/05/13/09"));
        assertEquals("15", SpokePathUtil.second("alksdjf/2014/10/05/13/09/15123aaa"));
        // TODO bc 11/20/14: get regex for millisecond to work
//        assertEquals(123, SpokePathUtil.millisecond("alksdjf/2014/10/05/13/09/15/123aaa"));
//        assertEquals(123, SpokePathUtil.millisecond("alksdjf/2014/10/05/13/09/15123aaa"));
        assertEquals(null, SpokePathUtil.minute("alksdjf/2014/10/05/13/0"));
        assertEquals("second", SpokePathUtil.smallestTimeResolution("lkjsldfjlkasd/2014/12/12/12/12/12"));
//        assertEquals("millisecond", SpokePathUtil.smallestTimeResolution("lkjsldfjlkasd/2014/12/12/12/12/12555abcd"));
        assertEquals("year", SpokePathUtil.smallestTimeResolution("lkjsldfjlkasd/2014/"));
        assertEquals("alksdjf/2014/10/05/13/09/15", SpokePathUtil.secondPathPart("alksdjf/2014/10/05/13/09/15123aaa"));
    }
}
