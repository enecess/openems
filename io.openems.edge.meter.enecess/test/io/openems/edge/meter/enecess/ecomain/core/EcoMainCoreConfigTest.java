package io.openems.edge.meter.enecess.ecomain.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.ReflectionUtils.ReflectionException;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.ComponentTest;

class EcoMainCoreConfigTest {

	@ParameterizedTest
	@ValueSource(strings = { "", "31415926535", "3141592653580", "31415926535A" })
	void rejectsMalformedSerialNumber(String serialNumber) throws Exception {
		var exception = assertThrows(ReflectionException.class, () -> activate(serialNumber));
		assertInstanceOf(OpenemsException.class, exception.getCause().getCause());
	}

	@Test
	void activatesWithoutLegacyConfigurationAdmin() {
		assertDoesNotThrow(() -> activate("314159265358"));
	}

	private static void activate(String serialNumber) throws Exception {
		new ComponentTest(new EcoMainCoreImpl()) //
				.addReference("setModbus", new DummyModbusBridge("modbus0")) //
				.activate(MyConfig.create() //
						.setId("ecoMain0") //
						.setModbusId("modbus0") //
						.setSerialNumber(serialNumber) //
						.build());
	}
}
