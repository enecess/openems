package io.openems.edge.app.meter.enecess;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.session.Language;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.app.enums.Phase;
import io.openems.edge.core.appmanager.AbstractOpenemsApp;
import io.openems.edge.core.appmanager.TranslationUtil;
import io.openems.edge.core.appmanager.dependency.aggregatetask.ComponentProperties;

final class EcoMainBranchConfiguration {

	private static final int MAX_BRANCHES = 40;
	private static final int MAX_CHANNEL = 10;
	private static final String VALIDATION_KEY_PREFIX = "App.Meter.enecess.ecoMain.Branch.validation.";
	private static final Set<String> ALLOWED_PROPERTIES = Arrays.stream(EcoMainBranchProperty.values()) //
			.map(EcoMainBranchProperty::name) //
			.collect(Collectors.toUnmodifiableSet());

	record SourceAddress(EcoMainSource source, int channel) {
	}

	record PhaseSlot(Phase phase, SourceAddress address, boolean invert) {
	}

	private record BranchDefinition(JsonObject json, int slot, String alias, EcoMainBranchMeterType type,
			EcoMainPhaseCount phaseCount) {
	}

	private record UsedAddress(int slot, Phase phase) {
	}

	private final int slot;
	private final String alias;
	private final EcoMainBranchMeterType type;
	private final EcoMainPhaseCount phaseCount;
	private final List<PhaseSlot> phases;

	private EcoMainBranchConfiguration(int slot, String alias, EcoMainBranchMeterType type,
			EcoMainPhaseCount phaseCount, List<PhaseSlot> phases) {
		this.slot = slot;
		this.alias = alias;
		this.type = type;
		this.phaseCount = phaseCount;
		this.phases = List.copyOf(phases);
	}

	static JsonElement normalize(JsonElement raw) {
		if (raw == null || raw.isJsonNull()) {
			return new JsonArray();
		}
		if (!raw.isJsonArray()) {
			return raw.deepCopy();
		}

		final var reservedSlots = new boolean[MAX_BRANCHES + 1];
		for (var element : raw.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			final var slot = integerOrNull(element.getAsJsonObject().get(EcoMainBranchProperty.SLOT.name()));
			if (slot != null && slot >= 1 && slot <= MAX_BRANCHES) {
				reservedSlots[slot] = true;
			}
		}

		final var result = new JsonArray();
		var nextFreeSlot = 1;
		for (var element : raw.getAsJsonArray()) {
			final var copy = element.deepCopy();
			result.add(copy);
			if (!copy.isJsonObject()) {
				continue;
			}

			final var branch = copy.getAsJsonObject();
			if (!branch.has(EcoMainBranchProperty.SLOT.name())) {
				while (nextFreeSlot <= MAX_BRANCHES && reservedSlots[nextFreeSlot]) {
					nextFreeSlot++;
				}
				if (nextFreeSlot <= MAX_BRANCHES) {
					branch.addProperty(EcoMainBranchProperty.SLOT.name(), nextFreeSlot);
					reservedSlots[nextFreeSlot] = true;
					nextFreeSlot++;
				}
			}

			final var slot = integerOrNull(branch.get(EcoMainBranchProperty.SLOT.name()));
			if (slot != null && slot >= 1 && slot <= MAX_BRANCHES) {
				normalizeAlias(branch, slot);
			}
			addDefault(branch, EcoMainBranchProperty.TYPE, EcoMainBranchMeterType.CONSUMPTION_METERED.name());
			addDefault(branch, EcoMainBranchProperty.PHASE_COUNT, EcoMainPhaseCount.THREE_PHASE.name());

			final var activePhaseCount = isSinglePhase(branch) ? 1 : 3;
			for (var index = 1; index <= activePhaseCount; index++) {
				addDefault(branch, phaseProperty(index), defaultPhase(index).name());
				addDefault(branch, invertProperty(index), false);
			}
		}
		return result;
	}

	static List<EcoMainBranchConfiguration> parse(JsonElement raw, Language language) throws OpenemsNamedException {
		if (raw == null || !raw.isJsonArray()) {
			throw translatedError(language, "structureInvalid");
		}

		final var branchObjects = new ArrayList<JsonObject>();
		for (var element : raw.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				throw translatedError(language, "structureInvalid");
			}
			final var branch = element.getAsJsonObject();
			if (!ALLOWED_PROPERTIES.containsAll(branch.keySet())) {
				throw translatedError(language, "structureInvalid");
			}
			branchObjects.add(branch);
		}
		if (branchObjects.size() > MAX_BRANCHES) {
			throw translatedError(language, "tooMany", MAX_BRANCHES);
		}
		for (var branch : branchObjects) {
			if (!hasUsableAlias(branch)) {
				throw translatedError(language, "structureInvalid");
			}
		}

