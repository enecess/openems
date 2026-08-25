package io.openems.edge.app.meter.enecess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.session.Language;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.core.appmanager.dependency.aggregatetask.ComponentProperties;

class EcoMainBranchConfigurationTest {

	@Test
	void normalizeTreatsJavaNullAndJsonNullAsNoBranches() {
		assertEquals(new JsonArray(), EcoMainBranchConfiguration.normalize(null));
		assertEquals(new JsonArray(), EcoMainBranchConfiguration.normalize(JsonNull.INSTANCE));
	}

	@Test
	void normalizeAssignsFirstFreeSlotsToNewBranchesInArrayOrder() {
		final var input = branches(//
				threePhaseBranch(0, "First"), //
				threePhaseBranch(0, "Second"));

		final var normalized = EcoMainBranchConfiguration.normalize(input).getAsJsonArray();

		assertEquals(1, normalized.get(0).getAsJsonObject().get("SLOT").getAsInt());
		assertEquals(2, normalized.get(1).getAsJsonObject().get("SLOT").getAsInt());
	}

	@Test
	void normalizeFillsTheGapBetweenExistingSlots() {
		final var input = branches(//
				threePhaseBranch(1, "First"), //
				threePhaseBranch(0, "New"), //
				threePhaseBranch(3, "Third"));

		final var normalized = EcoMainBranchConfiguration.normalize(input).getAsJsonArray();

		assertEquals(2, normalized.get(1).getAsJsonObject().get("SLOT").getAsInt());
	}

	@Test
	void normalizePreservesSlotsWhenBranchesAreReordered() {
		final var input = branches(//
				threePhaseBranch(3, "Third"), //
				threePhaseBranch(1, "First"));

		final var normalized = EcoMainBranchConfiguration.normalize(input).getAsJsonArray();

		assertEquals(3, normalized.get(0).getAsJsonObject().get("SLOT").getAsInt());
		assertEquals(1, normalized.get(1).getAsJsonObject().get("SLOT").getAsInt());
	}

	@Test
	void normalizeReusesADeletedSlotForTheNextNewBranch() {
		final var input = branches(//
				threePhaseBranch(1, "First"), //
				threePhaseBranch(3, "Third"), //
				threePhaseBranch(0, "Replacement"));

		final var normalized = EcoMainBranchConfiguration.normalize(input).getAsJsonArray();

		assertEquals(2, normalized.get(2).getAsJsonObject().get("SLOT").getAsInt());
	}

	@Test
	void normalizeReplacesABlankAliasWithTheSlotBasedAlias() {
		final var input = branches(threePhaseBranch(2, " \t "));

		final var normalized = EcoMainBranchConfiguration.normalize(input).getAsJsonArray();

		assertEquals("Branch 2", normalized.get(0).getAsJsonObject().get("ALIAS").getAsString());
	}

	@Test
	void normalizeAddsOnlyTheDocumentedDefaults() {
		final var incomplete = new JsonObject();
		incomplete.addProperty("SLOT", 4);
		incomplete.addProperty("ALIAS", "Branch four");

		final var normalized = EcoMainBranchConfiguration.normalize(branches(incomplete)) //
				.getAsJsonArray().get(0).getAsJsonObject();

		assertEquals("CONSUMPTION_METERED", normalized.get("TYPE").getAsString());
		assertEquals("THREE_PHASE", normalized.get("PHASE_COUNT").getAsString());
		assertEquals("L1", normalized.get("PHASE_1").getAsString());
		assertEquals("L2", normalized.get("PHASE_2").getAsString());
		assertEquals("L3", normalized.get("PHASE_3").getAsString());
		assertFalse(normalized.get("INVERT_1").getAsBoolean());
		assertFalse(normalized.get("INVERT_2").getAsBoolean());
		assertFalse(normalized.get("INVERT_3").getAsBoolean());
		assertFalse(normalized.has("SOURCE_1"));
		assertFalse(normalized.has("SOURCE_2"));
		assertFalse(normalized.has("SOURCE_3"));
		assertFalse(normalized.has("CHANNEL_1"));
		assertFalse(normalized.has("CHANNEL_2"));
		assertFalse(normalized.has("CHANNEL_3"));
	}

