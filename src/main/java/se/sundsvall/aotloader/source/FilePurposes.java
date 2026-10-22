package se.sundsvall.aotloader.source;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.FileNames;
import se.sundsvall.aotloader.support.Texts;

import static java.util.Map.entry;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.service.model.Source.ALKT;

/**
 * Tells which attachment purpose of the namespace a file of the sources gets on its errand (decision 40): a referral
 * by the template it was made from, a referral answer or a self-monitoring program by the type of its event, and a
 * drawing by its name. Any other file gets none. Also tells which files are material of the investigation of their
 * errand (decision 60): referrals and their answers, investigations, the communication of them and proposed decisions
 * by their template or the type of their event, and the reports of inspections.
 */
public final class FilePurposes {

	public static final String POLICE_REQUEST = "REFERRAL_POLICE_REQUEST";
	public static final String POLICE_RESPONSE = "REFERRAL_POLICE_RESPONSE";
	public static final String TAX_AGENCY_REQUEST = "REFERRAL_TAX_AGENCY_REQUEST";
	public static final String TAX_AGENCY_RESPONSE = "REFERRAL_TAX_AGENCY_RESPONSE";
	public static final String ENFORCEMENT_AUTHORITY_REQUEST = "REFERRAL_ENFORCEMENT_AUTHORITY_REQUEST";
	public static final String ENFORCEMENT_AUTHORITY_RESPONSE = "REFERRAL_ENFORCEMENT_AUTHORITY_RESPONSE";
	public static final String RESCUE_SERVICE_REQUEST = "REFERRAL_RESCUE_SERVICE_REQUEST";
	public static final String RESCUE_SERVICE_RESPONSE = "REFERRAL_RESCUE_SERVICE_RESPONSE";
	public static final String ENVIRONMENT_OFFICE_REQUEST = "REFERRAL_ENVIRONMENT_OFFICE_REQUEST";
	public static final String ENVIRONMENT_OFFICE_RESPONSE = "REFERRAL_ENVIRONMENT_OFFICE_RESPONSE";
	public static final String CREDIT_REPORT_RESPONSE = "REFERRAL_CREDIT_REPORT_RESPONSE";
	public static final String STATEMENT_REQUEST = "STATEMENT_REQUEST";
	public static final String STATEMENT_RESPONSE = "STATEMENT_RESPONSE";
	public static final String SELF_MONITORING_PROGRAM = "SELF_MONITORING_PROGRAM";
	public static final String SERVING_AREA_DRAWING = "SERVING_AREA_DRAWING";
	public static final String SALES_PREMISES_DRAWING = "SALES_PREMISES_DRAWING";
	public static final String KITCHEN_FLOOR_PLAN = "KITCHEN_FLOOR_PLAN";

	/** Every purpose a file can get, which the namespace must have. */
	public static final Set<String> ALL = Set.of(POLICE_REQUEST, POLICE_RESPONSE, TAX_AGENCY_REQUEST, TAX_AGENCY_RESPONSE, ENFORCEMENT_AUTHORITY_REQUEST,
		ENFORCEMENT_AUTHORITY_RESPONSE, RESCUE_SERVICE_REQUEST, RESCUE_SERVICE_RESPONSE, ENVIRONMENT_OFFICE_REQUEST, ENVIRONMENT_OFFICE_RESPONSE, CREDIT_REPORT_RESPONSE,
		STATEMENT_REQUEST, STATEMENT_RESPONSE, SELF_MONITORING_PROGRAM, SERVING_AREA_DRAWING, SALES_PREMISES_DRAWING, KITCHEN_FLOOR_PLAN);

	private static final Set<String> DRAWINGS = Set.of(SERVING_AREA_DRAWING, SALES_PREMISES_DRAWING, KITCHEN_FLOOR_PLAN);

