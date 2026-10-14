package se.sundsvall.aotloader.service.model;

/**
 * The type of errand a source item becomes. It decides the configuration that applies to the errand: classification,
 * labels, and type and title in PartyAssets.
 */
public enum ErrandType {

	SERVING_PERMIT(Source.ALKT, Kind.TILLSTAND, true),
	TOBACCO_PERMIT(Source.ALKOL, Kind.TILLSTAND, true),
	FOLKOL_SALES_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true),
	FOLKOL_SERVING_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true),
	ECIGARETTE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true),
	TOBACCO_FREE_NICOTINE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true),
	ALCOHOL_INSPECTION(Source.ALKT, Kind.TILLSYN, false),
	TOBACCO_INSPECTION(Source.ALKOL, Kind.TILLSYN, false),
	ALCOHOL_INSPECTION_VISIT(Source.ALKT, Kind.TILLSYNSBESOK, false),
	TOBACCO_INSPECTION_VISIT(Source.ALKOL, Kind.TILLSYNSBESOK, false);

	private final Source source;
	private final Kind kind;
	private final boolean withAsset;

	ErrandType(final Source source, final Kind kind, final boolean withAsset) {
		this.source = source;
		this.kind = kind;
		this.withAsset = withAsset;
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
		return withAsset;
	}
}
