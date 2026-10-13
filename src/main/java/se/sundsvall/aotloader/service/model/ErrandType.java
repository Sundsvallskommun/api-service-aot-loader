package se.sundsvall.aotloader.service.model;

import static se.sundsvall.aotloader.Constants.LABEL_ALCOHOL_INSPECTION;
import static se.sundsvall.aotloader.Constants.LABEL_ECIGARETTE;
import static se.sundsvall.aotloader.Constants.LABEL_FOLKOL_SALES;
import static se.sundsvall.aotloader.Constants.LABEL_FOLKOL_SERVING;
import static se.sundsvall.aotloader.Constants.LABEL_TOBACCO_FREE_NICOTINE;
import static se.sundsvall.aotloader.Constants.LABEL_TOBACCO_INSPECTION;
import static se.sundsvall.aotloader.Constants.LABEL_TOBACCO_PERMIT;

/**
 * The type of errand a source item becomes. It decides the configuration that applies to the errand: classification,
 * type and title in PartyAssets.
 */
public enum ErrandType {

	SERVING_PERMIT(Source.ALKT, Kind.TILLSTAND, true, null),
	TOBACCO_PERMIT(Source.ALKOL, Kind.TILLSTAND, true, LABEL_TOBACCO_PERMIT),
	FOLKOL_SALES_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true, LABEL_FOLKOL_SALES),
	FOLKOL_SERVING_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true, LABEL_FOLKOL_SERVING),
	ECIGARETTE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true, LABEL_ECIGARETTE),
	TOBACCO_FREE_NICOTINE_NOTIFICATION(Source.ALKOL, Kind.ANMALAN, true, LABEL_TOBACCO_FREE_NICOTINE),
	ALCOHOL_INSPECTION(Source.ALKT, Kind.TILLSYN, false, LABEL_ALCOHOL_INSPECTION),
	TOBACCO_INSPECTION(Source.ALKOL, Kind.TILLSYN, false, LABEL_TOBACCO_INSPECTION);

	private final Source source;
	private final Kind kind;
	private final boolean withAsset;
	private final String labelPath;

	ErrandType(final Source source, final Kind kind, final boolean withAsset, final String labelPath) {
		this.source = source;
		this.kind = kind;
		this.withAsset = withAsset;
		this.labelPath = labelPath;
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

	/**
	 * @return the resource path of the label every errand of the type gets; null for a serving permit, whose label is
	 *         that of its subtype (section 7.3.4)
	 */
	public String getLabelPath() {
		return labelPath;
	}
}
