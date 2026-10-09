package io.openems.edge.app.meter.enecess;

import io.openems.common.session.Language;
import io.openems.edge.app.enums.TranslatableEnum;
import io.openems.edge.core.appmanager.AbstractOpenemsApp;
import io.openems.edge.core.appmanager.TranslationUtil;

public enum EcoMainPhaseCount implements TranslatableEnum {
	SINGLE_PHASE, //
	THREE_PHASE, //
;

	@Override
	public String getTranslation(Language language) {
		final var bundle = AbstractOpenemsApp.getTranslationBundle(language);
		return TranslationUtil.getTranslation(bundle,
				"App.Meter.enecess.ecoMain.Branch.phaseCount." + this.name());
	}
}
