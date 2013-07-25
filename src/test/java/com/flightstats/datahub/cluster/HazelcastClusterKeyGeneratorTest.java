package com.flightstats.datahub.cluster;

import com.flightstats.datahub.model.DataHubKey;
import com.flightstats.datahub.service.ChannelLockExecutor;
import com.flightstats.datahub.util.TimeProvider;
import com.hazelcast.core.AtomicNumber;
import com.hazelcast.core.HazelcastInstance;
import org.junit.Test;

import java.nio.channels.AlreadyBoundException;
import java.util.Date;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class HazelcastClusterKeyGeneratorTest {

	@Test
	public void testIncrementNoRollover() throws Exception {
		//GIVEN
		String channelName = "mychanisgood";
		Date currentDate = new Date(12345678L);
		DataHubKey expectedA = new DataHubKey(currentDate, (short)0);
		DataHubKey expectedB = new DataHubKey(currentDate, (short)1);

		TimeProvider timeProvider = mock(TimeProvider.class);
		HazelcastInstance hazelcast = mock(HazelcastInstance.class);
		ChannelLockExecutor channelLockExecutor = new ChannelLockExecutor(new ReentrantChannelLockFactory());
		AtomicNumber atomicDateNumber = mock(AtomicNumber.class);
		AtomicNumber atomicSeqNumber = mock(AtomicNumber.class);

		HazelcastClusterKeyGenerator testClass = new HazelcastClusterKeyGenerator(timeProvider, hazelcast, channelLockExecutor);

		//WHEN
		when(timeProvider.getDate()).thenReturn(currentDate);
		when(atomicDateNumber.get()).thenReturn(currentDate.getTime() - 10);
		when(hazelcast.getAtomicNumber("CHANNEL_NAME_DATE:mychanisgood")).thenReturn(atomicDateNumber);
		when(hazelcast.getAtomicNumber("CHANNEL_NAME_SEQ:mychanisgood")).thenReturn(atomicSeqNumber);
		when(atomicSeqNumber.getAndAdd(1)).thenReturn(0L).thenReturn(1L);
		DataHubKey resultA = testClass.newKey(channelName);
		DataHubKey resultB = testClass.newKey(channelName);

		//THEN
		assertEquals(expectedA, resultA);
		assertEquals(expectedB, resultB);
	}

	@Test
	public void testRollover() throws Exception {
		//GIVEN
		String channelName = "mychanisgood";
		Date currentDate = new Date(12345678L);
		DataHubKey expectedA = new DataHubKey(currentDate, Short.MAX_VALUE);
		DataHubKey expectedB = new DataHubKey(currentDate, (short)0);

		TimeProvider timeProvider = mock(TimeProvider.class);
		HazelcastInstance hazelcast = mock(HazelcastInstance.class);
		ChannelLockExecutor channelLockExecutor = new ChannelLockExecutor(new ReentrantChannelLockFactory());
		AtomicNumber atomicDateNumber = mock(AtomicNumber.class);
		AtomicNumber atomicSeqNumber = mock(AtomicNumber.class);

		HazelcastClusterKeyGenerator testClass = new HazelcastClusterKeyGenerator(timeProvider, hazelcast, channelLockExecutor);

		//WHEN
		when(timeProvider.getDate()).thenReturn(currentDate);
		when(atomicDateNumber.get()).thenReturn(currentDate.getTime() - 10);
		when(hazelcast.getAtomicNumber("CHANNEL_NAME_DATE:mychanisgood")).thenReturn(atomicDateNumber);
		when(hazelcast.getAtomicNumber("CHANNEL_NAME_SEQ:mychanisgood")).thenReturn(atomicSeqNumber);
		when(atomicSeqNumber.getAndAdd(1)).thenReturn(0L);
		when(atomicSeqNumber.compareAndSet(Short.MAX_VALUE,0)).thenReturn(true).thenReturn(false);
		DataHubKey resultA = testClass.newKey(channelName);
		DataHubKey resultB = testClass.newKey(channelName);

		//THEN
		assertEquals(expectedA, resultA);
		assertEquals(expectedB, resultB);
	}

	@Test(expected = RuntimeException.class)
	public void testExceptionCoerced() throws Exception {
		//GIVEN
		ChannelLockExecutor channelLockExecutor = mock(ChannelLockExecutor.class);

		HazelcastClusterKeyGenerator testClass = new HazelcastClusterKeyGenerator(null, null, channelLockExecutor);

		//WHEN
		when(channelLockExecutor.execute(anyString(), any(Callable.class))).thenThrow(new AlreadyBoundException());
		testClass.newKey("mychanisgood");
	}
}
