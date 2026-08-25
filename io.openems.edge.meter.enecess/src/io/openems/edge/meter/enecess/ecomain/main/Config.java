package io.openems.edge.meter.enecess.ecomain.main;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "enecess ecoMain Main Meter", //
		description = "Provides the main-circuit readings of an enecess ecoMain as an electricity meter.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Core-ID", description = "ID of the enecess ecoMain Core component")
	String core_id() default "ecoMain0";

	String webconsole_configurationFactory_nameHint() default "enecess ecoMain Main Meter [{id}]";
}
