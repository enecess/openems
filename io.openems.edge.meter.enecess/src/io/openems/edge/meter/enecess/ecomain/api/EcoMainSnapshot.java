package io.openems.edge.meter.enecess.ecomain.api;

import java.util.Map;
import java.util.Objects;

public record EcoMainSnapshot(long sequence, EcoMainIdentityStatus identityStatus, EcoMainMainReading main,
		Map<EcoMainChannelAddress, EcoMainPhaseReading> branchReadings) {

	public EcoMainSnapshot {
		identityStatus = Objects.requireNonNull(identityStatus);
		branchReadings = Map.copyOf(branchReadings);
	}
}
