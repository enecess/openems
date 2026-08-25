package io.openems.backend.edge.manager;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.Test;

import io.openems.common.jsonrpc.notification.EdgeConfigNotification.Ordering;

public class EdgeConfigVersionTrackerTest {

	private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
	private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

	private static UUID source(long number) {
		return new UUID(0L, number);
	}

	@Test
	public void shouldDropOlderAndDuplicateRevisions() {
		var sut = new EdgeConfigVersionTracker();
		var applied = new ArrayList<Long>();

		assertTrue(sut.apply("edge0", new Ordering(SOURCE_A, 2L), () -> applied.add(2L)));
		assertFalse(sut.apply("edge0", new Ordering(SOURCE_A, 1L), () -> applied.add(1L)));
		assertFalse(sut.apply("edge0", new Ordering(SOURCE_A, 2L), () -> applied.add(2L)));
		assertEquals(List.of(2L), applied);
	}

	@Test
	public void shouldRetirePreviousSource() {
		var sut = new EdgeConfigVersionTracker();
		var applied = new ArrayList<String>();

		assertTrue(sut.apply("edge0", new Ordering(SOURCE_A, 5L), () -> applied.add("A5")));
		assertTrue(sut.apply("edge0", new Ordering(SOURCE_B, 0L), () -> applied.add("B0")));
		assertFalse(sut.apply("edge0", new Ordering(SOURCE_A, 6L), () -> applied.add("A6")));
		assertEquals(List.of("A5", "B0"), applied);
	}

	@Test
	public void shouldRetryRevisionAfterApplyFailure() {
		var sut = new EdgeConfigVersionTracker();
		var ordering = new Ordering(SOURCE_A, 1L);

		assertThrows(IllegalStateException.class,
				() -> sut.apply("edge0", ordering, () -> {
					throw new IllegalStateException("failed");
				}));
		assertTrue(sut.apply("edge0", ordering, () -> {
		}));
	}

	@Test
	public void shouldNotBlockDifferentEdges() throws Exception {
		var sut = new EdgeConfigVersionTracker();
		var firstStarted = new CountDownLatch(1);
		var releaseFirst = new CountDownLatch(1);
		var secondFinished = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			executor.execute(() -> sut.apply("edge0", new Ordering(SOURCE_A, 0L), () -> {
				firstStarted.countDown();
				await(releaseFirst);
			}));
			assertTrue(firstStarted.await(2, SECONDS));
			executor.execute(() -> sut.apply("edge1", new Ordering(SOURCE_A, 0L), secondFinished::countDown));
			assertTrue(secondFinished.await(2, SECONDS));
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	public void shouldKeepOnlyEightRetiredSources() {
		var sut = new EdgeConfigVersionTracker();
		var sources = new ArrayList<UUID>();
		for (var i = 0L; i < 10L; i++) {
			sources.add(source(i));
		}

		assertTrue(sut.apply("edge0", new Ordering(sources.get(0), 0L), () -> {
		}));
		for (var i = 1; i < sources.size(); i++) {
			assertTrue(sut.apply("edge0", new Ordering(sources.get(i), 0L), () -> {
			}));
		}

		for (var i = 1; i <= 8; i++) {
			assertFalse(sut.apply("edge0", new Ordering(sources.get(i), 1L), () -> {
			}));
		}
		assertTrue(sut.apply("edge0", new Ordering(sources.get(0), 1L), () -> {
		}));
	}

	@Test
	public void shouldNotCommitSourceSwitchAfterApplyFailure() {
		var sut = new EdgeConfigVersionTracker();
		var applied = new ArrayList<String>();

		assertTrue(sut.apply("edge0", new Ordering(SOURCE_A, 0L), () -> applied.add("A0")));
		assertThrows(IllegalStateException.class,
				() -> sut.apply("edge0", new Ordering(SOURCE_B, 0L), () -> {
					throw new IllegalStateException("failed");
				}));
		assertTrue(sut.apply("edge0", new Ordering(SOURCE_A, 1L), () -> applied.add("A1")));
		assertTrue(sut.apply("edge0", new Ordering(SOURCE_B, 0L), () -> applied.add("B0")));
		assertEquals(List.of("A0", "A1", "B0"), applied);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
