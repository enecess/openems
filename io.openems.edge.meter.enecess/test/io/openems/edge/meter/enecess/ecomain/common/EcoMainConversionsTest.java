package io.openems.edge.meter.enecess.ecomain.common;

import static io.openems.edge.meter.enecess.ecomain.common.EcoMainConversions.milliamps;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainConversions.millivolts;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainConversions.wattHours;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainConversions.watts;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class EcoMainConversionsTest {

	@Test
	public void testRawRegisterScaling() {
		assertEquals(1235, watts(123456));
		assertEquals(-1235, watts(-123456));
		assertEquals(230120, millivolts(23012));
		assertEquals(12340, milliamps(1234));
		assertEquals(123456L, wattHours(123456789L));
	}
}
