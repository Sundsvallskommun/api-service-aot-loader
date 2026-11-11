package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.HolderHistory;
import se.sundsvall.aotloader.service.model.HolderHistory.EarlierOwner;
import se.sundsvall.aotloader.service.model.HolderHistory.EarlierPermit;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.DiaryNumberCounts;
import se.sundsvall.aotloader.source.FileList;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.FormerHolders;
import se.sundsvall.aotloader.source.Journal;
import se.sundsvall.aotloader.source.OwnerNumbers;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PermitFiles;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.TextRow;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Source.ALKT;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_AREA;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_DECISION;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_EVENT;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_PLACE_GROUP;

/**
 * The snapshot of AlkT indexed by place and errand, with what the serving permits and the inspections both read from
 * it: the place and its owner, the current permit of a place, the documents of a place, the files of the errands and
 * the texts of their investigations.
 */
final class AlktIndex {

	private static final Set<String> PLACE_MARKINGS = Set.of("UNG", "UTOK");
	private static final String PICTURE_HEADING = "Bild";
	private static final Comparator<PlaceDocument> DOCUMENT_ORDER = Comparator
		.comparing(PlaceDocument::tidpunkt, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
		.thenComparing(PlaceDocument::order, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()));

	private final AlktSnapshot snapshot;
	private final LocalDate today;
	private final AotLoaderProperties properties;
	private final OwnerNumbers ownerNumbers;
	private final DiaryNumberCounts diaryNumbers = new DiaryNumberCounts();
	private final Map<Integer, List<PermitRow>> permitRowsByPlace = new HashMap<>();
	private final Map<Integer, List<DecisionRow>> decisionsByErrand = new HashMap<>();
	private final Map<Integer, List<ErrandRow>> errandsByPlace = new HashMap<>();
	private final Map<Integer, List<PlaceDocument>> documentsByPlace = new HashMap<>();
	private final SharedFileNames sharedFileNames;

	AlktIndex(final AlktSnapshot snapshot, final LocalDate today, final AotLoaderProperties properties) {
		this.snapshot = snapshot;
		this.today = today;
		this.properties = properties;
		this.ownerNumbers = new OwnerNumbers(today);
		this.sharedFileNames = buildSharedFileNames(snapshot, properties);
		snapshot.permits().forEach(permit -> {
			Optional.ofNullable(permit.objektId()).ifPresent(objektId -> permitRowsByPlace.computeIfAbsent(objektId, _ -> new ArrayList<>()).add(permit));
			// G16 counts places, so that the rows of one place (G26) with the same diary number count once
			diaryNumbers.add(permit.utfardandeDiarienr(), permit.objektId());
			ownerNumbers.add(ownerOf(placeOf(permit.objektId())).orElse(null));
		});
		snapshot.decisions().values().stream()
			.filter(decision -> decision.arendeId() != null)
			.forEach(decision -> decisionsByErrand.computeIfAbsent(decision.arendeId(), _ -> new ArrayList<>()).add(decision));
		snapshot.errands().values().stream()
			.filter(errand -> errand.objektId() != null)
			.forEach(errand -> errandsByPlace.computeIfAbsent(errand.objektId(), _ -> new ArrayList<>()).add(errand));
		indexDocuments();
	}

	AlktSnapshot snapshot() {
		return snapshot;
	}

	LocalDate today() {
		return today;
	}

	AotLoaderProperties properties() {
		return properties;
	}

	/**
	 * @return the rows of Gällande_Tillstånd of the place, more than one for a place with several (G26)
	 */
	List<PermitRow> permitRowsOf(final Integer objektId) {
		return permitRowsByPlace.getOrDefault(objektId, List.of());
	}

	/**
	 * @return true when the place has a current permit
	 */
	boolean hasPermit(final Integer objektId) {
		return permitRowsByPlace.containsKey(objektId);
	}

	PlaceRow placeOf(final Integer objektId) {
		return objektId == null ? null : snapshot.places().get(objektId);
	}

	/**
	 * @return the errands of the place, of any type and holder
	 */
	List<ErrandRow> errandsOfPlace(final Integer objektId) {
		return errandsByPlace.getOrDefault(objektId, List.of());
	}

