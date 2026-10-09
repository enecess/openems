package io.openems.edge.meter.enecess.ecomain.core;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

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
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedQuadruplewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.element.WordOrder;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.StringReadChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainCore;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainSnapshot;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainConstants;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.enecess.ecoMain.Core", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@GenerateTargetsFromReferences("Modbus")
@EventTopics({ TOPIC_CYCLE_AFTER_PROCESS_IMAGE })
public class EcoMainCoreImpl extends AbstractOpenemsModbusComponent
		implements EcoMainCore, ModbusComponent, OpenemsComponent, EventHandler {

	@Override
	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.modbus_id})(enabled=true))")
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	private String expectedSerialNumber;
	private volatile EcoMainIdentityStatus identityStatus = EcoMainIdentityStatus.UNKNOWN;
	private final EcoMainMeasurementBuffer measurementBuffer = new EcoMainMeasurementBuffer();
	private final AtomicReference<EcoMainSnapshot> snapshot = new AtomicReference<>();
	private long sequence;

	public EcoMainCoreImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				EcoMainCore.ChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (config.serialNumber() == null || !config.serialNumber().matches(EcoMainConstants.SERIAL_NUMBER_REGEX)) {
			throw new OpenemsException("Serial number must contain exactly 12 digits");
		}
		this.expectedSerialNumber = config.serialNumber();
		super.activate(context, config.id(), config.alias(), config.enabled(), EcoMainConstants.UNIT_ID);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this, //
				new FC3ReadRegistersTask(0, Priority.HIGH, this.energyElements(0, 124)), //
				new FC3ReadRegistersTask(124, Priority.HIGH, this.energyElements(124, 248)), //
				new FC3ReadRegistersTask(248, Priority.HIGH, this.energyElements(248, 352)), //
				new FC3ReadRegistersTask(1000, Priority.HIGH, this.activePowerElements()), //
				new FC3ReadRegistersTask(1200, Priority.HIGH, this.electricalElements(1200, 1323)), //
				new FC3ReadRegistersTask(1323, Priority.HIGH, this.electricalElements(1323, 1329)), //
				new FC3ReadRegistersTask(3000, Priority.HIGH, //
						m(EcoMainCore.ChannelId.MODEL,
								new StringWordElement(3000, 2).byteOrder(ByteOrder.LITTLE_ENDIAN)), //
						m(EcoMainCore.ChannelId.SERIAL_NUMBER,
								new StringWordElement(3002, 6).byteOrder(ByteOrder.LITTLE_ENDIAN)), //
						new DummyRegisterElement(3008), //
						m(EcoMainCore.ChannelId.SOFTWARE_VERSION, new UnsignedWordElement(3009))));
	}

	@Override
	public void handleEvent(Event event) {
		if (TOPIC_CYCLE_AFTER_PROCESS_IMAGE.equals(event.getTopic())) {
			var identity = this.evaluateIdentity();
			this.snapshot.set(this.measurementBuffer.freeze(++this.sequence, identity.status()));
		}
	}

	private EcoMainIdentity evaluateIdentity() {
		var identity = EcoMainIdentity.evaluate(//
				this.<StringReadChannel>channel(EcoMainCore.ChannelId.MODEL).value().get(), //
				this.<StringReadChannel>channel(EcoMainCore.ChannelId.SERIAL_NUMBER).value().get(), //
				this.<IntegerReadChannel>channel(EcoMainCore.ChannelId.SOFTWARE_VERSION).value().get(), //
				this.expectedSerialNumber);
		this.identityStatus = identity.status();
		this.channel(EcoMainCore.ChannelId.WRONG_DEVICE_MODEL).setNextValue(identity.wrongDeviceModel());
		this.channel(EcoMainCore.ChannelId.UNSUPPORTED_SOFTWARE_VERSION)
				.setNextValue(identity.unsupportedSoftwareVersion());
		this.channel(EcoMainCore.ChannelId.SERIAL_NUMBER_MISMATCH).setNextValue(identity.serialNumberMismatch());
		return identity;
	}

	@Override
	public EcoMainIdentityStatus getIdentityStatus() {
		return this.identityStatus;
	}

	@Override
	public EcoMainSnapshot getSnapshot() {
		return this.snapshot.get();
	}

	private ModbusElement[] energyElements(int fromAddress, int toAddress) {
		var elements = new ArrayList<ModbusElement>((toAddress - fromAddress) / 4);
		for (var address = fromAddress; address < toAddress; address += 4) {
			final var element = new UnsignedQuadruplewordElement(address).wordOrder(WordOrder.LSWMSW);
			if (address < 16) {
				var index = address / 4;
				element.onUpdateCallback(value -> this.measurementBuffer.updateMainForwardEnergy(index, value));
			} else if (address < 32) {
				var index = (address - 16) / 4;
				element.onUpdateCallback(value -> this.measurementBuffer.updateMainReverseEnergy(index, value));
			} else if (address < 192) {
				var index = (address - 32) / 4;
				element.onUpdateCallback(value -> this.measurementBuffer.updateBranchForwardEnergy(index, value));
			} else {
				var index = (address - 192) / 4;
				element.onUpdateCallback(value -> this.measurementBuffer.updateBranchReverseEnergy(index, value));
			}
			elements.add(element);
		}
		return elements.toArray(ModbusElement[]::new);
	}

	private ModbusElement[] activePowerElements() {
		var elements = new ArrayList<ModbusElement>(44);
		for (var address = 1000; address < 1088; address += 2) {
			final var element = new SignedDoublewordElement(address).wordOrder(WordOrder.LSWMSW);
			if (address < 1008) {
				var index = (address - 1000) / 2;
				element.onUpdateCallback(value -> this.measurementBuffer.updateMainActivePower(index, value));
			} else {
				var index = (address - 1008) / 2;
				element.onUpdateCallback(value -> this.measurementBuffer.updateBranchActivePower(index, value));
			}
			elements.add(element);
		}
		return elements.toArray(ModbusElement[]::new);
	}

	private ModbusElement[] electricalElements(int fromAddress, int toAddress) {
		var elements = new ArrayList<ModbusElement>(toAddress - fromAddress);
		for (var address = fromAddress; address < toAddress; address += 3) {
			if (address < 1209) {
				var index = (address - 1200) / 3;
				elements.add(new UnsignedWordElement(address)
						.onUpdateCallback(value -> this.measurementBuffer.updateMainVoltage(index, value)));
				elements.add(new UnsignedWordElement(address + 1)
						.onUpdateCallback(value -> this.measurementBuffer.updateMainCurrent(index, value)));
			} else {
				var index = (address - 1209) / 3;
				elements.add(new UnsignedWordElement(address)
						.onUpdateCallback(value -> this.measurementBuffer.updateBranchVoltage(index, value)));
				elements.add(new UnsignedWordElement(address + 1)
						.onUpdateCallback(value -> this.measurementBuffer.updateBranchCurrent(index, value)));
			}
			elements.add(new DummyRegisterElement(address + 2));
		}
		return elements.toArray(ModbusElement[]::new);
	}
}
