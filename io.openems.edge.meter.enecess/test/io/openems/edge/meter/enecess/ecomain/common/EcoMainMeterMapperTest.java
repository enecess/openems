package io.openems.edge.meter.enecess.ecomain.common;

import static io.openems.edge.common.type.Phase.SinglePhase.L1;
import static io.openems.edge.common.type.Phase.SinglePhase.L2;
import static io.openems.edge.common.type.Phase.SinglePhase.L3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.openems.common.types.MeterType;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainMainReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainPhaseReading;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainSnapshot;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainMeterReading.Phase;

public class EcoMainMeterMapperTest {

	private static final EcoMainChannelAddress SOURCE_0_CHANNEL_1 = new EcoMainChannelAddress(0, 1);
	private static final EcoMainChannelAddress SOURCE_1_CHANNEL_7 = new EcoMainChannelAddress(1, 7);
	private static final EcoMainChannelAddress SOURCE_3_CHANNEL_10 = new EcoMainChannelAddress(3, 10);

	@Test
	public void testMapMainNeverAutomaticallyInvertsPower() {
		var snapshot = validSnapshot(mainReading(17, 23), Map.of());
		var reading = EcoMainMeterMapper.mapMain(snapshot, MeterType.GRID).orElseThrow();

		assertEquals(17, reading.l1().activePower());
		assertEquals(23, reading.totalActivePower());
	}

	@Test
	public void testMapMainUsesIndependentDeviceTotals() {
		var main = new EcoMainMainReading(//
				new EcoMainPhaseReading(10, 230_000, -100, 100L, 10L), //
				new EcoMainPhaseReading(-20, 231_000, 200, 200L, 20L), //
				new EcoMainPhaseReading(0, 232_000, 300, 300L, 30L), //
				999, 1_000L, 2_000L);

		var actual = EcoMainMeterMapper.mapMain(validSnapshot(main, Map.of()), MeterType.GRID).orElseThrow();

		assertEquals(new Phase(10, 230_000, 100, 10L, 100L), actual.l1());
		assertEquals(new Phase(-20, 231_000, -200, 20L, 200L), actual.l2());
		assertEquals(new Phase(0, 232_000, 300, 30L, 300L), actual.l3());
		assertEquals(999, actual.totalActivePower());
		assertEquals(2_000L, actual.totalProductionEnergy());
		assertEquals(1_000L, actual.totalConsumptionEnergy());
	}

	/**
	 * Tests that main mappings reject every non-grid meter type.
	 *
	 * @param type the non-grid meter type
	 */
	@ParameterizedTest
	@EnumSource(value = MeterType.class, mode = EnumSource.Mode.EXCLUDE, names = "GRID")
	public void testMapMainRejectsEveryNonGridMeterType(MeterType type) {
		var snapshot = validSnapshot(mainReading(17, 23), Map.of());

		assertTrue(EcoMainMeterMapper.mapMain(snapshot, type).isEmpty(), type.name());
	}

	@Test
	public void testMapInvertedSinglePhaseOnL1L2AndL3() {
		var phases = List.of(L1, L2, L3);
		var addresses = List.of(SOURCE_0_CHANNEL_1, SOURCE_1_CHANNEL_7, SOURCE_3_CHANNEL_10);
		for (var index = 0; index < phases.size(); index++) {
			var logicalPhase = phases.get(index);
			var address = addresses.get(index);
			var definition = new EcoMainBranchDefinition(
					List.of(new EcoMainPhaseDefinition(logicalPhase, address, true)));
			var snapshot = validSnapshot(null,
					Map.of(address, new EcoMainPhaseReading(25, 230_000, 7, 40L, 50L)));

			var actual = EcoMainMeterMapper.mapBranch(snapshot, MeterType.CONSUMPTION_METERED, definition)
					.orElseThrow();
			var expectedPhase = new Phase(-25, 230_000, -7, 40L, 50L);
			assertEquals(logicalPhase == L1 ? expectedPhase : null, actual.l1(), logicalPhase.name());
			assertEquals(logicalPhase == L2 ? expectedPhase : null, actual.l2(), logicalPhase.name());
			assertEquals(logicalPhase == L3 ? expectedPhase : null, actual.l3(), logicalPhase.name());
			assertEquals(-25, actual.totalActivePower(), logicalPhase.name());
			assertEquals(40L, actual.totalProductionEnergy(), logicalPhase.name());
			assertEquals(50L, actual.totalConsumptionEnergy(), logicalPhase.name());
		}
	}