	/**
	 * @return the errands of the place whose decisions may change its permit: all but those of former holders, which
	 *         concern nothing of the current holder (decisions 72 and 115)
	 */
	List<ErrandRow> errandsThatMayChange(final Integer objektId) {
		final var former = formerHolders(objektId).errandIds();
		return errandsOfPlace(objektId).stream().filter(errand -> !former.contains(errand.arendeId())).toList();
	}

	/**
	 * @return what of the errands and the days of the place was a former holder's, told by all its errands (decision 72)
	 */
	FormerHolders formerHolders(final Integer objektId) {
		return FormerHolders.of(errandsOfPlace(objektId), snapshot.applicants(), ownerOf(placeOf(objektId)).orElse(null), today);
	}

	Optional<ContactRow> ownerOf(final PlaceRow place) {
		return Optional.ofNullable(place).map(PlaceRow::agarId).map(snapshot.owners()::get);
	}

	List<DecisionRow> decisionsOf(final Integer arendeId) {
		return decisionsByErrand.getOrDefault(arendeId, List.of());
	}

	/**
	 * G7 when another owner post has the number of the owner.
	 */
	void ownerNumberHits(final ContactRow owner, final List<RuleHit> hits) {
		ownerNumbers.hits(owner, hits);
	}

	/**
	 * G15 for a permit without a diary number, and G16 for one whose number another place's permit has too.
	 */
	void diaryNumberHits(final PermitRow permit, final List<RuleHit> hits) {
		diaryNumbers.hits(permit.utfardandeDiarienr(), null, hits);
	}

	/**
	 * @return a new list for the files of an item, which marks the names that another place refers to too
	 */
	FileList newFileList() {
		return new FileList(sharedFileNames);
	}

	/**
	 * @return the file names of AlkT that more than one place refers to
	 */
	SharedFileNames sharedFileNames() {
		return sharedFileNames;
	}

	Place place(final PlaceRow place) {
		return new Place(place.namn(), place.adress(), place.postnummer(), place.postort(), place.telefon(), place.email(), snapshot.codeText(CODE_TYPE_AREA, place.omrade()),
			place.restaurangBeteckning());
	}

	/**
	 * @return the types of the place as one text, such as "Utpräglad matrestaurang, Hotellrestaurang": the texts of its
	 *         groups in code list Q, in the order of ObjektGrupp1-3 and each once, without the markings UNG and UTOK, which
	 *         are no types; null when it has none (decision 95)
	 */
	String placeTypes(final PlaceRow place) {
		return Texts.join(", ", place.objektGrupper().stream()
			.filter(group -> !CodeTexts.isOneOf(group, PLACE_MARKINGS))
			.map(group -> Texts.trimToNull(snapshot.codeText(CODE_TYPE_PLACE_GROUP, group)))
			.distinct()
			.toList());
	}

	/**
	 * @return the current permit of the place, which its inspections are related to (decision 61); null when the place
	 *         has none. A place with several permits has no day that tells which holder an inspection concerned.
	 */
	PlacePermit placePermit(final Integer objektId) {
		final var rows = permitRowsOf(objektId);
		if (rows.isEmpty()) {
			return null;
		}
		final var key = new ItemKey(ALKT, TILLSTAND, String.valueOf(objektId));
		if (rows.size() > 1) {
			return new PlacePermit(key, null, null, false);
		}
		final var permit = rows.getFirst();
		final var place = placeOf(objektId);
		return new PlacePermit(key, permit.utfardandeDatum(), ServingPermits.validTo(permit, PermitPeriods.of(permit)), issuedToOwner(permit, place), holderHistory(objektId, place));
	}

	/**
	 * @return the earlier permits and owners of the place, with the number of its current owner, the holder of its permit;
	 *         they show whether an inspection before the day the permit was issued concerned that holder (decision 114).
	 *         An earlier permit without a number of its own has the number of its owner post.
	 */
	HolderHistory holderHistory(final Integer objektId, final PlaceRow place) {
		final var holder = ownerOf(place).flatMap(owner -> PartyRows.numberKey(owner.organisationsNr(), today)).orElse(null);
		final var permits = snapshot.earlierPermitsOf(objektId).stream()
			.map(row -> new EarlierPermit(row.utfardandeDatum(), PartyRows.numberKey(row.organisationsNr(), today)
				.or(() -> Optional.ofNullable(row.agarId()).map(snapshot.owners()::get).flatMap(owner -> PartyRows.numberKey(owner.organisationsNr(), today)))
				.orElse(null)))
			.toList();
		final var owners = snapshot.earlierOwnersOf(objektId).stream()
			.map(row -> new EarlierOwner(row.datumFrom(), row.datumTom(), PartyRows.numberKey(row.organisationsNr(), today).orElse(null)))
			.toList();
		return new HolderHistory(holder, permits, owners);
	}

