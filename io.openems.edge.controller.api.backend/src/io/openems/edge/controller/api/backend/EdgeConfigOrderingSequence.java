package io.openems.edge.controller.api.backend;

import java.util.Objects;
import java.util.UUID;

import io.openems.common.jsonrpc.notification.EdgeConfigNotification.Ordering;

final class EdgeConfigOrderingSequence {

	private UUID sourceId;
	private long nextRevision;

	EdgeConfigOrderingSequence(UUID sourceId) {
		this(sourceId, 0L);
	}

	EdgeConfigOrderingSequence(UUID sourceId, long nextRevision) {
		this.sourceId = Objects.requireNonNull(sourceId);
		if (nextRevision < 0) {
			throw new IllegalArgumentException("Next revision must not be negative");
		}
		this.nextRevision = nextRevision;
	}

	synchronized Ordering next() {
		var result = new Ordering(this.sourceId, this.nextRevision);
		if (this.nextRevision == Long.MAX_VALUE) {
			this.sourceId = UUID.randomUUID();
			this.nextRevision = 0L;
		} else {
			this.nextRevision++;
		}
		return result;
	}
}
