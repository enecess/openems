package io.openems.edge.app.meter.enecess;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.session.Language;
import io.openems.common.session.Role;
import io.openems.common.types.EdgeConfig;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.user.User;
import io.openems.edge.core.appmanager.AppManagerTestBundle;
import io.openems.edge.core.appmanager.Apps;
import io.openems.edge.core.appmanager.OpenemsAppCardinality;
import io.openems.edge.core.appmanager.OpenemsAppCategory;
import io.openems.edge.core.appmanager.formly.enums.Validation;
import io.openems.edge.core.appmanager.jsonrpc.AddAppInstance;
import io.openems.edge.core.appmanager.jsonrpc.UpdateAppInstance;

class TestAppEcoMain {

	private static final User ENGLISH_ADMIN = new User("admin", "admin", Language.EN, Role.ADMIN);

	private AppManagerTestBundle bundle;
	private AppEcoMain app;

	@BeforeEach
	void beforeEach() throws Exception {
		this.bundle = new AppManagerTestBundle(null, null, t -> List.of(//
				this.app = Apps.ecoMain(t)), null, //
				new AppManagerTestBundle.PseudoComponentManagerFactory());
		this.bundle.addComponentAggregateTask();
	}

	@Test
	void testMetadataPropertiesAndAssistantRules() {
		assertEquals("App.Meter.enecess.ecoMain", this.app.getAppId());
		assertArrayEquals(new OpenemsAppCategory[] { OpenemsAppCategory.METER }, this.app.getCategories());
		assertEquals(OpenemsAppCardinality.MULTIPLE, this.app.getCardinality());
		assertEquals(this.app, this.bundle.sut.findAppById("App.Meter.enecess.ecoMain").orElseThrow());

		this.bundle.withApp(this.app).hasOnlyProperties(expectedProperties());
		this.bundle.withApp(this.app) //
				.assertThatProperty(AppEcoMain.Property.HAS_MAIN) //
				.hasDefaultValue(true);
		assertNull(AppEcoMain.Property.IP.def().getDefaultValue());

		final var assistant = this.app.getAppAssistant(ENGLISH_ADMIN);
		for (var hiddenProperty : hiddenComponentIdProperties()) {
			assertFalse(hasField(assistant.fields, hiddenProperty),
					() -> "Component ID must stay hidden: " + hiddenProperty);
		}

		final var ipField = findField(assistant.fields, AppEcoMain.Property.IP.name());
		assertTrue(ipField.getAsJsonObject("templateOptions").get("required").getAsBoolean());
		assertEquals(Validation.IP.getPattern(),
				ipField.getAsJsonObject("templateOptions").get("pattern").getAsString());

		final var serialField = findField(assistant.fields, AppEcoMain.Property.SERIAL_NUMBER.name());
		assertEquals("^[0-9]{12}$",
				serialField.getAsJsonObject("templateOptions").get("pattern").getAsString());
		assertEquals("Serial number must consist of exactly 12 digits.", serialField.getAsJsonObject("validation") //
				.getAsJsonObject("messages").get("pattern").getAsString());

		final var branchesField = findField(assistant.fields, "BRANCHES");
		assertEquals("repeat", branchesField.get("type").getAsString());
		assertEquals(new JsonArray(), branchesField.getAsJsonArray("defaultValue"));
	}

	@Test
	void testDefaultInstallationCreatesExactComponents() throws Exception {
		final var response = this.install("Device A", this.properties(true));

		assertIds(response.instance().properties, "modbus0", "ecoMain0", "meter0");
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
	}

	@Test
	void testInitiallyDisabledMainOmitsMainIdAndComponent() throws Exception {
		final var response = this.install("Device A", this.properties(false));

		assertCoreIds(response.instance().properties, "modbus0", "ecoMain0");
		assertFalse(response.instance().properties.has(AppEcoMain.Property.MAIN_ID.name()));
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		assertFalse(this.hasComponent("meter0"));
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Main").isEmpty());
	}

