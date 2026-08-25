package io.openems.edge.meter.enecess.ecomain.branch;

import org.osgi.service.event.EventHandler;

import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;

public interface MeterEcoMainBranch extends ElectricityMeter, OpenemsComponent, EventHandler {
}
