package io.openems.edge.meter.enecess.ecomain.test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainCore;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainSnapshot;

public final class DummyEcoMainCore extends AbstractOpenemsComponent implements EcoMainCore {

	private final AtomicReference<EcoMainSnapshot> snapshot = new AtomicReference<>();
	private final AtomicInteger snapshotReadCount = new AtomicInteger();

	public DummyEcoMainCore() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				EcoMainCore.ChannelId.values());
	}

	public void setSnapshot(EcoMainSnapshot snapshot) {
		this.snapshot.set(snapshot);
	}

	/**
	 * Gets the number of snapshot reads.
	 *
	 * @return the snapshot read count
	 */
	public int snapshotReadCount() {
		return this.snapshotReadCount.get();
	}

	@Override
	public EcoMainIdentityStatus getIdentityStatus() {
		var snapshot = this.snapshot.get();
		return snapshot == null ? EcoMainIdentityStatus.UNKNOWN : snapshot.identityStatus();
	}

	@Override
	public EcoMainSnapshot getSnapshot() {
		this.snapshotReadCount.incrementAndGet();
		return this.snapshot.get();
	}

	@Override
	public String debugLog() {
		return "Snapshot:" + this.snapshot.get();
	}
}