	/**
	 * @return false when the permit may have been issued to another than the current owner of the place (G2), or the place
	 *         is missing; true when either owner is unknown. Two owner posts with the same number are the same holder, as
	 *         holders are compared everywhere (section 7.7.1), and without a number for both the posts are compared.
	 */
	boolean issuedToOwner(final PermitRow permit, final PlaceRow place) {
		if (place == null) {
			return false;
		}
		if (permit.gtAgarId() == null || place.agarId() == null || permit.gtAgarId().equals(place.agarId())) {
			return true;
		}
		final var permitNumber = Optional.ofNullable(snapshot.owners().get(permit.gtAgarId())).flatMap(owner -> PartyRows.numberKey(owner.organisationsNr(), today));
		final var placeNumber = Optional.ofNullable(snapshot.owners().get(place.agarId())).flatMap(owner -> PartyRows.numberKey(owner.organisationsNr(), today));
		return permitNumber.isPresent() && permitNumber.equals(placeNumber);
	}

	/**
	 * @return the latest document of the place made from one of the templates, from its events or decisions
	 */
	Optional<PlaceDocument> latestDocument(final Integer objektId, final Set<String> templates) {
		return documentsByPlace.getOrDefault(objektId, List.of()).stream()
			.filter(document -> PermitFiles.isTemplate(document.mall(), templates))
			.max(DOCUMENT_ORDER);
	}

	/**
	 * Adds the files of the events, pictures and decisions of the errands, each with its purpose and date.
	 */
	FileList errandFiles(final Collection<Integer> arendeIds, final FileList files) {
		for (final var arendeId : arendeIds) {
			snapshot.filesOf(arendeId).forEach(file -> files.add(file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS, file.details()));
			decisionsOf(arendeId).forEach(decision -> files.addDecisionFiles(ALKT, decision));
		}
		return files;
	}

	/**
	 * @return the texts of the investigation of the errands: their notes, their events and the texts of their pictures, and
	 *         their decisions with their texts and who made them (decisions 44, 46, 52 and 53)
	 */
	InvestigationTexts investigationTexts(final Collection<Integer> arendeIds) {
		return investigationTexts(arendeIds, List.of(), List.of());
	}

	/**
	 * @param  ownNotes   notes of the permit itself, which come first among the notes; a note of an errand that is the
	 *                    same comes only once, since AlkT copies the notes of the issuing errand to the permit
	 * @param  shownNotes notes the errand's description already shows, which the investigation leaves out
	 * @return            the texts of the investigation of the errands, as above, with the permit's own notes first and
	 *                    without the notes already shown
	 */
	InvestigationTexts investigationTexts(final Collection<Integer> arendeIds, final List<String> ownNotes, final Collection<String> shownNotes) {
		final var errandNotes = arendeIds.stream().map(snapshot.errands()::get).filter(Objects::nonNull).flatMap(errand -> errand.notes().stream());
		return Journal.investigation(
			Stream.concat(ownNotes.stream(), errandNotes).filter(note -> !Texts.isBlank(note)).filter(note -> !shownNotes.contains(note)).distinct().toList(),
			arendeIds.stream()
				.flatMap(arendeId -> entries(snapshot.textsOf(arendeId), Optional.ofNullable(snapshot.errands().get(arendeId)).map(ErrandRow::diarieNr).orElse(null)).stream())
				.toList(),
			arendeIds.stream().flatMap(arendeId -> decisionsOf(arendeId).stream())
				.map(decision -> Journal.decisionEntry(decision, snapshot.codeText(CODE_TYPE_DECISION, decision.beslutsTyp()), snapshot.visits().handlerName(decision.beslutsfattare())))
				.toList());
	}

	/**
	 * @param  errandDiaryNumber the diary number of the errand of the events, null for events without an errand
	 * @return                   the events and pictures as entries of a journal, each headed by the text of its event type,
	 *                           marked when confidential and with its own diary number when it is another than the
	 *                           errand's, or "Bild" for a picture
	 */
	List<Journal.Entry> entries(final List<TextRow> texts, final String errandDiaryNumber) {
		return texts.stream()
			.map(text -> new Journal.Entry(text.tidpunkt(), text.picture() ? PICTURE_HEADING
				: Journal.eventHeading(snapshot.codeText(CODE_TYPE_EVENT, text.handelseTyp()), text.confidential(), Journal.otherDiaryNumber(text.diarieNr(), errandDiaryNumber)),
				text.text()))
			.toList();
	}

