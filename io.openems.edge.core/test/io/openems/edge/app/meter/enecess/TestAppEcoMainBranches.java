package io.openems.edge.app.meter.enecess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.session.Language;
import io.openems.common.session.Role;
import io.openems.common.types.EdgeConfig;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.user.User;
import io.openems.edge.core.appmanager.AppManagerTestBundle;
import io.openems.edge.core.appmanager.Apps;
import io.openems.edge.core.appmanager.ConfigurationTarget;
import io.openems.edge.core.appmanager.jsonrpc.AddAppInstance;
import io.openems.edge.core.appmanager.jsonrpc.DeleteAppInstance;
import io.openems.edge.core.appmanager.jsonrpc.UpdateAppInstance;
import io.openems.edge.core.appmanager.validator.ExpressionEvaluator;

class TestAppEcoMainBranches {

	private static final User ENGLISH_ADMIN = new User("admin", "admin", Language.EN, Role.ADMIN);
	private static final User GERMAN_ADMIN = new User("admin", "admin", Language.DE, Role.ADMIN);
	private static final String BRANCH_FACTORY_ID = "Meter.enecess.ecoMain.Branch";
	private static final String MAX_BRANCHES_EXPRESSION = "(control.value ?? []).length <= 40";
	private static final String PHASES_UNIQUE_EXPRESSION = "(() => { const branches = control.value ?? []; " //
			+ "return branches.every(branch => branch.PHASE_COUNT != 'THREE_PHASE' || " //
			+ "(new Set([branch.PHASE_1, branch.PHASE_2, branch.PHASE_3]).size == 3 && " //
			+ "['L1', 'L2', 'L3'].every(phase => " //
			+ "[branch.PHASE_1, branch.PHASE_2, branch.PHASE_3].includes(phase)))); })()";
	private static final String ADDRESS_UNIQUE_EXPRESSION = "(() => { " //
			+ "const branches = control.value ?? []; const addresses = new Set(); " //
			+ "return branches.every(branch => " //
			+ "(branch.PHASE_COUNT == 'SINGLE_PHASE' ? [1] : [1, 2, 3]).every(phaseIndex => { " //
			+ "const source = branch[`SOURCE_${phaseIndex}`]; " //
			+ "const channel = branch[`CHANNEL_${phaseIndex}`]; " //
			+ "if (source == null || channel == null) return true; " //
			+ "const address = `${source}:${channel}`; " //
			+ "if (addresses.has(address)) return false; addresses.add(address); return true; })); " //
			+ "})()";
	private static final String ENGLISH_PHASES_UNIQUE_MESSAGE = "(() => { " //
			+ "const branches = control.value ?? []; " //
			+ "const branchIndex = branches.findIndex(branch => branch.PHASE_COUNT == 'THREE_PHASE' && " //
			+ "(new Set([branch.PHASE_1, branch.PHASE_2, branch.PHASE_3]).size != 3 || " //
			+ "!['L1', 'L2', 'L3'].every(phase => " //
			+ "[branch.PHASE_1, branch.PHASE_2, branch.PHASE_3].includes(phase)))); " //
			+ "if (branchIndex < 0) return ''; const branch = branches[branchIndex]; " //
			+ "return `Branch ${branch.SLOT ?? branchIndex + 1} must assign L1, L2 and L3 exactly once.`; " //
			+ "})()";
	private static final String GERMAN_PHASES_UNIQUE_MESSAGE = "(() => { " //
			+ "const branches = control.value ?? []; " //
			+ "const branchIndex = branches.findIndex(branch => branch.PHASE_COUNT == 'THREE_PHASE' && " //
			+ "(new Set([branch.PHASE_1, branch.PHASE_2, branch.PHASE_3]).size != 3 || " //
			+ "!['L1', 'L2', 'L3'].every(phase => " //
			+ "[branch.PHASE_1, branch.PHASE_2, branch.PHASE_3].includes(phase)))); " //
			+ "if (branchIndex < 0) return ''; const branch = branches[branchIndex]; " //
			+ "return `Branch ${branch.SLOT ?? branchIndex + 1} muss L1, L2 und L3 jeweils genau einmal zuordnen.`; " //
			+ "})()";
	private static final String ENGLISH_ADDRESS_UNIQUE_MESSAGE = "(() => { " //
			+ "const branches = control.value ?? []; " //
			+ "const sourceLabels = { ECOMAIN: 'ecoMain', ECOSUB_1: 'ecoSub 1', ECOSUB_2: 'ecoSub 2', " //
			+ "ECOSUB_3: 'ecoSub 3' }; " //
			+ "const entries = branches.flatMap((branch, branchIndex) => " //
			+ "(branch.PHASE_COUNT == 'SINGLE_PHASE' ? [1] : [1, 2, 3]).map(phaseIndex => " //
			+ "({ branch, branchIndex, phaseIndex, source: branch[`SOURCE_${phaseIndex}`], " //
			+ "channel: branch[`CHANNEL_${phaseIndex}`] }))).filter(entry => " //
			+ "entry.source != null && entry.channel != null); " //
			+ "const duplicateIndex = entries.findIndex((entry, index) => entries.findIndex(candidate => " //
			+ "candidate.source == entry.source && candidate.channel == entry.channel) != index); " //
			+ "if (duplicateIndex < 0) return ''; const duplicate = entries[duplicateIndex]; " //
			+ "const conflict = entries.find((entry, index) => index < duplicateIndex && " //
			+ "entry.source == duplicate.source && entry.channel == duplicate.channel); " //
			+ "return `${sourceLabels[duplicate.source] ?? duplicate.source} / channel ${duplicate.channel} " //
			+ "is already used by Branch ${conflict.branch.SLOT ?? conflict.branchIndex + 1} " //
			+ "${conflict.branch[`PHASE_${conflict.phaseIndex}`]}.`; })()";
	private static final String GERMAN_ADDRESS_UNIQUE_MESSAGE = "(() => { " //
			+ "const branches = control.value ?? []; " //
			+ "const sourceLabels = { ECOMAIN: 'ecoMain', ECOSUB_1: 'ecoSub 1', ECOSUB_2: 'ecoSub 2', " //
			+ "ECOSUB_3: 'ecoSub 3' }; " //
			+ "const entries = branches.flatMap((branch, branchIndex) => " //
			+ "(branch.PHASE_COUNT == 'SINGLE_PHASE' ? [1] : [1, 2, 3]).map(phaseIndex => " //
			+ "({ branch, branchIndex, phaseIndex, source: branch[`SOURCE_${phaseIndex}`], " //
			+ "channel: branch[`CHANNEL_${phaseIndex}`] }))).filter(entry => " //
			+ "entry.source != null && entry.channel != null); " //
			+ "const duplicateIndex = entries.findIndex((entry, index) => entries.findIndex(candidate => " //
			+ "candidate.source == entry.source && candidate.channel == entry.channel) != index); " //
			+ "if (duplicateIndex < 0) return ''; const duplicate = entries[duplicateIndex]; " //
			+ "const conflict = entries.find((entry, index) => index < duplicateIndex && " //
			+ "entry.source == duplicate.source && entry.channel == duplicate.channel); " //
			+ "return `${sourceLabels[duplicate.source] ?? duplicate.source} / Kanal ${duplicate.channel} " //
			+ "wird bereits von Branch ${conflict.branch.SLOT ?? conflict.branchIndex + 1} " //
			+ "${conflict.branch[`PHASE_${conflict.phaseIndex}`]} verwendet.`; })()";

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
	void testAssistantDefinesExactBranchFieldsAndSelectOptions() {
		final var branchField = this.branchAssistantField();
		assertEquals("repeat", branchField.get("type").getAsString());
		assertEquals(new JsonArray(), branchField.getAsJsonArray("defaultValue"));

		final var fields = branchFieldGroup(branchField);
		assertEquals(List.of(//
				"SLOT", "ALIAS", "TYPE", "PHASE_COUNT", //
				"PHASE_1", "SOURCE_1", "CHANNEL_1", "INVERT_1", //
				"PHASE_2", "SOURCE_2", "CHANNEL_2", "INVERT_2", //
				"PHASE_3", "SOURCE_3", "CHANNEL_3", "INVERT_3"), fieldKeys(fields));

		final var slot = findField(fields, "SLOT");
		assertEquals("input", slot.get("type").getAsString());
		assertTrue(slot.get("hide").getAsBoolean());
		assertFalse(isRequired(slot));
		assertFalse(isRequired(findField(fields, "ALIAS")));

		final var typeValues = stringOptionValues(findField(fields, "TYPE"));
		assertEquals(List.of("PRODUCTION", "PRODUCTION_AND_CONSUMPTION", "CONSUMPTION_METERED",
				"CONSUMPTION_NOT_METERED"), typeValues);
		assertFalse(typeValues.stream().anyMatch(value -> value.contains("GRID")));
		assertEquals(List.of("SINGLE_PHASE", "THREE_PHASE"),
				stringOptionValues(findField(fields, "PHASE_COUNT")));

		assertEquals(List.of("L1", "L2", "L3"), stringOptionValues(findField(fields, "PHASE_1")));
		assertEquals(List.of("L1", "L2", "L3"), stringOptionValues(findField(fields, "PHASE_2")));
		assertEquals(List.of("L1", "L2", "L3"), stringOptionValues(findField(fields, "PHASE_3")));
		assertEquals(List.of("ECOMAIN", "ECOSUB_1", "ECOSUB_2", "ECOSUB_3"),
				stringOptionValues(findField(fields, "SOURCE_1")));
		assertEquals(List.of("ECOMAIN", "ECOSUB_1", "ECOSUB_2", "ECOSUB_3"),
				stringOptionValues(findField(fields, "SOURCE_2")));
		assertEquals(List.of("ECOMAIN", "ECOSUB_1", "ECOSUB_2", "ECOSUB_3"),
				stringOptionValues(findField(fields, "SOURCE_3")));

		assertChannelOptions(findField(fields, "CHANNEL_1"));
		assertChannelOptions(findField(fields, "CHANNEL_2"));
		assertChannelOptions(findField(fields, "CHANNEL_3"));
	}

