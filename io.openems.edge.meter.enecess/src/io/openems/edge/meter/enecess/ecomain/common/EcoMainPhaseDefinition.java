package io.openems.edge.meter.enecess.ecomain.common;

import java.util.Objects;

import io.openems.edge.common.type.Phase.SinglePhase;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;

public record EcoMainPhaseDefinition(SinglePhase phase, EcoMainChannelAddress address, boolean invert) {

	public EcoMainPhaseDefinition {
		Objects.requireNonNull(phase);
		Objects.requireNonNull(address);
	}
}
