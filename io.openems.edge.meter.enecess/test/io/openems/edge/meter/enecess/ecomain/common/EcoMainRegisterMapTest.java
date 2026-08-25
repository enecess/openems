package io.openems.edge.meter.enecess.ecomain.common;

import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.activePower;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.current;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.forwardEnergy;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.reverseEnergy;
import static io.openems.edge.meter.enecess.ecomain.common.EcoMainRegisterMap.voltage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;

public class EcoMainRegisterMapTest {

	@Test
	public void testRegisterAddresses() {
		assertEquals(32, forwardEnergy(new EcoMainChannelAddress(0, 1)));
		assertEquals(96, forwardEnergy(new EcoMainChannelAddress(1, 7)));
		assertEquals(188, forwardEnergy(new EcoMainChannelAddress(3, 10)));
		assertEquals(192, reverseEnergy(new EcoMainChannelAddress(0, 1)));
		assertEquals(348, reverseEnergy(new EcoMainChannelAddress(3, 10)));
		assertEquals(1008, activePower(new EcoMainChannelAddress(0, 1)));
		assertEquals(1086, activePower(new EcoMainChannelAddress(3, 10)));
		assertEquals(1209, voltage(new EcoMainChannelAddress(0, 1)));
		assertEquals(1327, current(new EcoMainChannelAddress(3, 10)));
	}

	@Test
	public void testRejectsOutOfRangeAddresses() {
		assertThrows(IllegalArgumentException.class, () -> new EcoMainChannelAddress(-1, 1));
		assertThrows(IllegalArgumentException.class, () -> new EcoMainChannelAddress(4, 1));
		assertThrows(IllegalArgumentException.class, () -> new EcoMainChannelAddress(0, 0));
		assertThrows(IllegalArgumentException.class, () -> new EcoMainChannelAddress(0, 11));
	}
}
