package io.openems.edge.meter.enecess.ecomain.main;

import org.osgi.service.event.EventHandler;

import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;

public interface MeterEcoMainMain extends ElectricityMeter, OpenemsComponent, EventHandler {
}
