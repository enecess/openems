package io.openems.edge.meter.enecess.ecomain.main;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE;
import static io.openems.edge.common.test.TestUtils.activateNextProcessImage;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.osgi.service.event.Event;

import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainMainReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainPhaseReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainSnapshot;
import io.openems.edge.meter.enecess.ecomain.test.DummyEcoMainCore;
import io.openems.edge.meter.api.ElectricityMeter;

class MeterEcoMainMainImplTest {

	private static final Event BEFORE_PROCESS_IMAGE = new Event(TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, Map.of());
	private static final String MANAGED_PV_INVERTER = "io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter";
	private static final ChannelId[] INSTANTANEOUS_CHANNELS = { //
			ElectricityMeter.ChannelId.ACTIVE_POWER, //
			ElectricityMeter.ChannelId.ACTIVE_POWER_L1, //
			ElectricityMeter.ChannelId.ACTIVE_POWER_L2, //
			ElectricityMeter.ChannelId.ACTIVE_POWER_L3, //
			ElectricityMeter.ChannelId.VOLTAGE, //
			ElectricityMeter.ChannelId.VOLTAGE_L1, //
			ElectricityMeter.ChannelId.VOLTAGE_L2, //
			ElectricityMeter.ChannelId.VOLTAGE_L3, //
			ElectricityMeter.ChannelId.CURRENT, //
			ElectricityMeter.ChannelId.CURRENT_L1, //
			ElectricityMeter.ChannelId.CURRENT_L2, //
			ElectricityMeter.ChannelId.CURRENT_L3 };
	private static final ChannelId[] ENERGY_CHANNELS = { //
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, //
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L1, //
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L2, //
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY_L3, //
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, //
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L1, //
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L2, //
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L3 };

	@Test
	void mapsEveryStandardMeasurementChannelAndUsesDeviceTotalPower() throws Exception {
		var core = new DummyEcoMainCore();
		core.setSnapshot(validSnapshot());
		var sut = activate(core, MyConfig.create().build());

		cycle(sut, core);

		assertValidMeasurementChannels(sut);
		assertEquals(MeterType.GRID, sut.getMeterType());
	}

	@Test
	void keepsEnergyUndefinedUntilFirstValidSnapshot() throws Exception {
		var core = new DummyEcoMainCore();
		var sut = activate(core, MyConfig.create().build());

		cycle(sut, core);

		assertInstantaneousChannelsUndefined(sut);
		assertEnergyChannelsUndefined(sut);
	}

