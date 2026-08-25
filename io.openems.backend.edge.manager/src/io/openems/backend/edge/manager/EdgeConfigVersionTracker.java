package io.openems.backend.edge.manager;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.openems.common.jsonrpc.notification.EdgeConfigNotification.Ordering;

final class EdgeConfigVersionTracker {

	private final ConcurrentHashMap<String, EdgeState> edgeStates = new ConcurrentHashMap<>();

	boolean apply(String edgeId, Ordering ordering, Runnable action) {
		Objects.requireNonNull(edgeId);
		Objects.requireNonNull(ordering);
		Objects.requireNonNull(action);
		return this.edgeStates.computeIfAbsent(edgeId, ignored -> new EdgeState()).apply(ordering, action);
	}

	private static final class EdgeState {

		private static final int MAX_RETIRED_SOURCE_IDS = 8;

		private UUID currentSourceId;
		private long highestRevision = -1L;
		private final ArrayDeque<UUID> retiredSourceIds = new ArrayDeque<>(MAX_RETIRED_SOURCE_IDS);
		private final HashSet<UUID> retiredSourceIdSet = new HashSet<>();

		private synchronized boolean apply(Ordering ordering, Runnable action) {
			var sourceId = ordering.sourceId();
			if (sourceId.equals(this.currentSourceId)) {
				if (ordering.revision() <= this.highestRevision) {
					return false;
				}
				action.run();
				this.highestRevision = ordering.revision();
				return true;
			}
			if (this.retiredSourceIdSet.contains(sourceId)) {
				return false;
			}

			action.run();
			if (this.currentSourceId != null) {
				this.retire(this.currentSourceId);
			}
			this.currentSourceId = sourceId;
			this.highestRevision = ordering.revision();
			return true;
		}

		private void retire(UUID sourceId) {
			if (this.retiredSourceIds.size() == MAX_RETIRED_SOURCE_IDS) {
				var evicted = this.retiredSourceIds.removeFirst();
				this.retiredSourceIdSet.remove(evicted);
			}
			this.retiredSourceIds.addLast(sourceId);
			this.retiredSourceIdSet.add(sourceId);
		}
	}
}
