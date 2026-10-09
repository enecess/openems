package io.openems.edge.meter.enecess.ecomain.api;

import io.openems.common.channel.Level;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

public interface EcoMainCore extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		MODEL(Doc.of(OpenemsType.STRING)), //
		SERIAL_NUMBER(Doc.of(OpenemsType.STRING)), //
		SOFTWARE_VERSION(Doc.of(OpenemsType.INTEGER)), //
		WRONG_DEVICE_MODEL(Doc.of(Level.FAULT)), //
		UNSUPPORTED_SOFTWARE_VERSION(Doc.of(Level.FAULT)), //
		SERIAL_NUMBER_MISMATCH(Doc.of(Level.FAULT));

		private final Doc doc;

		ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the latest evaluated device identity status.
	 *
	 * @return the device identity status
	 */
	EcoMainIdentityStatus getIdentityStatus();

	/**
	 * Gets the latest immutable measurement snapshot. Individual fields may be
	 * undefined or {@code null} when their Modbus task could not be read.
	 *
	 * @return the snapshot; or {@code null} before one is available
	 */
	EcoMainSnapshot getSnapshot();
}
