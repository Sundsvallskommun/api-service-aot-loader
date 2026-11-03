package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.JsonContent;
import se.sundsvall.aotloader.service.model.JsonParameterType;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.support.JsonFields;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

/**
 * The inspection protocols Draken shows the investigation of an inspection errand as (decision 77): the template, by
 * the key Draken picks it by from the type of the investigation, and its fields in the JSON parameter of the
 * investigation, by the keys of the template, as Draken saves them (decision 92). Only what the source tells is filled
 * in; a field it has nothing for, such as the summary or the number of guests, is left out, and Draken then shows it
 * empty or as it fills in a new protocol.
 */
public final class InspectionProtocols {

	/** The protocol of a visit to a place with a serving permit, "Serveringstillstånd – tillsyn". */
	public static final String SERVING_PERMIT = "serving_permit";
	/** The protocol of an inspection of the permit holder without a visit, "Inre tillsyn". */
	public static final String INTERNAL_INSPECTION = "internal_inspection";

	private InspectionProtocols() {}

	/**
	 * @param  place             the place visited, null when unknown
	 * @param  date              the day of the visit, null when unknown
	 * @param  start             the time the visit started, "hh:mm", null when unknown
	 * @param  end               the time it ended, "hh:mm", null when unknown
	 * @param  responsibleOnSite whether the owner or someone responsible for the serving was on site, null when unknown
	 *                           (decision 80)
	 * @param  inspectors        who made the visit, the first two of whom fill the protocol's two fields
	 * @param  texts             the texts of the visit, written as its description
	 * @return                   the fields of the protocol {@link #SERVING_PERMIT}
	 */
	public static JsonContent visit(final Place place, final LocalDate date, final String start, final String end, final Boolean responsibleOnSite, final List<String> inspectors,
		final InvestigationTexts texts) {
		final var fields = JsonFields.object();
		if (place != null) {
			fields.text("premisesName", place.name())
				.text("premisesAddress", address(place));
		}
		return new JsonContent(JsonParameterType.SERVING_PERMIT_PROTOCOL, fields
			.date("inspectionDate", date)
			.text("visitStartTime", clock(start))
			.text("visitEndTime", clock(end))
			.text("responsiblePerson", responsiblePerson(responsibleOnSite))
			.text("additionalRemarks", plain(texts))
			.text("inspector1NameAndTitle", inspectors.isEmpty() ? null : inspectors.getFirst())
			.text("inspector2NameAndTitle", inspectors.size() < 2 ? null : inspectors.get(1))
			.toNode());
	}

	/**
	 * @param  holder   the holder the inspection concerned, null when unknown
	 * @param  place    the place of the holder, null when unknown
	 * @param  type     the type of the inspection errand in the source
	 * @param  date     the day the inspection started, null when unknown
	 * @param  handlers who made the inspection
	 * @return          the fields of the protocol {@link #INTERNAL_INSPECTION}
	 */
	public static JsonContent internal(final Holder holder, final Place place, final String type, final LocalDate date, final List<String> handlers) {
		final var fields = JsonFields.object();
		if (holder != null) {
			fields.text("businessName", holder.name())
				// A sole trader's number is a personal identity number, which Draken leaves out of the field (decision 81)
				.text("businessOrgNumber", LegalIds.formatOrganizationNumber(holder.legalId()));
		}
		if (place != null) {
			fields.text("businessAddress", address(place));
		}
		return new JsonContent(JsonParameterType.INTERNAL_INSPECTION_PROTOCOL, fields
			.text("inspectionType", type)
			.date("inspectionDate", date)
			.text("inspectorName", Texts.join(", ", handlers))
			.toNode());
	}

	/**
	 * @return the value of the option Draken offers for the field, the one labelled "Ja" or the one labelled "Ingen
	 *         serveringsansvarig på plats"; null when unknown
	 */
	private static String responsiblePerson(final Boolean responsibleOnSite) {
		if (responsibleOnSite == null) {
			return null;
		}
		return responsibleOnSite ? "present" : "absent";
	}

	private static String address(final Place place) {
		return Texts.join(", ", Arrays.asList(place.address(), Texts.joinWords(place.zipCode(), place.city())));
	}

	/**
	 * @return the time as Draken takes it, "hh:mm" with 24:00 as 00:00; null for one that cannot be read
	 */
	private static String clock(final String time) {
		return SourceValues.parseTime(time).map(value -> "24:00".equals(value) ? "00:00" : value).orElse(null);
	}

	/**
	 * @return the texts as plain text, each part under its heading; null without texts
	 */
	private static String plain(final InvestigationTexts texts) {
		return Texts.trimToNull(String.join("\n\n", texts.parts().stream().map(part -> part.heading() + ":\n" + part.text()).toList()));
	}
}