	/**
	 * @return the decision as "type, date", null for no decision
	 */
	String decisionText(final DecisionRow decision) {
		if (decision == null) {
			return null;
		}
		return Texts.join(", ", Arrays.asList(snapshot.codeText(CODE_TYPE_DECISION, decision.beslutsTyp()), SourceValues.format(decision.beslutsDatum())));
	}

	/**
	 * Indexes the documents of the events and the decisions by their place: the place of their errand, or, for an event
	 * without an errand, the event's own place.
	 */
	private void indexDocuments() {
		snapshot.documents().forEach(document -> Optional.ofNullable(document.arendeId())
			.map(snapshot.errands()::get)
			.map(ErrandRow::objektId)
			.or(() -> Optional.ofNullable(document.objektId()))
			.ifPresent(objektId -> documentsByPlace.computeIfAbsent(objektId, _ -> new ArrayList<>())
				.add(new PlaceDocument(document.name(), document.mall(), document.tidpunkt(), document.handelseId()))));
		snapshot.decisions().values().stream()
			.filter(decision -> decision.worddokument() != null && decision.arendeId() != null)
			.forEach(decision -> Optional.ofNullable(snapshot.errands().get(decision.arendeId()))
				.map(ErrandRow::objektId)
				.ifPresent(objektId -> documentsByPlace.computeIfAbsent(objektId, _ -> new ArrayList<>())
					.add(new PlaceDocument(decision.worddokument(), decision.mall(), decision.beslutad(), decision.beslutsId()))));
	}

	/**
	 * Every file reference of AlkT with the place it belongs to: the place of its errand or visit, or the errand or visit
	 * itself when it has no place, and the place's own files.
	 */
	private static SharedFileNames buildSharedFileNames(final AlktSnapshot snapshot, final AotLoaderProperties properties) {
		final var names = SharedFileNames.builder(properties.fileArea(ALKT));
		snapshot.errandFiles().forEach((arendeId, files) -> {
			final var place = placeKeyOfErrand(snapshot, arendeId);
			files.forEach(file -> names.add(place, file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS));
		});
		snapshot.decisions().values().forEach(decision -> {
			final var place = decision.arendeId() == null ? "beslut:" + decision.beslutsId() : placeKeyOfErrand(snapshot, decision.arendeId());
			names.add(place, decision.worddokument(), DOCUMENTS).add(place, decision.bifogadfil(), ATTACHMENTS);
		});
		snapshot.places().forEach((objektId, place) -> names
			.add(placeKey(objektId), place.bifogadfil(), ATTACHMENTS)
			.add(placeKey(objektId), place.bifogadfil2(), ATTACHMENTS));
		snapshot.placeEvents().forEach((objektId, events) -> events.stream()
			.flatMap(event -> event.files().stream())
			.forEach(file -> names.add(placeKey(objektId), file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS)));
		snapshot.unplacedFiles().forEach((handelseId, files) -> files
			.forEach(file -> names.add("handelse:" + handelseId, file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS)));
		snapshot.visits().addFileNames(names, AlktIndex::placeKey);
		return names.build();
	}

	private static String placeKeyOfErrand(final AlktSnapshot snapshot, final Integer arendeId) {
		return Optional.ofNullable(snapshot.errands().get(arendeId))
			.map(ErrandRow::objektId)
			.map(AlktIndex::placeKey)
			.orElse("arende:" + arendeId);
	}

	private static String placeKey(final Integer objektId) {
		return "objekt:" + objektId;
	}

	/**
	 * A document of a place, from an event or a decision.
	 *
	 * @param tidpunkt when the event or decision was, null when unknown
	 * @param order    the id of the event or decision, which orders documents of the same time
	 */
	record PlaceDocument(String name, String mall, LocalDateTime tidpunkt, Integer order) {

		LocalDate datum() {
			return tidpunkt == null ? null : tidpunkt.toLocalDate();
		}

		FileDetails details() {
			return FilePurposes.ofGenerated(ALKT, mall, null, tidpunkt);
		}
	}
}
