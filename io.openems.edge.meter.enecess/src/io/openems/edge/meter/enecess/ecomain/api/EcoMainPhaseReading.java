package io.openems.edge.meter.enecess.ecomain.api;

public record EcoMainPhaseReading(Integer activePower, Integer voltage, Integer current, Long forwardEnergy,
		Long reverseEnergy) {
}
