package io.openems.edge.meter.enecess.ecomain.core;

import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.INVALID;
import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.UNKNOWN;
import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainCore;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;

class EcoMainIdentityTest {

	private static final String EXPECTED_SERIAL_NUMBER = "031415926535";

	@Test
	void validatesDeviceIdentityAndPreservesLeadingZeroes() throws Exception {
		var sut = new EcoMainCoreImpl();
		var bridge = validIdentityBridge(139);

		newComponentTest(sut, bridge) //
				.next(new TestCase("valid identity") //
						.output(EcoMainCore.ChannelId.MODEL, "2401") //
						.output(EcoMainCore.ChannelId.SERIAL_NUMBER, EXPECTED_SERIAL_NUMBER) //
						.output(EcoMainCore.ChannelId.SOFTWARE_VERSION, 139) //
						.output(EcoMainCore.ChannelId.WRONG_DEVICE_MODEL, false) //
						.output(EcoMainCore.ChannelId.UNSUPPORTED_SOFTWARE_VERSION, false) //
						.output(EcoMainCore.ChannelId.SERIAL_NUMBER_MISMATCH, false)); //

		assertEquals(VALID, sut.getIdentityStatus());
		assertEquals(255, sut.getUnitId());
	}

	@Test
	void rejectsWrongDeviceModel() throws Exception {
		assertIdentity(new DummyModbusBridge("modbus0").withRegisters(3000, //
				0x3532, 0x3130, // 2501
				0x3330, 0x3431, 0x3531, 0x3239, 0x3536, 0x3533, // 031415926535
				0x0001, 0x008B), // 139
				INVALID, true, false, false);
	}

	@Test
	void rejectsSoftwareVersionBelowMinimum() throws Exception {
		assertIdentity(validIdentityBridge(138), INVALID, false, true, false);
	}

	@Test
	void acceptsSoftwareVersionAboveMinimum() throws Exception {
		assertIdentity(validIdentityBridge(140), VALID, false, false, false);
	}

	@Test
	void rejectsSerialNumberMismatch() throws Exception {
		assertIdentity(new DummyModbusBridge("modbus0").withRegisters(3000, //
				0x3432, 0x3130, // 2401
				0x3330, 0x3431, 0x3531, 0x3239, 0x3536, 0x3633, // 031415926536
				0x0001, 0x008B), // 139
				INVALID, false, false, true);
	}

	@Test
	void remainsUnknownWhenIdentityRegistersAreUnread() throws Exception {
		assertIdentity(new DummyModbusBridge("modbus0"), UNKNOWN, false, false, false);
	}

	@Test
	void recoversWhenExpectedIdentityReturns() throws Exception {
		var sut = new EcoMainCoreImpl();
		var bridge = new DummyModbusBridge("modbus0").withRegisters(3000, //
				0x3432, 0x3130, // 2401
				0x3330, 0x3431, 0x3531, 0x3239, 0x3536, 0x3633, // 031415926536
				0x0001, 0x008B); // 139
		var componentTest = newComponentTest(sut, bridge);

		componentTest.next(new TestCase("wrong serial number") //
				.output(EcoMainCore.ChannelId.WRONG_DEVICE_MODEL, false) //
				.output(EcoMainCore.ChannelId.UNSUPPORTED_SOFTWARE_VERSION, false) //
				.output(EcoMainCore.ChannelId.SERIAL_NUMBER_MISMATCH, true)); //
		assertEquals(INVALID, sut.getIdentityStatus());

		bridge.withRegisters(3000, //
				0x3432, 0x3130, // 2401
				0x3330, 0x3431, 0x3531, 0x3239, 0x3536, 0x3533, // 031415926535
				0x0001, 0x008B); // 139
		componentTest.next(new TestCase("identity recovered") //
				.output(EcoMainCore.ChannelId.MODEL, "2401") //
				.output(EcoMainCore.ChannelId.SERIAL_NUMBER, EXPECTED_SERIAL_NUMBER) //
				.output(EcoMainCore.ChannelId.SOFTWARE_VERSION, 139) //
				.output(EcoMainCore.ChannelId.WRONG_DEVICE_MODEL, false) //
				.output(EcoMainCore.ChannelId.UNSUPPORTED_SOFTWARE_VERSION, false) //
				.output(EcoMainCore.ChannelId.SERIAL_NUMBER_MISMATCH, false)); //
		assertEquals(VALID, sut.getIdentityStatus());
	}

	private static void assertIdentity(DummyModbusBridge bridge, EcoMainIdentityStatus expectedStatus,
			boolean wrongModel,
			boolean unsupportedVersion, boolean serialMismatch) throws Exception {
		var sut = new EcoMainCoreImpl();
		newComponentTest(sut, bridge) //
				.next(new TestCase() //
						.output(EcoMainCore.ChannelId.WRONG_DEVICE_MODEL, wrongModel) //
						.output(EcoMainCore.ChannelId.UNSUPPORTED_SOFTWARE_VERSION, unsupportedVersion) //
						.output(EcoMainCore.ChannelId.SERIAL_NUMBER_MISMATCH, serialMismatch)); //
		assertEquals(expectedStatus, sut.getIdentityStatus());
	}

	private static ComponentTest newComponentTest(EcoMainCoreImpl sut, DummyModbusBridge bridge) throws Exception {
		return new ComponentTest(sut) //
				.addReference("setModbus", bridge) //
				.activate(MyConfig.create() //
						.setId("ecoMain0") //
						.setModbusId("modbus0") //
						.setSerialNumber(EXPECTED_SERIAL_NUMBER) //
						.build());
	}

	private static DummyModbusBridge validIdentityBridge(int softwareVersion) {
		return new DummyModbusBridge("modbus0").withRegisters(3000, //
				0x3432, 0x3130, // 2401
				0x3330, 0x3431, 0x3531, 0x3239, 0x3536, 0x3533, // 031415926535
				0x0001, softwareVersion);
	}
}
