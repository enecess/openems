package io.openems.edge.meter.enecess.ecomain.core;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.INVALID;
import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.VALID;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.activePower;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.current;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.forwardEnergy;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.reverseEnergy;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.voltage;
import static io.openems.common.utils.ReflectionUtils.getValueViaReflection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.service.event.Event;

import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.AbstractModbusElement;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainPhaseReading;

class EcoMainSnapshotIntegrationTest {

	private static final String SERIAL_NUMBER = "314159265358";
	private static final EcoMainChannelAddress BRANCH_0_1 = new EcoMainChannelAddress(0, 1);
	private static final EcoMainChannelAddress BRANCH_1_7 = new EcoMainChannelAddress(1, 7);
	private static final EcoMainChannelAddress BRANCH_3_10 = new EcoMainChannelAddress(3, 10);

	@Test
	void publishesConvertedMainAndBranchMeasurementsOncePerCycle() throws Exception {
		var registers = completeRegisterImage();
		var sut = new EcoMainCoreImpl();
		var componentTest = newComponentTest(sut, registers.newBridge());

		componentTest.next(new TestCase("first complete measurement cycle"));
		var first = sut.getSnapshot();
		assertNotNull(first);

		assertEquals(VALID, first.identityStatus());
		assertPhase(first.main().l1(), 1111, 230010, 1230, 101L, 404L);
		assertPhase(first.main().l2(), -2223, 231020, 2340, 202L, 505L);
		assertPhase(first.main().l3(), 3334, 232030, 3450, 303L, 606L);
		assertEquals(4444, first.main().totalActivePower());
		assertEquals(909L, first.main().totalForwardEnergy());
		assertEquals(808L, first.main().totalReverseEnergy());
		assertEquals(40, first.branchReadings().size());
		assertPhase(first.branchReadings().get(BRANCH_0_1), 123, 220010, 1010, 1234L, 2345L);
		assertPhase(first.branchReadings().get(BRANCH_1_7), -457, 221020, 2020, 3456L, 4567L);
		assertPhase(first.branchReadings().get(BRANCH_3_10), 1000, 222030, 3030, 5678L, 6789L);

		componentTest.next(new TestCase("next complete measurement cycle"));
		var second = sut.getSnapshot();
		assertNotNull(second);
		assertEquals(first.sequence() + 1, second.sequence());
		assertNotSame(first, second);
	}

	@Test
	void clearsOnlyMeasurementsCoveredByAnUnavailableTask() throws Exception {
		var sut = new EcoMainCoreImpl();
		var componentTest = newComponentTest(sut, completeRegisterImage().newBridge());
		componentTest.next(new TestCase("complete measurement cycle"));
		var complete = sut.getSnapshot();
		assertNotNull(complete);

		var powerTask = protocol(sut).getTaskManager().getTasks().stream() //
				.filter(task -> task.getStartAddress() == 1000) //
				.findFirst().orElseThrow();
		for (var element : powerTask.getElements()) {
			((AbstractModbusElement<?, ?, ?>) element).setInputValue(null);
		}
		sut.handleEvent(new Event(TOPIC_CYCLE_AFTER_PROCESS_IMAGE, Map.of()));
		var partial = sut.getSnapshot();
		assertNotNull(partial);

		assertEquals(complete.sequence() + 1, partial.sequence());
		assertNull(partial.main().l1().activePower());
		assertNull(partial.main().totalActivePower());
		assertNull(partial.branchReadings().get(BRANCH_0_1).activePower());
		assertEquals(220010, partial.branchReadings().get(BRANCH_0_1).voltage());
		assertEquals(1010, partial.branchReadings().get(BRANCH_0_1).current());
		assertEquals(1234L, partial.branchReadings().get(BRANCH_0_1).forwardEnergy());
		assertEquals(2345L, partial.branchReadings().get(BRANCH_0_1).reverseEnergy());
		assertNotNull(partial.branchReadings().get(new EcoMainChannelAddress(2, 5)));
		assertEquals(0, partial.branchReadings().get(new EcoMainChannelAddress(2, 5)).voltage());
		assertEquals(123, complete.branchReadings().get(BRANCH_0_1).activePower());
	}

