package se.sundsvall.aotloader.service.model;

/**
 * The JSON parameters the migration writes, each by an AoT schema of its own (decisions 87, 92, 98 and 102). The key of
 * the parameter and the id of its schema are the settings of the type, under aot-loader.json-parameters by the name of
 * the type.
 */
public enum JsonParameterType {

	/** The post in PartyAssets of a serving permit or a notification of low-alcohol beer, aot_alcohol_party_asset. */
	ALCOHOL_PARTY_ASSET("alcohol-party-asset", null),

	/** The post in PartyAssets of a tobacco permit or a notification of a tobacco product, aot_tobacco_party_asset. */
	TOBACCO_PARTY_ASSET("tobacco-party-asset", null),

	/** What the decision on a serving permit says of the permit, aot_alcohol_decision. */
	ALCOHOL_DECISION("alcohol-decision", null),

	/** What the decision on a tobacco permit says of the permit, aot_tobacco_decision. */
	TOBACCO_DECISION("tobacco-decision", null),

	/**
	 * What the section of the premises of the investigation of a permit establishes, aot_investigation_premises_section.
	 */
	PREMISES_SECTION("premises-section", null),

	/**
	 * The fields of the inspection protocol "Yttre tillsyn" of a visit to a serving place, aot_inspection_serving_permit.
	 */
	SERVING_PERMIT_PROTOCOL("serving-permit-protocol", null),

	/** The fields of the inspection protocol "Inre tillsyn" of an inspection errand, aot_inspection_internal. */
	INTERNAL_INSPECTION_PROTOCOL("internal-inspection-protocol", null),

	/** The errand data of an internal inspection errand, the form of its type, aot_inspection_internal_inspection. */
	INTERNAL_INSPECTION_FORM("internal-inspection-form", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION"),

	/** The errand data of an external inspection errand, the form of its type, aot_inspection_external_inspection. */
	EXTERNAL_INSPECTION_FORM("external-inspection-form", "CATEGORYROOT/INSPECTION/EXTERNAL_INSPECTION"),

	/** The errand data of a notification of sales of low-alcohol beer, aot_alcohol_folkol_folkol_sales_notification. */
	FOLKOL_SALES_NOTIFICATION_FORM("folkol-sales-notification-form", "CATEGORYROOT/ALCOHOL/FOLKOL/FOLKOL_SALES_NOTIFICATION"),

	/** The errand data of a notification of serving of low-alcohol beer, aot_alcohol_folkol_folkol_serving_notification. */
	FOLKOL_SERVING_NOTIFICATION_FORM("folkol-serving-notification-form", "CATEGORYROOT/ALCOHOL/FOLKOL/FOLKOL_SERVING_NOTIFICATION"),

	/** The errand data of a notification of sales of e-cigarettes, aot_tobacco_ecigarette_sales_notification. */
	ECIGARETTE_NOTIFICATION_FORM("ecigarette-notification-form", "CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION"),

	/**
	 * The errand data of a notification of sales of tobacco-free nicotine products,
	 * aot_tobacco_tobacco_free_nicotine_sales_notification.
	 */
	TOBACCO_FREE_NICOTINE_NOTIFICATION_FORM("tobacco-free-nicotine-notification-form", "CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION");

	private final String settingsName;
	private final String formLabel;

	JsonParameterType(final String settingsName, final String formLabel) {
		this.settingsName = settingsName;
		this.formLabel = formLabel;
	}

	/**
	 * @return the name of the settings of the parameter, under aot-loader.json-parameters
	 */
	public String settingsName() {
		return settingsName;
	}

	/**
	 * @return for the form of an errand type, the resource path of the label of the type, which picks the form as it picks
	 *         its schema in Draken; null for a parameter that is no form
	 */
	public String formLabel() {
		return formLabel;
	}
}
