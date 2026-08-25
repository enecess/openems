package io.openems.edge.meter.enecess.ecomain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.openems.common.types.MeterType;

public class EcoMainMeterTypePolicyTest {

	private static final List<MeterType> BRANCH_TYPES = List.of(//
			MeterType.PRODUCTION, //
			MeterType.PRODUCTION_AND_CONSUMPTION, //
			MeterType.CONSUMPTION_METERED, //
			MeterType.CONSUMPTION_NOT_METERED);

	@Test
	public void testBranchSupportAcceptsOnlyApprovedMeterTypes() {
		BRANCH_TYPES.forEach(type -> assertTrue(EcoMainMeterTypePolicy.isBranchSupported(type), type.name()));
		assertFalse(EcoMainMeterTypePolicy.isBranchSupported(MeterType.GRID));
		assertFalse(EcoMainMeterTypePolicy.isBranchSupported(MeterType.GRID_GENSET));
		assertFalse(EcoMainMeterTypePolicy.isBranchSupported(MeterType.MANAGED_CONSUMPTION_METERED));
		assertFalse(EcoMainMeterTypePolicy.isBranchSupported(null));
	}

	@Test
	public void testMapsEnergyDirectionByMeterType() {
		assertEnergy(MeterType.GRID, 20L, 10L);
		assertEnergy(MeterType.CONSUMPTION_METERED, 20L, 10L);
		assertEnergy(MeterType.CONSUMPTION_NOT_METERED, 20L, 10L);
		assertEnergy(MeterType.PRODUCTION, 10L, 20L);
		assertEnergy(MeterType.PRODUCTION_AND_CONSUMPTION, 10L, 20L);
	}

	@Test
	public void testRejectsEnergyMappingForUnsupportedMeterTypes() {
		for (var type : List.of(MeterType.GRID_GENSET, MeterType.MANAGED_CONSUMPTION_METERED)) {
			assertThrows(IllegalArgumentException.class,
					() -> EcoMainMeterTypePolicy.productionEnergy(type, 10L, 20L));
			assertThrows(IllegalArgumentException.class,
					() -> EcoMainMeterTypePolicy.consumptionEnergy(type, 10L, 20L));
		}
		assertThrows(IllegalArgumentException.class,
				() -> EcoMainMeterTypePolicy.productionEnergy(null, 10L, 20L));
		assertThrows(IllegalArgumentException.class,
				() -> EcoMainMeterTypePolicy.consumptionEnergy(null, 10L, 20L));
	}

	private static void assertEnergy(MeterType type, long productionEnergy, long consumptionEnergy) {
		assertEquals(productionEnergy, EcoMainMeterTypePolicy.productionEnergy(type, 10L, 20L), type.name());
		assertEquals(consumptionEnergy, EcoMainMeterTypePolicy.consumptionEnergy(type, 10L, 20L), type.name());
	}
}
