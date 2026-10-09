package io.openems.edge.meter.enecess.ecomain.main;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.MeterType;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainCore;
import io.openems.edge.meter.enecess.ecomain.common.AbstractEcoMainMeter;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainMeterMapper;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.enecess.ecoMain.Main", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ TOPIC_CYCLE_BEFORE_PROCESS_IMAGE })
@GenerateTargetsFromReferences("core")
public class MeterEcoMainMainImpl extends AbstractEcoMainMeter
		implements MeterEcoMainMain, ElectricityMeter, OpenemsComponent, EventHandler {

	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.core_id})(enabled=true))")
	private EcoMainCore core;

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.GRID;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled() || !TOPIC_CYCLE_BEFORE_PROCESS_IMAGE.equals(event.getTopic())) {
			return;
		}

		EcoMainMeterMapper.mapMain(this.core.getSnapshot(), MeterType.GRID) //
				.ifPresentOrElse(this::applyReading, this::invalidateMeasurementChannels);
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}
}
