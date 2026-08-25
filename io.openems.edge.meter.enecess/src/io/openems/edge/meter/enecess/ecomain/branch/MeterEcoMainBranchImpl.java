package io.openems.edge.meter.enecess.ecomain.branch;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.util.List;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.MeterType;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainCore;
import io.openems.edge.meter.enecess.ecomain.common.AbstractEcoMainMeter;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainBranchDefinition;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainMeterMapper;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainMeterTypePolicy;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainPhaseDefinition;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.enecess.ecoMain.Branch", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ TOPIC_CYCLE_BEFORE_PROCESS_IMAGE })
@GenerateTargetsFromReferences("core")
public class MeterEcoMainBranchImpl extends AbstractEcoMainMeter
		implements MeterEcoMainBranch, ElectricityMeter, OpenemsComponent, EventHandler {

	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.core_id})(enabled=true))")
	private EcoMainCore core;

	private MeterType meterType = MeterType.CONSUMPTION_METERED;
	private EcoMainBranchDefinition definition;

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		try {
			var meterType = config.type() == null ? null : config.type().getMeterType();
			if (!EcoMainMeterTypePolicy.isBranchSupported(meterType)) {
				throw new IllegalArgumentException("Unsupported branch meter type: " + config.type());
			}
			this.definition = createDefinition(config);
			this.meterType = meterType;
		} catch (IllegalArgumentException e) {
			throw new OpenemsException("Invalid ecoMain branch meter configuration", e);
		}
		super.activate(context, config.id(), config.alias(), config.enabled());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled() || !TOPIC_CYCLE_BEFORE_PROCESS_IMAGE.equals(event.getTopic())) {
			return;
		}

		var snapshot = this.core.getSnapshot();
		EcoMainMeterMapper.mapBranch(snapshot, this.meterType, this.definition) //
				.ifPresentOrElse(this::applyReading, this::invalidateMeasurementChannels);
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	private static EcoMainBranchDefinition createDefinition(Config config) {
		var phase1 = createPhaseDefinition(config.phase1(), config.source1(), config.channel1(), config.invert1());
		return switch (config.phaseCount()) {
		case SINGLE_PHASE -> new EcoMainBranchDefinition(List.of(phase1));
		case THREE_PHASE -> new EcoMainBranchDefinition(List.of(//
				phase1, //
				createPhaseDefinition(config.phase2(), config.source2(), config.channel2(), config.invert2()), //
				createPhaseDefinition(config.phase3(), config.source3(), config.channel3(), config.invert3())));
		};
	}

	private static EcoMainPhaseDefinition createPhaseDefinition(
			io.openems.edge.common.type.Phase.SinglePhase phase, int source, int channel, boolean invert) {
		return new EcoMainPhaseDefinition(phase, new EcoMainChannelAddress(source, channel), invert);
	}
}
