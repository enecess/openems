package io.openems.edge.meter.enecess.ecomain.branch;

import static io.openems.common.utils.ConfigUtils.generateReferenceTargetFilter;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.common.type.Phase.SingleOrThreePhase;
import io.openems.edge.common.type.Phase.SinglePhase;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id = "meter0";
		private String coreId = "ecoMain0";
		private EcoMainBranchMeterType type = EcoMainBranchMeterType.CONSUMPTION_METERED;
		private SingleOrThreePhase phaseCount = SingleOrThreePhase.THREE_PHASE;
		private SinglePhase phase1 = SinglePhase.L1;
		private int source1 = 0;
		private int channel1 = 1;
		private boolean invert1;
		private SinglePhase phase2 = SinglePhase.L2;
		private int source2 = 0;
		private int channel2 = 2;
		private boolean invert2;
		private SinglePhase phase3 = SinglePhase.L3;
		private int source3 = 0;
		private int channel3 = 3;
		private boolean invert3;

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

		public Builder setType(EcoMainBranchMeterType type) {
			this.type = type;
			return this;
		}

		public Builder setPhaseCount(SingleOrThreePhase phaseCount) {
			this.phaseCount = phaseCount;
			return this;
		}

		public Builder setPhase1(SinglePhase phase, int source, int channel, boolean invert) {
			this.phase1 = phase;
			this.source1 = source;
			this.channel1 = channel;
			this.invert1 = invert;
			return this;
		}

		public Builder setPhase2(SinglePhase phase, int source, int channel, boolean invert) {
			this.phase2 = phase;
			this.source2 = source;
			this.channel2 = channel;
			this.invert2 = invert;
			return this;
		}

		public Builder setPhase3(SinglePhase phase, int source, int channel, boolean invert) {
			this.phase3 = phase;
			this.source3 = source;
			this.channel3 = channel;
			this.invert3 = invert;
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

	@Override
	public EcoMainBranchMeterType type() {
		return this.builder.type;
	}

	@Override
	public SingleOrThreePhase phaseCount() {
		return this.builder.phaseCount;
	}

	@Override
	public SinglePhase phase1() {
		return this.builder.phase1;
	}

	@Override
	public int source1() {
		return this.builder.source1;
	}

	@Override
	public int channel1() {
		return this.builder.channel1;
	}

	@Override
	public boolean invert1() {
		return this.builder.invert1;
	}

	@Override
	public SinglePhase phase2() {
		return this.builder.phase2;
	}

	@Override
	public int source2() {
		return this.builder.source2;
	}

	@Override
	public int channel2() {
		return this.builder.channel2;
	}

	@Override
	public boolean invert2() {
		return this.builder.invert2;
	}

	@Override
	public SinglePhase phase3() {
		return this.builder.phase3;
	}

	@Override
	public int source3() {
		return this.builder.source3;
	}

	@Override
	public int channel3() {
		return this.builder.channel3;
	}

	@Override
	public boolean invert3() {
		return this.builder.invert3;
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