	@Test
	void testInitiallyDisabledMainGetsIdWhenEnabled() throws Exception {
		final var installed = this.install("Device A", this.properties(false));
		final var withMain = installed.instance().properties.deepCopy();
		withMain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), true);

		final var enabled = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(installed.instance().instanceId, "Device A", withMain));

		assertIds(enabled.instance().properties, "modbus0", "ecoMain0", "meter0");
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
	}

	@Test
	void testToggleHasMainPreservesIdsAndRecreatesOwnedMain() throws Exception {
		final var installed = this.install("Device A", this.properties(true));

		final var withoutMain = installed.instance().properties.deepCopy();
		withoutMain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), false);
		final var disabled = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(installed.instance().instanceId, "Device A", withoutMain));

		assertIds(disabled.instance().properties, "modbus0", "ecoMain0", "meter0");
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		assertFalse(this.hasComponent("meter0"));

		final var withMainAgain = disabled.instance().properties.deepCopy();
		withMainAgain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), true);
		final var enabled = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(installed.instance().instanceId, "Device A", withMainAgain));

		assertIds(enabled.instance().properties, "modbus0", "ecoMain0", "meter0");
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
	}

	@Test
	void testEditingConnectionPreservesAllComponentIds() throws Exception {
		final var installed = this.install("Device A", this.properties(true));
		final var updatedProperties = installed.instance().properties.deepCopy();
		updatedProperties.addProperty(AppEcoMain.Property.IP.name(), "10.0.8.89");
		updatedProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "123456789012");

		final var updated = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(installed.instance().instanceId, "Device A", updatedProperties));

		assertIds(updated.instance().properties, "modbus0", "ecoMain0", "meter0");
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "10.0.8.89", "123456789012");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
	}

	@Test
	void testMultipleInstancesUseDistinctIdsAndKeepComponentsIsolated() throws Exception {
		final var first = this.install("Device A", this.properties(false));
		final var secondProperties = this.properties(false);
		secondProperties.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");
		secondProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");
		final var second = this.install("Device B", secondProperties);

		assertCoreIds(first.instance().properties, "modbus0", "ecoMain0");
		assertFalse(first.instance().properties.has(AppEcoMain.Property.MAIN_ID.name()));
		assertCoreIds(second.instance().properties, "modbus1", "ecoMain1");
		assertFalse(second.instance().properties.has(AppEcoMain.Property.MAIN_ID.name()));
		assertEquals(2, this.bundle.sut.getInstantiatedApps().size());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Main").isEmpty());

		final var firstWithMain = first.instance().properties.deepCopy();
		firstWithMain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), true);
		final var firstEnabled = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(first.instance().instanceId, "Device A", firstWithMain));
		final var secondWithMain = second.instance().properties.deepCopy();
		secondWithMain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), true);
		final var secondEnabled = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(second.instance().instanceId, "Device B", secondWithMain));

		assertIds(firstEnabled.instance().properties, "modbus0", "ecoMain0", "meter0");
		assertIds(secondEnabled.instance().properties, "modbus1", "ecoMain1", "meter1");
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
		this.assertCoreComponents("modbus1", "ecoMain1", "Device B", "192.0.2.11", "271828182845");
		this.assertMainComponent("meter1", "ecoMain1", "Device B");

		final var firstWithoutMain = firstEnabled.instance().properties.deepCopy();
		firstWithoutMain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), false);
		final var firstDisabled = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(first.instance().instanceId, "Device A", firstWithoutMain));

		assertIds(firstDisabled.instance().properties, "modbus0", "ecoMain0", "meter0");
		assertEquals(2, this.bundle.sut.getInstantiatedApps().size());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		assertFalse(this.hasComponent("meter0"));
		this.assertCoreComponents("modbus1", "ecoMain1", "Device B", "192.0.2.11", "271828182845");
		this.assertMainComponent("meter1", "ecoMain1", "Device B");
	}

	@Test
	void testDuplicateIpOnAddLeavesExistingAppAndComponentsUnchanged() throws Exception {
		this.install("Device A", this.properties(true));
		final var originalComponents = this.ownedComponentSnapshots();
		final var duplicateIp = this.properties(false);
		duplicateIp.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");

		final var exception = assertThrows(OpenemsNamedException.class,
				() -> this.install("Device B", duplicateIp));

		assertTrue(exception.getMessage().contains("IP address 192.0.2.10 is already used by another ecoMain App."));
		assertEquals(1, this.bundle.sut.getInstantiatedApps().size());
		assertEquals(originalComponents, this.ownedComponentSnapshots());
	}

	@Test
	void testDuplicateSerialOnAddLeavesExistingAppAndComponentsUnchanged() throws Exception {
		this.install("Device A", this.properties(true));
		final var originalComponents = this.ownedComponentSnapshots();
		final var duplicateSerial = this.properties(false);
		duplicateSerial.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");

		final var exception = assertThrows(OpenemsNamedException.class,
				() -> this.install("Device B", duplicateSerial));

		assertTrue(exception.getMessage()
				.contains("Serial number 314159265358 is already used by another ecoMain App."));
		assertEquals(1, this.bundle.sut.getInstantiatedApps().size());
		assertEquals(originalComponents, this.ownedComponentSnapshots());
	}

	@Test
	void testDuplicateIpOnUpdateLeavesBothAppsAndComponentsUnchanged() throws Exception {
		this.install("Device A", this.properties(true));
		final var secondProperties = this.properties(false);
		secondProperties.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");
		secondProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");
		final var second = this.install("Device B", secondProperties);
		final var originalSecondProperties = this.bundle.sut.getInstantiatedApps().stream()
				.filter(instance -> instance.instanceId.equals(second.instance().instanceId)).findFirst().orElseThrow().properties
				.deepCopy();
		final var originalComponents = this.ownedComponentSnapshots();
		final var duplicateIp = second.instance().properties.deepCopy();
		duplicateIp.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.10");

		final var exception = assertThrows(OpenemsNamedException.class,
				() -> this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
						new UpdateAppInstance.Request(second.instance().instanceId, "Device B", duplicateIp)));

		assertTrue(exception.getMessage().contains("IP address 192.0.2.10 is already used by another ecoMain App."));
		assertEquals(2, this.bundle.sut.getInstantiatedApps().size());
		assertEquals(originalSecondProperties, this.bundle.sut.getInstantiatedApps().stream()
				.filter(instance -> instance.instanceId.equals(second.instance().instanceId)).findFirst().orElseThrow().properties);
		assertEquals(originalComponents, this.ownedComponentSnapshots());
	}

	@Test
	void testDuplicateSerialOnUpdateLeavesBothAppsAndComponentsUnchanged() throws Exception {
		this.install("Device A", this.properties(true));
		final var secondProperties = this.properties(false);
		secondProperties.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");
		secondProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");
		final var second = this.install("Device B", secondProperties);
		final var originalSecondProperties = this.bundle.sut.getInstantiatedApps().stream()
				.filter(instance -> instance.instanceId.equals(second.instance().instanceId)).findFirst().orElseThrow().properties
				.deepCopy();
		final var originalComponents = this.ownedComponentSnapshots();
		final var duplicateSerial = second.instance().properties.deepCopy();
		duplicateSerial.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "314159265358");

		final var exception = assertThrows(OpenemsNamedException.class,
				() -> this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
						new UpdateAppInstance.Request(second.instance().instanceId, "Device B", duplicateSerial)));

		assertTrue(exception.getMessage()
				.contains("Serial number 314159265358 is already used by another ecoMain App."));
		assertEquals(2, this.bundle.sut.getInstantiatedApps().size());
		assertEquals(originalSecondProperties, this.bundle.sut.getInstantiatedApps().stream()
				.filter(instance -> instance.instanceId.equals(second.instance().instanceId)).findFirst().orElseThrow().properties);
		assertEquals(originalComponents, this.ownedComponentSnapshots());
	}

	@Test
	void testCoreIdCannotBeChangedToBypassDuplicateUpdateValidation() throws Exception {
		this.install("Device A", this.properties(true));
		final var secondProperties = this.properties(false);
		secondProperties.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");
		secondProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");
		final var second = this.install("Device B", secondProperties);
		final var originalSecondProperties = this.bundle.sut.getInstantiatedApps().stream()
				.filter(instance -> instance.instanceId.equals(second.instance().instanceId)).findFirst().orElseThrow().properties
				.deepCopy();
		final var originalComponents = this.ownedComponentSnapshots();
		final var forgedIdentity = second.instance().properties.deepCopy();
		forgedIdentity.addProperty(AppEcoMain.Property.CORE_ID.name(), "forgedCore");
		forgedIdentity.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.10");

		final var exception = assertThrows(OpenemsNamedException.class,
				() -> this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
						new UpdateAppInstance.Request(second.instance().instanceId, "Device B", forgedIdentity)));

		assertTrue(exception.getMessage().contains("User is not allowed to edit CORE_ID"));
		assertEquals(2, this.bundle.sut.getInstantiatedApps().size());
		assertEquals(originalSecondProperties, this.bundle.sut.getInstantiatedApps().stream()
				.filter(instance -> instance.instanceId.equals(second.instance().instanceId)).findFirst().orElseThrow().properties);
		assertEquals(originalComponents, this.ownedComponentSnapshots());
	}

	@Test
	void testSecondInstanceCanUpdateFromVisibleUiPropertiesWithoutComponentIds() throws Exception {
		this.install("Device A", this.properties(true));
		final var secondProperties = this.properties(false);
		secondProperties.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");
		secondProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");
		final var second = this.install("Device B", secondProperties);
		final var visibleProperties = this.properties(false);
		visibleProperties.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");
		visibleProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");

		final var updated = this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN,
				new UpdateAppInstance.Request(second.instance().instanceId, "Device B updated", visibleProperties));

		assertCoreIds(updated.instance().properties, "modbus1", "ecoMain1");
		assertEquals("Device B updated", updated.instance().alias);
		this.assertCoreComponents("modbus1", "ecoMain1", "Device B updated", "192.0.2.11", "271828182845");
	}

	@ParameterizedTest
	@ValueSource(strings = { "4159265358", "31415926535A", "3141592653580" })
	void testInvalidSerialLeavesNoAppOrComponents(String serialNumber) {
		final var invalidProperties = this.properties(true);
		invalidProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), serialNumber);

		assertThrows(OpenemsNamedException.class, () -> this.install("Device A", invalidProperties));

		assertTrue(this.bundle.sut.getInstantiatedApps().isEmpty());
		assertTrue(this.componentIds("Bridge.Modbus.Tcp").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Core").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Main").isEmpty());
	}

	@Test
	void testInvalidIpOnAddLeavesNoAppOrComponents() {
		final var invalidProperties = this.properties(true);
		invalidProperties.addProperty(AppEcoMain.Property.IP.name(), "not-an-ip");

		assertThrows(OpenemsNamedException.class, () -> this.install("Device A", invalidProperties));

		assertTrue(this.bundle.sut.getInstantiatedApps().isEmpty());
		assertTrue(this.componentIds("Bridge.Modbus.Tcp").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Core").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Main").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Branch").isEmpty());
	}

	@Test
	void testInvalidIpOnUpdatePreservesPropertiesAndComponents() throws Exception {
		final var installed = this.install("Device A", this.properties(true));
		final var originalProperties = this.bundle.sut.getInstantiatedApps().get(0).properties.deepCopy();
		final var originalComponents = this.ownedComponentSnapshots();
		final var invalidProperties = installed.instance().properties.deepCopy();
		invalidProperties.addProperty(AppEcoMain.Property.IP.name(), "300.168.1.89");

		assertThrows(OpenemsNamedException.class,
				() -> this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN, new UpdateAppInstance.Request(
						installed.instance().instanceId, installed.instance().alias, invalidProperties)));

		assertEquals(1, this.bundle.sut.getInstantiatedApps().size());
		assertEquals(originalProperties, this.bundle.sut.getInstantiatedApps().get(0).properties);
		assertEquals(originalComponents, this.ownedComponentSnapshots());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Branch").isEmpty());
	}

	private AddAppInstance.Response install(String alias, JsonObject properties) throws OpenemsNamedException {
		return this.bundle.sut.handleAddAppInstanceRequest(ENGLISH_ADMIN,
				new AddAppInstance.Request(this.app.getAppId(), "key", alias, properties));
	}

	private JsonObject properties(boolean hasMain) {
		return JsonUtils.buildJsonObject() //
				.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.10") //
				.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "314159265358") //
				.addProperty(AppEcoMain.Property.HAS_MAIN.name(), hasMain) //
				.build();
	}

	private void assertCoreComponents(String modbusId, String coreId, String alias, String ip, String serialNumber)
			throws OpenemsNamedException {
		this.bundle.assertComponentsExist(//
				new EdgeConfig.Component(modbusId, "Communication with ecoMain", "Bridge.Modbus.Tcp",
						JsonUtils.buildJsonObject() //
								.addProperty("ip", ip) //
								.addProperty("port", 502) //
								.build()), //
				new EdgeConfig.Component(coreId, alias, "Meter.enecess.ecoMain.Core", JsonUtils.buildJsonObject() //
						.addProperty("modbus.id", modbusId) //
						.addProperty("serialNumber", serialNumber) //
						.build()));
		assertEquals("Communication with ecoMain", this.component(modbusId).getAlias());
		assertEquals(alias, this.component(coreId).getAlias());
	}

	private void assertMainComponent(String mainId, String coreId, String alias) throws OpenemsNamedException {
		this.bundle.assertComponentExist(new EdgeConfig.Component(mainId, alias, "Meter.enecess.ecoMain.Main",
				JsonUtils.buildJsonObject() //
						.addProperty("core.id", coreId) //
						.build()));
		final var main = this.component(mainId);
		assertEquals(alias, main.getAlias());
		assertFalse(main.getProperties().containsKey("type"));
	}

	private EdgeConfig.Component component(String componentId) {
		return this.bundle.componentManger.getEdgeConfig().getComponent(componentId).orElseThrow();
	}

	private boolean hasComponent(String componentId) {
		return this.bundle.componentManger.getEdgeConfig().getComponent(componentId).isPresent();
	}

	private List<String> componentIds(String factoryId) {
		return this.bundle.componentManger.getEdgeConfig().getComponentIdsByFactory(factoryId);
	}

	private List<JsonObject> ownedComponentSnapshots() {
		return Stream.of("Bridge.Modbus.Tcp", "Meter.enecess.ecoMain.Core", "Meter.enecess.ecoMain.Main",
				"Meter.enecess.ecoMain.Branch") //
				.flatMap(factoryId -> this.componentIds(factoryId).stream()) //
				.sorted() //
				.map(componentId -> JsonUtils.buildJsonObject() //
						.addProperty("id", componentId) //
						.add("component", this.component(componentId)
								.toJson(EdgeConfig.Component.JsonFormat.WITHOUT_CHANNELS).deepCopy()) //
						.build()) //
				.toList();
	}

	private static void assertIds(JsonObject properties, String modbusId, String coreId, String mainId) {
		assertCoreIds(properties, modbusId, coreId);
		assertEquals(mainId, properties.get(AppEcoMain.Property.MAIN_ID.name()).getAsString());
	}

	private static void assertCoreIds(JsonObject properties, String modbusId, String coreId) {
		assertEquals(modbusId, properties.get(AppEcoMain.Property.MODBUS_ID.name()).getAsString());
		assertEquals(coreId, properties.get(AppEcoMain.Property.CORE_ID.name()).getAsString());
	}

	private static JsonObject findField(Iterable<com.google.gson.JsonElement> fields, String key) {
		for (var field : fields) {
			final var fieldObject = field.getAsJsonObject();
			if (fieldObject.has("key") && key.equals(fieldObject.get("key").getAsString())) {
				return fieldObject;
			}
		}
		throw new AssertionError("No assistant field found for key [" + key + "]");
	}

	private static boolean hasField(Iterable<com.google.gson.JsonElement> fields, String key) {
		for (var field : fields) {
			final var fieldObject = field.getAsJsonObject();
			if (fieldObject.has("key") && key.equals(fieldObject.get("key").getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static AppEcoMain.Property[] expectedProperties() {
		return List.of(//
				"MODBUS_ID", "CORE_ID", "MAIN_ID", //
				"BRANCH_01_ID", "BRANCH_02_ID", "BRANCH_03_ID", "BRANCH_04_ID", "BRANCH_05_ID", //
				"BRANCH_06_ID", "BRANCH_07_ID", "BRANCH_08_ID", "BRANCH_09_ID", "BRANCH_10_ID", //
				"BRANCH_11_ID", "BRANCH_12_ID", "BRANCH_13_ID", "BRANCH_14_ID", "BRANCH_15_ID", //
				"BRANCH_16_ID", "BRANCH_17_ID", "BRANCH_18_ID", "BRANCH_19_ID", "BRANCH_20_ID", //
				"BRANCH_21_ID", "BRANCH_22_ID", "BRANCH_23_ID", "BRANCH_24_ID", "BRANCH_25_ID", //
				"BRANCH_26_ID", "BRANCH_27_ID", "BRANCH_28_ID", "BRANCH_29_ID", "BRANCH_30_ID", //
				"BRANCH_31_ID", "BRANCH_32_ID", "BRANCH_33_ID", "BRANCH_34_ID", "BRANCH_35_ID", //
				"BRANCH_36_ID", "BRANCH_37_ID", "BRANCH_38_ID", "BRANCH_39_ID", "BRANCH_40_ID", //
				"ALIAS", "IP", "SERIAL_NUMBER", "HAS_MAIN", "BRANCHES") //
				.stream() //
				.map(AppEcoMain.Property::valueOf) //
				.toArray(AppEcoMain.Property[]::new);
	}

	private static List<String> hiddenComponentIdProperties() {
		return List.of(//
				"MODBUS_ID", "CORE_ID", "MAIN_ID", //
				"BRANCH_01_ID", "BRANCH_02_ID", "BRANCH_03_ID", "BRANCH_04_ID", "BRANCH_05_ID", //
				"BRANCH_06_ID", "BRANCH_07_ID", "BRANCH_08_ID", "BRANCH_09_ID", "BRANCH_10_ID", //
				"BRANCH_11_ID", "BRANCH_12_ID", "BRANCH_13_ID", "BRANCH_14_ID", "BRANCH_15_ID", //
				"BRANCH_16_ID", "BRANCH_17_ID", "BRANCH_18_ID", "BRANCH_19_ID", "BRANCH_20_ID", //
				"BRANCH_21_ID", "BRANCH_22_ID", "BRANCH_23_ID", "BRANCH_24_ID", "BRANCH_25_ID", //
				"BRANCH_26_ID", "BRANCH_27_ID", "BRANCH_28_ID", "BRANCH_29_ID", "BRANCH_30_ID", //
				"BRANCH_31_ID", "BRANCH_32_ID", "BRANCH_33_ID", "BRANCH_34_ID", "BRANCH_35_ID", //
				"BRANCH_36_ID", "BRANCH_37_ID", "BRANCH_38_ID", "BRANCH_39_ID", "BRANCH_40_ID");
	}
}