	@Test
	void normalizeDoesNotRepairMalformedInputReservedForParsing() {
		final var nonArray = new JsonObject();
		nonArray.addProperty("BRANCHES", "not-an-array");
		assertEquals(nonArray, EcoMainBranchConfiguration.normalize(nonArray));

		final var nonObjectItem = branches(new JsonPrimitive("not-an-object"));
		assertEquals(nonObjectItem, EcoMainBranchConfiguration.normalize(nonObjectItem));

		final var zeroSlot = threePhaseBranch(1, "Zero");
		zeroSlot.addProperty("SLOT", 0);
		final var fractionalSlot = threePhaseBranch(1, "Fractional");
		fractionalSlot.addProperty("SLOT", 1.5);
		final var malformedSlots = branches(//
				zeroSlot, //
				fractionalSlot, //
				threePhaseBranch(41, "Out of range"), //
				threePhaseBranch(3, "Duplicate A"), //
				threePhaseBranch(3, "Duplicate B"));
		assertEquals(malformedSlots, EcoMainBranchConfiguration.normalize(malformedSlots));

		final var illegalEnums = branch(4, "Invalid enums", "GRID", "UNKNOWN_COUNT", //
				"UNKNOWN_PHASE", "UNKNOWN_SOURCE", 1, false, //
				"L2", "ECOSUB_1", 2, false, //
				"L3", "ECOSUB_2", 3, false);
		final var malformedEnums = branches(illegalEnums);
		assertEquals(malformedEnums, EcoMainBranchConfiguration.normalize(malformedEnums));
	}

	@Test
	void normalizeLeavesFortyFirstNewBranchForTooManyValidation() {
		final var input = new JsonArray();
		for (var index = 0; index < 41; index++) {
			input.add(threePhaseBranch(0, " "));
		}

		final var normalized = EcoMainBranchConfiguration.normalize(input).getAsJsonArray();
		assertFalse(normalized.get(40).getAsJsonObject().has("SLOT"));

		final var exception = assertThrows(OpenemsNamedException.class,
				() -> EcoMainBranchConfiguration.parse(normalized, Language.EN));
		assertEquals("At most 40 branches may be configured.", exception.getMessage());
	}

