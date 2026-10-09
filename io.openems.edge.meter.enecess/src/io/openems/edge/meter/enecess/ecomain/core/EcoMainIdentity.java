package io.openems.edge.meter.enecess.ecomain.core;

import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.INVALID;
import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.UNKNOWN;
import static io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus.VALID;

import io.openems.edge.meter.enecess.ecomain.api.EcoMainIdentityStatus;
import io.openems.edge.meter.enecess.ecomain.common.EcoMainConstants;

record EcoMainIdentity(EcoMainIdentityStatus status, boolean wrongDeviceModel, boolean unsupportedSoftwareVersion,
		boolean serialNumberMismatch) {

	static EcoMainIdentity evaluate(String model, String serialNumber, Integer softwareVersion,
			String expectedSerialNumber) {
		var wrongDeviceModel = model != null && !EcoMainConstants.EXPECTED_MODEL.equals(model);
		var unsupportedSoftwareVersion = softwareVersion != null
				&& softwareVersion < EcoMainConstants.MINIMUM_SOFTWARE_VERSION;
		var serialNumberMismatch = serialNumber != null && !expectedSerialNumber.equals(serialNumber);

		final EcoMainIdentityStatus status;
		if (wrongDeviceModel || unsupportedSoftwareVersion || serialNumberMismatch) {
			status = INVALID;
		} else if (model != null && serialNumber != null && softwareVersion != null) {
			status = VALID;
		} else {
			status = UNKNOWN;
		}
		return new EcoMainIdentity(status, wrongDeviceModel, unsupportedSoftwareVersion, serialNumberMismatch);
	}
}
