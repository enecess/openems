package io.openems.edge.meter.enecess.ecomain.common;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainMeterReading.Phase;
import io.openems.edge.meter.api.ElectricityMeter;

public abstract class AbstractEcoMainMeter extends AbstractOpenemsComponent implements ElectricityMeter {

	protected AbstractEcoMainMeter() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values());

		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);
	}

	protected final void applyReading(EcoMainMeterReading reading) {
		this._setActivePower(reading.totalActivePower());
		this.applyPhase(reading.l1(), //
				ElectricityMeter.ChannelId.ACTIVE_POWER_L1, //
				ElectricityMeter.ChannelId.VOLTAGE_L1, //
				ElectricityMeter.ChannelId.CURRENT_L1, //
				ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L1, //
				ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L1);
		this.applyPhase(reading.l2(), //
				ElectricityMeter.ChannelId.ACTIVE_POWER_L2, //
				ElectricityMeter.ChannelId.VOLTAGE_L2, //
				ElectricityMeter.ChannelId.CURRENT_L2, //
				ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L2, //
				ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L2);
		this.applyPhase(reading.l3(), //
				ElectricityMeter.ChannelId.ACTIVE_POWER_L3, //
				ElectricityMeter.ChannelId.VOLTAGE_L3, //
				ElectricityMeter.ChannelId.CURRENT_L3, //
				ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L3, //
				ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L3);
		this._setActiveProductionEnergy(reading.totalProductionEnergy());
		this._setActiveConsumptionEnergy(reading.totalConsumptionEnergy());
	}

	protected final void invalidateMeasurementChannels() {
		this._setActivePower(null);
		this.applyPhase(null, //
				ElectricityMeter.ChannelId.ACTIVE_POWER_L1, //
				ElectricityMeter.ChannelId.VOLTAGE_L1, //
				ElectricityMeter.ChannelId.CURRENT_L1, //
				ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L1, //
				ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L1);
		this.applyPhase(null, //
				ElectricityMeter.ChannelId.ACTIVE_POWER_L2, //
				ElectricityMeter.ChannelId.VOLTAGE_L2, //
				ElectricityMeter.ChannelId.CURRENT_L2, //
				ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L2, //
				ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L2);
		this.applyPhase(null, //
				ElectricityMeter.ChannelId.ACTIVE_POWER_L3, //
				ElectricityMeter.ChannelId.VOLTAGE_L3, //
				ElectricityMeter.ChannelId.CURRENT_L3, //
				ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L3, //
				ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L3);
		this._setVoltage(null);
		this._setCurrent(null);
	}

	private void applyPhase(Phase phase, ElectricityMeter.ChannelId activePowerChannel,
			ElectricityMeter.ChannelId voltageChannel, ElectricityMeter.ChannelId currentChannel,
			ElectricityMeter.ChannelId productionEnergyChannel, ElectricityMeter.ChannelId consumptionEnergyChannel) {
		this.channel(activePowerChannel).setNextValue(phase == null ? null : phase.activePower());
		this.channel(voltageChannel).setNextValue(phase == null ? null : phase.voltage());
		this.channel(currentChannel).setNextValue(phase == null ? null : phase.current());
		if (phase != null) {
			this.channel(productionEnergyChannel).setNextValue(phase.productionEnergy());
			this.channel(consumptionEnergyChannel).setNextValue(phase.consumptionEnergy());
		}
	}
}
