package se.sundsvall.aotloader;

/**
 * Names the migrated errands and permits share with the target systems and with pw-alkt.
 */
public final class Constants {

	/** The external tag that marks a migrated errand with the source item it came from; the key against duplicates. */
	public static final String TAG_MIGRATION_ID = "migreringsid";
	/** The external tag with the diary number of the source (decision 4). */
	public static final String TAG_DIARY_NUMBER = "diarienummer";
	/** The external tag of a notification, "{FSID}-{product}" (decision 22). */
	public static final String TAG_NOTIFICATION = "alkolAnmalan";

	/** The label that marks a migrated errand and keeps every process away from it (decision 17). */
	public static final String LABEL_MIGRATED = "TAGROOT/MIGRATED_ERRAND";
	/** The attribute of a label that keeps every process away from an errand that carries it. */
	public static final String LABEL_ATTRIBUTE_PROCESS_BLOCKED = "processBlocked";

	/** The parameter of the errand with the restaurant number of the serving place, as pw-alkt reads it (decision 18). */
	public static final String ERRAND_PARAMETER_RESTAURANT_NUMBER = "restaurantNumber";
	/**
	 * The parameter of the errand with the name of the serving place or sales place, as pw-alkt reads it (section 7.2.1).
	 */
	public static final String ERRAND_PARAMETER_PREMISES_NAME = "premisesName";
	/** The parameter of the errand with the street address of the place, as pw-alkt reads it. */
	public static final String ERRAND_PARAMETER_PREMISES_STREET_ADDRESS = "premisesStreetAddress";
	/** The parameter of the errand with the postal code of the place, without blanks, as pw-alkt reads it. */
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_CODE = "premisesPostalCode";
	/** The parameter of the errand with the postal area of the place, as pw-alkt reads it. */
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_AREA = "premisesPostalArea";
	/** The parameter of the errand with the phone number of the place, named like the key pw-alkt reads. */
	public static final String ERRAND_PARAMETER_PREMISES_PHONE = "premisesPhone";
	/** The parameter of the errand with the email address of the place, named like the key pw-alkt reads. */
	public static final String ERRAND_PARAMETER_PREMISES_EMAIL = "premisesEmail";
	/**
	 * The parameter of the errand and of the permit with the municipality code of the place, e.g. 2262 for Timrå, which
	 * picks e.g. the document templates (decision 19).
	 */
	public static final String PARAMETER_PREMISES_MUNICIPALITY_ID = "premisesMunicipalityId";
	/**
	 * The parameter that tells a person with significant influence apart from other stakeholders with the same role, as
	 * Draken writes it, with the value true and no display name.
	 */
	public static final String PARAMETER_PBI = "PBI";
	/**
	 * The parameter of a person with significant influence that tells where the stakeholder came from, as Draken writes it.
	 */
	public static final String PARAMETER_PBI_SOURCE = "PBI_SOURCE";
	/** The value of {@link #PARAMETER_PBI_SOURCE} for a stakeholder that a case handler added. */
	public static final String PBI_SOURCE_MANUAL = "MANUAL";
	/** The parameter of a person with significant influence with the person's roles in one value, e.g. "Ledamot, VD". */
	public static final String PARAMETER_PBI_ROLE = "PBI_ROLE";
	/** The parameter of a company stakeholder with its organization number, as new errands in AOT have it. */
	public static final String PARAMETER_ORGANIZATION_NUMBER = "organizationNumber";
	/** The display name of {@link #PARAMETER_ORGANIZATION_NUMBER}. */
	public static final String PARAMETER_ORGANIZATION_NUMBER_DISPLAY_NAME = "Organisationsnummer";
	/** The parameter of the permit that links it to its errand, as pw-alkt writes it. */
	public static final String ASSET_PARAMETER_ERRAND_ID = "errandId";
	/** The parameter of the permit with its conditions, as pw-alkt writes it. */
	public static final String ASSET_PARAMETER_CONDITIONS = "conditions";
	/** The parameter of the permit with its legal basis, as pw-alkt writes it (decision 58). */
	public static final String ASSET_PARAMETER_LEGAL_BASIS = "legalBasis";
	/** The origin the permits get in PartyAssets, as pw-alkt gives the permits it creates. */
	public static final String ASSET_ORIGIN = "SUPPORTMANAGEMENT";
	/**
	 * The category of the permit certificate on a permit in PartyAssets, the one pw-alkt replaces when the permit changes.
	 */
	public static final String ASSET_CATEGORY_CERTIFICATE = "Tillståndsbevis";
	/** The category of the annex of the permit decision on a permit in PartyAssets. */
	public static final String ASSET_CATEGORY_ANNEX = "Beslutsbilaga";
	/**
	 * The category of a drawing of the premises in PartyAssets when it has no purpose; one with a purpose gets the display
	 * name of the purpose.
	 */
	public static final String ASSET_CATEGORY_DRAWING = "Ritning";
	/**
	 * The category of the permit decisions of the issuing errand, documents and attachments, on a permit in PartyAssets.
	 */
	public static final String ASSET_CATEGORY_DECISION = "Beslut";

	/** The longest value of an errand parameter in SupportManagement. */
	public static final int ERRAND_PARAMETER_MAX_LENGTH = 3000;
	/** The longest value of an additional parameter, the title or the description of a permit in PartyAssets. */
	public static final int ASSET_TEXT_MAX_LENGTH = 255;
	/** The longest reason, detail and message in the status table and the report, whose columns are varchar(4000). */
	public static final int REPORT_TEXT_MAX_LENGTH = 4000;
	/** The longest name of a place in the status table and the report, whose columns are varchar(255). */
	public static final int PLACE_NAME_MAX_LENGTH = 255;
	/** The longest restaurant number in the status table and the report, whose columns are varchar(20). */
	public static final int PLACE_NUMBER_MAX_LENGTH = 20;
	/** The longest errand number in the status table and the report, whose columns are varchar(50). */
	public static final int ERRAND_NUMBER_MAX_LENGTH = 50;

	public static final String RELATION_TYPE_LINK = "LINK";
	public static final String RESOURCE_TYPE_CASE = "case";
	/** The name pw-alkt and PartyAssets give SupportManagement in relations (section 7.7.1). */
	public static final String RELATION_SERVICE_SUPPORT_MANAGEMENT = "supportmanagement";

	private Constants() {}
}
