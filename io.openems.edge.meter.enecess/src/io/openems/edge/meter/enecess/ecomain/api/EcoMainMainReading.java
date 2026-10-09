package io.openems.edge.meter.enecess.ecomain.api;

public record EcoMainMainReading(EcoMainPhaseReading l1, EcoMainPhaseReading l2, EcoMainPhaseReading l3,
		Integer totalActivePower, Long totalForwardEnergy, Long totalReverseEnergy) {
}