	@Test
	void testAssistantDefaultsPhysicalAddressesToUnselectedAndHidesInactivePhases() {
		final var fields = branchFieldGroup(this.branchAssistantField());

		assertEquals("CONSUMPTION_METERED", findField(fields, "TYPE").get("defaultValue").getAsString());
		assertEquals("THREE_PHASE", findField(fields, "PHASE_COUNT").get("defaultValue").getAsString());
		assertEquals("L1", findField(fields, "PHASE_1").get("defaultValue").getAsString());
		assertEquals("L2", findField(fields, "PHASE_2").get("defaultValue").getAsString());
		assertEquals("L3", findField(fields, "PHASE_3").get("defaultValue").getAsString());
		assertFalse(findField(fields, "INVERT_1").get("defaultValue").getAsBoolean());
		assertFalse(findField(fields, "INVERT_2").get("defaultValue").getAsBoolean());
		assertFalse(findField(fields, "INVERT_3").get("defaultValue").getAsBoolean());

		assertNoDefaultValue(findField(fields, "SOURCE_1"));
		assertNoDefaultValue(findField(fields, "SOURCE_2"));
		assertNoDefaultValue(findField(fields, "SOURCE_3"));
		assertNoDefaultValue(findField(fields, "CHANNEL_1"));
		assertNoDefaultValue(findField(fields, "CHANNEL_2"));
		assertNoDefaultValue(findField(fields, "CHANNEL_3"));

		assertOnlyVisibleForThreePhase(findField(fields, "PHASE_2"));
		assertOnlyVisibleForThreePhase(findField(fields, "SOURCE_2"));
		assertOnlyVisibleForThreePhase(findField(fields, "CHANNEL_2"));
		assertOnlyVisibleForThreePhase(findField(fields, "INVERT_2"));
		assertOnlyVisibleForThreePhase(findField(fields, "PHASE_3"));
		assertOnlyVisibleForThreePhase(findField(fields, "SOURCE_3"));
		assertOnlyVisibleForThreePhase(findField(fields, "CHANNEL_3"));
		assertOnlyVisibleForThreePhase(findField(fields, "INVERT_3"));
	}

