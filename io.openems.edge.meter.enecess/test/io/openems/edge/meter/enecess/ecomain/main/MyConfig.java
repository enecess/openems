package io.openems.edge.meter.enecess.ecomain.main;

import static io.openems.common.utils.ConfigUtils.generateReferenceTargetFilter;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id = "meter0";
		private String coreId = "ecoMain0";

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setCoreId(String coreId) {
			this.coreId = coreId;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Creates a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String core_id() {
		return this.builder.coreId;
	}

	/**
	 * Gets the OSGi reference target filter for the Core reference.
	 *
	 * @return the reference target filter
	 */
	// CHECKSTYLE:OFF
	public String Core_target() {
		return generateReferenceTargetFilter(this.id(), this.core_id());
	}
	// CHECKSTYLE:ON
}
