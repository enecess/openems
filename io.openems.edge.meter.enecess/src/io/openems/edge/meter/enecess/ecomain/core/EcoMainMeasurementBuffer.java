package io.openems.edge.meter.enecess.ecomain.core;

import java.util.LinkedHashMap;

import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainMainReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainPhaseReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainSnapshot;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainConstants;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainConversions;

final class EcoMainMeasurementBuffer {

	private static final int MAIN_PHASE_COUNT = 3;
	private static final int MAIN_VALUE_COUNT = 4;
	private static final int BRANCH_COUNT = EcoMainConstants.SOURCE_COUNT * EcoMainConstants.CHANNELS_PER_SOURCE;

	private final Object lock = new Object();
	private final Integer[] mainActivePower = new Integer[MAIN_VALUE_COUNT];
	private final Long[] mainForwardEnergy = new Long[MAIN_VALUE_COUNT];
	private final Long[] mainReverseEnergy = new Long[MAIN_VALUE_COUNT];
	private final Integer[] mainVoltage = new Integer[MAIN_PHASE_COUNT];
	private final Integer[] mainCurrent = new Integer[MAIN_PHASE_COUNT];
	private final Integer[] branchActivePower = new Integer[BRANCH_COUNT];
	private final Integer[] branchVoltage = new Integer[BRANCH_COUNT];
	private final Integer[] branchCurrent = new Integer[BRANCH_COUNT];
	private final Long[] branchForwardEnergy = new Long[BRANCH_COUNT];
	private final Long[] branchReverseEnergy = new Long[BRANCH_COUNT];

	void updateMainActivePower(int index, Long raw) {
		synchronized (this.lock) {
			this.mainActivePower[index] = raw == null ? null : EcoMainConversions.watts(raw.intValue());
		}
	}

	void updateMainForwardEnergy(int index, Long raw) {
		synchronized (this.lock) {
			this.mainForwardEnergy[index] = raw == null ? null : EcoMainConversions.wattHours(raw);
		}
	}

	void updateMainReverseEnergy(int index, Long raw) {
		synchronized (this.lock) {
			this.mainReverseEnergy[index] = raw == null ? null : EcoMainConversions.wattHours(raw);
		}
	}

	void updateMainVoltage(int index, Integer raw) {
		synchronized (this.lock) {
			this.mainVoltage[index] = raw == null ? null : EcoMainConversions.millivolts(raw);
		}
	}

	void updateMainCurrent(int index, Integer raw) {
		synchronized (this.lock) {
			this.mainCurrent[index] = raw == null ? null : EcoMainConversions.milliamps(raw);
		}
	}

	void updateBranchActivePower(int index, Long raw) {
		synchronized (this.lock) {
			this.branchActivePower[index] = raw == null ? null : EcoMainConversions.watts(raw.intValue());
		}
	}

	void updateBranchVoltage(int index, Integer raw) {
		synchronized (this.lock) {
			this.branchVoltage[index] = raw == null ? null : EcoMainConversions.millivolts(raw);
		}
	}

	void updateBranchCurrent(int index, Integer raw) {
		synchronized (this.lock) {
			this.branchCurrent[index] = raw == null ? null : EcoMainConversions.milliamps(raw);
		}
	}

	void updateBranchForwardEnergy(int index, Long raw) {
		synchronized (this.lock) {
			this.branchForwardEnergy[index] = raw == null ? null : EcoMainConversions.wattHours(raw);
		}
	}

	void updateBranchReverseEnergy(int index, Long raw) {
		synchronized (this.lock) {
			this.branchReverseEnergy[index] = raw == null ? null : EcoMainConversions.wattHours(raw);
		}
	}

	EcoMainSnapshot freeze(long sequence, EcoMainIdentityStatus identityStatus) {
		synchronized (this.lock) {
			var main = new EcoMainMainReading(//
					this.mainPhase(0), //
					this.mainPhase(1), //
					this.mainPhase(2), //
					this.mainActivePower[3], //
					this.mainForwardEnergy[3], //
					this.mainReverseEnergy[3]);
			var branchReadings = new LinkedHashMap<EcoMainChannelAddress, EcoMainPhaseReading>(BRANCH_COUNT);
			for (var source = 0; source < EcoMainConstants.SOURCE_COUNT; source++) {
				for (var channel = 1; channel <= EcoMainConstants.CHANNELS_PER_SOURCE; channel++) {
					var index = branchIndex(source, channel);
					branchReadings.put(new EcoMainChannelAddress(source, channel), new EcoMainPhaseReading(//
							this.branchActivePower[index], //
							this.branchVoltage[index], //
							this.branchCurrent[index], //
							this.branchForwardEnergy[index], //
							this.branchReverseEnergy[index]));
				}
			}
			return new EcoMainSnapshot(sequence, identityStatus, main, branchReadings);
		}
	}

	private EcoMainPhaseReading mainPhase(int index) {
		return new EcoMainPhaseReading(//
				this.mainActivePower[index], //
				this.mainVoltage[index], //
				this.mainCurrent[index], //
				this.mainForwardEnergy[index], //
				this.mainReverseEnergy[index]);
	}

	private static int branchIndex(int source, int channel) {
		return source * EcoMainConstants.CHANNELS_PER_SOURCE + channel - 1;
	}
}