		final var slots = new ArrayList<Integer>();
		final var usedSlots = new HashSet<Integer>();
		for (var branch : branchObjects) {
			final var slot = requireInteger(branch, EcoMainBranchProperty.SLOT, language);
			if (slot < 1 || slot > MAX_BRANCHES) {
				throw translatedError(language, "slotInvalid");
			}
			if (!usedSlots.add(slot)) {
				throw translatedError(language, "slotDuplicate", slot);
			}
			slots.add(slot);
		}

		final var definitions = new ArrayList<BranchDefinition>();
		for (var index = 0; index < branchObjects.size(); index++) {
			final var branch = branchObjects.get(index);
			final var type = requireEnum(branch, EcoMainBranchProperty.TYPE, EcoMainBranchMeterType.class, language);
			final var phaseCount = requireEnum(branch, EcoMainBranchProperty.PHASE_COUNT, EcoMainPhaseCount.class,
					language);
			definitions.add(new BranchDefinition(branch, slots.get(index),
					branch.get(EcoMainBranchProperty.ALIAS.name()).getAsString(), type, phaseCount));
		}

		final var phasesByBranch = new ArrayList<List<PhaseSlot>>();
		for (var definition : definitions) {
			final var phases = new ArrayList<PhaseSlot>();
			final var activePhaseCount = definition.phaseCount() == EcoMainPhaseCount.SINGLE_PHASE ? 1 : 3;
			for (var index = 1; index <= activePhaseCount; index++) {
				final var phase = requireEnum(definition.json(), phaseProperty(index), Phase.class, language);
				if (phase == Phase.ALL) {
					throw translatedError(language, "phaseInvalid");
				}
				final var source = requireEnum(definition.json(), sourceProperty(index), EcoMainSource.class,
						language);
				final var channel = requireInteger(definition.json(), channelProperty(index), language);
				if (channel < 1 || channel > MAX_CHANNEL) {
					throw translatedError(language, "channelInvalid", MAX_CHANNEL);
				}
				final var invert = requireBoolean(definition.json(), invertProperty(index), language);
				phases.add(new PhaseSlot(phase, new SourceAddress(source, channel), invert));
			}
			phasesByBranch.add(List.copyOf(phases));
		}

		for (var index = 0; index < definitions.size(); index++) {
			final var definition = definitions.get(index);
			final var phases = phasesByBranch.get(index);
			final var logicalPhases = phases.stream() //
					.map(PhaseSlot::phase) //
					.collect(Collectors.toSet());
			if (definition.phaseCount() == EcoMainPhaseCount.THREE_PHASE
					&& !logicalPhases.equals(EnumSet.of(Phase.L1, Phase.L2, Phase.L3))) {
				throw translatedError(language, "phasesUnique", definition.slot());
			}
			if (phases.stream().map(PhaseSlot::address).distinct().count() != phases.size()) {
				throw translatedError(language, "addressesUnique", definition.slot());
			}
		}

		final Map<SourceAddress, UsedAddress> globallyUsedAddresses = new HashMap<>();
		for (var index = 0; index < definitions.size(); index++) {
			for (var phase : phasesByBranch.get(index)) {
				final var previous = globallyUsedAddresses.putIfAbsent(phase.address(),
						new UsedAddress(definitions.get(index).slot(), phase.phase()));
				if (previous != null) {
					throw translatedError(language, "addressInUse", phase.address().source().getTranslation(language),
							phase.address().channel(), previous.slot(), previous.phase().name());
				}
			}
		}

