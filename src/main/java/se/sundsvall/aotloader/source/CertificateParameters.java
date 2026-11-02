package se.sundsvall.aotloader.source;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.DECISION_PARAMETER_DECISION_DATE;
import static se.sundsvall.aotloader.Constants.DECISION_PARAMETER_VALID_FROM;

/**
 * Builds the parameters Draken gives the decision on a permit for its certificate, under the keys the certificate
 * template reads, so that a migrated decision reads like a new one (decision 87). A value the source does not have is
 * left out, as Draken leaves it out, and so is a key whose value only a handler gives in Draken, such as who issued the
 * certificate. A yes or no is "true" or "false", and the lines of a multiline value are separated by line breaks.
 */
public final class CertificateParameters {

	public static final String PERMIT_HOLDER_NAME = "permitHolderName";
	public static final String PERMIT_HOLDER_ORG_NUMBER = "permitHolderOrgNumber";
	public static final String PERMIT_HOLDER_STREET = "permitHolderStreet";
	public static final String PERMIT_HOLDER_POSTAL_ADDRESS = "permitHolderPostalAddress";
	public static final String PERMIT_HOLDER_PHONE = "permitHolderPhone";
	public static final String PREMISES_NAME = "premisesName";
	public static final String PREMISES_STREET = "premisesStreet";
	public static final String PREMISES_POSTAL_ADDRESS = "premisesPostalAddress";
	public static final String PREMISES_PHONE = "premisesPhone";
	public static final String SERVING_TO_PUBLIC = "servingToPublic";
	public static final String SERVING_TO_CLOSED_COMPANY = "servingToClosedCompany";
	public static final String SCOPE_ALL_YEAR = "scopeAllYear";
	public static final String SCOPE_ANNUAL_PERIOD = "scopeAnnualPeriod";
	public static final String SCOPE_TASTING = "scopeTasting";
	public static final String SCOPE_PERIOD_FROM_TO = "scopePeriodFromTo";
	public static final String BEVERAGE_SPIRITS = "beverageSpirits";
	public static final String BEVERAGE_WINE = "beverageWine";
	public static final String BEVERAGE_BEER = "beverageBeer";
	public static final String BEVERAGE_OTHER_FERMENTED = "beverageOtherFermented";
	public static final String SERVING_AREA_DESCRIPTION = "servingAreaDescription";
	public static final String SERVING_AREA_DRAWING_REFERENCE = "servingAreaDrawingReference";
	public static final String SERVING_AREA_SEATS = "servingAreaSeats";
	public static final String SERVING_AREA_MAX_PERSONS = "servingAreaMaxPersons";
	public static final String SERVING_HOURS = "servingHours";
	public static final String SALES_RETAIL = "salesRetail";
	public static final String SALES_WHOLESALE = "salesWholesale";
	public static final String SALES_ONLINE = "salesOnline";
	public static final String VALIDITY_INDEFINITE = "validityIndefinite";
	public static final String VALIDITY_TEMPORARY = "validityTemporary";
	public static final String TEMPORARY_PERIOD = "temporaryPeriod";
	public static final String CASE_NUMBER = "caseNumber";
	public static final String EXECUTION_DATE = "executionDate";
	public static final String DECISION_DATE_AND_CASE_NUMBER = "decisionDateAndCaseNumber";
	public static final String DECISION_OFFICER_AND_CASE_NUMBER = "decisionOfficerAndCaseNumber";
	public static final String DECISION_CASE_NUMBER = "decisionCaseNumber";
	public static final String DECISION_MAKER = "decisionMaker";

	private final Map<String, String> values = new LinkedHashMap<>();

	/**
	 * Adds the value, stripped, unless it is blank.
	 */
	public CertificateParameters text(final String key, final String value) {
		final var stripped = Texts.trimToNull(value);
		if (stripped != null) {
			values.put(key, stripped);
		}
		return this;
	}

	public CertificateParameters flag(final String key, final boolean value) {
		values.put(key, Boolean.toString(value));
		return this;
	}

	/**
	 * Adds the values that are not blank, one per line.
	 */
	public CertificateParameters lines(final String key, final Collection<String> lines) {
		return text(key, Texts.join("\n", lines));
	}

	/**
	 * Adds the permit holder: its name, organization number, street and postal address. A sole trader has no organization
	 * number, as Draken writes none for one either (decision 81).
	 */
	public CertificateParameters holder(final Holder holder) {
		if (holder == null) {
			return this;
		}
		return text(PERMIT_HOLDER_NAME, holder.name())
			.text(PERMIT_HOLDER_ORG_NUMBER, LegalIds.formatOrganizationNumber(holder.legalId()))
			.text(PERMIT_HOLDER_STREET, holder.address())
			.text(PERMIT_HOLDER_POSTAL_ADDRESS, postalAddress(holder.zipCode(), holder.city()));
	}

	/**
	 * Adds the serving place or sales place: its name, street, postal address and phone. The postal code is without
	 * blanks, as on the errand. The restaurant number is left out, as Draken leaves it to the process that issues the
	 * certificate.
	 */
	public CertificateParameters premises(final Place place) {
		if (place == null) {
			return this;
		}
		return text(PREMISES_NAME, place.name())
			.text(PREMISES_STREET, place.address())
			.text(PREMISES_POSTAL_ADDRESS, postalAddress(Texts.withoutBlanks(place.zipCode()), place.city()))
			.text(PREMISES_PHONE, place.phone());
	}

	/**
	 * Adds what Draken writes of the decision itself: its diary number, its decision maker, the day it was made and its
	 * first day. The text of its outcome is added when the decision is written, as the namespace names it.
	 */
	public CertificateParameters decision(final DecisionData decision) {
		return text(DECISION_CASE_NUMBER, decision.caseNumber())
			.text(DECISION_MAKER, decision.maker())
			.text(DECISION_PARAMETER_DECISION_DATE, SourceValues.format(decision.decidedAt().toLocalDate()))
			.text(DECISION_PARAMETER_VALID_FROM, SourceValues.format(decision.validFrom()));
	}

	/**
	 * @return the day of the decision and its diary number, as Draken writes them on the certificate of a serving permit,
	 *         e.g. "2019-04-30, SN 2019/123"
	 */
	public static String decisionDateAndCaseNumber(final DecisionData decision) {
		return Texts.join(", ", Arrays.asList(SourceValues.format(decision.decidedAt().toLocalDate()), decision.caseNumber()));
	}

	/**
	 * @return the decision maker and the diary number of the decision, as Draken writes them on the certificate of a
	 *         tobacco permit
	 */
	public static String decisionOfficerAndCaseNumber(final DecisionData decision) {
		return Texts.join(", ", Arrays.asList(decision.maker(), decision.caseNumber()));
	}

	public Map<String, String> build() {
		return new LinkedHashMap<>(values);
	}

	/**
	 * @return the postal code and the city, separated by a blank, e.g. "85230 Sundsvall"
	 */
	private static String postalAddress(final String zipCode, final String city) {
		return Texts.joinWords(zipCode, city);
	}
}
