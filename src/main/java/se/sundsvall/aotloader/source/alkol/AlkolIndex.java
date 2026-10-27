package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.DiaryNumberCounts;
import se.sundsvall.aotloader.source.ErrandParameters;
import se.sundsvall.aotloader.source.FileList;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.FormerHolders;
import se.sundsvall.aotloader.source.Journal;
import se.sundsvall.aotloader.source.OwnerNumbers;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_COPY_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_MISSING;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.source.alkol.PermitKind.GRANTING_DECISIONS;

/**
 * The snapshot of AlkOL indexed by sales place and errand, with what the tobacco permits, the notifications and the
 * inspections all read from it: the owner and its rules, the latest grant and what ended it, the files of the errands
 * and the texts of their investigations.
 */
final class AlkolIndex {

	/** Orders decisions by when they were made, those without a time first, and then by their ids. */
	static final Comparator<DecisionRow> DECISION_ORDER = Comparator
		.comparing(DecisionRow::beslutad, Comparator.nullsFirst(Comparator.naturalOrder()))
		.thenComparing(DecisionRow::beslutsId, Comparator.nullsFirst(Comparator.naturalOrder()));
	private static final Set<String> ENDING_DECISIONS = Set.of("GTDEU", "GTDEÅ");
	private static final String UNDATED = "utan datum";

	private final AlkolSnapshot snapshot;
	private final LocalDate today;
	private final AotLoaderProperties properties;
	private final Map<Integer, SalesPlaceRow> placesById = new LinkedHashMap<>();
	private final Map<Integer, ErrandRow> errandsById = new HashMap<>();
	private final Map<Integer, List<ErrandRow>> errandsByPlace = new HashMap<>();
	private final Map<Integer, List<DecisionRow>> decisionsByPlace = new HashMap<>();
	private final Map<Integer, List<DecisionRow>> decisionsByErrand = new HashMap<>();
	private final Map<Integer, List<EventRow>> eventsByPlace = new HashMap<>();
	private final Map<Integer, List<EventRow>> eventsByErrand = new HashMap<>();
	private final OwnerNumbers ownerNumbers;
	private final DiaryNumberCounts diaryNumbers = new DiaryNumberCounts();
	private final SharedFileNames sharedFileNames;

	AlkolIndex(final AlkolSnapshot snapshot, final LocalDate today, final AotLoaderProperties properties) {
		this.snapshot = snapshot;
		this.today = today;
		this.properties = properties;
		this.ownerNumbers = new OwnerNumbers(today);
		snapshot.places().forEach(place -> placesById.put(place.fsId(), place));
		indexErrands();
		indexDecisions();
		indexEvents();
		indexOwnerNumbers();
		indexDiaryNumbers();
		this.sharedFileNames = buildSharedFileNames();
	}

	AlkolSnapshot snapshot() {
		return snapshot;
	}

	LocalDate today() {
		return today;
	}

	AotLoaderProperties properties() {
		return properties;
	}

	/**
	 * @return the sales places, in the order read
	 */
	Collection<SalesPlaceRow> places() {
		return placesById.values();
	}

	/**
	 * @return the sales place, null when there is none with the FSID
	 */
	SalesPlaceRow salesPlace(final Integer fsId) {
		return fsId == null ? null : placesById.get(fsId);
	}

	/**
	 * @return the errand, null when Ärende has none with the id
	 */
	ErrandRow errand(final Integer arendeId) {
		return errandsById.get(arendeId);
	}

	List<ErrandRow> errandsOfPlace(final Integer fsId) {
		return errandsByPlace.getOrDefault(fsId, List.of());
	}

	List<EventRow> eventsOfPlace(final Integer fsId) {
		return eventsByPlace.getOrDefault(fsId, List.of());
	}

	List<EventRow> eventsOfErrand(final Integer arendeId) {
		return eventsByErrand.getOrDefault(arendeId, List.of());
	}

