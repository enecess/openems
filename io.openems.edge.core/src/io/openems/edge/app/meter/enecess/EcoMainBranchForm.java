package io.openems.edge.app.meter.enecess;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

import com.google.gson.JsonArray;

import io.openems.common.session.Language;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.app.enums.OptionsFactory;
import io.openems.edge.app.enums.Phase;
import io.openems.edge.core.appmanager.TranslationUtil;
import io.openems.edge.core.appmanager.formly.JsonFormlyUtil;
import io.openems.edge.core.appmanager.formly.builder.FormlyBuilder;
import io.openems.edge.core.appmanager.formly.builder.RepeatBuilder;
import io.openems.edge.core.appmanager.formly.expression.BooleanExpression;
import io.openems.edge.core.appmanager.formly.expression.StringExpression;

final class EcoMainBranchForm {

	private static final String TRANSLATION_PREFIX = "App.Meter.enecess.ecoMain.Branch.";
	private static final BooleanExpression THREE_PHASE = new BooleanExpression("model.PHASE_COUNT == 'THREE_PHASE'");
	private static final BooleanExpression PHASES_UNIQUE = new BooleanExpression(//
			"(() => { const branches = control.value ?? []; " //
					+ "return branches.every(branch => branch.PHASE_COUNT != 'THREE_PHASE' || " //
					+ "(new Set([branch.PHASE_1, branch.PHASE_2, branch.PHASE_3]).size == 3 && " //
					+ "['L1', 'L2', 'L3'].every(phase => " //
					+ "[branch.PHASE_1, branch.PHASE_2, branch.PHASE_3].includes(phase)))); })()");
	private static final BooleanExpression ADDRESS_UNIQUE = new BooleanExpression("(() => { " //
			+ "const branches = control.value ?? []; const addresses = new Set(); " //
			+ "return branches.every(branch => " //
			+ "(branch.PHASE_COUNT == 'SINGLE_PHASE' ? [1] : [1, 2, 3]).every(phaseIndex => { " //
			+ "const source = branch[`SOURCE_${phaseIndex}`]; " //
			+ "const channel = branch[`CHANNEL_${phaseIndex}`]; " //
			+ "if (source == null || channel == null) return true; " //
			+ "const address = `${source}:${channel}`; " //
			+ "if (addresses.has(address)) return false; addresses.add(address); return true; })); " //
			+ "})()");

	static void configure(RepeatBuilder field, Language language, ResourceBundle bundle) {
		field.setAddText(translate(bundle, "add")) //
				.setDescription(translate(bundle, "manualSelectionHelp")) //
				.setCustomValidation("maxBranches", new BooleanExpression("(control.value ?? []).length <= 40"),
						translate(bundle, "validation.tooMany", 40)) //
				.setCustomValidation("phasesUnique", PHASES_UNIQUE,
						new StringExpression(translate(bundle, "validation.phasesUnique.formly")), null) //
				.setCustomValidation("addressUnique", ADDRESS_UNIQUE,
						new StringExpression(translate(bundle, "validation.addressInUse.formly")), null) //
				.setFieldArray(JsonUtils.buildJsonObject() //
						.add("fieldGroup", JsonUtils.buildJsonArray() //
								.add(input(EcoMainBranchProperty.SLOT, bundle).hide(true).build()) //
								.add(input(EcoMainBranchProperty.ALIAS, bundle).build()) //
								.add(JsonFormlyUtil.buildSelectFromNameable(EcoMainBranchProperty.TYPE) //
										.setLabel(translate(bundle, "type.label")) //
										.setOptions(OptionsFactory.of(EcoMainBranchMeterType.class), language) //
										.setDefaultValue(EcoMainBranchMeterType.CONSUMPTION_METERED.name()) //
										.isRequired(true) //
										.build()) //
								.add(JsonFormlyUtil.buildSelectFromNameable(EcoMainBranchProperty.PHASE_COUNT) //
										.setLabel(translate(bundle, "phaseCount.label")) //
										.setOptions(OptionsFactory.of(EcoMainPhaseCount.class), language) //
										.setDefaultValue(EcoMainPhaseCount.THREE_PHASE.name()) //
										.isRequired(true) //
										.build()) //
								.add(phase(1, Phase.L1, language, bundle).build()) //
								.add(source(1, language, bundle).build()) //
								.add(channel(1, bundle).build()) //
								.add(invert(1, bundle).build()) //
								.add(phase(2, Phase.L2, language, bundle).onlyShowIf(THREE_PHASE).build()) //
								.add(source(2, language, bundle).onlyShowIf(THREE_PHASE).build()) //
								.add(channel(2, bundle).onlyShowIf(THREE_PHASE).build()) //
								.add(invert(2, bundle).onlyShowIfWithoutRequired(THREE_PHASE).build()) //
								.add(phase(3, Phase.L3, language, bundle).onlyShowIf(THREE_PHASE).build()) //
								.add(source(3, language, bundle).onlyShowIf(THREE_PHASE).build()) //
								.add(channel(3, bundle).onlyShowIf(THREE_PHASE).build()) //
								.add(invert(3, bundle).onlyShowIfWithoutRequired(THREE_PHASE).build()) //
								.build()) //
						.build());
		field.build().add("wrappers", JsonUtils.buildJsonArray().add("form-field").build());
	}