	@Test
	public void testZeroPowerKeepsPositiveCurrentWhenInverted() {
		var definition = new EcoMainBranchDefinition(
				List.of(new EcoMainPhaseDefinition(L2, SOURCE_0_CHANNEL_1, true)));
		var snapshot = validSnapshot(null,
				Map.of(SOURCE_0_CHANNEL_1, new EcoMainPhaseReading(0, 230_000, 12, 40L, 50L)));

		var actual = EcoMainMeterMapper.mapBranch(snapshot, MeterType.CONSUMPTION_METERED, definition).orElseThrow();

		assertEquals(new Phase(0, 230_000, 12, 40L, 50L), actual.l2());
		assertEquals(0, actual.totalActivePower());
	}

	@Test
	public void testAggregatesThreePhasesAcrossSources() {
		var definition = new EcoMainBranchDefinition(List.of(//
				new EcoMainPhaseDefinition(L2, SOURCE_1_CHANNEL_7, false), //
				new EcoMainPhaseDefinition(L3, SOURCE_3_CHANNEL_10, true), //
				new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false)));
		var snapshot = validSnapshot(null, Map.of(//
				SOURCE_0_CHANNEL_1, new EcoMainPhaseReading(10, 230_000, 1, 100L, 10L), //
				SOURCE_1_CHANNEL_7, new EcoMainPhaseReading(-20, 231_000, 2, 200L, 20L), //
				SOURCE_3_CHANNEL_10, new EcoMainPhaseReading(30, 232_000, 3, 300L, 30L)));

		var actual = EcoMainMeterMapper.mapBranch(snapshot, MeterType.PRODUCTION_AND_CONSUMPTION, definition)
				.orElseThrow();

