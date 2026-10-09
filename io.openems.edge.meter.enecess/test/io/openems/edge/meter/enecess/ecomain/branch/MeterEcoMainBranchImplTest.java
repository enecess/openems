package io.openems.edge.meter.enecess.ecomain.branch;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE;
import static io.openems.edge.common.test.TestUtils.activateNextProcessImage;
import static io.openems.edge.common.type.Phase.SingleOrThreePhase.SINGLE_PHASE;
import static io.openems.edge.common.type.Phase.SinglePhase.L1;
import static io.openems.edge.common.type.Phase.SinglePhase.L2;
import static io.openems.edge.common.type.Phase.SinglePhase.L3;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.osgi.service.event.Event;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.type.Phase.SinglePhase;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainMainReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainPhaseReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainSnapshot;
import io.openems.edge.meter.enecess.ecomain.common.AbstractEcoMainMeter;
import io.openems.edge.meter.enecess.ecomain.main.MeterEcoMainMainImpl;
import io.openems.edge.meter.enecess.ecomain.test.DummyEcoMainCore;
import io.openems.edge.meter.api.ElectricityMeter;

class MeterEcoMainBranchImplTest {

	private static final Event BEFORE_PROCESS_IMAGE = new Event(TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, Map.of());
	private static final EcoMainChannelAddress SOURCE_0_CHANNEL_1 = new EcoMainChannelAddress(0, 1);
	private static final EcoMainChannelAddress SOURCE_1_CHANNEL_7 = new EcoMainChannelAddress(1, 7);
	private static final EcoMainChannelAddress SOURCE_3_CHANNEL_10 = new EcoMainChannelAddress(3, 10);
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

	@ParameterizedTest
	@EnumSource(SinglePhase.class)
	void mapsSinglePhaseToAnyLogicalPhaseAndLeavesTheOthersUndefined(SinglePhase logicalPhase) throws Exception {
		var core = new DummyEcoMainCore();
		core.setSnapshot(snapshot(EcoMainIdentityStatus.VALID, null,
				Map.of(SOURCE_0_CHANNEL_1, new EcoMainPhaseReading(25, 230_000, 7, 40L, 50L))));
		var sut = activate(core, MyConfig.create() //
				.setPhaseCount(SINGLE_PHASE) //
				.setPhase1(logicalPhase, 0, 1, false) //
				.build());

		cycle(sut, core);

		assertEquals(25, sut.getActivePower().get());
		assertEquals(230_000, sut.getVoltage().get());
		assertEquals(7, sut.getCurrent().get());
		assertEquals(50L, sut.getActiveProductionEnergy().get());
		assertEquals(40L, sut.getActiveConsumptionEnergy().get());
		assertPhase(sut, L1, logicalPhase == L1 ? 25 : null, logicalPhase == L1 ? 230_000 : null,
				logicalPhase == L1 ? 7 : null,
				logicalPhase == L1 ? 50L : null, logicalPhase == L1 ? 40L : null);
		assertPhase(sut, L2, logicalPhase == L2 ? 25 : null, logicalPhase == L2 ? 230_000 : null,
				logicalPhase == L2 ? 7 : null,
				logicalPhase == L2 ? 50L : null, logicalPhase == L2 ? 40L : null);
		assertPhase(sut, L3, logicalPhase == L3 ? 25 : null, logicalPhase == L3 ? 230_000 : null,
				logicalPhase == L3 ? 7 : null,
				logicalPhase == L3 ? 50L : null, logicalPhase == L3 ? 40L : null);
	}

	@Test
	void mapsThreePhasesByLogicalConfigurationInsteadOfPhysicalAddressOrder() throws Exception {
		var core = new DummyEcoMainCore();
		core.setSnapshot(baseBranchSnapshot());
		var sut = activate(core, MyConfig.create() //
				.setPhase1(L2, 0, 1, false) //
				.setPhase2(L3, 1, 7, false) //
				.setPhase3(L1, 3, 10, false) //
				.build());

		cycle(sut, core);

		assertPhase(sut, L1, 30, 232_000, 300, 33L, 303L);
		assertPhase(sut, L2, 10, 230_000, 100, 11L, 101L);
		assertPhase(sut, L3, -20, 231_000, -200, 22L, 202L);
		assertEquals(20, sut.getActivePower().get());
		assertEquals(231_000, sut.getVoltage().get());
		assertEquals(200, sut.getCurrent().get());
		assertEquals(66L, sut.getActiveProductionEnergy().get());
		assertEquals(606L, sut.getActiveConsumptionEnergy().get());
	}

