package io.openems.edge.app.meter.enecess;

import io.openems.common.session.Language;
import io.openems.edge.app.enums.TranslatableEnum;
import io.openems.edge.core.appmanager.AbstractOpenemsApp;
import io.openems.edge.core.appmanager.TranslationUtil;

public enum EcoMainSource implements TranslatableEnum {
	ECOMAIN(0), //
	ECOSUB_1(1), //
	ECOSUB_2(2), //
	ECOSUB_3(3), //
;

	private final int source;

	private EcoMainSource(int source) {
		this.source = source;
	}

	/**
	 * Gets the zero-based physical source index used by the component factory.
	 *
	 * @return the physical source index
	 */
	public int source() {
		return this.source;
	}

	@Override
	public String getTranslation(Language language) {
		final var bundle = AbstractOpenemsApp.getTranslationBundle(language);
		return TranslationUtil.getTranslation(bundle, "App.Meter.enecess.ecoMain.Branch.source." + this.name());
	}
}
