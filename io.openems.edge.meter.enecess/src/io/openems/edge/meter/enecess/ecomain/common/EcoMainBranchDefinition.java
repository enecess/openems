package io.openems.edge.meter.enecess.ecomain.common;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import io.openems.edge.common.type.Phase.SinglePhase;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;

public record EcoMainBranchDefinition(List<EcoMainPhaseDefinition> phases) {

	public EcoMainBranchDefinition {
		phases = List.copyOf(phases);
		if (phases.size() != 1 && phases.size() != 3) {
			throw new IllegalArgumentException("A branch must define one or three phases");
		}

		var logicalPhases = EnumSet.noneOf(SinglePhase.class);
		var physicalAddresses = new HashSet<EcoMainChannelAddress>();
		for (var phase : phases) {
			if (!logicalPhases.add(phase.phase())) {
				throw new IllegalArgumentException("Logical phases must not be repeated");
			}
			if (!physicalAddresses.add(phase.address())) {
				throw new IllegalArgumentException("Physical addresses must not be repeated");
			}
		}
		if (phases.size() == 3 && !logicalPhases.equals(EnumSet.allOf(SinglePhase.class))) {
			throw new IllegalArgumentException("A three-phase branch must define L1, L2 and L3");
		}
	}
}