	private static FormlyBuilder<?> input(EcoMainBranchProperty property, ResourceBundle bundle) {
		return JsonFormlyUtil.buildInputFromNameable(property).setLabel(translate(bundle, labelKey(property)));
	}

	private static FormlyBuilder<?> phase(int index, Phase defaultValue, Language language, ResourceBundle bundle) {
		return JsonFormlyUtil.buildSelectFromNameable(phaseProperty(index)) //
				.setLabel(translate(bundle, "phase.label", index)) //
				.setOptions(OptionsFactory.of(Phase.class, Phase.ALL), language) //
				.setDefaultValue(defaultValue.name()) //
				.isRequired(true);
	}

	private static FormlyBuilder<?> source(int index, Language language, ResourceBundle bundle) {
		return JsonFormlyUtil.buildSelectFromNameable(sourceProperty(index)) //
				.setLabel(translate(bundle, "source.label", index)) //
				.setDescription(translate(bundle, "manualSelectionHelp")) //
				.setOptions(OptionsFactory.of(EcoMainSource.class), language) //
				.isRequired(true);
	}

	private static FormlyBuilder<?> channel(int index, ResourceBundle bundle) {
		return JsonFormlyUtil.buildSelectFromNameable(channelProperty(index)) //
				.setLabel(translate(bundle, "channel.label", index)) //
				.setDescription(translate(bundle, "manualSelectionHelp")) //
				.setOptions(channelOptions()) //
				.isRequired(true);
	}

	private static FormlyBuilder<?> invert(int index, ResourceBundle bundle) {
		return JsonFormlyUtil.buildCheckboxFromNameable(invertProperty(index)) //
				.setLabel(translate(bundle, "invert.label", index)) //
				.setDefaultValue(false);
	}

	private static JsonArray channelOptions() {
		return IntStream.rangeClosed(1, 10) //
				.mapToObj(channel -> Map.entry(Integer.toString(channel), channel)) //
				.map(option -> JsonUtils.buildJsonObject() //
						.addProperty("label", option.getKey()) //
						.addProperty("value", option.getValue()) //
						.build()) //
				.collect(JsonUtils.toJsonArray());
	}

	private static EcoMainBranchProperty phaseProperty(int index) {
		return EcoMainBranchProperty.valueOf("PHASE_" + index);
	}

	private static EcoMainBranchProperty sourceProperty(int index) {
		return EcoMainBranchProperty.valueOf("SOURCE_" + index);
	}

	private static EcoMainBranchProperty channelProperty(int index) {
		return EcoMainBranchProperty.valueOf("CHANNEL_" + index);
	}

	private static EcoMainBranchProperty invertProperty(int index) {
		return EcoMainBranchProperty.valueOf("INVERT_" + index);
	}

	private static String labelKey(EcoMainBranchProperty property) {
		return switch (property) {
		case SLOT -> "slot.label";
		case ALIAS -> "alias.label";
		default -> throw new IllegalArgumentException("No input label for " + property);
		};
	}

	private static String translate(ResourceBundle bundle, String key, Object... parameters) {
		return TranslationUtil.getTranslation(bundle, TRANSLATION_PREFIX + key, parameters);
	}

	private EcoMainBranchForm() {
	}
}
