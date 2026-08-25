package io.openems.edge.meter.enecess.ecomain.core;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "enecess ecoMain Core", //
		description = "Validates the identity of an enecess ecoMain device.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ecoMain0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Modbus-ID", description = "ID of Modbus bridge.")
	String modbus_id() default "modbus0";

	@AttributeDefinition(name = "Serial Number", description = "Expected 12-digit device serial number")
	String serialNumber();

	String webconsole_configurationFactory_nameHint() default "enecess ecoMain Core [{id}]";
}