	@ParameterizedTest
	@CsvSource({ //
			"true, false, false, -10, 20, 30, -100, 200, 300, 101, 22, 33, 11, 202, 303, 40, 156, 516", //
			"false, true, false, 10, -20, 30, 100, -200, 300, 11, 202, 33, 101, 22, 303, 20, 246, 426", //
			"false, false, true, 10, 20, -30, 100, 200, -300, 11, 22, 303, 101, 202, 33, 0, 336, 336" })
	void appliesInvertIndependentlyToPowerCurrentAndBothEnergyDirections(boolean invert1, boolean invert2,
			boolean invert3, int power1, int power2, int power3, int current1, int current2, int current3,
			long production1, long production2, long production3, long consumption1, long consumption2,
			long consumption3, int totalPower, long totalProduction, long totalConsumption) throws Exception {
		var core = new DummyEcoMainCore();
		core.setSnapshot(positiveBranchSnapshot());
		var sut = activate(core, MyConfig.create() //
				.setPhase1(L1, 0, 1, invert1) //
				.setPhase2(L2, 1, 7, invert2) //
				.setPhase3(L3, 3, 10, invert3) //
				.build());

		cycle(sut, core);

		assertPhase(sut, L1, power1, 230_000, current1, production1, consumption1);
		assertPhase(sut, L2, power2, 231_000, current2, production2, consumption2);
		assertPhase(sut, L3, power3, 232_000, current3, production3, consumption3);
		assertEquals(totalPower, sut.getActivePower().get());
		assertEquals(totalProduction, sut.getActiveProductionEnergy().get());
		assertEquals(totalConsumption, sut.getActiveConsumptionEnergy().get());
	}

	@Test
	void invalidatesInstantaneousMeasurementsAndPreservesEnergyUntilRecovery() throws Exception {
		var valid = positiveBranchSnapshot();
		var complete = valid.branchReadings();
		var invalidSnapshots = new ArrayList<EcoMainSnapshot>();
		invalidSnapshots.add(null);
		invalidSnapshots.add(snapshot(EcoMainIdentityStatus.UNKNOWN, null, complete));
		invalidSnapshots.add(snapshot(EcoMainIdentityStatus.INVALID, null, complete));
		invalidSnapshots.add(snapshot(EcoMainIdentityStatus.VALID, null, Map.of()));
		invalidSnapshots.add(withFirstPhase(new EcoMainPhaseReading(null, 230_000, 100, 101L, 11L)));
		invalidSnapshots.add(withFirstPhase(new EcoMainPhaseReading(10, null, 100, 101L, 11L)));
		invalidSnapshots.add(withFirstPhase(new EcoMainPhaseReading(10, 230_000, null, 101L, 11L)));
		invalidSnapshots.add(withFirstPhase(new EcoMainPhaseReading(10, 230_000, 100, null, 11L)));
		invalidSnapshots.add(withFirstPhase(new EcoMainPhaseReading(10, 230_000, 100, 101L, null)));

		var core = new DummyEcoMainCore();
		var sut = activate(core, naturalThreePhaseConfig());
		for (var invalid : invalidSnapshots) {
			core.setSnapshot(valid);
			cycle(sut, core);
			assertNaturalMeasurements(sut);

			core.setSnapshot(invalid);
			cycle(sut, core);
			assertInstantaneousChannelsUndefined(sut);
			assertNaturalEnergy(sut);

			core.setSnapshot(valid);
			cycle(sut, core);
			assertNaturalMeasurements(sut);
		}
	}

	@Test
	void keepsEnergyUndefinedBeforeFirstValidSnapshot() throws Exception {
		var core = new DummyEcoMainCore();
		var sut = activate(core, naturalThreePhaseConfig());

		cycle(sut, core);

		assertInstantaneousChannelsUndefined(sut);
		for (var channelId : ENERGY_CHANNELS) {
			assertNull(sut.channel(channelId).value().get(), channelId.id());
		}
	}

