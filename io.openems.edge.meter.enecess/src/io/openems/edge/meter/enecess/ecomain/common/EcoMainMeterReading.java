package io.openems.edge.meter.enecess.ecomain.common;

public record EcoMainMeterReading(Phase l1, Phase l2, Phase l3, Integer totalActivePower,
		Long totalProductionEnergy, Long totalConsumptionEnergy) {

	public record Phase(Integer activePower, Integer voltage, Integer current, Long productionEnergy,
			Long consumptionEnergy) {
	}
}
