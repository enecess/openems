package io.openems.edge.meter.enecess.ecomain.common;

import java.util.Objects;
import java.util.Optional;

import io.openems.common.types.MeterType;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainMainReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainPhaseReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainSnapshot;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainMeterReading.Phase;

public final class EcoMainMeterMapper {

	private EcoMainMeterMapper() {
	}

	/**
	 * Maps the main meter from a valid snapshot.
	 *
	 * @param snapshot the ecoMain snapshot
	 * @param type     the meter type
	 * @return the mapped reading, or empty if required input is unavailable
	 */
	public static Optional<EcoMainMeterReading> mapMain(EcoMainSnapshot snapshot, MeterType type) {
		if (!hasValidIdentity(snapshot) || type != MeterType.GRID) {
			return Optional.empty();
		}
		var main = snapshot.main();
		if (!isComplete(main)) {
			return Optional.empty();
		}

		return Optional.of(new EcoMainMeterReading(//
				mapPhase(main.l1(), type, false), //
				mapPhase(main.l2(), type, false), //
				mapPhase(main.l3(), type, false), //
				main.totalActivePower(), //
				EcoMainMeterTypePolicy.productionEnergy(type, main.totalForwardEnergy(), main.totalReverseEnergy()), //
				EcoMainMeterTypePolicy.consumptionEnergy(type, main.totalForwardEnergy(), main.totalReverseEnergy())));
	}

	/**
	 * Maps the configured phases of a branch meter from a valid snapshot.
	 *
	 * @param snapshot   the ecoMain snapshot
	 * @param type       the meter type
	 * @param definition the branch definition
	 * @return the mapped reading, or empty if required input is unavailable
	 */
	public static Optional<EcoMainMeterReading> mapBranch(EcoMainSnapshot snapshot, MeterType type,
			EcoMainBranchDefinition definition) {
		Objects.requireNonNull(definition);
		if (!hasValidIdentity(snapshot) || !EcoMainMeterTypePolicy.isBranchSupported(type)) {
			return Optional.empty();
		}

		Phase l1 = null;
		Phase l2 = null;
		Phase l3 = null;
		var totalActivePower = 0;
		var totalProductionEnergy = 0L;
		var totalConsumptionEnergy = 0L;
		for (var phaseDefinition : definition.phases()) {
			var source = snapshot.branchReadings().get(phaseDefinition.address());
			if (!isComplete(source)) {
				return Optional.empty();
			}
			var phase = mapPhase(source, type, phaseDefinition.invert());
			switch (phaseDefinition.phase()) {
			case L1 -> l1 = phase;
			case L2 -> l2 = phase;
			case L3 -> l3 = phase;
			}
			totalActivePower += phase.activePower();
			totalProductionEnergy += phase.productionEnergy();
			totalConsumptionEnergy += phase.consumptionEnergy();
		}

		return Optional.of(new EcoMainMeterReading(l1, l2, l3, totalActivePower, totalProductionEnergy,
				totalConsumptionEnergy));
	}

	private static boolean hasValidIdentity(EcoMainSnapshot snapshot) {
		return snapshot != null && snapshot.identityStatus() == EcoMainIdentityStatus.VALID;
	}

	private static boolean isComplete(EcoMainMainReading reading) {
		return reading != null //
				&& isComplete(reading.l1()) //
				&& isComplete(reading.l2()) //
				&& isComplete(reading.l3()) //
				&& reading.totalActivePower() != null //
				&& reading.totalForwardEnergy() != null //
				&& reading.totalReverseEnergy() != null;
	}

	private static boolean isComplete(EcoMainPhaseReading reading) {
		return reading != null //
				&& reading.activePower() != null //
				&& reading.voltage() != null //
				&& reading.current() != null //
				&& reading.forwardEnergy() != null //
				&& reading.reverseEnergy() != null;
	}

	private static Phase mapPhase(EcoMainPhaseReading source, MeterType type, boolean invert) {
		var activePower = invert ? -source.activePower() : source.activePower();
		var forwardEnergy = invert ? source.reverseEnergy() : source.forwardEnergy();
		var reverseEnergy = invert ? source.forwardEnergy() : source.reverseEnergy();
		var currentMagnitude = Math.abs(source.current());
		var current = activePower < 0 ? -currentMagnitude : currentMagnitude;
		return new Phase(//
				activePower, //
				source.voltage(), //
				current, //
				EcoMainMeterTypePolicy.productionEnergy(type, forwardEnergy, reverseEnergy), //
				EcoMainMeterTypePolicy.consumptionEnergy(type, forwardEnergy, reverseEnergy));
	}
}