	@Test
	void preservesEnergyChannelIdentityAndCallbacksAcrossInvalidSnapshot() throws Exception {
		var core = new DummyEcoMainCore();
		core.setSnapshot(positiveBranchSnapshot());
		var sut = activate(core, naturalThreePhaseConfig());
		cycle(sut, core);
		var energyChannels = energyChannels(sut);
		var callbackCount = new AtomicInteger();
		energyChannels.forEach(channel -> channel.onSetNextValue(ignore -> callbackCount.incrementAndGet()));

		core.setSnapshot(null);
		cycle(sut, core);
		assertEquals(0, callbackCount.get(), "energy callbacks on invalid snapshot");
		assertNaturalEnergy(sut);

		core.setSnapshot(recoveredBranchSnapshot());
		cycle(sut, core);

		assertAll(//
				() -> assertEnergyChannelIdentity(sut, energyChannels), //
				() -> assertEquals(ENERGY_CHANNELS.length, callbackCount.get()), //
				() -> assertRecoveredEnergy(sut));
	}

	@Test
	void capturesAnImmutableDefinitionDuringActivation() throws Exception {
		var builder = MyConfig.create() //
				.setPhaseCount(SINGLE_PHASE) //
				.setPhase1(L1, 0, 1, false);
		var core = new DummyEcoMainCore();
		var sut = activate(core, builder.build());
		builder.setPhase1(L3, 3, 10, true);
		core.setSnapshot(snapshot(EcoMainIdentityStatus.VALID, null,
				Map.of(SOURCE_0_CHANNEL_1, new EcoMainPhaseReading(25, 230_000, 7, 40L, 50L))));

		cycle(sut, core);

		assertEquals(25, sut.getActivePowerL1().get());
		assertNull(sut.getActivePowerL3().get());
	}

	@Test
	void multipleLogicalMetersShareTheCoreSnapshotWithoutImplementingModbus() throws Exception {
		var snapshot = snapshot(EcoMainIdentityStatus.VALID, new EcoMainMainReading(//
				new EcoMainPhaseReading(1, 230_000, 10, 100L, 10L), //
				new EcoMainPhaseReading(2, 231_000, 20, 200L, 20L), //
				new EcoMainPhaseReading(3, 232_000, 30, 300L, 30L), //
				6, 600L, 60L), positiveBranchSnapshot().branchReadings());
		var core = new DummyEcoMainCore();
		core.setSnapshot(snapshot);
		var branch1 = activate(core, MyConfig.create() //
				.setId("meter1") //
				.setPhaseCount(SINGLE_PHASE) //
				.setPhase1(L1, 0, 1, false) //
				.build());
		var branch2 = activate(core, MyConfig.create() //
				.setId("meter2") //
				.setPhaseCount(SINGLE_PHASE) //
				.setPhase1(L2, 1, 7, false) //
				.build());
		var main = new MeterEcoMainMainImpl();
		new ComponentTest(main) //
				.addReference("core", core) //
				.activate(new MainConfig());

		branch1.handleEvent(BEFORE_PROCESS_IMAGE);
		branch2.handleEvent(BEFORE_PROCESS_IMAGE);
		main.handleEvent(BEFORE_PROCESS_IMAGE);
		activateNextProcessImage(branch1);
		activateNextProcessImage(branch2);
		activateNextProcessImage(main);

		assertEquals(3, core.snapshotReadCount());
		assertEquals(10, branch1.getActivePower().get());
		assertEquals(20, branch2.getActivePower().get());
		assertEquals(6, main.getActivePower().get());
		assertFalse(ModbusComponent.class.isAssignableFrom(AbstractEcoMainMeter.class));
		assertFalse(ModbusComponent.class.isAssignableFrom(MeterEcoMainBranchImpl.class));
		assertFalse(ModbusComponent.class.isAssignableFrom(MeterEcoMainMainImpl.class));
	}

