package se.sundsvall.aotloader.service.model;

import static se.sundsvall.aotloader.service.model.Severity.BLOCKING;
import static se.sundsvall.aotloader.service.model.Severity.INFO;
import static se.sundsvall.aotloader.service.model.Severity.NOT_MIGRATED;

/**
 * The review rules of the solution document (section 7.1.1). The code is the one the business sees in the report and in
 * the review queries. A rule that is not overridable cannot be lifted by the exception list, since the migration cannot
 * build the errand without the data the rule finds missing.
 */
public enum ReviewRule {

	PLACE_MISSING("G1", "Serverings- eller försäljningsstället saknas", BLOCKING, false),
	OWNER_DIFFERS("G2", "Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare", BLOCKING, true),
	DECISION_EXPIRED("G3", "Det kopplade beslutet har passerat sitt sista giltighetsdatum", BLOCKING, true),
	PERIOD_PASSED("G4", "Datumperioden har passerat", BLOCKING, true),
	PERIOD_MISSING("G5", "Gäller inte året runt men saknar period", BLOCKING, true),
	RESTAURANT_NUMBER_INVALID("G6", "Serveringsstället saknar serveringsställenummer, eller numret har inte 8 siffror", BLOCKING, true),
	LEGAL_ID_ON_SEVERAL_OWNERS("G7", "Ägarens organisationsnummer finns på flera ägarposter", BLOCKING, true),
	LEGAL_ID_INVALID("G8", "Ägarens nummer saknas eller har fel format", BLOCKING, false),
	UNPARSEABLE("G9", "En säsong, period, tid, ett datum eller ett villkors giltighetsdatum går inte att tolka", BLOCKING, true),
	ISSUE_DATE_MISSING("G9", "Utfärdandedatum saknas, och PartyAssets kräver ett datum", BLOCKING, false),
	HOLDER_NOT_IN_PARTY("G10", "Tillståndshavaren finns inte i Party", BLOCKING, false),
	INSPECTION_HOLDER_NOT_IN_PARTY("G10", "Tillståndshavaren som ärendet gällde finns inte i Party och läggs in utan partyId", INFO, true),
	PERIOD_ON_ALL_YEAR("G12", "Gäller året runt men har en datumperiod, troligen ett tillfälligt tillägg", BLOCKING, true),
	NO_ISSUING_ERRAND("G13", "Tillståndet går inte att koppla till ärendet som utfärdade det och får inga filer därifrån automatiskt", INFO, true),
	FILE_NOT_UPLOADED("G14", "En fil som ska följa med finns inte på filarean, eller är för stor, och laddas inte upp", INFO, true),
	DIARY_NUMBER_MISSING("G15", "Tillståndet saknar diarienummer", BLOCKING, false),
	DIARY_NUMBER_ON_SEVERAL("G16", "Diarienumret finns på mer än ett tillstånd", BLOCKING, true),
	SUBTYPE_MISSING("G17", "Tillståndet går inte att ge någon undertyp (avsnitt 7.3.4)", BLOCKING, false),
	PERMIT_ENDED("G18", "Ett beslut om upphörande eller återkallelse är senare än det senaste beviljande beslutet", NOT_MIGRATED, true),
	APPLICANT_NOT_OWNER("G19", "Sökanden i det beviljande ärendet är inte nuvarande ägare", BLOCKING, true),
	OWNER_COPY_DIFFERS("G20", "Ägarens organisationsnummer på försäljningsstället skiljer sig från ägarposten", BLOCKING, true),
	OWNER_MISSING("G21", "Försäljningsstället saknar ägare, eller ägaren saknas", BLOCKING, false),
	NOTIFICATION_ONLY_FLAG("G22", "Anmälan finns bara som markering på försäljningsstället och får försäljningsställets tidigaste kända datum", INFO, true),
	NOTIFICATION_DATE_MISSING("G23", "Anmälan saknar datum, och försäljningsstället har inga händelser eller ärenden", BLOCKING, false),
	RESTAURANT_NUMBER_DIFFERS("G24", "Serveringsställenumret på tillståndet skiljer sig från serveringsställets", BLOCKING, true),
	SALES_PLACE_CEASED("G25", "Försäljningsstället är markerat som upphört men har gällande tobakstillstånd", NOT_MIGRATED, true),
	PERMIT_ROWS_ON_PLACE("G26", "Serveringsstället har flera rader i Gällande_Tillstånd, och bara en kan migreras", BLOCKING, false),
	DECISION_MISSING("G27", "Tillståndet pekar på ett beslut som saknas i AlkT", INFO, true),
	VISIT_DATE_INVALID("G28", "Tillsynsbesöket saknar datum eller har ett datum efter migreringsdagen och kan vara planerat men inte genomfört", BLOCKING, true),
	CERTIFICATE_MISSING("G29", "Tillståndet har inget tillståndsbevis i källsystemet och får inget i PartyAssets", INFO, true),
	CERTIFICATE_OLDER("G29", "Det senaste tillståndsbeviset är äldre än tillståndet, eller saknar datum, och kan höra till en tidigare tillståndshavare. Det läggs bara på ärendet, inte på tillståndet i PartyAssets",
		INFO, true),
	PERSON_LOOKUP_FAILED("G30", "En person med betydande inflytande gick inte att slå upp i Party. Undantagslistan kan låta personen läggas in utan partyId", BLOCKING, true),
	NOTIFICATION_OF_FORMER_HOLDER("G31", "Produkten registrerades också av en tidigare ägare. Den registreringens filer läggs bara på ärendet, inte på anmälan i PartyAssets", INFO, true),
	FORMER_HOLDER("G32", "Ärendet eller händelsen gällde en tidigare ägare av stället, och bara den nuvarande tillståndshavarens följer med (beslut 72)", NOT_MIGRATED, true),
	GRANT_DECISION_MISSING("G33", "Källsystemet har inget beslut som beviljade tillståndet, så ärendet får ett beslut som bygger på tillståndet (beslut 75)", INFO, true),
	SERVING_TIME_UNPLACED("G34", "En serveringstid går inte att lägga i beslutets serveringstider i Draken och står bara på ärendet (beslut 76)", INFO, true),
	ERRAND_OPEN("Ä1", "Ärendet är inte avslutat i källsystemet och ska avslutas eller flyttas för hand (beslut 16 och 23)", BLOCKING, false),
	UNDERLYING_ERRAND_OPEN("Ä1", "Ett ärende som tillståndet eller anmälan bygger på är inte avslutat i källsystemet och ska avslutas, eller flyttas för hand och föras in i undantagslistan (beslut 16 och 23)", BLOCKING, true);

	private final String code;
	private final String description;
	private final Severity severity;
	private final boolean overridable;

	ReviewRule(final String code, final String description, final Severity severity, final boolean overridable) {
		this.code = code;
		this.description = description;
		this.severity = severity;
		this.overridable = overridable;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public Severity getSeverity() {
		return severity;
	}

	/**
	 * @return true when the exception list can decide that the item is migrated despite a hit on the rule
	 */
	public boolean isOverridable() {
		return overridable;
	}
}
