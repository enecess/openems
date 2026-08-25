package io.openems.edge.meter.enecess.ecomain.branch;

import static io.openems.edge.common.type.Phase.SingleOrThreePhase.SINGLE_PHASE;
import static io.openems.edge.common.type.Phase.SingleOrThreePhase.THREE_PHASE;
import static io.openems.edge.common.type.Phase.SinglePhase.L1;
import static io.openems.edge.common.type.Phase.SinglePhase.L2;
import static io.openems.edge.common.type.Phase.SinglePhase.L3;
import static io.openems.edge.meter.enecess.ecomain.branch.EcoMainBranchMeterType.CONSUMPTION_METERED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.ReflectionUtils.ReflectionException;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.enecess.ecomain.test.DummyEcoMainCore;

class MeterEcoMainBranchConfigTest {

	@Test
	void exposesOnlyTheSpecifiedConfigurationFieldsAndDefaults() throws Exception {
		var fields = Arrays.stream(Config.class.getDeclaredMethods()) //
				.map(method -> method.getName()) //
				.filter(name -> !name.equals("webconsole_configurationFactory_nameHint")) //
				.collect(Collectors.toSet());
		assertEquals(Set.of(//
				"id", "alias", "enabled", "core_id", "type", "phaseCount", //
				"phase1", "source1", "channel1", "invert1", //
				"phase2", "source2", "channel2", "invert2", //
				"phase3", "source3", "channel3", "invert3"), fields);
		assertEquals("ecoMain0", Config.class.getMethod("core_id").getDefaultValue());
		assertEquals(CONSUMPTION_METERED, Config.class.getMethod("type").getDefaultValue());
		assertEquals(THREE_PHASE, Config.class.getMethod("phaseCount").getDefaultValue());
		assertEquals(L1, Config.class.getMethod("phase1").getDefaultValue());
		assertEquals(0, Config.class.getMethod("source1").getDefaultValue());
		assertEquals(1, Config.class.getMethod("channel1").getDefaultValue());
		assertEquals(false, Config.class.getMethod("invert1").getDefaultValue());
		assertEquals(L2, Config.class.getMethod("phase2").getDefaultValue());
		assertEquals(0, Config.class.getMethod("source2").getDefaultValue());
		assertEquals(2, Config.class.getMethod("channel2").getDefaultValue());
		assertEquals(false, Config.class.getMethod("invert2").getDefaultValue());
		assertEquals(L3, Config.class.getMethod("phase3").getDefaultValue());
		assertEquals(0, Config.class.getMethod("source3").getDefaultValue());
		assertEquals(3, Config.class.getMethod("channel3").getDefaultValue());
		assertEquals(false, Config.class.getMethod("invert3").getDefaultValue());
	}

	@Test
	void logicalMeterDefaultsBindToTheDefaultCore() throws Exception {
		var coreId = io.openems.edge.meter.enecess.ecomain.core.Config.class.getDeclaredMethod("id").getDefaultValue();
		var mainCoreId = io.openems.edge.meter.enecess.ecomain.main.Config.class.getDeclaredMethod("core_id")
				.getDefaultValue();
		var branchCoreId = Config.class.getDeclaredMethod("core_id").getDefaultValue();

		assertEquals("ecoMain0", coreId);
		assertEquals(coreId, mainCoreId);
		assertEquals(coreId, branchCoreId);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 4 })
	void rejectsOutOfRangeSources(int source) {
		assertActivationRejected(MyConfig.create().setPhase2(L2, source, 2, false).build());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 11 })
	void rejectsOutOfRangeChannels(int channel) {
		assertActivationRejected(MyConfig.create().setPhase3(L3, 0, channel, false).build());
	}

	@Test
	void rejectsRepeatedLogicalPhases() {
		assertActivationRejected(MyConfig.create() //
				.setPhase1(L1, 0, 1, false) //
				.setPhase2(L1, 0, 2, false) //
				.setPhase3(L3, 0, 3, false) //
				.build());
	}

	@Test
	void rejectsThreePhaseConfigurationMissingOneOfL1L2L3() {
		assertActivationRejected(MyConfig.create() //
				.setPhase1(L1, 0, 1, false) //
				.setPhase2(L2, 0, 2, false) //
				.setPhase3(L2, 0, 3, false) //
				.build());
	}

	@Test
	void rejectsRepeatedPhysicalAddresses() {
		assertActivationRejected(MyConfig.create() //
				.setPhase1(L1, 1, 7, false) //
				.setPhase2(L2, 1, 7, false) //
				.build());
	}

	@ParameterizedTest
	@EnumSource(EcoMainBranchMeterType.class)
	void acceptsSupportedBranchMeterTypes(EcoMainBranchMeterType type) {
		assertDoesNotThrow(() -> activate(MyConfig.create().setType(type).build()));
	}

	@Test
	void singlePhaseConsumesOnlySlotOne() {
		assertDoesNotThrow(() -> activate(MyConfig.create() //
				.setPhaseCount(SINGLE_PHASE) //
				.setPhase1(L3, 3, 10, true) //
				.setPhase2(L3, -1, 0, true) //
				.setPhase3(L3, 4, 11, true) //
				.build()));
	}

	private static void assertActivationRejected(MyConfig config) {
		var exception = assertThrows(ReflectionException.class, () -> activate(config));
		assertInstanceOf(OpenemsException.class, exception.getCause().getCause());
	}

	private static MeterEcoMainBranchImpl activate(MyConfig config) throws Exception {
		var sut = new MeterEcoMainBranchImpl();
		new ComponentTest(sut) //
				.addReference("core", new DummyEcoMainCore()) //
				.activate(config);
		return sut;
	}
}
