package se.sundsvall.aotloader.service.model;

/**
 * The AoT schemas the JSON parameter of a post in PartyAssets follows, one for alcohol and one for tobacco (decision
 * 87). The key of the parameter and the id of the schema are the settings of the JSON parameter by the name of the
 * schema.
 */
public enum AssetSchema {

	/** aot_alcohol_party_asset: serving permits and notifications of low-alcohol beer. */
	ALCOHOL("alcohol-party-asset", "premises"),

	/** aot_tobacco_party_asset: tobacco permits and notifications of e-cigarettes and tobacco-free nicotine products. */
	TOBACCO("tobacco-party-asset", "pointOfSale");

	private final String settingsName;
	private final String placeField;

	AssetSchema(final String settingsName, final String placeField) {
		this.settingsName = settingsName;
		this.placeField = placeField;
	}

	/**
	 * @return the name of the settings of the JSON parameter, under aot-loader.json-parameters
	 */
	public String settingsName() {
		return settingsName;
	}

	/**
	 * @return the field of the place, the serving place or sales place, which holds the municipality of the place
	 */
	public String placeField() {
		return placeField;
	}
}