	@Test
	void publishesDiagnosticMeasurementsForInvalidIdentity() throws Exception {
		var registers = completeRegisterImage();
		var bridge = registers.newBridge();
		var sut = new EcoMainCoreImpl();
		var componentTest = newComponentTest(sut, bridge);
		componentTest.next(new TestCase("valid identity"));

		registers.putIdentity("2501", SERIAL_NUMBER, 139).writeTo(bridge);
		componentTest.next(new TestCase("invalid identity"));
		var diagnostic = sut.getSnapshot();
		assertNotNull(diagnostic);

		assertEquals(INVALID, diagnostic.identityStatus());
		assertEquals(4444, diagnostic.main().totalActivePower());
		assertPhase(diagnostic.branchReadings().get(BRANCH_3_10), 1000, 222030, 3030, 5678L, 6789L);
	}

	private static EcoMainTestRegisters completeRegisterImage() {
		var registers = new EcoMainTestRegisters() //
				.putInt32Lsw(1000, 111_149) //
				.putInt32Lsw(1002, -222_251) //
				.putInt32Lsw(1004, 333_350) //
				.putInt32Lsw(1006, 444_449) //
				.putUInt64Lsw(0, 101_999L) //
				.putUInt64Lsw(4, 202_001L) //
				.putUInt64Lsw(8, 303_999L) //
				.putUInt64Lsw(12, 909_999L) //
				.putUInt64Lsw(16, 404_999L) //
				.putUInt64Lsw(20, 505_999L) //
				.putUInt64Lsw(24, 606_999L) //
				.putUInt64Lsw(28, 808_999L) //
				.putUInt16(1200, 23_001).putUInt16(1201, 123) //
				.putUInt16(1203, 23_102).putUInt16(1204, 234) //
				.putUInt16(1206, 23_203).putUInt16(1207, 345) //
				.putIdentity("2401", SERIAL_NUMBER, 139);
		putBranch(registers, BRANCH_0_1, 12_345, 22_001, 101, 1_234_567L, 2_345_678L);
		putBranch(registers, BRANCH_1_7, -45_678, 22_102, 202, 3_456_789L, 4_567_890L);
		putBranch(registers, BRANCH_3_10, 99_999, 22_203, 303, 5_678_901L, 6_789_012L);
		return registers;
	}

	private static void putBranch(EcoMainTestRegisters registers, EcoMainChannelAddress address, int power,
			int rawVoltage, int rawCurrent, long forward, long reverse) {
		registers.putInt32Lsw(activePower(address), power) //
				.putUInt16(voltage(address), rawVoltage) //
				.putUInt16(current(address), rawCurrent) //
				.putUInt64Lsw(forwardEnergy(address), forward) //
				.putUInt64Lsw(reverseEnergy(address), reverse);
	}

	private static ComponentTest newComponentTest(EcoMainCoreImpl sut, DummyModbusBridge bridge) throws Exception {
		return new ComponentTest(sut) //
				.addReference("setModbus", bridge) //
				.activate(MyConfig.create() //
						.setId("ecoMain0") //
						.setModbusId("modbus0") //
						.setSerialNumber(SERIAL_NUMBER) //
						.build());
	}

	private static void assertPhase(EcoMainPhaseReading actual, Integer activePower, Integer voltage, Integer current,
			Long forwardEnergy, Long reverseEnergy) {
		assertNotNull(actual);
		assertEquals(activePower, actual.activePower());
		assertEquals(voltage, actual.voltage());
		assertEquals(current, actual.current());
		assertEquals(forwardEnergy, actual.forwardEnergy());
		assertEquals(reverseEnergy, actual.reverseEnergy());
	}

	private static ModbusProtocol protocol(EcoMainCoreImpl sut) {
		return getValueViaReflection(sut, "protocol");
	}
}
