package se.sundsvall.aotloader.service.model;

/**
 * The type of errand a source item becomes. It decides the configuration that applies to the errand: classification,
 * labels, and type, title and schema in PartyAssets.
 */
public enum ErrandType {

	SERVING_PERMIT(Source.ALKT, Kind.TILLSTAND, AssetSchema.ALCOHOL),
	TOBACCO_PERMIT(Source.ALKOL, Kind.TILLSTAND, AssetSchema.TOBACCO),
	FOLKOL_SALES_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.ALCOHOL),
	FOLKOL_SERVING_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.ALCOHOL),
	ECIGARETTE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.TOBACCO),
	TOBACCO_FREE_NICOTINE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, AssetSchema.TOBACCO),
	ALCOHOL_INSPECTION(Source.ALKT, Kind.TILLSYN, null),
	TOBACCO_INSPECTION(Source.ALKOL, Kind.TILLSYN, null),
	ALCOHOL_INSPECTION_VISIT(Source.ALKT, Kind.TILLSYNSBESOK, null),
	TOBACCO_INSPECTION_VISIT(Source.ALKOL, Kind.TILLSYNSBESOK, null),
	ALCOHOL_DOCUMENTATION(Source.ALKT, Kind.DOKUMENTATION, null),
	TOBACCO_DOCUMENTATION(Source.ALKOL, Kind.DOKUMENTATION, null);

	private final Source source;
	private final Kind kind;
	private final AssetSchema assetSchema;

	ErrandType(final Source source, final Kind kind, final AssetSchema assetSchema) {
		this.source = source;
		this.kind = kind;
		this.assetSchema = assetSchema;
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
}