		final var result = new ArrayList<EcoMainBranchConfiguration>();
		for (var index = 0; index < definitions.size(); index++) {
			final var definition = definitions.get(index);
			result.add(new EcoMainBranchConfiguration(definition.slot(), definition.alias(), definition.type(),
					definition.phaseCount(), phasesByBranch.get(index)));
		}
		return List.copyOf(result);
	}

	int slot() {
		return this.slot;
	}

	String alias() {
		return this.alias;
	}

	ComponentProperties toComponentProperties(String coreId) {
		final var properties = JsonUtils.buildJsonObject() //
				.addProperty("core.id", coreId) //
				.addProperty("type", this.type.name()) //
				.addProperty("phaseCount", this.phaseCount.name());
		for (var index = 0; index < this.phases.size(); index++) {
			final var suffix = Integer.toString(index + 1);
			final var phase = this.phases.get(index);
			properties.addProperty("phase" + suffix, phase.phase().name()) //
					.addProperty("source" + suffix, phase.address().source().source()) //
					.addProperty("channel" + suffix, phase.address().channel()) //
					.addProperty("invert" + suffix, phase.invert());
		}
		return ComponentProperties.fromJson(properties.build());
	}

	private static void normalizeAlias(JsonObject branch, int slot) {
		final var propertyName = EcoMainBranchProperty.ALIAS.name();
		final var alias = branch.get(propertyName);
		if (alias == null || alias.isJsonPrimitive() && alias.getAsJsonPrimitive().isString()
				&& alias.getAsString().isBlank()) {
			branch.addProperty(propertyName, "Branch " + slot);
		}
	}

	private static boolean isSinglePhase(JsonObject branch) {
		final var value = branch.get(EcoMainBranchProperty.PHASE_COUNT.name());
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				&& EcoMainPhaseCount.SINGLE_PHASE.name().equals(value.getAsString());
	}

	private static void addDefault(JsonObject branch, EcoMainBranchProperty property, String value) {
		if (!branch.has(property.name())) {
			branch.addProperty(property.name(), value);
		}
	}

	private static void addDefault(JsonObject branch, EcoMainBranchProperty property, boolean value) {
		if (!branch.has(property.name())) {
			branch.addProperty(property.name(), value);
		}
	}

	private static boolean hasUsableAlias(JsonObject branch) {
		final var alias = branch.get(EcoMainBranchProperty.ALIAS.name());
		return alias != null && alias.isJsonPrimitive() && alias.getAsJsonPrimitive().isString()
				&& !alias.getAsString().isBlank();
	}

	private static int requireInteger(JsonObject branch, EcoMainBranchProperty property, Language language)
			throws OpenemsNamedException {
		final var value = integerOrNull(branch.get(property.name()));
		if (value == null) {
			throw translatedError(language, switch (property) {
			case SLOT -> "slotInvalid";
			case CHANNEL_1, CHANNEL_2, CHANNEL_3 -> "channelInvalid";
			default -> "structureInvalid";
			});
		}
		return value;
	}

	private static Integer integerOrNull(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			return new BigDecimal(element.getAsString()).intValueExact();
		} catch (ArithmeticException | NumberFormatException e) {
			return null;
		}
	}

	private static <E extends Enum<E>> E requireEnum(JsonObject branch, EcoMainBranchProperty property,
			Class<E> type, Language language) throws OpenemsNamedException {
		final var element = branch.get(property.name());
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw translatedError(language, invalidEnumKey(property));
		}
		try {
			return Enum.valueOf(type, element.getAsString());
		} catch (IllegalArgumentException e) {
			throw translatedError(language, invalidEnumKey(property));
		}
	}

	private static String invalidEnumKey(EcoMainBranchProperty property) {
		return switch (property) {
		case TYPE -> "typeInvalid";
		case PHASE_COUNT -> "phaseCountInvalid";
		case PHASE_1, PHASE_2, PHASE_3 -> "phaseInvalid";
		case SOURCE_1, SOURCE_2, SOURCE_3 -> "sourceInvalid";
		default -> "structureInvalid";
		};
	}

	private static boolean requireBoolean(JsonObject branch, EcoMainBranchProperty property, Language language)
			throws OpenemsNamedException {
		final var element = branch.get(property.name());
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw translatedError(language, "structureInvalid");
		}
		return element.getAsBoolean();
	}

	private static EcoMainBranchProperty phaseProperty(int index) {
		return switch (index) {
		case 1 -> EcoMainBranchProperty.PHASE_1;
		case 2 -> EcoMainBranchProperty.PHASE_2;
		case 3 -> EcoMainBranchProperty.PHASE_3;
		default -> throw new IllegalArgumentException("Invalid phase index: " + index);
		};
	}

	private static Phase defaultPhase(int index) {
		return switch (index) {
		case 1 -> Phase.L1;
		case 2 -> Phase.L2;
		case 3 -> Phase.L3;
		default -> throw new IllegalArgumentException("Invalid phase index: " + index);
		};
	}

	private static EcoMainBranchProperty sourceProperty(int index) {
		return switch (index) {
		case 1 -> EcoMainBranchProperty.SOURCE_1;
		case 2 -> EcoMainBranchProperty.SOURCE_2;
		case 3 -> EcoMainBranchProperty.SOURCE_3;
		default -> throw new IllegalArgumentException("Invalid phase index: " + index);
		};
	}

	private static EcoMainBranchProperty channelProperty(int index) {
		return switch (index) {
		case 1 -> EcoMainBranchProperty.CHANNEL_1;
		case 2 -> EcoMainBranchProperty.CHANNEL_2;
		case 3 -> EcoMainBranchProperty.CHANNEL_3;
		default -> throw new IllegalArgumentException("Invalid phase index: " + index);
		};
	}

	private static EcoMainBranchProperty invertProperty(int index) {
		return switch (index) {
		case 1 -> EcoMainBranchProperty.INVERT_1;
		case 2 -> EcoMainBranchProperty.INVERT_2;
		case 3 -> EcoMainBranchProperty.INVERT_3;
		default -> throw new IllegalArgumentException("Invalid phase index: " + index);
		};
	}

	private static OpenemsException translatedError(Language language, String key, Object... parameters) {
		final var bundle = AbstractOpenemsApp.getTranslationBundle(language);
		return new OpenemsException(TranslationUtil.getTranslation(bundle, VALIDATION_KEY_PREFIX + key, parameters));
	}
}