	@Test
	void testAssistantExposesRepeatWideValidatorsOnUiPostprocessedNodeAndManualSelectionHelp() {
		final var branchField = this.branchAssistantField();
		final var validators = branchField.getAsJsonObject("validators");
		assertNotNull(validators);
		assertEquals(expectedMaxBranchesValidator(), validators.getAsJsonObject("maxBranches"));
		assertExactValidator(validators.getAsJsonObject("phasesUnique"), PHASES_UNIQUE_EXPRESSION,
				ENGLISH_PHASES_UNIQUE_MESSAGE);
		assertExactValidator(validators.getAsJsonObject("addressUnique"), ADDRESS_UNIQUE_EXPRESSION,
				ENGLISH_ADDRESS_UNIQUE_MESSAGE);

		final var fields = branchFieldGroup(branchField);
		assertFalse(findField(fields, "PHASE_3").has("validators"));
		assertFalse(findField(fields, "CHANNEL_1").has("validators"));
		assertFalse(findField(fields, "CHANNEL_2").has("validators"));
		assertFalse(findField(fields, "CHANNEL_3").has("validators"));

		final var helpText = Stream.concat(Stream.of(branchField), fields.asList().stream().map(JsonElement::getAsJsonObject))
				.map(TestAppEcoMainBranches::descriptionOrEmpty) //
				.reduce("", (left, right) -> left + " " + right) //
				.toLowerCase(Locale.ENGLISH);
		assertTrue(helpText.contains("source"));
		assertTrue(helpText.contains("channel"));
		assertTrue(helpText.contains("manually"));
	}

	@Test
	void testAssistantWrapsBranchRepeatToRenderValidatorMessages() {
		final var wrappers = this.branchAssistantField().getAsJsonArray("wrappers");

		assertNotNull(wrappers);
		assertEquals(List.of("form-field"),
				wrappers.asList().stream().map(JsonElement::getAsString).toList());
	}

	@Test
	void testAssistantExposesExactGermanRepeatValidatorMessagesWithNewBranchFallback() {
		final var validators = this.branchAssistantField(GERMAN_ADMIN).getAsJsonObject("validators");
		assertNotNull(validators);
		assertExactValidator(validators.getAsJsonObject("addressUnique"), ADDRESS_UNIQUE_EXPRESSION,
				GERMAN_ADDRESS_UNIQUE_MESSAGE);
		assertExactValidator(validators.getAsJsonObject("phasesUnique"), PHASES_UNIQUE_EXPRESSION,
				GERMAN_PHASES_UNIQUE_MESSAGE);
	}

	@Test
	void testEmptyBranchListCreatesOnlyBridgeAndCoreWhenMainIsDisabled() throws Exception {
		final var installed = this.install("Device A", this.properties(false, new JsonArray()));

		assertEquals(new JsonArray(), installed.instance().properties.getAsJsonArray("BRANCHES"));
		assertEquals(List.of("modbus0"), this.componentIds("Bridge.Modbus.Tcp"));
		assertEquals(List.of("ecoMain0"), this.componentIds("Meter.enecess.ecoMain.Core"));
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Main").isEmpty());
		assertTrue(this.componentIds(BRANCH_FACTORY_ID).isEmpty());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
	}

	@Test
	void testSinglePhaseBranchCreatesExactComponentAndAllocatesSlotAndId() throws Exception {
		final var installed = this.install("Device A", this.properties(true, branches(//
				singlePhaseBranch(null, "Solar", "PRODUCTION", phase("L2", "ECOMAIN", 7, false)))));

		assertEquals(1, installed.instance().properties.getAsJsonArray("BRANCHES").get(0).getAsJsonObject()
				.get("SLOT").getAsInt());
		assertEquals("meter1", installed.instance().properties.get("BRANCH_01_ID").getAsString());
		this.assertSinglePhaseBranchComponent("meter1", "Solar", "ecoMain0", "PRODUCTION", "L2", 0, 7, false);
		assertEquals(1, this.componentIds(BRANCH_FACTORY_ID).size());
	}

	@Test
	void testThreePhaseBranchSupportsNonContiguousChannelsAcrossSources() throws Exception {
		this.install("Device A", this.properties(false, branches(//
				threePhaseBranch(null, "Workshop", "CONSUMPTION_METERED", //
						phase("L1", "ECOMAIN", 10, false), //
						phase("L2", "ECOSUB_2", 3, true), //
						phase("L3", "ECOSUB_3", 9, false)))));

		this.assertExactComponent(new EdgeConfig.Component("meter1", "Workshop", BRANCH_FACTORY_ID,
				JsonUtils.buildJsonObject() //
						.addProperty("core.id", "ecoMain0") //
						.addProperty("type", "CONSUMPTION_METERED") //
						.addProperty("phaseCount", "THREE_PHASE") //
						.addProperty("phase1", "L1") //
						.addProperty("source1", 0) //
						.addProperty("channel1", 10) //
						.addProperty("invert1", false) //
						.addProperty("phase2", "L2") //
						.addProperty("source2", 2) //
						.addProperty("channel2", 3) //
						.addProperty("invert2", true) //
						.addProperty("phase3", "L3") //
						.addProperty("source3", 3) //
						.addProperty("channel3", 9) //
						.addProperty("invert3", false) //
						.build()));
	}

	@Test
	void testMultipleBranchesReferenceTheSameOwnedCoreWithoutDependencies() throws Exception {
		final var properties = this.properties(false, branches(//
				singlePhaseBranch(1, "Load", "CONSUMPTION_METERED", phase("L1", "ECOMAIN", 1, false)), //
				singlePhaseBranch(2, "PV", "PRODUCTION", phase("L3", "ECOSUB_1", 8, true))));

		final var configuration = this.app.getAppConfiguration(ConfigurationTarget.ADD, properties, Language.EN);
		assertTrue(configuration.dependencies().isEmpty());

		final var installed = this.install("Device A", properties);
		assertTrue(installed.instance().dependencies == null || installed.instance().dependencies.isEmpty());
		this.assertSinglePhaseBranchComponent("meter1", "Load", "ecoMain0", "CONSUMPTION_METERED", "L1", 0,
				1, false);
		this.assertSinglePhaseBranchComponent("meter2", "PV", "ecoMain0", "PRODUCTION", "L3", 1, 8, true);
	}

	@Test
	void testBlankBranchAliasIsNormalizedInResponseAndComponent() throws Exception {
		final var installed = this.install("Device A", this.properties(true, branches(//
				singlePhaseBranch(null, " \t ", "CONSUMPTION_METERED", phase("L1", "ECOMAIN", 2, false)))));

		assertEquals("Branch 1", installed.instance().properties.getAsJsonArray("BRANCHES").get(0).getAsJsonObject()
				.get("ALIAS").getAsString());
		this.assertSinglePhaseBranchComponent("meter1", "Branch 1", "ecoMain0", "CONSUMPTION_METERED", "L1", 0,
				2, false);
	}

