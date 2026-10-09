package io.openems.edge.controller.api.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.UUID;

import org.junit.Test;

public class EdgeConfigOrderingSequenceTest {

	@Test
	public void shouldIncrementRevisionWithinSource() {
		var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		var sut = new EdgeConfigOrderingSequence(sourceId);

		assertEquals(0L, sut.next().revision());
		var second = sut.next();
		assertEquals(sourceId, second.sourceId());
		assertEquals(1L, second.revision());
	}

	@Test
	public void shouldRotateSourceAfterMaximumRevision() {
		var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		var sut = new EdgeConfigOrderingSequence(sourceId, Long.MAX_VALUE);

		var maximum = sut.next();
		var rotated = sut.next();
		assertEquals(sourceId, maximum.sourceId());
		assertEquals(Long.MAX_VALUE, maximum.revision());
		assertNotEquals(sourceId, rotated.sourceId());
		assertEquals(0L, rotated.revision());
	}
}