	@Test
	void invalidatesInstantaneousMeasurementsAndPreservesEnergyAfterEveryInvalidSnapshot() throws Exception {
		var valid = validSnapshot();
		var invalidSnapshots = new ArrayList<EcoMainSnapshot>();
		invalidSnapshots.add(null);
		invalidSnapshots.add(snapshot(EcoMainIdentityStatus.UNKNOWN, valid.main()));
		invalidSnapshots.add(snapshot(EcoMainIdentityStatus.INVALID, valid.main()));
		invalidSnapshots.add(snapshot(EcoMainIdentityStatus.VALID, null));

		var completePhase = valid.main().l1();
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(null, completePhase, completePhase, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(completePhase, null, completePhase, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(completePhase, completePhase, null, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(
				new EcoMainPhaseReading(null, 230_000, 100, 101L, 11L), completePhase, completePhase, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(
				new EcoMainPhaseReading(10, null, 100, 101L, 11L), completePhase, completePhase, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(
				new EcoMainPhaseReading(10, 230_000, null, 101L, 11L), completePhase, completePhase, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(
				new EcoMainPhaseReading(10, 230_000, 100, null, 11L), completePhase, completePhase, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(new EcoMainMainReading(
				new EcoMainPhaseReading(10, 230_000, 100, 101L, null), completePhase, completePhase, 999, 1_000L,
				2_000L)));
		invalidSnapshots.add(validSnapshot(
				new EcoMainMainReading(completePhase, completePhase, completePhase, null, 1_000L, 2_000L)));
		invalidSnapshots.add(validSnapshot(
				new EcoMainMainReading(completePhase, completePhase, completePhase, 999, null, 2_000L)));
		invalidSnapshots.add(validSnapshot(
				new EcoMainMainReading(completePhase, completePhase, completePhase, 999, 1_000L, null)));

		var core = new DummyEcoMainCore();
		var sut = activate(core, MyConfig.create().build());
		for (var invalid : invalidSnapshots) {
			core.setSnapshot(valid);
			cycle(sut, core);
			assertValidMeasurementChannels(sut);

			core.setSnapshot(invalid);
			cycle(sut, core);
			assertInstantaneousChannelsUndefined(sut);
			assertValidEnergyChannels(sut);

			core.setSnapshot(valid);
			cycle(sut, core);
			assertValidMeasurementChannels(sut);
		}
	}

	@Test
	void preservesEnergyChannelIdentityAndCallbacksAcrossInvalidSnapshot() throws Exception {
		var core = new DummyEcoMainCore();
		core.setSnapshot(validSnapshot());
		var sut = activate(core, MyConfig.create().build());
		cycle(sut, core);
		var energyChannels = energyChannels(sut);
		var callbackCount = new AtomicInteger();
		energyChannels.forEach(channel -> channel.onSetNextValue(ignore -> callbackCount.incrementAndGet()));

		core.setSnapshot(null);
		cycle(sut, core);
		assertInstantaneousChannelsUndefined(sut);
		assertEquals(0, callbackCount.get(), "energy callbacks on invalid snapshot");

		core.setSnapshot(recoveredSnapshot());
		cycle(sut, core);

		assertAll(//
				() -> assertEnergyChannelIdentity(sut, energyChannels), //
				() -> assertEquals(ENERGY_CHANNELS.length, callbackCount.get(), "energy callbacks after recovery"), //
				() -> assertRecoveredEnergyChannels(sut));
	}

	@Test
	void hasNoTypeConfigurationAndAlwaysReportsGrid() throws Exception {
		var fields = Arrays.stream(Config.class.getDeclaredMethods())
				.map(Method::getName)
				.filter(name -> !name.equals("webconsole_configurationFactory_nameHint"))
				.collect(Collectors.toSet());

		assertEquals(Set.of("id", "alias", "enabled", "core_id"), fields);
		var sut = activate(new DummyEcoMainCore(), MyConfig.create().build());
		assertEquals(MeterType.GRID, sut.getMeterType());
	}

	@Test
	void hasNoModbusOrPvControlNature() {
		assertFalse(ModbusComponent.class.isAssignableFrom(MeterEcoMainMainImpl.class));
		assertFalse(implementsInterface(MeterEcoMainMainImpl.class, MANAGED_PV_INVERTER));
	}

	private static MeterEcoMainMainImpl activate(DummyEcoMainCore core, MyConfig config) throws Exception {
		var sut = new MeterEcoMainMainImpl();
		new ComponentTest(sut) //
				.addReference("core", core) //
				.activate(config);
		return sut;
	}

	private static void cycle(MeterEcoMainMainImpl sut, DummyEcoMainCore core) {
		var before = core.snapshotReadCount();
		sut.handleEvent(BEFORE_PROCESS_IMAGE);
		assertEquals(before + 1, core.snapshotReadCount(), "getSnapshot() calls per cycle");
		activateNextProcessImage(sut);
	}

	private static EcoMainSnapshot validSnapshot() {
		return validSnapshot(new EcoMainMainReading(//
				new EcoMainPhaseReading(10, 230_000, 100, 101L, 11L), //
				new EcoMainPhaseReading(-20, 231_000, 200, 202L, 22L), //
				new EcoMainPhaseReading(30, 232_000, 300, 303L, 33L), //
				999, 1_000L, 2_000L));
	}

	private static EcoMainSnapshot validSnapshot(EcoMainMainReading main) {
		return snapshot(EcoMainIdentityStatus.VALID, main);
	}

	private static EcoMainSnapshot recoveredSnapshot() {
		return validSnapshot(new EcoMainMainReading(//
				new EcoMainPhaseReading(10, 230_000, 100, 111L, 21L), //
				new EcoMainPhaseReading(-20, 231_000, 200, 212L, 32L), //
				new EcoMainPhaseReading(30, 232_000, 300, 313L, 43L), //
				999, 1_100L, 2_100L));
	}

	private static EcoMainSnapshot snapshot(EcoMainIdentityStatus status, EcoMainMainReading main) {
		return new EcoMainSnapshot(1L, status, main, Map.of());
	}

	private static void assertValidMeasurementChannels(MeterEcoMainMainImpl sut) {
		assertEquals(999, sut.getActivePower().get());
		assertEquals(10, sut.getActivePowerL1().get());
		assertEquals(-20, sut.getActivePowerL2().get());
		assertEquals(30, sut.getActivePowerL3().get());
		assertEquals(231_000, sut.getVoltage().get());
		assertEquals(230_000, sut.getVoltageL1().get());
		assertEquals(231_000, sut.getVoltageL2().get());
		assertEquals(232_000, sut.getVoltageL3().get());
		assertEquals(200, sut.getCurrent().get());
		assertEquals(100, sut.getCurrentL1().get());
		assertEquals(-200, sut.getCurrentL2().get());
		assertEquals(300, sut.getCurrentL3().get());
		assertValidEnergyChannels(sut);
	}

	private static void assertValidEnergyChannels(MeterEcoMainMainImpl sut) {
		assertEquals(2_000L, sut.getActiveProductionEnergy().get());
		assertEquals(11L, sut.getActiveProductionEnergyL1().get());
		assertEquals(22L, sut.getActiveProductionEnergyL2().get());
		assertEquals(33L, sut.getActiveProductionEnergyL3().get());
		assertEquals(1_000L, sut.getActiveConsumptionEnergy().get());
		assertEquals(101L, sut.getActiveConsumptionEnergyL1().get());
		assertEquals(202L, sut.getActiveConsumptionEnergyL2().get());
		assertEquals(303L, sut.getActiveConsumptionEnergyL3().get());
	}

	private static void assertRecoveredEnergyChannels(MeterEcoMainMainImpl sut) {
		assertEquals(2_100L, sut.getActiveProductionEnergy().get());
		assertEquals(21L, sut.getActiveProductionEnergyL1().get());
		assertEquals(32L, sut.getActiveProductionEnergyL2().get());
		assertEquals(43L, sut.getActiveProductionEnergyL3().get());
		assertEquals(1_100L, sut.getActiveConsumptionEnergy().get());
		assertEquals(111L, sut.getActiveConsumptionEnergyL1().get());
		assertEquals(212L, sut.getActiveConsumptionEnergyL2().get());
		assertEquals(313L, sut.getActiveConsumptionEnergyL3().get());
	}

	private static void assertInstantaneousChannelsUndefined(MeterEcoMainMainImpl sut) {
		for (var channelId : INSTANTANEOUS_CHANNELS) {
			assertNull(sut.channel(channelId).value().get(), channelId.id());
		}
	}

	private static void assertEnergyChannelsUndefined(MeterEcoMainMainImpl sut) {
		for (var channelId : ENERGY_CHANNELS) {
			assertNull(sut.channel(channelId).value().get(), channelId.id());
		}
	}

	private static List<Channel<Long>> energyChannels(MeterEcoMainMainImpl sut) {
		var result = new ArrayList<Channel<Long>>(ENERGY_CHANNELS.length);
		for (var channelId : ENERGY_CHANNELS) {
			result.add(sut.channel(channelId));
		}
		return result;
	}

	private static void assertEnergyChannelIdentity(MeterEcoMainMainImpl sut, List<Channel<Long>> expectedChannels) {
		for (var i = 0; i < ENERGY_CHANNELS.length; i++) {
			var channelId = ENERGY_CHANNELS[i];
			assertSame(expectedChannels.get(i), sut.channel(channelId), channelId.id());
		}
	}

	private static boolean implementsInterface(Class<?> type, String interfaceName) {
		for (var implementedInterface : type.getInterfaces()) {
			if (implementedInterface.getName().equals(interfaceName)
					|| implementsInterface(implementedInterface, interfaceName)) {
				return true;
			}
		}
		var superclass = type.getSuperclass();
		return superclass != null && implementsInterface(superclass, interfaceName);
	}

}