	@Test
	void testFortySinglePhaseBranchesUseAllPhysicalAddresses() throws Exception {
		final var installed = this.install("Device A", this.properties(false, allPhysicalAddresses()));

		final var normalized = installed.instance().properties.getAsJsonArray("BRANCHES");
		assertEquals(40, normalized.size());
		assertEquals(1, normalized.get(0).getAsJsonObject().get("SLOT").getAsInt());
		assertEquals(40, normalized.get(39).getAsJsonObject().get("SLOT").getAsInt());
		for (var slot = 1; slot <= 40; slot++) {
			assertEquals("meter" + slot,
					installed.instance().properties.get(String.format(Locale.ROOT, "BRANCH_%02d_ID", slot)).getAsString());
		}
		assertEquals(40, this.componentIds(BRANCH_FACTORY_ID).size());
		this.assertMaximumAddressBlockComponents();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidBranchConfigurations")
	void testInvalidBranchAddIsRejectedAtomically(String description, JsonArray invalidBranches) {
		final var exception = assertThrows(OpenemsNamedException.class,
				() -> this.install("Device A", this.properties(true, invalidBranches)), description);
		assertFalse(exception.getMessage().contains("Unknown Configuration Property"),
				"The branch model must be rejected by backend validation, not as an unknown App property");

		assertTrue(this.bundle.sut.getInstantiatedApps().isEmpty());
		assertTrue(this.componentIds("Bridge.Modbus.Tcp").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Core").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Main").isEmpty());
		assertTrue(this.componentIds(BRANCH_FACTORY_ID).isEmpty());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidBranchUpdates")
	void testInvalidBranchUpdatePreservesPropertiesAndComponents(String description, boolean maximumConfiguration,
			Consumer<JsonArray> mutation) throws Exception {
		final var initialBranches = maximumConfiguration ? allPhysicalAddresses() : validUpdateBranches();
		final var installed = this.install("Device A", this.properties(true, initialBranches));
		final var originalProperties = this.bundle.sut.getInstantiatedApps().get(0).properties.deepCopy();
		final var originalComponents = this.ownedComponentSnapshots();
		final var invalidProperties = installed.instance().properties.deepCopy();
		final var invalidBranches = invalidProperties.getAsJsonArray("BRANCHES");
		mutation.accept(invalidBranches);

		assertThrows(OpenemsNamedException.class,
				() -> this.update(installed, invalidProperties), description);

		assertEquals(originalProperties, this.bundle.sut.getInstantiatedApps().get(0).properties);
		assertEquals(originalComponents, this.ownedComponentSnapshots());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
		if (maximumConfiguration) {
			assertEquals(40, this.componentIds(BRANCH_FACTORY_ID).size());
			this.assertMaximumAddressBlockComponents();
		} else {
			assertEquals(List.of("meter1", "meter2"), this.componentIds(BRANCH_FACTORY_ID));
			this.assertValidUpdateBranchComponents();
		}
	}

	@Test
	void testEditingEveryBranchSettingPreservesSlotAndComponentId() throws Exception {
		final var installed = this.install("Device A", this.properties(true, branches(//
				singlePhaseBranch(null, "Initial", "CONSUMPTION_METERED", phase("L1", "ECOMAIN", 1, false)))));
		final var updatedProperties = installed.instance().properties.deepCopy();
		updatedProperties.add("BRANCHES", branches(//
				threePhaseBranch(1, "Workshop", "PRODUCTION_AND_CONSUMPTION", //
						phase("L3", "ECOSUB_3", 10, true), //
						phase("L1", "ECOMAIN", 4, true), //
						phase("L2", "ECOSUB_2", 6, false))));

		final var updated = this.update(installed, updatedProperties);

		assertEquals(1, updated.instance().properties.getAsJsonArray("BRANCHES").get(0).getAsJsonObject()
				.get("SLOT").getAsInt());
		assertEquals("meter1", updated.instance().properties.get("BRANCH_01_ID").getAsString());
		this.assertExactComponent(new EdgeConfig.Component("meter1", "Workshop", BRANCH_FACTORY_ID,
				JsonUtils.buildJsonObject() //
						.addProperty("core.id", "ecoMain0") //
						.addProperty("type", "PRODUCTION_AND_CONSUMPTION") //
						.addProperty("phaseCount", "THREE_PHASE") //
						.addProperty("phase1", "L3") //
						.addProperty("source1", 3) //
						.addProperty("channel1", 10) //
						.addProperty("invert1", true) //
						.addProperty("phase2", "L1") //
						.addProperty("source2", 0) //
						.addProperty("channel2", 4) //
						.addProperty("invert2", true) //
						.addProperty("phase3", "L2") //
						.addProperty("source3", 2) //
						.addProperty("channel3", 6) //
						.addProperty("invert3", false) //
						.build()));
	}

	@Test
	void testReorderingBranchesWithSlotsPreservesBothComponentIds() throws Exception {
		final var installed = this.install("Device A", this.properties(false, branches(//
				singlePhaseBranch(null, "First", "CONSUMPTION_METERED", phase("L1", "ECOMAIN", 1, false)), //
				singlePhaseBranch(null, "Second", "PRODUCTION", phase("L2", "ECOMAIN", 2, true)))));
		final var installedBranches = installed.instance().properties.getAsJsonArray("BRANCHES");
		final var reordered = branches(installedBranches.get(1).deepCopy(), installedBranches.get(0).deepCopy());
		final var updatedProperties = installed.instance().properties.deepCopy();
		updatedProperties.add("BRANCHES", reordered);

		final var updated = this.update(installed, updatedProperties);

		final var responseBranches = updated.instance().properties.getAsJsonArray("BRANCHES");
		assertEquals(2, responseBranches.get(0).getAsJsonObject().get("SLOT").getAsInt());
		assertEquals(1, responseBranches.get(1).getAsJsonObject().get("SLOT").getAsInt());
		assertEquals("meter1", updated.instance().properties.get("BRANCH_01_ID").getAsString());
		assertEquals("meter2", updated.instance().properties.get("BRANCH_02_ID").getAsString());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertSinglePhaseBranchComponent("meter1", "First", "ecoMain0", "CONSUMPTION_METERED", "L1", 0,
				1, false);
		this.assertSinglePhaseBranchComponent("meter2", "Second", "ecoMain0", "PRODUCTION", "L2", 0, 2, true);
	}

	@Test
	void testDeletingAndAddingBranchReusesReleasedSlotAndPreservedId() throws Exception {
		final var installed = this.install("Device A", this.properties(false, branches(//
				singlePhaseBranch(null, "First", "CONSUMPTION_METERED", phase("L1", "ECOMAIN", 1, false)), //
				singlePhaseBranch(null, "Second", "CONSUMPTION_METERED", phase("L2", "ECOMAIN", 2, false)))));
		final var keepSecond = branches(
				installed.instance().properties.getAsJsonArray("BRANCHES").get(1).deepCopy());
		final var deleteProperties = installed.instance().properties.deepCopy();
		deleteProperties.add("BRANCHES", keepSecond);

		final var deleted = this.update(installed, deleteProperties);

		assertFalse(this.hasComponent("meter1"));
		assertTrue(this.hasComponent("meter2"));
		this.assertSecondBranchComponent();
		assertEquals("meter1", deleted.instance().properties.get("BRANCH_01_ID").getAsString());
		assertEquals("meter2", deleted.instance().properties.get("BRANCH_02_ID").getAsString());

		final var withReplacement = deleted.instance().properties.getAsJsonArray("BRANCHES").deepCopy();
		withReplacement.add(singlePhaseBranch(null, "Replacement", "PRODUCTION",
				phase("L3", "ECOSUB_1", 7, true)));
		final var replacementProperties = deleted.instance().properties.deepCopy();
		replacementProperties.add("BRANCHES", withReplacement);

		final var replaced = this.update(deleted, replacementProperties);

		final var responseBranches = replaced.instance().properties.getAsJsonArray("BRANCHES");
		assertEquals(2, responseBranches.get(0).getAsJsonObject().get("SLOT").getAsInt());
		assertEquals(1, responseBranches.get(1).getAsJsonObject().get("SLOT").getAsInt());
		assertEquals("meter1", replaced.instance().properties.get("BRANCH_01_ID").getAsString());
		assertEquals("meter2", replaced.instance().properties.get("BRANCH_02_ID").getAsString());
		this.assertSinglePhaseBranchComponent("meter1", "Replacement", "ecoMain0", "PRODUCTION", "L3", 1, 7,
				true);
		this.assertSecondBranchComponent();
	}

	@Test
	void testMainTogglePreservesBranchIdAndComponent() throws Exception {
		final var installed = this.install("Device A", this.properties(true, branches(//
				singlePhaseBranch(null, "Load", "CONSUMPTION_METERED", phase("L1", "ECOMAIN", 5, false)))));
		final var withoutMain = installed.instance().properties.deepCopy();
		withoutMain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), false);