	private static MeterEcoMainBranchImpl activate(DummyEcoMainCore core, MyConfig config) throws Exception {
		var sut = new MeterEcoMainBranchImpl();
		new ComponentTest(sut) //
				.addReference("core", core) //
				.activate(config);
		return sut;
	}

	private static void cycle(MeterEcoMainBranchImpl sut, DummyEcoMainCore core) {
		var before = core.snapshotReadCount();
		sut.handleEvent(BEFORE_PROCESS_IMAGE);
		assertEquals(before + 1, core.snapshotReadCount(), "getSnapshot() calls per cycle");
		activateNextProcessImage(sut);
	}

	private static MyConfig naturalThreePhaseConfig() {
		return MyConfig.create() //
				.setPhase1(L1, 0, 1, false) //
				.setPhase2(L2, 1, 7, false) //
				.setPhase3(L3, 3, 10, false) //
				.build();
	}

	private static EcoMainSnapshot baseBranchSnapshot() {
		return snapshot(EcoMainIdentityStatus.VALID, null, Map.of(//
				SOURCE_0_CHANNEL_1, new EcoMainPhaseReading(10, 230_000, 100, 101L, 11L), //
				SOURCE_1_CHANNEL_7, new EcoMainPhaseReading(-20, 231_000, 200, 202L, 22L), //
				SOURCE_3_CHANNEL_10, new EcoMainPhaseReading(30, 232_000, 300, 303L, 33L)));
	}

	private static EcoMainSnapshot positiveBranchSnapshot() {
		return snapshot(EcoMainIdentityStatus.VALID, null, Map.of(//
				SOURCE_0_CHANNEL_1, new EcoMainPhaseReading(10, 230_000, 100, 101L, 11L), //
				SOURCE_1_CHANNEL_7, new EcoMainPhaseReading(20, 231_000, 200, 202L, 22L), //
				SOURCE_3_CHANNEL_10, new EcoMainPhaseReading(30, 232_000, 300, 303L, 33L)));
	}

	private static EcoMainSnapshot recoveredBranchSnapshot() {
		return snapshot(EcoMainIdentityStatus.VALID, null, Map.of(//
				SOURCE_0_CHANNEL_1, new EcoMainPhaseReading(10, 230_000, 100, 111L, 21L), //
				SOURCE_1_CHANNEL_7, new EcoMainPhaseReading(20, 231_000, 200, 212L, 32L), //
				SOURCE_3_CHANNEL_10, new EcoMainPhaseReading(30, 232_000, 300, 313L, 43L)));
	}

	private static EcoMainSnapshot withFirstPhase(EcoMainPhaseReading firstPhase) {
		return snapshot(EcoMainIdentityStatus.VALID, null, Map.of(//
				SOURCE_0_CHANNEL_1, firstPhase, //
				SOURCE_1_CHANNEL_7, new EcoMainPhaseReading(20, 231_000, 200, 202L, 22L), //
				SOURCE_3_CHANNEL_10, new EcoMainPhaseReading(30, 232_000, 300, 303L, 33L)));
	}

	private static EcoMainSnapshot snapshot(EcoMainIdentityStatus status, EcoMainMainReading main,
			Map<EcoMainChannelAddress, EcoMainPhaseReading> branchReadings) {
		return new EcoMainSnapshot(1L, status, main, branchReadings);
	}

	private static void assertPhase(MeterEcoMainBranchImpl sut, SinglePhase phase, Integer power, Integer voltage,
			Integer current, Long productionEnergy, Long consumptionEnergy) {
		switch (phase) {
		case L1 -> assertAll(//
				() -> assertEquals(power, sut.getActivePowerL1().get()), //
				() -> assertEquals(voltage, sut.getVoltageL1().get()), //
				() -> assertEquals(current, sut.getCurrentL1().get()), //
				() -> assertEquals(productionEnergy, sut.getActiveProductionEnergyL1().get()), //
				() -> assertEquals(consumptionEnergy, sut.getActiveConsumptionEnergyL1().get()));
		case L2 -> assertAll(//
				() -> assertEquals(power, sut.getActivePowerL2().get()), //
				() -> assertEquals(voltage, sut.getVoltageL2().get()), //
				() -> assertEquals(current, sut.getCurrentL2().get()), //
				() -> assertEquals(productionEnergy, sut.getActiveProductionEnergyL2().get()), //
				() -> assertEquals(consumptionEnergy, sut.getActiveConsumptionEnergyL2().get()));
		case L3 -> assertAll(//
				() -> assertEquals(power, sut.getActivePowerL3().get()), //
				() -> assertEquals(voltage, sut.getVoltageL3().get()), //
				() -> assertEquals(current, sut.getCurrentL3().get()), //
				() -> assertEquals(productionEnergy, sut.getActiveProductionEnergyL3().get()), //
				() -> assertEquals(consumptionEnergy, sut.getActiveConsumptionEnergyL3().get()));
		}
	}