	@Test
	void parseMapsSinglePhaseL1ToExactComponentProperties() throws Exception {
		final var parsed = EcoMainBranchConfiguration.parse(branches(//
				branch(8, "Solar", "PRODUCTION", "SINGLE_PHASE", //
						"L1", "ECOSUB_1", 7, true)), Language.EN);

		final var properties = parsed.get(0).toComponentProperties("ecoMain0");

		assertEquals(ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
				.addProperty("core.id", "ecoMain0") //
				.addProperty("type", "PRODUCTION") //
				.addProperty("phaseCount", "SINGLE_PHASE") //
				.addProperty("phase1", "L1") //
				.addProperty("source1", 1) //
				.addProperty("channel1", 7) //
				.addProperty("invert1", true) //
				.build()), properties);
	}

	@Test
	void parseMapsNonContiguousThreePhaseChannelsToExactComponentProperties() throws Exception {
		final var parsed = EcoMainBranchConfiguration.parse(branches(//
				branch(5, "Workshop", "CONSUMPTION_METERED", "THREE_PHASE", //
						"L1", "ECOMAIN", 10, false, //
						"L2", "ECOSUB_2", 3, true, //
						"L3", "ECOSUB_3", 9, false)), Language.EN);

		final var properties = parsed.get(0).toComponentProperties("ecoMain0");

		assertEquals(ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
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
				.build()), properties);
	}

	@Test
	void parseAcceptsThreePhaseChannelsAcrossEcoMainEcoSub1AndEcoSub3() throws Exception {
		final var parsed = EcoMainBranchConfiguration.parse(branches(//
				branch(6, "Mixed sources", "PRODUCTION_AND_CONSUMPTION", "THREE_PHASE", //
						"L1", "ECOMAIN", 1, false, //
						"L2", "ECOSUB_1", 7, false, //
						"L3", "ECOSUB_3", 10, true)), Language.EN);

		final var properties = parsed.get(0).toComponentProperties("ecoMain4");

		assertEquals(ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
				.addProperty("core.id", "ecoMain4") //
				.addProperty("type", "PRODUCTION_AND_CONSUMPTION") //
				.addProperty("phaseCount", "THREE_PHASE") //
				.addProperty("phase1", "L1") //
				.addProperty("source1", 0) //
				.addProperty("channel1", 1) //
				.addProperty("invert1", false) //
				.addProperty("phase2", "L2") //
				.addProperty("source2", 1) //
				.addProperty("channel2", 7) //
				.addProperty("invert2", false) //
				.addProperty("phase3", "L3") //
				.addProperty("source3", 3) //
				.addProperty("channel3", 10) //
				.addProperty("invert3", true) //
				.build()), properties);
	}

	@ParameterizedTest
	@ValueSource(strings = { "PRODUCTION", "PRODUCTION_AND_CONSUMPTION", "CONSUMPTION_METERED",
			"CONSUMPTION_NOT_METERED" })
	void parseAcceptsEverySupportedMeterType(String type) throws Exception {
		final var parsed = EcoMainBranchConfiguration.parse(branches(//
				branch(1, "Supported type", type, "SINGLE_PHASE", //
						"L1", "ECOMAIN", 1, false)), Language.EN);

		final var properties = parsed.get(0).toComponentProperties("ecoMain0");

		assertEquals(ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
				.addProperty("core.id", "ecoMain0") //
				.addProperty("type", type) //
				.addProperty("phaseCount", "SINGLE_PHASE") //
				.addProperty("phase1", "L1") //
				.addProperty("source1", 0) //
				.addProperty("channel1", 1) //
				.addProperty("invert1", false) //
				.build()), properties);
	}

	@Test
	void parseAcceptsTheMaximumSlotForty() throws Exception {
		final var parsed = EcoMainBranchConfiguration.parse(branches(//
				singlePhaseBranch(40, "Maximum slot", "ECOSUB_3", 10)), Language.EN);

		final var properties = parsed.get(0).toComponentProperties("ecoMain0");

		assertEquals(1, parsed.size());
		assertEquals(ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
				.addProperty("core.id", "ecoMain0") //
				.addProperty("type", "CONSUMPTION_METERED") //
				.addProperty("phaseCount", "SINGLE_PHASE") //
				.addProperty("phase1", "L1") //
				.addProperty("source1", 3) //
				.addProperty("channel1", 10) //
				.addProperty("invert1", false) //
				.build()), properties);
	}

	@Test
	void parseAcceptsFortyBranchesUsingEveryPhysicalAddressExactlyOnce() throws Exception {
		final var parsed = EcoMainBranchConfiguration.parse(maximalBranchConfiguration(), Language.EN);

		final var lastProperties = parsed.get(39).toComponentProperties("ecoMain7");

		assertEquals(40, parsed.size());
		assertEquals(ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
				.addProperty("core.id", "ecoMain7") //
				.addProperty("type", "CONSUMPTION_METERED") //
				.addProperty("phaseCount", "SINGLE_PHASE") //
				.addProperty("phase1", "L1") //
				.addProperty("source1", 3) //
				.addProperty("channel1", 10) //
				.addProperty("invert1", false) //
				.build()), lastProperties);
	}

	@Test
	void parseDoesNotReadOrMapHiddenSinglePhaseSlotsTwoAndThree() throws Exception {
		final var singlePhase = branch(9, "Single phase", "CONSUMPTION_NOT_METERED", "SINGLE_PHASE", //
				"L1", "ECOMAIN", 4, false);
		singlePhase.add("PHASE_2", new JsonArray());
		singlePhase.addProperty("SOURCE_2", 99);
		singlePhase.addProperty("CHANNEL_2", "not-a-number");
		singlePhase.addProperty("INVERT_2", "not-a-boolean");
		singlePhase.addProperty("PHASE_3", "ALL");
		singlePhase.addProperty("SOURCE_3", "UNKNOWN_SOURCE");
		singlePhase.addProperty("CHANNEL_3", 0);
		singlePhase.add("INVERT_3", new JsonObject());

		final var parsed = EcoMainBranchConfiguration.parse(branches(singlePhase), Language.EN);
		final var properties = parsed.get(0).toComponentProperties("ecoMain2");

		assertEquals(ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
				.addProperty("core.id", "ecoMain2") //
				.addProperty("type", "CONSUMPTION_NOT_METERED") //
				.addProperty("phaseCount", "SINGLE_PHASE") //
				.addProperty("phase1", "L1") //
				.addProperty("source1", 0) //
				.addProperty("channel1", 4) //
				.addProperty("invert1", false) //
				.build()), properties);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidBranchConfigurations")
	void parseRejectsInvalidBranchConfigurations(String description, JsonElement input) {
		assertThrows(OpenemsNamedException.class,
				() -> EcoMainBranchConfiguration.parse(input, Language.EN), description);
	}

	@Test
	void parseReturnsLocalizedMessagesInEnglishAndGerman() {
		final var zeroSlot = singlePhaseBranch(1, "Invalid slot");
		zeroSlot.addProperty("SLOT", 0);
		final var input = branches(zeroSlot);

		final var english = assertThrows(OpenemsNamedException.class,
				() -> EcoMainBranchConfiguration.parse(input, Language.EN)).getMessage();
		final var german = assertThrows(OpenemsNamedException.class,
				() -> EcoMainBranchConfiguration.parse(input, Language.DE)).getMessage();

		assertEquals("Branch slot must be an integer between 1 and 40.", english);
		assertEquals("Der Branch-Slot muss eine Ganzzahl zwischen 1 und 40 sein.", german);
	}

	static Stream<Arguments> invalidBranchConfigurations() {
		final var fortyOneBranches = new JsonArray();
		for (var slot = 1; slot <= 41; slot++) {
			fortyOneBranches.add(singlePhaseBranch(slot, "Branch " + slot));
		}

		final var zeroSlot = singlePhaseBranch(1, "Zero slot");
		zeroSlot.addProperty("SLOT", 0);
		final var fractionalSlot = singlePhaseBranch(1, "Fractional slot");
		fractionalSlot.addProperty("SLOT", 1.5);

		return Stream.of(//
				arguments("BRANCHES is not an array", new JsonObject()), //
				arguments("array item is not an object", branches(new JsonPrimitive("invalid"))), //
				arguments("unknown nested property",
						branches(withProperty(singlePhaseBranch(1, "Unknown property"), "UNKNOWN", true))), //
				arguments("41 branches", fortyOneBranches), //
				arguments("SLOT is missing",
						branches(withoutProperty(singlePhaseBranch(1, "Missing slot"), "SLOT"))), //
				arguments("SLOT is not an integer", branches(fractionalSlot)), //
				arguments("SLOT is zero", branches(zeroSlot)), //
				arguments("SLOT is 41", branches(singlePhaseBranch(41, "Out of range"))), //
				arguments("SLOT is duplicated", branches(//
						singlePhaseBranch(2, "Duplicate A"), //
						singlePhaseBranch(2, "Duplicate B", "ECOMAIN", 2))), //
				arguments("TYPE is not a string",
						branches(withProperty(singlePhaseBranch(1, "Bad type"), "TYPE", 7))), //
				arguments("TYPE is unknown",
						branches(withProperty(singlePhaseBranch(1, "Bad type"), "TYPE", "UNKNOWN_TYPE"))), //
				arguments("TYPE GRID is unsupported",
						branches(withProperty(singlePhaseBranch(1, "Grid"), "TYPE", "GRID"))), //
				arguments("TYPE GRID_GENSET is unsupported",
						branches(withProperty(singlePhaseBranch(1, "Genset"), "TYPE", "GRID_GENSET"))), //
				arguments("TYPE MANAGED_CONSUMPTION_METERED is unsupported", branches(withProperty(//
						singlePhaseBranch(1, "Managed"), "TYPE", "MANAGED_CONSUMPTION_METERED"))), //
				arguments("PHASE_COUNT is not a string",
						branches(withProperty(singlePhaseBranch(1, "Bad count"), "PHASE_COUNT", 3))), //
				arguments("PHASE_COUNT is unknown", branches(withProperty(//
						singlePhaseBranch(1, "Bad count"), "PHASE_COUNT", "TWO_PHASE"))), //
				arguments("active PHASE_2 is missing",
						branches(withoutProperty(threePhaseBranch(1, "Missing phase"), "PHASE_2"))), //
				arguments("active PHASE_1 has the wrong JSON type",
						branches(withProperty(singlePhaseBranch(1, "Bad phase"), "PHASE_1", new JsonArray()))), //
				arguments("active SOURCE_2 is missing",
						branches(withoutProperty(threePhaseBranch(1, "Missing source"), "SOURCE_2"))), //
				arguments("active SOURCE_1 has the wrong JSON type",
						branches(withProperty(singlePhaseBranch(1, "Bad source"), "SOURCE_1", 0))), //
				arguments("active SOURCE_1 is unknown", branches(withProperty(//
						singlePhaseBranch(1, "Unknown source"), "SOURCE_1", "ECOSUB_4"))), //
				arguments("active CHANNEL_3 is missing",
						branches(withoutProperty(threePhaseBranch(1, "Missing channel"), "CHANNEL_3"))), //
				arguments("active CHANNEL_1 has the wrong JSON type", branches(withProperty(//
						singlePhaseBranch(1, "Bad channel"), "CHANNEL_1", "one"))), //
				arguments("active INVERT_2 is missing",
						branches(withoutProperty(threePhaseBranch(1, "Missing invert"), "INVERT_2"))), //
				arguments("active INVERT_3 has the wrong JSON type",
						branches(withProperty(threePhaseBranch(1, "Bad invert"), "INVERT_3", "false"))), //
				arguments("channel is zero",
						branches(singlePhaseBranch(1, "Zero channel", "ECOMAIN", 0))), //
				arguments("channel is 11",
						branches(singlePhaseBranch(1, "High channel", "ECOMAIN", 11))), //
				arguments("channel is fractional", branches(withProperty(//
						singlePhaseBranch(1, "Fractional channel"), "CHANNEL_1", 1.5))), //
				arguments("single phase uses ALL", branches(withProperty(//
						singlePhaseBranch(1, "All phase"), "PHASE_1", "ALL"))), //
				arguments("single phase uses an unknown phase", branches(withProperty(//
						singlePhaseBranch(1, "Unknown phase"), "PHASE_1", "UNKNOWN_PHASE"))), //
				arguments("three-phase logical phases repeat", branches(branch(1, "Repeated phase",
						"CONSUMPTION_METERED", "THREE_PHASE", //
						"L1", "ECOMAIN", 1, false, //
						"L1", "ECOMAIN", 2, false, //
						"L3", "ECOMAIN", 3, false))), //
				arguments("three-phase logical phases are not L1 L2 L3", branches(branch(1, "Incomplete phases",
						"CONSUMPTION_METERED", "THREE_PHASE", //
						"L1", "ECOMAIN", 1, false, //
						"L2", "ECOMAIN", 2, false, //
						"ALL", "ECOMAIN", 3, false))), //
				arguments("physical address repeats inside one branch", branches(branch(1, "Repeated address",
						"CONSUMPTION_METERED", "THREE_PHASE", //
						"L1", "ECOMAIN", 1, false, //
						"L2", "ECOMAIN", 1, false, //
						"L3", "ECOMAIN", 3, false))), //
				arguments("physical address repeats across branches", branches(//
						singlePhaseBranch(1, "First", "ECOSUB_3", 10), //
						singlePhaseBranch(2, "Second", "ECOSUB_3", 10))));
	}

	private static JsonObject branch(int slot, String alias, String type, String phaseCount, Object... phases) {
		final var result = new JsonObject();
		if (slot > 0) {
			result.addProperty("SLOT", slot);
		}
		result.addProperty("ALIAS", alias);
		result.addProperty("TYPE", type);
		result.addProperty("PHASE_COUNT", phaseCount);
		// phases: logical phase, source enum name, channel, invert
		for (var index = 0; index < phases.length / 4; index++) {
			final var offset = index * 4;
			final var suffix = "_" + (index + 1);
			result.addProperty("PHASE" + suffix, (String) phases[offset]);
			result.addProperty("SOURCE" + suffix, (String) phases[offset + 1]);
			result.addProperty("CHANNEL" + suffix, (Number) phases[offset + 2]);
			result.addProperty("INVERT" + suffix, (Boolean) phases[offset + 3]);
		}
		return result;
	}

	private static JsonObject singlePhaseBranch(int slot, String alias) {
		return singlePhaseBranch(slot, alias, "ECOMAIN", 1);
	}

	private static JsonObject singlePhaseBranch(int slot, String alias, String source, int channel) {
		return branch(slot, alias, "CONSUMPTION_METERED", "SINGLE_PHASE", //
				"L1", source, channel, false);
	}

	private static JsonObject threePhaseBranch(int slot, String alias) {
		return branch(slot, alias, "CONSUMPTION_METERED", "THREE_PHASE", //
				"L1", "ECOMAIN", 1, false, //
				"L2", "ECOMAIN", 2, false, //
				"L3", "ECOMAIN", 3, false);
	}

	private static JsonArray maximalBranchConfiguration() {
		final var result = new JsonArray();
		final var sources = new String[] { "ECOMAIN", "ECOSUB_1", "ECOSUB_2", "ECOSUB_3" };
		var slot = 1;
		for (var source : sources) {
			for (var channel = 1; channel <= 10; channel++) {
				result.add(singlePhaseBranch(slot, "Branch " + slot, source, channel));
				slot++;
			}
		}
		return result;
	}

	private static JsonArray branches(JsonElement... branches) {
		final var result = new JsonArray();
		for (var branch : branches) {
			result.add(branch);
		}
		return result;
	}

	private static JsonObject withoutProperty(JsonObject source, String property) {
		source.remove(property);
		return source;
	}

	private static JsonObject withProperty(JsonObject source, String property, Object value) {
		switch (value) {
		case Boolean booleanValue -> source.addProperty(property, booleanValue);
		case Number numberValue -> source.addProperty(property, numberValue);
		case String stringValue -> source.addProperty(property, stringValue);
		case JsonElement jsonValue -> source.add(property, jsonValue);
		default -> throw new IllegalArgumentException("Unsupported JSON fixture value");
		}
		return source;
	}

}
