package io.openems.edge.app.meter.enecess;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.function.ThrowingTriFunction;
import io.openems.common.session.Language;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.app.common.props.CommonProps;
import io.openems.edge.app.meter.enecess.AppEcoMain.Property;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.core.appmanager.AbstractOpenemsApp;
import io.openems.edge.core.appmanager.AbstractOpenemsAppWithProps;
import io.openems.edge.core.appmanager.AppConfiguration;
import io.openems.edge.core.appmanager.AppDef;
import io.openems.edge.core.appmanager.AppManagerUtil;
import io.openems.edge.core.appmanager.AppManagerUtilSupplier;
import io.openems.edge.core.appmanager.ComponentUtil;
import io.openems.edge.core.appmanager.ConfigurationTarget;
import io.openems.edge.core.appmanager.OpenemsApp;
import io.openems.edge.core.appmanager.OpenemsAppCardinality;
import io.openems.edge.core.appmanager.OpenemsAppCategory;
import io.openems.edge.core.appmanager.TranslationUtil;
import io.openems.edge.core.appmanager.Type;
import io.openems.edge.core.appmanager.Type.Parameter;
import io.openems.edge.core.appmanager.Type.Parameter.BundleParameter;
import io.openems.edge.core.appmanager.dependency.Tasks;
import io.openems.edge.core.appmanager.dependency.aggregatetask.ComponentDef;
import io.openems.edge.core.appmanager.dependency.aggregatetask.ComponentProperties;
import io.openems.edge.core.appmanager.formly.JsonFormlyUtil;
import io.openems.edge.core.appmanager.formly.enums.Validation;

/**
 * Describes an App for installing an enecess ecoMain device.
 */