		assertEquals(new Phase(10, 230_000, 1, 100L, 10L), actual.l1());
		assertEquals(new Phase(-20, 231_000, -2, 200L, 20L), actual.l2());
		assertEquals(new Phase(-30, 232_000, -3, 30L, 300L), actual.l3());
		assertEquals(-40, actual.totalActivePower());
		assertEquals(330L, actual.totalProductionEnergy());
		assertEquals(330L, actual.totalConsumptionEnergy());
	}

	/**
	 * Tests that branch mappings accept every supported meter type.
	 *
	 * @param type the supported branch meter type
	 */
	@ParameterizedTest
	@EnumSource(value = MeterType.class, names = { "PRODUCTION", "PRODUCTION_AND_CONSUMPTION",
			"CONSUMPTION_METERED", "CONSUMPTION_NOT_METERED" })
	public void testMapBranchAcceptsEverySupportedMeterType(MeterType type) {
		var definition = new EcoMainBranchDefinition(
				List.of(new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false)));
		var snapshot = validSnapshot(null, Map.of(SOURCE_0_CHANNEL_1, phaseReading(17)));

		assertTrue(EcoMainMeterMapper.mapBranch(snapshot, type, definition).isPresent(), type.name());
	}

	/**
	 * Tests that branch mappings reject every unsupported meter type.
	 *
	 * @param type the unsupported branch meter type
	 */
	@ParameterizedTest
	@EnumSource(value = MeterType.class, mode = EnumSource.Mode.EXCLUDE, names = { "PRODUCTION",
			"PRODUCTION_AND_CONSUMPTION", "CONSUMPTION_METERED", "CONSUMPTION_NOT_METERED" })
	public void testMapBranchRejectsEveryUnsupportedMeterType(MeterType type) {
		var definition = new EcoMainBranchDefinition(
				List.of(new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false)));
		var snapshot = validSnapshot(null, Map.of(SOURCE_0_CHANNEL_1, phaseReading(17)));

		assertTrue(EcoMainMeterMapper.mapBranch(snapshot, type, definition).isEmpty(), type.name());
	}

	@Test
	public void testPhaseAndBranchDefinitionsValidateAndCopyInput() {
		assertThrows(NullPointerException.class,
				() -> new EcoMainPhaseDefinition(null, SOURCE_0_CHANNEL_1, false));
		assertThrows(NullPointerException.class, () -> new EcoMainPhaseDefinition(L1, null, false));
		assertThrows(IllegalArgumentException.class, () -> new EcoMainBranchDefinition(List.of()));
		assertThrows(IllegalArgumentException.class, () -> new EcoMainBranchDefinition(List.of(//
				new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false), //
				new EcoMainPhaseDefinition(L2, SOURCE_1_CHANNEL_7, false))));
		assertThrows(IllegalArgumentException.class, () -> new EcoMainBranchDefinition(List.of(//
				new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false), //
				new EcoMainPhaseDefinition(L1, SOURCE_1_CHANNEL_7, false), //
				new EcoMainPhaseDefinition(L3, SOURCE_3_CHANNEL_10, false))));
		assertThrows(IllegalArgumentException.class, () -> new EcoMainBranchDefinition(List.of(//
				new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false), //
				new EcoMainPhaseDefinition(L2, SOURCE_0_CHANNEL_1, false), //
				new EcoMainPhaseDefinition(L3, SOURCE_3_CHANNEL_10, false))));

		var input = new ArrayList<EcoMainPhaseDefinition>();
		var l2 = new EcoMainPhaseDefinition(L2, SOURCE_1_CHANNEL_7, true);
		input.add(l2);
		var definition = new EcoMainBranchDefinition(input);
		input.clear();
		assertEquals(List.of(l2), definition.phases());
		assertThrows(UnsupportedOperationException.class,
				() -> definition.phases().add(new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false)));
	}

	@Test
	public void testMapMainFailsClosedForSnapshotIdentityTypeAndMissingFields() {
		var completeMain = mainReading(17, 23);
		assertTrue(EcoMainMeterMapper.mapMain(null, MeterType.GRID).isEmpty());
		for (var status : List.of(EcoMainIdentityStatus.UNKNOWN, EcoMainIdentityStatus.INVALID)) {
			assertTrue(EcoMainMeterMapper.mapMain(snapshot(status, completeMain, Map.of()), MeterType.GRID).isEmpty());
		}
		assertTrue(EcoMainMeterMapper.mapMain(validSnapshot(null, Map.of()), MeterType.GRID).isEmpty());
		assertTrue(EcoMainMeterMapper.mapMain(validSnapshot(completeMain, Map.of()), null).isEmpty());

		var completePhase = phaseReading(17);
		for (var incompleteMain : List.of(//
				new EcoMainMainReading(null, completePhase, completePhase, 23, 400L, 500L), //
				new EcoMainMainReading(completePhase, null, completePhase, 23, 400L, 500L), //
				new EcoMainMainReading(completePhase, completePhase, null, 23, 400L, 500L), //
				new EcoMainMainReading(new EcoMainPhaseReading(null, 230_000, 100, 40L, 50L), completePhase,
						completePhase, 23, 400L, 500L), //
				new EcoMainMainReading(new EcoMainPhaseReading(17, null, 100, 40L, 50L), completePhase,
						completePhase, 23, 400L, 500L), //
				new EcoMainMainReading(new EcoMainPhaseReading(17, 230_000, null, 40L, 50L), completePhase,
						completePhase, 23, 400L, 500L), //
				new EcoMainMainReading(new EcoMainPhaseReading(17, 230_000, 100, null, 50L), completePhase,
						completePhase, 23, 400L, 500L), //
				new EcoMainMainReading(new EcoMainPhaseReading(17, 230_000, 100, 40L, null), completePhase,
						completePhase, 23, 400L, 500L), //
				new EcoMainMainReading(completePhase, completePhase, completePhase, null, 400L, 500L), //
				new EcoMainMainReading(completePhase, completePhase, completePhase, 23, null, 500L), //
				new EcoMainMainReading(completePhase, completePhase, completePhase, 23, 400L, null))) {
			assertTrue(EcoMainMeterMapper.mapMain(validSnapshot(incompleteMain, Map.of()), MeterType.GRID).isEmpty());
		}
	}

	@Test
	public void testMapBranchFailsClosedForSnapshotIdentityTypeAddressAndMissingFields() {
		var definition = new EcoMainBranchDefinition(
				List.of(new EcoMainPhaseDefinition(L1, SOURCE_0_CHANNEL_1, false)));
		var completePhase = phaseReading(17);
		var completeSnapshot = validSnapshot(null, Map.of(SOURCE_0_CHANNEL_1, completePhase));
		assertTrue(EcoMainMeterMapper.mapBranch(null, MeterType.CONSUMPTION_METERED, definition).isEmpty());
		for (var status : List.of(EcoMainIdentityStatus.UNKNOWN, EcoMainIdentityStatus.INVALID)) {
			assertTrue(EcoMainMeterMapper
					.mapBranch(snapshot(status, null, Map.of(SOURCE_0_CHANNEL_1, completePhase)),
							MeterType.CONSUMPTION_METERED, definition)
					.isEmpty());
		}
		assertTrue(EcoMainMeterMapper.mapBranch(completeSnapshot, null, definition).isEmpty());
		assertTrue(EcoMainMeterMapper
				.mapBranch(validSnapshot(null, Map.of()), MeterType.CONSUMPTION_METERED, definition).isEmpty());
		assertThrows(NullPointerException.class,
				() -> EcoMainMeterMapper.mapBranch(completeSnapshot, MeterType.CONSUMPTION_METERED, null));

		for (var incompletePhase : List.of(//
				new EcoMainPhaseReading(null, 230_000, 100, 40L, 50L), //
				new EcoMainPhaseReading(17, null, 100, 40L, 50L), //
				new EcoMainPhaseReading(17, 230_000, null, 40L, 50L), //
				new EcoMainPhaseReading(17, 230_000, 100, null, 50L), //
				new EcoMainPhaseReading(17, 230_000, 100, 40L, null))) {
			assertTrue(EcoMainMeterMapper
					.mapBranch(validSnapshot(null, Map.of(SOURCE_0_CHANNEL_1, incompletePhase)),
							MeterType.CONSUMPTION_METERED, definition)
					.isEmpty());
		}
	}

	private static EcoMainMainReading mainReading(int phasePower, int totalPower) {
		var phase = phaseReading(phasePower);
		return new EcoMainMainReading(phase, phase, phase, totalPower, 400L, 500L);
	}

	private static EcoMainPhaseReading phaseReading(int power) {
		return new EcoMainPhaseReading(power, 230_000, 100, 40L, 50L);
	}

	private static EcoMainSnapshot validSnapshot(EcoMainMainReading main,
			Map<EcoMainChannelAddress, EcoMainPhaseReading> branches) {
		return snapshot(EcoMainIdentityStatus.VALID, main, branches);
	}

	private static EcoMainSnapshot snapshot(EcoMainIdentityStatus status, EcoMainMainReading main,
			Map<EcoMainChannelAddress, EcoMainPhaseReading> branches) {
		return new EcoMainSnapshot(1L, status, main, branches);
	}
}