	private static void assertNaturalMeasurements(MeterEcoMainBranchImpl sut) {
		assertEquals(60, sut.getActivePower().get());
		assertEquals(10, sut.getActivePowerL1().get());
		assertEquals(20, sut.getActivePowerL2().get());
		assertEquals(30, sut.getActivePowerL3().get());
		assertEquals(231_000, sut.getVoltage().get());
		assertEquals(230_000, sut.getVoltageL1().get());
		assertEquals(231_000, sut.getVoltageL2().get());
		assertEquals(232_000, sut.getVoltageL3().get());
		assertEquals(600, sut.getCurrent().get());
		assertEquals(100, sut.getCurrentL1().get());
		assertEquals(200, sut.getCurrentL2().get());
		assertEquals(300, sut.getCurrentL3().get());
		assertNaturalEnergy(sut);
	}

	private static void assertNaturalEnergy(MeterEcoMainBranchImpl sut) {
		assertEquals(66L, sut.getActiveProductionEnergy().get());
		assertEquals(11L, sut.getActiveProductionEnergyL1().get());
		assertEquals(22L, sut.getActiveProductionEnergyL2().get());
		assertEquals(33L, sut.getActiveProductionEnergyL3().get());
		assertEquals(606L, sut.getActiveConsumptionEnergy().get());
		assertEquals(101L, sut.getActiveConsumptionEnergyL1().get());
		assertEquals(202L, sut.getActiveConsumptionEnergyL2().get());
		assertEquals(303L, sut.getActiveConsumptionEnergyL3().get());
	}

	private static void assertRecoveredEnergy(MeterEcoMainBranchImpl sut) {
		assertEquals(96L, sut.getActiveProductionEnergy().get());
		assertEquals(21L, sut.getActiveProductionEnergyL1().get());
		assertEquals(32L, sut.getActiveProductionEnergyL2().get());
		assertEquals(43L, sut.getActiveProductionEnergyL3().get());
		assertEquals(636L, sut.getActiveConsumptionEnergy().get());
		assertEquals(111L, sut.getActiveConsumptionEnergyL1().get());
		assertEquals(212L, sut.getActiveConsumptionEnergyL2().get());
		assertEquals(313L, sut.getActiveConsumptionEnergyL3().get());
	}

	private static void assertInstantaneousChannelsUndefined(MeterEcoMainBranchImpl sut) {
		for (var channelId : INSTANTANEOUS_CHANNELS) {
			assertNull(sut.channel(channelId).value().get(), channelId.id());
		}
	}

	private static List<Channel<Long>> energyChannels(MeterEcoMainBranchImpl sut) {
		var result = new ArrayList<Channel<Long>>(ENERGY_CHANNELS.length);
		for (var channelId : ENERGY_CHANNELS) {
			result.add(sut.channel(channelId));
		}
		return result;
	}

	private static void assertEnergyChannelIdentity(MeterEcoMainBranchImpl sut,
			List<Channel<Long>> expectedChannels) {
		for (var i = 0; i < ENERGY_CHANNELS.length; i++) {
			var channelId = ENERGY_CHANNELS[i];
			assertSame(expectedChannels.get(i), sut.channel(channelId), channelId.id());
		}
	}

	private static final class MainConfig extends AbstractComponentConfig
			implements io.openems.edge.meter.enecess.ecomain.main.Config {

		private MainConfig() {
			super(io.openems.edge.meter.enecess.ecomain.main.Config.class, "meterMain");
		}

		@Override
		public String core_id() {
			return "ecoMain0";
		}
	}
}