@Component(name = AppEcoMain.APP_ID)
public final class AppEcoMain extends AbstractOpenemsAppWithProps<AppEcoMain, Property, Parameter.BundleParameter>
		implements OpenemsApp, AppManagerUtilSupplier {

	public static final String APP_ID = "App.Meter.enecess.ecoMain";
	private final AppManagerUtil appManagerUtil;

	public enum Property implements Type<Property, AppEcoMain, Parameter.BundleParameter> {
		// Component IDs
		MODBUS_ID(AppDef.componentId("modbus0")), //
		CORE_ID(AppDef.<AppEcoMain, Property, Parameter.BundleParameter>componentId("ecoMain0") //
				.appendIsAllowedToEdit((app, property, l, parameter, user) -> false)), //
		MAIN_ID(AppDef.componentId("meter0")), //
		BRANCH_01_ID(AppDef.componentId("meter1")), //
		BRANCH_02_ID(AppDef.componentId("meter2")), //
		BRANCH_03_ID(AppDef.componentId("meter3")), //
		BRANCH_04_ID(AppDef.componentId("meter4")), //
		BRANCH_05_ID(AppDef.componentId("meter5")), //
		BRANCH_06_ID(AppDef.componentId("meter6")), //
		BRANCH_07_ID(AppDef.componentId("meter7")), //
		BRANCH_08_ID(AppDef.componentId("meter8")), //
		BRANCH_09_ID(AppDef.componentId("meter9")), //
		BRANCH_10_ID(AppDef.componentId("meter10")), //
		BRANCH_11_ID(AppDef.componentId("meter11")), //
		BRANCH_12_ID(AppDef.componentId("meter12")), //
		BRANCH_13_ID(AppDef.componentId("meter13")), //
		BRANCH_14_ID(AppDef.componentId("meter14")), //
		BRANCH_15_ID(AppDef.componentId("meter15")), //
		BRANCH_16_ID(AppDef.componentId("meter16")), //
		BRANCH_17_ID(AppDef.componentId("meter17")), //
		BRANCH_18_ID(AppDef.componentId("meter18")), //
		BRANCH_19_ID(AppDef.componentId("meter19")), //
		BRANCH_20_ID(AppDef.componentId("meter20")), //
		BRANCH_21_ID(AppDef.componentId("meter21")), //
		BRANCH_22_ID(AppDef.componentId("meter22")), //
		BRANCH_23_ID(AppDef.componentId("meter23")), //
		BRANCH_24_ID(AppDef.componentId("meter24")), //
		BRANCH_25_ID(AppDef.componentId("meter25")), //
		BRANCH_26_ID(AppDef.componentId("meter26")), //
		BRANCH_27_ID(AppDef.componentId("meter27")), //
		BRANCH_28_ID(AppDef.componentId("meter28")), //
		BRANCH_29_ID(AppDef.componentId("meter29")), //
		BRANCH_30_ID(AppDef.componentId("meter30")), //
		BRANCH_31_ID(AppDef.componentId("meter31")), //
		BRANCH_32_ID(AppDef.componentId("meter32")), //
		BRANCH_33_ID(AppDef.componentId("meter33")), //
		BRANCH_34_ID(AppDef.componentId("meter34")), //
		BRANCH_35_ID(AppDef.componentId("meter35")), //
		BRANCH_36_ID(AppDef.componentId("meter36")), //
		BRANCH_37_ID(AppDef.componentId("meter37")), //
		BRANCH_38_ID(AppDef.componentId("meter38")), //
		BRANCH_39_ID(AppDef.componentId("meter39")), //
		BRANCH_40_ID(AppDef.componentId("meter40")), //

		// Properties
		ALIAS(AppDef.copyOfGeneric(CommonProps.alias())), //
		IP(AppDef.copyOfGeneric(CommonProps.defaultDef(), def -> def //
				.setTranslatedLabel("communication.ipAddress") //
				.setTranslatedDescription("App.Meter.ip.description") //
				.setRequired(true) //
				.setField(JsonFormlyUtil::buildInputFromNameable,
						(app, property, l, parameter, field) -> field.setValidation(Validation.IP)))), //
		SERIAL_NUMBER(AppDef.copyOfGeneric(CommonProps.defaultDef(), def -> def //
				.setTranslatedLabel("App.Meter.enecess.ecoMain.serialNumber.label") //
				.setTranslatedDescription("App.Meter.enecess.ecoMain.serialNumber.description") //
				.setRequired(true) //
				.setField(JsonFormlyUtil::buildInputFromNameable,
						(app, property, l, parameter, field) -> field.setValidation("^[0-9]{12}$",
								TranslationUtil.getTranslation(parameter.bundle(),
										"App.Meter.enecess.ecoMain.serialNumber.invalid"))))), //
		HAS_MAIN(AppDef.copyOfGeneric(CommonProps.defaultDef(), def -> def //
				.setTranslatedLabel("App.Meter.enecess.ecoMain.hasMain.label") //
				.setTranslatedDescription("App.Meter.enecess.ecoMain.hasMain.description") //
				.setDefaultValue(true) //
				.setField(JsonFormlyUtil::buildCheckboxFromNameable))), //
		BRANCHES(AppDef.copyOfGeneric(CommonProps.defaultDef(), def -> def //
				.setTranslatedLabel("App.Meter.enecess.ecoMain.branches.label") //
				.setTranslatedDescription("App.Meter.enecess.ecoMain.branches.description") //
				.setDefaultValue((app, property, language, parameter) -> new JsonArray()) //
				.setField(JsonFormlyUtil::buildRepeatFromNameable,
						(app, property, language, parameter, field) -> EcoMainBranchForm.configure(field, language,
								parameter.bundle())) //
				.valueMapper((app, property, language, parameter, properties) -> EcoMainBranchConfiguration
						.normalize(properties.get(property.name())))));

		private final AppDef<? super AppEcoMain, ? super Property, ? super BundleParameter> def;

		Property(AppDef<? super AppEcoMain, ? super Property, ? super BundleParameter> def) {
			this.def = def;
		}

		@Override
		public Type<Property, AppEcoMain, BundleParameter> self() {
			return this;
		}

		@Override
		public AppDef<? super AppEcoMain, ? super Property, ? super BundleParameter> def() {
			return this.def;
		}

		@Override
		public Function<GetParameterValues<AppEcoMain>, BundleParameter> getParamter() {
			return Parameter.functionOf(AbstractOpenemsApp::getTranslationBundle);
		}
	}

	@Activate
	public AppEcoMain(@Reference ComponentManager componentManager, ComponentContext componentContext,
			@Reference ConfigurationAdmin cm, @Reference ComponentUtil componentUtil,
			@Reference AppManagerUtil appManagerUtil) {
		super(componentManager, componentContext, cm, componentUtil);
		this.appManagerUtil = appManagerUtil;
	}

	@Override
	protected ThrowingTriFunction<ConfigurationTarget, Map<Property, JsonElement>, Language, AppConfiguration, OpenemsNamedException> appPropertyConfigurationFactory() {
		return (t, p, l) -> {
			final var alias = this.getString(p, l, Property.ALIAS);
			final var ip = this.getString(p, l, Property.IP);
			final var serialNumber = this.getString(p, l, Property.SERIAL_NUMBER);
			final var hasMain = this.getBoolean(p, Property.HAS_MAIN);

			if (!ip.matches(Validation.IP.getPattern())) {
				throw translatedError(l, "App.Meter.enecess.ecoMain.ip.invalid");
			}
			if (!serialNumber.matches("^[0-9]{12}$")) {
				throw translatedError(l, "App.Meter.enecess.ecoMain.serialNumber.invalid");
			}
			final var branches = EcoMainBranchConfiguration.parse(p.get(Property.BRANCHES), l);
			this.validateUniqueDevice(t, p, l, ip, serialNumber);

			final var modbusId = this.getId(t, p, Property.MODBUS_ID);
			final var coreId = this.getId(t, p, Property.CORE_ID);
			final var mainId = this.getId(t, p, Property.MAIN_ID);

			final var bundle = AbstractOpenemsApp.getTranslationBundle(l);
			final var components = new ArrayList<ComponentDef>();
			components.add(new ComponentDef(modbusId,
					TranslationUtil.getTranslation(bundle, "App.Meter.enecess.ecoMain.communication.alias"),
					"Bridge.Modbus.Tcp", ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
							.addProperty("ip", ip) //
							.addProperty("port", 502) //
							.build()),
					ComponentDef.Configuration.defaultConfig()));

			components.add(new ComponentDef(coreId, alias, "Meter.enecess.ecoMain.Core",
					ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
							.addProperty("modbus.id", modbusId) //
							.addProperty("serialNumber", serialNumber) //
							.build()),
					ComponentDef.Configuration.defaultConfig()));

			if (hasMain) {
				components.add(new ComponentDef(mainId, alias, "Meter.enecess.ecoMain.Main",
						ComponentProperties.fromJson(JsonUtils.buildJsonObject() //
								.addProperty("core.id", coreId) //
								.build()),
						ComponentDef.Configuration.defaultConfig()));
			}

			for (var branch : branches) {
				final var branchId = this.getId(t, p, branchIdProperty(branch.slot()));
				components.add(new ComponentDef(branchId, branch.alias(), "Meter.enecess.ecoMain.Branch",
						branch.toComponentProperties(coreId), ComponentDef.Configuration.defaultConfig()));
			}

			return AppConfiguration.create() //
					.addTask(Tasks.componentFromComponentConfig(components)) //
					.build();
		};
	}

	private static Property branchIdProperty(int slot) {
		return Property.valueOf("BRANCH_%02d_ID".formatted(slot));
	}

	private void validateUniqueDevice(ConfigurationTarget target, Map<Property, JsonElement> properties,
			Language language, String ip, String serialNumber) throws OpenemsException {
		if (target.isDeleteOrTest()) {
			return;
		}
		final var instances = this.appManagerUtil.getInstantiatedAppsOfApp(APP_ID);
		final String currentCoreId;
		if (target == ConfigurationTarget.ADD) {
			currentCoreId = null;
		} else {
			currentCoreId = this.getId(target, properties, Property.CORE_ID);
			// AppManager invokes UPDATE again with temporary numeric component IDs after
			// validating the real properties. That internal pass has no matching instance.
			if (instances.stream().noneMatch(
					instance -> currentCoreId.equals(stringProperty(instance.properties, Property.CORE_ID)))) {
				return;
			}
		}
		for (var instance : instances) {
			if (currentCoreId != null && currentCoreId.equals(stringProperty(instance.properties, Property.CORE_ID))) {
				continue;
			}
			if (ip.equals(stringProperty(instance.properties, Property.IP))) {
				throw translatedError(language, "App.Meter.enecess.ecoMain.ip.duplicate", ip);
			}
			if (serialNumber.equals(stringProperty(instance.properties, Property.SERIAL_NUMBER))) {
				throw translatedError(language, "App.Meter.enecess.ecoMain.serialNumber.duplicate", serialNumber);
			}
		}
	}

	private static String stringProperty(JsonObject properties, Property property) {
		if (properties == null) {
			return null;
		}
		return JsonUtils.getAsOptionalString(properties.get(property.name())).orElse(null);
	}

	private static OpenemsException translatedError(Language language, String key, Object... parameters) {
		return new OpenemsException(
				TranslationUtil.getTranslation(AbstractOpenemsApp.getTranslationBundle(language), key, parameters));
	}

	@Override
	public OpenemsAppCardinality getCardinality() {
		return OpenemsAppCardinality.MULTIPLE;
	}

	@Override
	public OpenemsAppCategory[] getCategories() {
		return new OpenemsAppCategory[] { OpenemsAppCategory.METER };
	}

	@Override
	public AppManagerUtil getAppManagerUtil() {
		return this.appManagerUtil;
	}

	@Override
	protected Property[] propertyValues() {
		return Property.values();
	}

	@Override
	protected AppEcoMain getApp() {
		return this;
	}
}