	/** The templates of the referrals, by source, in upper case. */
	private static final Map<Source, Map<String, String>> REFERRAL_TEMPLATES = Map.of(
		ALKT, Map.ofEntries(
			entry("REMP", POLICE_REQUEST),
			// An order from the criminal record, which the police keep
			entry("REMBL", POLICE_REQUEST),
			entry("REMK", ENFORCEMENT_AUTHORITY_REQUEST),
			entry("REMR", RESCUE_SERVICE_REQUEST),
			entry("REMM", ENVIRONMENT_OFFICE_REQUEST),
			entry("SKVNF", TAX_AGENCY_REQUEST),
			entry("SKVIN", TAX_AGENCY_REQUEST),
			entry("REMA", STATEMENT_REQUEST)),
		ALKOL, Map.ofEntries(
			entry("BEBEL", POLICE_REQUEST),
			entry("REMSKV", TAX_AGENCY_REQUEST),
			entry("REMSKVB", TAX_AGENCY_REQUEST),
			entry("REMKRO", ENFORCEMENT_AUTHORITY_REQUEST)));

	/** The types of the events whose attachment is a referral answer, by source, in upper case. */
	private static final Map<Source, Set<String>> RESPONSE_EVENTS = Map.of(ALKT, Set.of("REMS"), ALKOL, Set.of("029"));

	/** The types of the events whose attachment is a self-monitoring program, by source, in upper case. */
	private static final Map<Source, Set<String>> SELF_MONITORING_EVENTS = Map.of(ALKT, Set.of(), ALKOL, Set.of("035", "037", "038"));

	/** The authorities a referral answer names, in the order they are looked for. */
	private static final List<Map.Entry<Pattern, String>> RESPONDENTS = List.of(
		entry(Pattern.compile("polis"), POLICE_RESPONSE),
		entry(Pattern.compile("skatte"), TAX_AGENCY_RESPONSE),
		entry(Pattern.compile("kronofogd|(?<![a-zåäö])kfm(?![a-zåäö])"), ENFORCEMENT_AUTHORITY_RESPONSE),
		entry(Pattern.compile("r[aä]ddning"), RESCUE_SERVICE_RESPONSE),
		entry(Pattern.compile("milj[oö]"), ENVIRONMENT_OFFICE_RESPONSE),
		entry(Pattern.compile("(?<![a-zåäö])syna(?![a-zåäö])|kreditupplys"), CREDIT_REPORT_RESPONSE));

	private static final String KITCHEN = "kök";
	private static final Pattern KITCHEN_ASCII = Pattern.compile("(?<![a-zåäö])kok(?![a-zåäö])");

	/** The purposes of referrals and their answers, whose files are material of the investigation. */
	private static final Set<String> REFERRALS = Set.of(POLICE_REQUEST, POLICE_RESPONSE, TAX_AGENCY_REQUEST, TAX_AGENCY_RESPONSE, ENFORCEMENT_AUTHORITY_REQUEST,
		ENFORCEMENT_AUTHORITY_RESPONSE, RESCUE_SERVICE_REQUEST, RESCUE_SERVICE_RESPONSE, ENVIRONMENT_OFFICE_REQUEST, ENVIRONMENT_OFFICE_RESPONSE, CREDIT_REPORT_RESPONSE,
		STATEMENT_REQUEST, STATEMENT_RESPONSE);

	/**
	 * The templates of the documents that are an investigation, the communication of one or of referral answers, or the
	 * report of an inspection, by source, in upper case.
	 */
	private static final Map<Source, Set<String>> INVESTIGATION_TEMPLATES = Map.of(
		ALKT, Set.of("TDU", "TDUT", "TNT", "TNA", "TNC", "KOUT", "KOMR", "KOMI"),
		ALKOL, Set.of("UTRTOB", "UTRÄNDT", "UTRTIBT", "KOMU", "KOMR", "KIU", "TOBRAPP", "TIFÖTO"));

	/**
	 * The types of the events whose files are such material, by source, in upper case: communication, the answer to it
	 * and a proposal to the board in AlkT; an investigation, communication, a proposed decision and an inspection
	 * report in AlkOL. AlkOL's investigation of a possible measure has the code {@code <Kod>} in its code list.
	 */
	private static final Map<Source, Set<String>> INVESTIGATION_EVENTS = Map.of(
		ALKT, Set.of("KOMU", "KOMS", "NAMF"),
		ALKOL, Set.of("092", "019", "<KOD>", "025", "094", "063"));

	private FilePurposes() {}

