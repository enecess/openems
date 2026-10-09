package io.openems.edge.meter.enecess.ecomain.api;

import io.openems.edge.meter.enecess.ecomain.common.EcoMainConstants;

public record EcoMainChannelAddress(int source, int channel) {

	public EcoMainChannelAddress {
		if (source < 0 || source >= EcoMainConstants.SOURCE_COUNT) {
			throw new IllegalArgumentException("Source must be between 0 and 3");
		}
		if (channel < 1 || channel > EcoMainConstants.CHANNELS_PER_SOURCE) {
			throw new IllegalArgumentException("Channel must be between 1 and 10");
		}
	}
}
