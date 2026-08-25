package io.openems.edge.meter.enecess.ecomain.common;

import io.openems.common.types.MeterType;

public final class EcoMainMeterTypePolicy {

	private EcoMainMeterTypePolicy() {
	}

	/**
	 * Checks whether the meter type is supported by an ecoMain branch meter.
	 *
	 * @param type the meter type
	 * @return {@code true} if the meter type is supported
	 */
	public static boolean isBranchSupported(MeterType type) {
		return type != null && switch (type) {
		case PRODUCTION, PRODUCTION_AND_CONSUMPTION, CONSUMPTION_METERED, CONSUMPTION_NOT_METERED -> true;
		case GRID, GRID_GENSET, MANAGED_CONSUMPTION_METERED -> false;
		};
	}

	/**
	 * Selects the production energy according to the meter type.
	 *
	 * @param type          the meter type
	 * @param forwardEnergy the forward energy
	 * @param reverseEnergy the reverse energy
	 * @return the production energy
	 */
	public static long productionEnergy(MeterType type, long forwardEnergy, long reverseEnergy) {
		requireEnergyMappingSupported(type);
		return switch (type) {
		case GRID, CONSUMPTION_METERED, CONSUMPTION_NOT_METERED -> reverseEnergy;
		case PRODUCTION, PRODUCTION_AND_CONSUMPTION -> forwardEnergy;
		case GRID_GENSET, MANAGED_CONSUMPTION_METERED -> throw unsupported(type);
		};
	}

	/**
	 * Selects the consumption energy according to the meter type.
	 *
	 * @param type          the meter type
	 * @param forwardEnergy the forward energy
	 * @param reverseEnergy the reverse energy
	 * @return the consumption energy
	 */
	public static long consumptionEnergy(MeterType type, long forwardEnergy, long reverseEnergy) {
		requireEnergyMappingSupported(type);
		return switch (type) {
		case GRID, CONSUMPTION_METERED, CONSUMPTION_NOT_METERED -> forwardEnergy;
		case PRODUCTION, PRODUCTION_AND_CONSUMPTION -> reverseEnergy;
		case GRID_GENSET, MANAGED_CONSUMPTION_METERED -> throw unsupported(type);
		};
	}

	private static boolean isEnergyMappingSupported(MeterType type) {
		return type == MeterType.GRID || isBranchSupported(type);
	}

	private static void requireEnergyMappingSupported(MeterType type) {
		if (!isEnergyMappingSupported(type)) {
			throw unsupported(type);
		}
	}

	private static IllegalArgumentException unsupported(MeterType type) {
		return new IllegalArgumentException("Unsupported meter type: " + type);
	}
}