	/**
	 * @return the decisions of the errands of the place
	 */
	List<DecisionRow> decisionsOfPlace(final Integer fsId) {
		return decisionsByPlace.getOrDefault(fsId, List.of());
	}

	List<DecisionRow> decisionsOfErrand(final Integer arendeId) {
		return decisionsByErrand.getOrDefault(arendeId, List.of());
	}

	/**
	 * @return a new list for the files of an item, which marks the names that another place refers to too
	 */
	FileList newFileList() {
		return new FileList(sharedFileNames);
	}

	/**
	 * @return the file names of AlkOL that more than one place refers to
	 */
	SharedFileNames sharedFileNames() {
		return sharedFileNames;
	}

	/**
	 * @return the events of the place without an errand; an event whose errand is missing in Ärende counts as one without
	 *         an errand, as in AlkT, so that its text and files are not lost
	 */
	Stream<EventRow> eventsWithoutErrand(final Integer fsId) {
		return eventsOfPlace(fsId).stream().filter(event -> !hasErrand(event));
	}

	/**
	 * @return true for an event whose errand is in Ärende
	 */
	private boolean hasErrand(final EventRow event) {
		return event.arendeId() != null && errandsById.containsKey(event.arendeId());
	}

	ContactRow ownerOf(final SalesPlaceRow place) {
		return place == null || place.agarId() == null ? null : snapshot.owners().get(place.agarId());
	}

	/**
	 * The rules about the owner of the sales place: G21, G8, G20 and G7.
	 */
	void ownerHits(final SalesPlaceRow place, final ContactRow owner, final List<RuleHit> hits) {
		if (place == null) {
			return;
		}
		if (owner == null) {
			hits.add(RuleHit.of(OWNER_MISSING, place.agarId() == null ? "ingen ägare kopplad" : "AgarID %d finns inte".formatted(place.agarId())));
			return;
		}
		final var holder = PartyRows.holder(owner, today);
		if (holder.legalId() == null) {
			hits.add(RuleHit.of(LEGAL_ID_INVALID, "AgarID " + owner.id()));
		}
		final var ownerNumber = PartyRows.numberKey(owner.organisationsNr(), today);
		final var copyNumber = PartyRows.numberKey(place.agareOrgNr(), today);
		if (copyNumber.isPresent() && !copyNumber.equals(ownerNumber)) {
			hits.add(RuleHit.of(OWNER_COPY_DIFFERS));
		}
		ownerNumbers.hits(owner, hits);
	}

	/**
	 * G15 for a grant whose errand has no diary number, and G16 for one whose number another tobacco permit has too.
	 */
	void diaryNumberHits(final ErrandRow grantErrand, final List<RuleHit> hits) {
		diaryNumbers.hits(grantErrand == null ? null : grantErrand.diarieNr(), "det beviljande ärendet saknar diarienummer", hits);
	}

	/**
	 * @return true when the applicant of the errand may not be the current owner of the place: both have a number, and
	 *         they differ, also when one of them cannot be normalized (G19)
	 */
	boolean applicantIsNotOwner(final ErrandRow errand, final ContactRow owner) {
		final var applicant = Optional.ofNullable(errand).map(ErrandRow::sokandeId).map(snapshot.applicants()::get).orElse(null);
		return PartyRows.mayBeOtherHolder(applicant, owner, today);
	}

	/**
	 * @param  errands the errands of the place, all of them or those of one kind
	 * @return         what of them and of the days of the place was a former holder's
	 */
	FormerHolders formerHolders(final SalesPlaceRow place, final List<ErrandRow> errands) {
		return FormerHolders.of(errands, snapshot.applicants(), ownerOf(place), today);
	}

