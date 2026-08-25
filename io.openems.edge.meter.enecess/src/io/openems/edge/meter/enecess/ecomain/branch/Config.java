package io.openems.edge.meter.enecess.ecomain.branch;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.common.type.Phase.SingleOrThreePhase;
import io.openems.edge.common.type.Phase.SinglePhase;

@ObjectClassDefinition(//
		name = "enecess ecoMain ecoSub Branch Meter", //
		description = "Provides a configurable ecoMain or ecoSub branch as an electricity meter.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Core-ID", description = "ID of the enecess ecoMain Core component")
	String core_id() default "ecoMain0";

	@AttributeDefinition(name = "Meter-Type", description = "What is measured by this Meter?")
	EcoMainBranchMeterType type() default EcoMainBranchMeterType.CONSUMPTION_METERED;

	@AttributeDefinition(name = "Phase count", description = "Whether this branch uses one or three phases")
	SingleOrThreePhase phaseCount() default SingleOrThreePhase.THREE_PHASE;

	@AttributeDefinition(name = "Logical phase 1", description = "Logical phase assigned to slot 1")
	SinglePhase phase1() default SinglePhase.L1;

	@AttributeDefinition(name = "Source 1", description = "Physical ecoMain source for slot 1")
	int source1() default 0;

	@AttributeDefinition(name = "Channel 1", description = "Physical ecoMain channel for slot 1")
	int channel1() default 1;

	@AttributeDefinition(name = "Invert 1", description = "Invert direction for slot 1")
	boolean invert1() default false;

	@AttributeDefinition(name = "Logical phase 2", description = "Logical phase assigned to slot 2")
	SinglePhase phase2() default SinglePhase.L2;

	@AttributeDefinition(name = "Source 2", description = "Physical ecoMain source for slot 2")
	int source2() default 0;

	@AttributeDefinition(name = "Channel 2", description = "Physical ecoMain channel for slot 2")
	int channel2() default 2;

	@AttributeDefinition(name = "Invert 2", description = "Invert direction for slot 2")
	boolean invert2() default false;

	@AttributeDefinition(name = "Logical phase 3", description = "Logical phase assigned to slot 3")
	SinglePhase phase3() default SinglePhase.L3;

	@AttributeDefinition(name = "Source 3", description = "Physical ecoMain source for slot 3")
	int source3() default 0;

	@AttributeDefinition(name = "Channel 3", description = "Physical ecoMain channel for slot 3")
	int channel3() default 3;

	@AttributeDefinition(name = "Invert 3", description = "Invert direction for slot 3")
	boolean invert3() default false;

	String webconsole_configurationFactory_nameHint() default "enecess ecoMain ecoSub Branch Meter [{id}]";
}
