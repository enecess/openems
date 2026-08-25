package io.openems.edge.meter.enecess.ecomain.common;

import io.openems.edge.meter.enecess.ecomain.api.EcoMainChannelAddress;

public final class EcoMainRegisterMap {

	private EcoMainRegisterMap() {
	}

	/**
	 * Gets the forward-energy register address.
	 *
	 * @param address the ecoMain channel address
	 * @return the register address
	 */
	public static int forwardEnergy(EcoMainChannelAddress address) {
		return 32 + 40 * address.source() + 4 * (address.channel() - 1);
	}

	/**
	 * Gets the reverse-energy register address.
	 *
	 * @param address the ecoMain channel address
	 * @return the register address
	 */
	public static int reverseEnergy(EcoMainChannelAddress address) {
		return 192 + 40 * address.source() + 4 * (address.channel() - 1);
	}

	/**
	 * Gets the active-power register address.
	 *
	 * @param address the ecoMain channel address
	 * @return the register address
	 */
	public static int activePower(EcoMainChannelAddress address) {
		return 1008 + 20 * address.source() + 2 * (address.channel() - 1);
	}

	/**
	 * Gets the voltage register address.
	 *
	 * @param address the ecoMain channel address
	 * @return the register address
	 */
	public static int voltage(EcoMainChannelAddress address) {
		return 1209 + 30 * address.source() + 3 * (address.channel() - 1);
	}

	/**
	 * Gets the current register address.
	 *
	 * @param address the ecoMain channel address
	 * @return the register address
	 */
	public static int current(EcoMainChannelAddress address) {
		return voltage(address) + 1;
	}
}