	/**
	 * @param  template  the code of the template the document was made from, null when none
	 * @param  eventType the type of the event the document is on, null for a document of a decision
	 * @param  received  when the event or decision was, null when unknown
	 * @return           the details of a document the source generated: its purpose, when it came in and whether it is
	 *                   material of the investigation of its errand
	 */
	public static FileDetails ofGenerated(final Source source, final String template, final String eventType, final LocalDateTime received) {
		final var purpose = ofDocument(source, template);
		return new FileDetails(purpose, received, isReferral(purpose) || CodeTexts.isOneOf(template, INVESTIGATION_TEMPLATES.get(source))
			|| CodeTexts.isOneOf(eventType, INVESTIGATION_EVENTS.get(source)));
	}

	/**
	 * @param  eventType the type of the event the attachment is on
	 * @param  name      the name of the attachment
	 * @param  received  when the event was, null when unknown
	 * @return           the details of an attachment of an event: its purpose, when it came in and whether it is material
	 *                   of the investigation of its errand
	 */
	public static FileDetails ofAttached(final Source source, final String eventType, final String name, final LocalDateTime received) {
		final var purpose = ofAttachment(source, eventType, name);
		return new FileDetails(purpose, received, isReferral(purpose) || CodeTexts.isOneOf(eventType, INVESTIGATION_EVENTS.get(source)));
	}

	/**
	 * @param  received when the visit was, null when unknown
	 * @return          the details of a file of an inspection visit, its protocol or an attachment, which are material of
	 *                  the investigation of the visit
	 */
	public static FileDetails ofVisit(final Source source, final FileFolder folder, final String name, final LocalDateTime received) {
		final var details = detailsOf(source, folder, name, received);
		return new FileDetails(details.purpose(), details.received(), true);
	}

	/**
	 * @param  template the code of the template the document was made from, null when none
	 * @return          the purpose of a document the source generated, null when it has none
	 */
	public static String ofDocument(final Source source, final String template) {
		return Optional.ofNullable(Texts.trimToNull(template))
			.map(code -> REFERRAL_TEMPLATES.get(source).get(code.toUpperCase(Locale.ROOT)))
			.orElse(null);
	}

	/**
	 * @param  eventType the type of the event the attachment is on, null for an attachment of no event
	 * @param  name      the name of the attachment
	 * @return           the purpose of an attachment, null when it has none
	 */
	public static String ofAttachment(final Source source, final String eventType, final String name) {
		final var type = Optional.ofNullable(Texts.trimToNull(eventType)).map(code -> code.toUpperCase(Locale.ROOT)).orElse("");
		// Composed, so that an å, ä or ö written as a letter and a combining mark matches too. The flag CANON_EQ would
		// do that in the patterns, but then their lookbehinds no longer stop a match after a letter.
		final var lowerCase = Optional.ofNullable(FileNames.normalized(name)).orElse("");
		if (RESPONSE_EVENTS.get(source).contains(type)) {
			return RESPONDENTS.stream()
				.filter(respondent -> respondent.getKey().matcher(lowerCase).find())
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(STATEMENT_RESPONSE);
		}
		if (SELF_MONITORING_EVENTS.get(source).contains(type)) {
			return SELF_MONITORING_PROGRAM;
		}
		if (PermitFiles.isDrawing(name)) {
			if (lowerCase.contains(KITCHEN) || KITCHEN_ASCII.matcher(lowerCase).find()) {
				return KITCHEN_FLOOR_PLAN;
			}
			return source == ALKT ? SERVING_AREA_DRAWING : SALES_PREMISES_DRAWING;
		}
		return null;
	}

	/**
	 * @param  received when the file came in, null when unknown
	 * @return          the details of a file that is on no event, whose only purpose can be that of a drawing
	 */
	public static FileDetails detailsOf(final Source source, final FileFolder folder, final String name, final LocalDateTime received) {
		return new FileDetails(folder == FileFolder.ATTACHMENTS ? ofAttachment(source, null, name) : null, received);
	}

	/**
	 * @return true for the purpose of a drawing, which goes on the post in PartyAssets
	 */
	public static boolean isDrawing(final String purpose) {
		return purpose != null && DRAWINGS.contains(purpose);
	}

	private static boolean isReferral(final String purpose) {
		return purpose != null && REFERRALS.contains(purpose);
	}
}
