package se.sundsvall.aotloader.service.model;

/**
 * The JSON parameters the migration writes, each by an AoT schema of its own (decisions 87 and 92). The key of the
 * parameter and the id of its schema are the settings of the type, under aot-loader.json-parameters by the name of the
 * type.
 */
public enum JsonParameterType {

	/** The post in PartyAssets of a serving permit or a notification of low-alcohol beer, aot_alcohol_party_asset. */
	ALCOHOL_PARTY_ASSET("alcohol-party-asset"),

	/** The post in PartyAssets of a tobacco permit or a notification of a tobacco product, aot_tobacco_party_asset. */
	TOBACCO_PARTY_ASSET("tobacco-party-asset"),

	/** What the decision on a serving permit says of the permit, aot_alcohol_decision. */
	ALCOHOL_DECISION("alcohol-decision"),

	/** What the decision on a tobacco permit says of the permit, aot_tobacco_decision. */
	TOBACCO_DECISION("tobacco-decision"),

	/**
	 * What the section of the premises of the investigation of a permit establishes, aot_investigation_premises_section.
	 */
	PREMISES_SECTION("premises-section"),

	/** The fields of the inspection protocol "Serveringstillstånd – tillsyn" of a visit, aot_inspection_serving_permit. */
	SERVING_PERMIT_PROTOCOL("serving-permit-protocol"),

	/** The fields of the inspection protocol "Inre tillsyn" of an inspection errand, aot_inspection_internal. */
	INTERNAL_INSPECTION_PROTOCOL("internal-inspection-protocol");

	private final String settingsName;

	JsonParameterType(final String settingsName) {
		this.settingsName = settingsName;
	}

	/**
	 * @return the name of the settings of the parameter, under aot-loader.json-parameters
	 */
	public String settingsName() {
		return settingsName;
	}
}