	Optional<DecisionRow> latestGrant(final Integer fsId) {
		return decisionsOfPlace(fsId).stream()
			.filter(decision -> CodeTexts.isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS))
			.max(DECISION_ORDER);
	}

	/**
	 * @return the latest decision that ended the permit after it was granted (G18), also one without a date that was
	 *         entered after the grant; empty when there is none or no grant
	 */
	Optional<DecisionRow> endingAfter(final Integer fsId, final DecisionRow grant) {
		return grant == null ? Optional.empty()
			: decisionsOfPlace(fsId).stream()
				.filter(decision -> CodeTexts.isOneOf(decision.beslutsTyp(), ENDING_DECISIONS))
				.filter(decision -> isAfter(decision, grant))
				.max(DECISION_ORDER);
	}

	/**
	 * @return true when the decision came after the other: by their dates, or by their ids, which follow the order the
	 *         decisions were entered in, when either has no date
	 */
	static boolean isAfter(final DecisionRow decision, final DecisionRow other) {
		if (decision.beslutad() == null || other.beslutad() == null) {
			return Comparator.nullsFirst(Comparator.<Integer>naturalOrder()).compare(decision.beslutsId(), other.beslutsId()) > 0;
		}
		return DECISION_ORDER.compare(decision, other) > 0;
	}

	/**
	 * @return the date of the decision, or "utan datum"
	 */
	static String dateText(final DecisionRow decision) {
		return Optional.ofNullable(SourceValues.format(decision.beslutsDatum())).orElse(UNDATED);
	}

	/**
	 * @return the day the tobacco permit was issued: the first day of the latest grant, else the day it was decided; null
	 *         without a grant
	 */
	static LocalDate issued(final DecisionRow grant) {
		return grant == null ? null : Optional.ofNullable(grant.giltigtFrom()).orElse(grant.beslutsDatum());
	}

	/**
	 * @return true when the place is in the view FS_GT_Tobak, also when a later decision ended its permit (G18)
	 */
	boolean hasPermit(final Integer fsId) {
		return fsId != null && snapshot.permitView().containsKey(fsId);
	}

	/**
	 * @return true when the place is in the view FS_GT_Tobak and no decision ended its permit after its latest grant
	 */
	boolean hasCurrentPermit(final Integer fsId) {
		return hasPermit(fsId) && endingAfter(fsId, latestGrant(fsId).orElse(null)).isEmpty();
	}

	boolean hasCeased(final Integer fsId) {
		return fsId != null && Optional.ofNullable(placesById.get(fsId)).map(SalesPlaceRow::upphort).orElse(false);
	}

	/**
	 * @return the current tobacco permit of the sales place, which its inspections are related to (decision 61); null when
	 *         the place has none
	 */
	PlacePermit placePermit(final Integer fsId) {
		if (!hasPermit(fsId)) {
			return null;
		}
		final var grant = latestGrant(fsId).orElse(null);
		final var grantErrand = grant == null ? null : errandsById.get(grant.arendeId());
		return new PlacePermit(new ItemKey(ALKOL, TILLSTAND, String.valueOf(fsId)), issued(grant), endingAfter(fsId, grant).map(DecisionRow::beslutsDatum).orElse(null),
			placesById.containsKey(fsId) && !applicantIsNotOwner(grantErrand, ownerOf(placesById.get(fsId))));
	}

	/**
	 * What the documentation errands of the place are filed under (decision 71): its tobacco permit when it is current, or
	 * else the notification of the first product it is marked for while it has not ceased, or else a tobacco permit that is
	 * not current, which the documentation then follows: it is migrated only if the permit is.
	 *
	 * @return the permit or notification, null when the place has neither
	 */
	PlacePermit filedUnder(final SalesPlaceRow place) {
		if (place == null) {
			return null;
		}
		if (hasCurrentPermit(place.fsId())) {
			return placePermit(place.fsId());
		}
		final var notification = place.upphort() ? Optional.<Product>empty() : Product.notifiedOn(place).stream().findFirst();
		return notification
			.map(product -> new PlacePermit(new ItemKey(ALKOL, ANMALAN, AlkolNotifications.notificationId(place, product.code())), null, null, true))
			.orElseGet(() -> placePermit(place.fsId()));
	}

	Place place(final SalesPlaceRow place) {
		final var area = snapshot.codes().area(place.omrade());
		return new Place(place.namn(), place.gatuadress(), place.postnummer(), place.postort(), place.telefon(), place.email(), area, new ParameterList()
			.add("kategori", "Kategori", snapshot.codes().category(place.kategori()))
			.add(ErrandParameters.AREA, "Område", area)
			.build());
	}

	/**
	 * @return the texts of the investigation of the errand: its notes, its events, and its decisions with their texts and
	 *         who made them (decisions 44, 46, 52 and 53)
	 */
	InvestigationTexts investigationTexts(final ErrandRow errand) {
		return investigationTexts(errand, null);
	}

	/**
	 * @param  shownNote a note the errand already shows as a parameter, which the investigation leaves out; null when none
	 * @return           the texts of the investigation of the errand, as above, without the note already shown
	 */
	InvestigationTexts investigationTexts(final ErrandRow errand, final String shownNote) {
		return Journal.investigation(
			Stream.ofNullable(errand.noteringar()).filter(note -> !note.equals(shownNote)).toList(),
			eventsOfErrand(errand.arendeId()).stream().map(event -> journalEntry(event, errand.diarieNr())).toList(),
			decisionsOfErrand(errand.arendeId()).stream().map(this::journalEntry).toList());
	}

	/**
	 * @param  errandDiaryNumber the diary number of the errand of the event, null for an event without an errand
	 * @return                   the event as an entry of a journal, headed by the text of its type, marked when
	 *                           confidential and with its own diary number when it is another than the errand's
	 */
	Journal.Entry journalEntry(final EventRow event, final String errandDiaryNumber) {
		return new Journal.Entry(event.tidpunkt(),
			Journal.eventHeading(snapshot.codes().eventType(event.handelseTyp()), event.confidential(), Journal.otherDiaryNumber(event.diarieNr(), errandDiaryNumber)),
			event.text());
	}

	/**
	 * @return the decision as an entry of a journal, headed by the text of its type and the name of who made it
	 */
	Journal.Entry journalEntry(final DecisionRow decision) {
		return Journal.decisionEntry(decision, snapshot.codes().decisionType(decision.beslutsTyp()), snapshot.codes().userName(decision.beslutsfattare()));
	}

	/**
	 * @return the decision as "type, date"
	 */
	String decisionText(final DecisionRow decision) {
		return Texts.join(", ", Arrays.asList(snapshot.codes().decisionType(decision.beslutsTyp()), SourceValues.format(decision.beslutsDatum())));
	}

	/**
	 * Adds the files of the events and decisions of the errands, each with its purpose and date.
	 */
	FileList addErrandFiles(final Collection<Integer> arendeIds, final FileList files) {
		for (final var arendeId : arendeIds) {
			eventsOfErrand(arendeId).forEach(event -> addEventFiles(files, event));
			decisionsOfErrand(arendeId).forEach(decision -> files.addDecisionFiles(ALKOL, decision));
		}
		return files;
	}

	/**
	 * Adds the document and the attachment of the event, each with its purpose and the time of the event.
	 */
	static FileList addEventFiles(final FileList files, final EventRow event) {
		return files
			.add(event.worddokument(), DOCUMENTS, FilePurposes.ofGenerated(ALKOL, event.mall(), event.handelseTyp(), event.tidpunkt()))
			.add(event.bifogadfil(), ATTACHMENTS, FilePurposes.ofAttached(ALKOL, event.handelseTyp(), event.bifogadfil(), event.tidpunkt()));
	}

	/**
	 * @return true for an event whose attachment is a drawing by its purpose
	 */
	static boolean attachesDrawing(final EventRow event) {
		return FilePurposes.isDrawing(FilePurposes.ofAttachment(ALKOL, event.handelseTyp(), event.bifogadfil()));
	}

	/**
	 * Every file reference of AlkOL with the sales place it belongs to: the place of the errand it goes to, or the errand
	 * itself when the errand has no place. An event without an errand, also one whose errand is missing (decision 54),
	 * belongs to its own place, or is a place of its own.
	 */
	private SharedFileNames buildSharedFileNames() {
		final var names = SharedFileNames.builder(properties.fileArea(ALKOL));
		snapshot.events().forEach(event -> {
			final var withoutErrand = event.fsId() != null ? placeKey(event.fsId()) : "handelse:" + event.handelseId();
			final var place = hasErrand(event) ? placeKeyOfErrand(event.arendeId(), withoutErrand) : withoutErrand;
			names.add(place, event.worddokument(), DOCUMENTS).add(place, event.bifogadfil(), ATTACHMENTS);
		});
		snapshot.decisions().forEach(decision -> {
			final var place = placeKeyOfErrand(decision.arendeId(), "beslut:" + decision.beslutsId());
			names.add(place, decision.worddokument(), DOCUMENTS).add(place, decision.bifogadfil(), ATTACHMENTS);
		});
		snapshot.visits().addFileNames(names, AlkolIndex::placeKey);
		return names.build();
	}

	private String placeKeyOfErrand(final Integer arendeId, final String withoutErrand) {
		if (arendeId == null) {
			return withoutErrand;
		}
		return Optional.ofNullable(errandsById.get(arendeId))
			.map(ErrandRow::fsId)
			.map(AlkolIndex::placeKey)
			.orElse("arende:" + arendeId);
	}

	private static String placeKey(final Integer fsId) {
		return "fs:" + fsId;
	}

	private void indexErrands() {
		snapshot.errands().forEach(errand -> {
			errandsById.put(errand.arendeId(), errand);
			Optional.ofNullable(errand.fsId()).ifPresent(fsId -> errandsByPlace.computeIfAbsent(fsId, _ -> new ArrayList<>()).add(errand));
		});
	}

	private void indexDecisions() {
		snapshot.decisions().stream().filter(decision -> decision.arendeId() != null).forEach(decision -> {
			decisionsByErrand.computeIfAbsent(decision.arendeId(), _ -> new ArrayList<>()).add(decision);
			Optional.ofNullable(errandsById.get(decision.arendeId())).map(ErrandRow::fsId)
				.ifPresent(fsId -> decisionsByPlace.computeIfAbsent(fsId, _ -> new ArrayList<>()).add(decision));
		});
	}

	private void indexEvents() {
		snapshot.events().forEach(event -> {
			Optional.ofNullable(event.fsId()).ifPresent(fsId -> eventsByPlace.computeIfAbsent(fsId, _ -> new ArrayList<>()).add(event));
			Optional.ofNullable(event.arendeId()).ifPresent(arendeId -> eventsByErrand.computeIfAbsent(arendeId, _ -> new ArrayList<>()).add(event));
		});
	}

	/**
	 * Counts the owners per number, for G7. Only the owners of the places the migration selects count, as in the review
	 * query.
	 */
	private void indexOwnerNumbers() {
		placesById.values().stream()
			.filter(place -> snapshot.permitView().containsKey(place.fsId()) || (!place.upphort() && Product.hasNotification(place)))
			.map(this::ownerOf)
			.forEach(ownerNumbers::add);
	}

	/**
	 * Counts the tobacco permits per diary number of their latest grant, for G16.
	 */
	private void indexDiaryNumbers() {
		snapshot.permitView().keySet().forEach(fsId -> latestGrant(fsId)
			.map(grant -> errandsById.get(grant.arendeId()))
			.ifPresent(errand -> diaryNumbers.add(errand.diarieNr(), fsId)));
	}
}
