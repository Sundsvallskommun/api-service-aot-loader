package se.sundsvall.aotloader.service.model;

/**
 * The type of errand a source item becomes. It decides the configuration that applies to the errand: classification,
 * labels, and type, title and schema in PartyAssets.
 */
public enum ErrandType {

	SERVING_PERMIT(Source.ALKT, Kind.TILLSTAND, AssetSchema.ALCOHOL, AssetSchema.ALCOHOL),
	TOBACCO_PERMIT(Source.ALKOL, Kind.TILLSTAND, AssetSchema.TOBACCO, AssetSchema.TOBACCO),
	FOLKOL_SALES_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.ALCOHOL, AssetSchema.ALCOHOL),
	FOLKOL_SERVING_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.ALCOHOL, AssetSchema.ALCOHOL),
	ECIGARETTE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.TOBACCO, AssetSchema.TOBACCO),
	TOBACCO_FREE_NICOTINE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.TOBACCO, AssetSchema.TOBACCO),
	ALCOHOL_INSPECTION(Source.ALKT, Kind.TILLSYN, null, AssetSchema.ALCOHOL),
	TOBACCO_INSPECTION(Source.ALKOL, Kind.TILLSYN, null, AssetSchema.TOBACCO),
	ALCOHOL_INSPECTION_VISIT(Source.ALKT, Kind.TILLSYNSBESOK, null, AssetSchema.ALCOHOL),
	TOBACCO_INSPECTION_VISIT(Source.ALKOL, Kind.TILLSYNSBESOK, null, AssetSchema.TOBACCO),
	ALCOHOL_DOCUMENTATION(Source.ALKT, Kind.DOKUMENTATION, null, AssetSchema.ALCOHOL),
	TOBACCO_DOCUMENTATION(Source.ALKOL, Kind.DOKUMENTATION, null, AssetSchema.TOBACCO);

	private final Source source;
	private final Kind kind;
	private final AssetSchema assetSchema;
	private final AssetSchema placeSchema;

	ErrandType(final Source source, final Kind kind, final AssetSchema assetSchema, final AssetSchema placeSchema) {
		this.source = source;
		this.kind = kind;
		this.assetSchema = assetSchema;
		this.placeSchema = placeSchema;
	}

	public Source getSource() {
		return source;
	}

	public Kind getKind() {
		return kind;
	}

	/**
	 * @return true when the errand gets a post in PartyAssets
	 */
	public boolean isWithAsset() {
		return assetSchema != null;
	}

	/**
	 * @return the AoT schema of the JSON parameter of the post in PartyAssets (decision 87), null for an errand without a
	 *         post
	 */
	public AssetSchema getAssetSchema() {
		return assetSchema;
	}

	/**
	 * @return the AoT schema whose field holds the place of the errand: the serving place, premises, of the alcohol schema,
	 *         or the sales place, pointOfSale, of the tobacco schema, also for an errand without a post (decision 98)
	 */
	public AssetSchema getPlaceSchema() {
		return placeSchema;
	}
}
