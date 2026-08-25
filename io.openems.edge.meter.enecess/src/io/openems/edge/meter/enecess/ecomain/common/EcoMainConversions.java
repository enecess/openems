package io.openems.edge.meter.enecess.ecomain.common;

public final class EcoMainConversions {

	private EcoMainConversions() {
	}

	/**
	 * Converts raw hundredths of a watt to watts.
	 *
	 * @param raw the raw register value
	 * @return the rounded value in watts
	 */
	public static int watts(int raw) {
		return (int) Math.round(raw / 100.0);
	}

	/**
	 * Converts raw hundredths of a volt to millivolts.
	 *
	 * @param raw the raw register value
	 * @return the value in millivolts
	 */
	public static int millivolts(int raw) {
		return raw * 10;
	}

	/**
	 * Converts raw hundredths of an ampere to milliamps.
	 *
	 * @param raw the raw register value
	 * @return the value in milliamps
	 */
	public static int milliamps(int raw) {
		return raw * 10;
	}

	/**
	 * Converts raw milliwatt-hours to whole watt-hours.
	 *
	 * @param raw the raw register value
	 * @return the truncated value in watt-hours
	 */
	public static long wattHours(long raw) {
		return raw / 1000L;
	}
}
