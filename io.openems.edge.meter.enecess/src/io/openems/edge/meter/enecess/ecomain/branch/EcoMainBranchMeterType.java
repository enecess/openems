package io.openems.edge.meter.enecess.ecomain.branch;

import io.openems.common.types.MeterType;

/**
 * Meter types supported by an ecoMain or ecoSub branch.
 */
public enum EcoMainBranchMeterType {
	PRODUCTION(MeterType.PRODUCTION), //
	PRODUCTION_AND_CONSUMPTION(MeterType.PRODUCTION_AND_CONSUMPTION), //
	CONSUMPTION_METERED(MeterType.CONSUMPTION_METERED), //
	CONSUMPTION_NOT_METERED(MeterType.CONSUMPTION_NOT_METERED);

	private final MeterType meterType;

	private EcoMainBranchMeterType(MeterType meterType) {
		this.meterType = meterType;
	}

	/**
	 * Gets the corresponding OpenEMS meter type.
	 *
	 * @return the OpenEMS meter type
	 */
	public MeterType getMeterType() {
		return this.meterType;
	}
}