		final var disabled = this.update(installed, withoutMain);

		assertFalse(this.hasComponent("meter0"));
		assertEquals("meter1", disabled.instance().properties.get("BRANCH_01_ID").getAsString());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertSinglePhaseBranchComponent("meter1", "Load", "ecoMain0", "CONSUMPTION_METERED", "L1", 0,
				5, false);

		final var withMain = disabled.instance().properties.deepCopy();
		withMain.addProperty(AppEcoMain.Property.HAS_MAIN.name(), true);
		final var enabled = this.update(disabled, withMain);

		assertEquals("meter0", enabled.instance().properties.get(AppEcoMain.Property.MAIN_ID.name()).getAsString());
		assertEquals("meter1", enabled.instance().properties.get("BRANCH_01_ID").getAsString());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
		this.assertSinglePhaseBranchComponent("meter1", "Load", "ecoMain0", "CONSUMPTION_METERED", "L1", 0,
				5, false);
	}

	@Test
	void testConnectionEditPreservesBranchIdAndOwnedCoreReference() throws Exception {
		final var installed = this.install("Device A", this.properties(true, branches(//
				singlePhaseBranch(null, "Load", "CONSUMPTION_NOT_METERED", phase("L2", "ECOSUB_2", 8, true)))));
		final var updatedProperties = installed.instance().properties.deepCopy();
		updatedProperties.addProperty(AppEcoMain.Property.IP.name(), "10.0.8.89");
		updatedProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "123456789012");

		final var updated = this.update(installed, updatedProperties);

		assertEquals("meter1", updated.instance().properties.get("BRANCH_01_ID").getAsString());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "10.0.8.89", "123456789012");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
		this.assertSinglePhaseBranchComponent("meter1", "Load", "ecoMain0", "CONSUMPTION_NOT_METERED", "L2", 2,
				8, true);
	}

	@Test
	void testDeletingAppRemovesEveryOwnedComponent() throws Exception {
		final var installed = this.install("Device A", this.properties(true, branches(//
				singlePhaseBranch(null, "Load", "CONSUMPTION_METERED", phase("L1", "ECOMAIN", 1, false)), //
				singlePhaseBranch(null, "PV", "PRODUCTION", phase("L3", "ECOSUB_3", 10, true)))));

		this.bundle.sut.handleDeleteAppInstanceRequest(ENGLISH_ADMIN,
				new DeleteAppInstance.Request(installed.instance().instanceId));

		assertTrue(this.bundle.sut.getInstantiatedApps().isEmpty());
		assertTrue(this.componentIds("Bridge.Modbus.Tcp").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Core").isEmpty());
		assertTrue(this.componentIds("Meter.enecess.ecoMain.Main").isEmpty());
		assertTrue(this.componentIds(BRANCH_FACTORY_ID).isEmpty());
	}

	@Test
	void testTwoInstancesKeepAllOwnedComponentsAndCoreReferencesIsolated() throws Exception {
		final var first = this.install("Device A", this.properties(true, branches(//
				singlePhaseBranch(null, "First branch", "CONSUMPTION_METERED",
						phase("L1", "ECOMAIN", 1, false)))));
		final var secondProperties = this.properties(true, branches(//
				singlePhaseBranch(null, "Second branch", "CONSUMPTION_METERED",
						phase("L1", "ECOMAIN", 1, false))));
		secondProperties.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.11");
		secondProperties.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "271828182845");

		final var second = this.install("Device B", secondProperties);

		assertEquals("modbus0", first.instance().properties.get(AppEcoMain.Property.MODBUS_ID.name()).getAsString());
		assertEquals("ecoMain0", first.instance().properties.get(AppEcoMain.Property.CORE_ID.name()).getAsString());
		assertEquals("meter0", first.instance().properties.get(AppEcoMain.Property.MAIN_ID.name()).getAsString());
		assertEquals("meter1", first.instance().properties.get("BRANCH_01_ID").getAsString());
		assertEquals("modbus1", second.instance().properties.get(AppEcoMain.Property.MODBUS_ID.name()).getAsString());
		assertEquals("ecoMain1", second.instance().properties.get(AppEcoMain.Property.CORE_ID.name()).getAsString());
		assertEquals("meter2", second.instance().properties.get(AppEcoMain.Property.MAIN_ID.name()).getAsString());
		assertEquals("meter3", second.instance().properties.get("BRANCH_01_ID").getAsString());
		this.assertCoreComponents("modbus0", "ecoMain0", "Device A", "192.0.2.10", "314159265358");
		this.assertMainComponent("meter0", "ecoMain0", "Device A");
		this.assertSinglePhaseBranchComponent("meter1", "First branch", "ecoMain0", "CONSUMPTION_METERED", "L1",
				0, 1, false);
		this.assertCoreComponents("modbus1", "ecoMain1", "Device B", "192.0.2.11", "271828182845");
		this.assertMainComponent("meter2", "ecoMain1", "Device B");
		this.assertSinglePhaseBranchComponent("meter3", "Second branch", "ecoMain1", "CONSUMPTION_METERED", "L1",
				0, 1, false);

		this.bundle.sut.handleDeleteAppInstanceRequest(ENGLISH_ADMIN,
				new DeleteAppInstance.Request(first.instance().instanceId));

		assertEquals(1, this.bundle.sut.getInstantiatedApps().size());
		assertEquals(second.instance().instanceId, this.bundle.sut.getInstantiatedApps().get(0).instanceId);
		assertEquals(List.of("modbus1"), this.componentIds("Bridge.Modbus.Tcp"));
		assertEquals(List.of("ecoMain1"), this.componentIds("Meter.enecess.ecoMain.Core"));
		assertEquals(List.of("meter2"), this.componentIds("Meter.enecess.ecoMain.Main"));
		assertEquals(List.of("meter3"), this.componentIds(BRANCH_FACTORY_ID));
		this.assertCoreComponents("modbus1", "ecoMain1", "Device B", "192.0.2.11", "271828182845");
		this.assertMainComponent("meter2", "ecoMain1", "Device B");
		this.assertSinglePhaseBranchComponent("meter3", "Second branch", "ecoMain1", "CONSUMPTION_METERED", "L1",
				0, 1, false);
	}

	static Stream<Arguments> invalidBranchConfigurations() {
		final var missingSource = singlePhaseBranch(null, "Missing source", "CONSUMPTION_METERED",
				phase("L1", "ECOMAIN", 1, false));
		missingSource.remove("SOURCE_1");
		return Stream.of(//
				arguments("forty-first branch", fortyOneBranches()), //
				arguments("missing active field", branches(missingSource)), //
				arguments("unsupported meter type", branches(//
						singlePhaseBranch(null, "Grid", "GRID", phase("L1", "ECOMAIN", 1, false)))), //
				arguments("out-of-range channel", branches(//
						singlePhaseBranch(null, "High channel", "CONSUMPTION_METERED",
								phase("L1", "ECOMAIN", 11, false)))), //
				arguments("repeated logical phase", branches(//
						threePhaseBranch(null, "Repeated phase", "CONSUMPTION_METERED", //
								phase("L1", "ECOMAIN", 1, false), //
								phase("L1", "ECOMAIN", 2, false), //
								phase("L3", "ECOMAIN", 3, false)))), //
				arguments("physical address repeated within branch", branches(//
						threePhaseBranch(null, "Repeated address", "CONSUMPTION_METERED", //
								phase("L1", "ECOMAIN", 1, false), //
								phase("L2", "ECOMAIN", 1, false), //
								phase("L3", "ECOMAIN", 3, false)))), //
				arguments("physical address repeated across branches", branches(//
						singlePhaseBranch(null, "First", "CONSUMPTION_METERED",
								phase("L1", "ECOSUB_3", 10, false)), //
						singlePhaseBranch(null, "Second", "PRODUCTION",
								phase("L2", "ECOSUB_3", 10, true)))));
	}

	static Stream<Arguments> invalidBranchUpdates() {
		return Stream.of(//
				invalidUpdate("forty-first branch", true, configured -> configured.add(//
						singlePhaseBranch(null, "Branch 41", "CONSUMPTION_METERED",
								phase("L1", "ECOMAIN", 1, false)))), //
				invalidUpdate("missing active field", false,
						configured -> configured.get(0).getAsJsonObject().remove("SOURCE_1")), //
				invalidUpdate("unsupported meter type", false,
						configured -> configured.get(0).getAsJsonObject().addProperty("TYPE", "GRID")), //
				invalidUpdate("out-of-range channel", false,
						configured -> configured.get(0).getAsJsonObject().addProperty("CHANNEL_1", 11)), //
				invalidUpdate("repeated logical phase", false,
						configured -> configured.get(0).getAsJsonObject().addProperty("PHASE_2", "L1")), //
				invalidUpdate("physical address repeated within branch", false,
						configured -> configured.get(0).getAsJsonObject().addProperty("CHANNEL_2", 1)), //
				invalidUpdate("physical address repeated across branches", false,
						configured -> configured.get(1).getAsJsonObject().addProperty("CHANNEL_1", 1)), //
				invalidUpdate("duplicate slot", false,
						configured -> configured.get(1).getAsJsonObject().addProperty("SLOT", 1)), //
				invalidUpdate("out-of-range slot", false,
						configured -> configured.get(0).getAsJsonObject().addProperty("SLOT", 41)));
	}

	private static Arguments invalidUpdate(String description, boolean maximumConfiguration,
			Consumer<JsonArray> mutation) {
		return arguments(description, maximumConfiguration, mutation);
	}

	private AddAppInstance.Response install(String alias, JsonObject properties) throws OpenemsNamedException {
		return this.bundle.sut.handleAddAppInstanceRequest(ENGLISH_ADMIN,
				new AddAppInstance.Request(this.app.getAppId(), "key", alias, properties));
	}

	private UpdateAppInstance.Response update(AddAppInstance.Response installed, JsonObject properties)
			throws OpenemsNamedException {
		return this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN, new UpdateAppInstance.Request(
				installed.instance().instanceId, installed.instance().alias, properties));
	}

	private UpdateAppInstance.Response update(UpdateAppInstance.Response installed, JsonObject properties)
			throws OpenemsNamedException {
		return this.bundle.sut.handleUpdateAppInstanceRequest(ENGLISH_ADMIN, new UpdateAppInstance.Request(
				installed.instance().instanceId, installed.instance().alias, properties));
	}

	private JsonObject properties(boolean hasMain, JsonArray branches) {
		return JsonUtils.buildJsonObject() //
				.addProperty(AppEcoMain.Property.IP.name(), "192.0.2.10") //
				.addProperty(AppEcoMain.Property.SERIAL_NUMBER.name(), "314159265358") //
				.addProperty(AppEcoMain.Property.HAS_MAIN.name(), hasMain) //
				.add("BRANCHES", branches) //
				.build();
	}

	private JsonObject branchAssistantField() {
		return this.branchAssistantField(ENGLISH_ADMIN);
	}

	private JsonObject branchAssistantField(User user) {
		return findField(this.app.getAppAssistant(user).fields, "BRANCHES");
	}

	private void assertCoreComponents(String modbusId, String coreId, String alias, String ip, String serialNumber)
			throws OpenemsNamedException {
		this.assertExactComponent(new EdgeConfig.Component(modbusId, "Communication with ecoMain", "Bridge.Modbus.Tcp",
				JsonUtils.buildJsonObject() //
						.addProperty("ip", ip) //
						.addProperty("port", 502) //
						.build()));
		this.assertExactComponent(new EdgeConfig.Component(coreId, alias, "Meter.enecess.ecoMain.Core",
				JsonUtils.buildJsonObject() //
						.addProperty("modbus.id", modbusId) //
						.addProperty("serialNumber", serialNumber) //
						.build()));
	}

	private void assertMainComponent(String mainId, String coreId, String alias) {
		this.assertExactComponent(new EdgeConfig.Component(mainId, alias, "Meter.enecess.ecoMain.Main",
				JsonUtils.buildJsonObject() //
						.addProperty("core.id", coreId) //
						.build()));
	}

	private void assertSinglePhaseBranchComponent(String id, String alias, String coreId, String type, String phase,
			int source, int channel, boolean invert) {
		this.assertExactComponent(new EdgeConfig.Component(id, alias, BRANCH_FACTORY_ID,
				JsonUtils.buildJsonObject() //
						.addProperty("core.id", coreId) //
						.addProperty("type", type) //
						.addProperty("phaseCount", "SINGLE_PHASE") //
						.addProperty("phase1", phase) //
						.addProperty("source1", source) //
						.addProperty("channel1", channel) //
						.addProperty("invert1", invert) //
						.build()));
	}

	private void assertSecondBranchComponent() {
		this.assertSinglePhaseBranchComponent("meter2", "Second", "ecoMain0", "CONSUMPTION_METERED", "L2", 0, 2,
				false);
	}

	private void assertValidUpdateBranchComponents() {
		this.assertExactComponent(new EdgeConfig.Component("meter1", "Existing A", BRANCH_FACTORY_ID,
				JsonUtils.buildJsonObject() //
						.addProperty("core.id", "ecoMain0") //
						.addProperty("type", "CONSUMPTION_METERED") //
						.addProperty("phaseCount", "THREE_PHASE") //
						.addProperty("phase1", "L1") //
						.addProperty("source1", 0) //
						.addProperty("channel1", 1) //
						.addProperty("invert1", false) //
						.addProperty("phase2", "L2") //
						.addProperty("source2", 0) //
						.addProperty("channel2", 2) //
						.addProperty("invert2", false) //
						.addProperty("phase3", "L3") //
						.addProperty("source3", 0) //
						.addProperty("channel3", 3) //
						.addProperty("invert3", false) //
						.build()));
		this.assertExactComponent(new EdgeConfig.Component("meter2", "Existing B", BRANCH_FACTORY_ID,
				JsonUtils.buildJsonObject() //
						.addProperty("core.id", "ecoMain0") //
						.addProperty("type", "PRODUCTION") //
						.addProperty("phaseCount", "THREE_PHASE") //
						.addProperty("phase1", "L1") //
						.addProperty("source1", 0) //
						.addProperty("channel1", 4) //
						.addProperty("invert1", true) //
						.addProperty("phase2", "L2") //
						.addProperty("source2", 0) //
						.addProperty("channel2", 5) //
						.addProperty("invert2", false) //
						.addProperty("phase3", "L3") //
						.addProperty("source3", 0) //
						.addProperty("channel3", 6) //
						.addProperty("invert3", true) //
						.build()));
	}

	private void assertMaximumAddressBlockComponents() {
		this.assertMaximumAddressBlock(1, 0);
		this.assertMaximumAddressBlock(11, 1);
		this.assertMaximumAddressBlock(21, 2);
		this.assertMaximumAddressBlock(31, 3);
	}

	private void assertMaximumAddressBlock(int firstSlot, int expectedSource) {
		for (var expectedChannel = 1; expectedChannel <= 10; expectedChannel++) {
			final var slot = firstSlot + expectedChannel - 1;
			this.assertSinglePhaseBranchComponent("meter" + slot, "Branch " + slot, "ecoMain0",
					"CONSUMPTION_METERED", "L1", expectedSource, expectedChannel, false);
		}
	}

	private void assertExactComponent(EdgeConfig.Component expected) {
		final var actual = this.component(expected.getId());
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getAlias(), actual.getAlias());
		assertEquals(expected.getFactoryId(), actual.getFactoryId());
		final var actualProperties = new TreeMap<>(actual.getProperties());
		actualProperties.remove("id");
		actualProperties.remove("service.factoryPid");
		assertEquals(expected.getProperties(), actualProperties);
	}

	private List<JsonObject> ownedComponentSnapshots() {
		return Stream.of("Bridge.Modbus.Tcp", "Meter.enecess.ecoMain.Core", "Meter.enecess.ecoMain.Main",
				BRANCH_FACTORY_ID) //
				.flatMap(factoryId -> this.componentIds(factoryId).stream()) //
				.sorted() //
				.map(componentId -> JsonUtils.buildJsonObject() //
						.addProperty("id", componentId) //
						.add("component", this.component(componentId).toJson(EdgeConfig.Component.JsonFormat.WITHOUT_CHANNELS)
								.deepCopy()) //
						.build()) //
				.toList();
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

	private static JsonObject phase(String phase, String source, int channel, boolean invert) {
		return JsonUtils.buildJsonObject() //
				.addProperty("PHASE", phase) //
				.addProperty("SOURCE", source) //
				.addProperty("CHANNEL", channel) //
				.addProperty("INVERT", invert) //
				.build();
	}

	private static JsonObject singlePhaseBranch(Integer slot, String alias, String type, JsonObject phase) {
		final var result = branchHeader(slot, alias, type, "SINGLE_PHASE");
		addPhase(result, 1, phase);
		return result;
	}

	private static JsonObject threePhaseBranch(Integer slot, String alias, String type, JsonObject phase1,
			JsonObject phase2, JsonObject phase3) {
		final var result = branchHeader(slot, alias, type, "THREE_PHASE");
		addPhase(result, 1, phase1);
		addPhase(result, 2, phase2);
		addPhase(result, 3, phase3);
		return result;
	}

	private static JsonObject branchHeader(Integer slot, String alias, String type, String phaseCount) {
		final var result = new JsonObject();
		if (slot != null) {
			result.addProperty("SLOT", slot);
		}
		result.addProperty("ALIAS", alias);
		result.addProperty("TYPE", type);
		result.addProperty("PHASE_COUNT", phaseCount);
		return result;
	}

	private static void addPhase(JsonObject branch, int index, JsonObject phase) {
		branch.add("PHASE_" + index, phase.get("PHASE").deepCopy());
		branch.add("SOURCE_" + index, phase.get("SOURCE").deepCopy());
		branch.add("CHANNEL_" + index, phase.get("CHANNEL").deepCopy());
		branch.add("INVERT_" + index, phase.get("INVERT").deepCopy());
	}

	private static JsonArray branches(JsonElement... branches) {
		final var result = new JsonArray();
		for (var branch : branches) {
			result.add(branch);
		}
		return result;
	}

	private static JsonArray allPhysicalAddresses() {
		final var result = new JsonArray();
		final var sources = List.of("ECOMAIN", "ECOSUB_1", "ECOSUB_2", "ECOSUB_3");
		var slot = 1;
		for (var source : sources) {
			for (var channel = 1; channel <= 10; channel++) {
				result.add(singlePhaseBranch(null, "Branch " + slot, "CONSUMPTION_METERED",
						phase("L1", source, channel, false)));
				slot++;
			}
		}
		return result;
	}

	private static JsonArray validUpdateBranches() {
		return branches(//
				threePhaseBranch(null, "Existing A", "CONSUMPTION_METERED", //
						phase("L1", "ECOMAIN", 1, false), //
						phase("L2", "ECOMAIN", 2, false), //
						phase("L3", "ECOMAIN", 3, false)), //
				threePhaseBranch(null, "Existing B", "PRODUCTION", //
						phase("L1", "ECOMAIN", 4, true), //
						phase("L2", "ECOMAIN", 5, false), //
						phase("L3", "ECOMAIN", 6, true)));
	}

	private static JsonArray fortyOneBranches() {
		final var result = allPhysicalAddresses();
		result.add(singlePhaseBranch(null, "Branch 41", "CONSUMPTION_METERED",
				phase("L1", "ECOMAIN", 1, false)));
		return result;
	}

	private static JsonArray branchFieldGroup(JsonObject branchField) {
		final var fieldArray = branchField.getAsJsonObject("fieldArray");
		assertNotNull(fieldArray);
		final var fieldGroup = fieldArray.getAsJsonArray("fieldGroup");
		assertNotNull(fieldGroup);
		return fieldGroup;
	}

	private static JsonObject findField(Iterable<JsonElement> fields, String key) {
		for (var field : fields) {
			final var fieldObject = field.getAsJsonObject();
			if (fieldObject.has("key") && key.equals(fieldObject.get("key").getAsString())) {
				return fieldObject;
			}
		}
		throw new AssertionError("No assistant field found for key [" + key + "]");
	}

	private static List<String> fieldKeys(JsonArray fields) {
		return fields.asList().stream() //
				.map(JsonElement::getAsJsonObject) //
				.map(field -> field.get("key").getAsString()) //
				.toList();
	}

	private static List<String> stringOptionValues(JsonObject field) {
		assertEquals("select", field.get("type").getAsString());
		return field.getAsJsonObject("templateOptions").getAsJsonArray("options").asList().stream() //
				.map(JsonElement::getAsJsonObject) //
				.map(option -> option.get("value").getAsString()) //
				.toList();
	}

	private static void assertChannelOptions(JsonObject field) {
		assertEquals("select", field.get("type").getAsString());
		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
				field.getAsJsonObject("templateOptions").getAsJsonArray("options").asList().stream() //
						.map(JsonElement::getAsJsonObject) //
						.map(option -> option.get("value").getAsInt()) //
						.toList());
	}

	private static boolean isRequired(JsonObject field) {
		final var required = field.getAsJsonObject("templateOptions").get("required");
		return required != null && required.getAsBoolean();
	}

	private static void assertNoDefaultValue(JsonObject field) {
		assertFalse(field.has("defaultValue"), () -> field.get("key").getAsString() + " must require user selection");
	}

	private static void assertOnlyVisibleForThreePhase(JsonObject field) {
		final var expressionProperties = field.getAsJsonObject("expressionProperties");
		assertNotNull(expressionProperties, () -> field.get("key").getAsString() + " has no visibility expression");
		final var hideExpression = expressionProperties.get("hide").getAsString();
		final var singlePhase = JsonUtils.buildJsonObject().addProperty("PHASE_COUNT", "SINGLE_PHASE").build();
		final var threePhase = JsonUtils.buildJsonObject().addProperty("PHASE_COUNT", "THREE_PHASE").build();
		assertTrue(ExpressionEvaluator.evaluateExpression(hideExpression, singlePhase));
		assertFalse(ExpressionEvaluator.evaluateExpression(hideExpression, threePhase));
	}

	private static JsonObject expectedMaxBranchesValidator() {
		return JsonUtils.buildJsonObject() //
				.addProperty("expressionString", MAX_BRANCHES_EXPRESSION) //
				.addProperty("message", "At most 40 branches may be configured.") //
				.build();
	}

	private static void assertExactValidator(JsonObject validator, String expression, String message) {
		assertNotNull(validator);
		assertEquals(expression, validator.get("expressionString").getAsString());
		assertEquals(message, validator.get("messageString").getAsString());
	}

	private static String descriptionOrEmpty(JsonObject field) {
		final var templateOptions = field.getAsJsonObject("templateOptions");
		if (templateOptions == null || !templateOptions.has("description")) {
			return "";
		}
		return templateOptions.get("description").getAsString();
	}
}
