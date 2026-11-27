package se.sundsvall.aotloader.service.model;

/**
 * The AoT schemas the JSON parameter of a post in PartyAssets follows, one for alcohol and one for tobacco (decision
 * 87), and the schema of the JSON parameter of the decision of a permit of the area, which extends it (decision 92).
 */
public enum AssetSchema {

	/** aot_alcohol_party_asset: serving permits and notifications of low-alcohol beer; aot_alcohol_decision. */
	ALCOHOL(JsonParameterType.ALCOHOL_PARTY_ASSET, JsonParameterType.ALCOHOL_DECISION, "premises"),

	/** aot_tobacco_party_asset: tobacco permits and notifications of tobacco products; aot_tobacco_decision. */
	TOBACCO(JsonParameterType.TOBACCO_PARTY_ASSET, JsonParameterType.TOBACCO_DECISION, "pointOfSale");

	private final JsonParameterType asset;
	private final JsonParameterType decision;
	private final String placeField;

	AssetSchema(final JsonParameterType asset, final JsonParameterType decision, final String placeField) {
		this.asset = asset;
		this.decision = decision;
		this.placeField = placeField;
	}

	/**
	 * @return the JSON parameter of the post in PartyAssets
	 */
	public JsonParameterType asset() {
		return asset;
	}

	/**
	 * @return the JSON parameter of the decision of a permit
	 */
	public JsonParameterType decision() {
		return decision;
	}

	/**
	 * @return the field of the place, the serving place or sales place, which holds the municipality of the place
	 */
	public String placeField() {
		return placeField;
	}
}
