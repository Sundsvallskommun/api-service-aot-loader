package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.CandidateTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.ErrandParameters;
import se.sundsvall.aotloader.source.FileList;
import se.sundsvall.aotloader.source.FormerHolders;
import se.sundsvall.aotloader.source.Journal;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PermitFiles;
import se.sundsvall.aotloader.source.SourceRules;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static java.util.stream.Collectors.toSet;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DRAWING;
import static se.sundsvall.aotloader.Constants.TAG_DIARY_NUMBER;
import static se.sundsvall.aotloader.Constants.TAG_NOTIFICATION;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.ReviewRule.APPLICANT_NOT_OWNER;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_OF_FORMER_HOLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_ONLY_FLAG;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.source.CandidateTexts.UNKNOWN_SALES_PLACE;
import static se.sundsvall.aotloader.source.CandidateTexts.line;

/**
 * Builds the candidates of the notifications of AlkOL, one per active sales place and notified product, from the
 * errands and events that register or confirm the product. Those of a former holder are told apart from the current
 * owner's.
 */
final class AlkolNotifications {

	private static final String SERVING_PLACE_CATEGORY_PREFIX = "serveringsställe";

	private final AlkolIndex index;
	private final AlkolSnapshot snapshot;
	private final LocalDate today;

	AlkolNotifications(final AlkolIndex index) {
		this.index = index;
		this.snapshot = index.snapshot();
		this.today = index.today();
	}

	/**
	 * @return the id of the notification of a product on a sales place, e.g. 12-FOLKOL
	 */
	static String notificationId(final SalesPlaceRow place, final String productCode) {
		return place.fsId() + "-" + productCode;
	}

	/**
	 * @param  place   an active sales place
	 * @param  product a product the place is marked for
	 * @return         the candidate of the notification of the product on the place
	 */
	Candidate notification(final SalesPlaceRow place, final Product product) {
		final var hits = new ArrayList<RuleHit>();
		final var owner = index.ownerOf(place);
		index.ownerHits(place, owner, hits);
		final var registrations = registrations(place, product, owner);
		final var notified = notifiedDate(place, registrations);
		notifiedDateHits(product, registrations, notified, hits);
		SourceRules.openErrandHits(registrations.errands(), hits);
		formerHolderHits(registrations, hits);

		final var current = registrations.ofCurrentHolder();
		final var type = product.errandType(isServing(place, product, current.errands(), current.events()));
		final var diaryNumber = latestDiaryNumber(current.errands(), current.events());
		final var name = Optional.ofNullable(place.namn()).orElse(UNKNOWN_SALES_PLACE);
		final var subject = "Anmälan om " + productText(type);
		final var premises = index.place(place);
		final var ownEvents = ownEvents(place, product);
		final var files = files(product, registrations, ownEvents);
		final var assetParameters = new AssetParameters()
			.premises(premises, null)
			.text(AssetParameters.PRODUCT, product.parameterText())
			.text(AssetParameters.DIARY_NUMBER, diaryNumber)
			.text(AssetParameters.NOTIFIED_DATE_ESTIMATED, notified.estimated() ? Boolean.TRUE.toString() : null)
			.text(AssetParameters.MIGRATED_FROM, ALKOL.getDisplayName())
			.build();

		return Candidate.builder(new ItemKey(ALKOL, ANMALAN, notificationId(place, product.code())), type)
			.placeName(place.namn())
			.ruleHits(hits)
			.holder(PartyRows.holder(owner, today))
			.place(premises)
			.title(Texts.title(subject, name))
			.description(Texts.join("\n", Arrays.asList(
				subject + " för " + name + ".",
				line("Anmäld", notified.text()),
				line("Diarienummer", diaryNumber))))
			.labelPaths(index.properties().errandType(type).labels())
			.externalTag(TAG_NOTIFICATION, notificationId(place, product.code()))
			.externalTag(TAG_DIARY_NUMBER, diaryNumber)
			.parameters(new ParameterList()
				.add(ErrandParameters.ALKOL_FS_ID, "Id i AlkOL", String.valueOf(place.fsId()))
				.add("produkt", "Produkt", product.parameterText())
				.add("anmald", "Anmäld", SourceValues.format(notified.date()))
				.add("anmaldUppskattat", "Datumet är uppskattat", notified.estimated() ? "Ja" : null)
				.add(ErrandParameters.DIARY_NUMBER, "Diarienummer i AlkOL", diaryNumber)
				.add(ErrandParameters.NOTES, "Noteringar i AlkOL", registrations.errands().stream().map(ErrandRow::noteringar).filter(Objects::nonNull).distinct().toList())
				.table(ErrandParameters.EVENT_LOG, "Händelselogg i AlkOL", Journal.COLUMNS, Journal.rows(log(registrations, ownEvents)))
				.add(CandidateTexts.migrated(today, ALKOL))
				.build())
			.asset(new AssetData(null, subject + " på " + name, notified.date(), null, assetParameters))
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	/**
	 * @return the events of the place without an errand whose files and texts go to the notification: those that register
	 *         or confirm the product (decision 37). The place's other events become documentation errands (decision 71).
	 */
	private List<EventRow> ownEvents(final SalesPlaceRow place, final Product product) {
		return index.eventsWithoutErrand(place.fsId())
			.filter(product::concerns)
			.toList();
	}

	/**
	 * The files of the notification: those of its errands and of its own events without an errand (decision 37). The
	 * drawings, the notification itself, which is an attachment of an event that registers the product, and the letters
	 * that confirm it, from its errands and the events that register or confirm the product, also go on the post in
	 * PartyAssets: the drawings in the category of their purpose, the others without one, as pw-alkt puts a file without a
	 * purpose there (decisions 38 and 40). Those of a former holder's registrations stay on the errand only (G31), as does
	 * anything else, such as an investigation or a referral answer.
	 */
	private FileList files(final Product product, final Registrations registrations, final List<EventRow> ownEvents) {
		final var files = index.addErrandFiles(registrations.errands().stream().map(ErrandRow::arendeId).toList(), index.newFileList());
		ownEvents.forEach(event -> AlkolIndex.addEventFiles(files, event));

		// Marked first, since a file marked for the errand only never goes on the post
		final var former = registrations.ofFormerHolders();
		postFileNames(product, former).forEach(file -> files.addToErrandOnly(file.name(), file.folder()));

		final var current = registrations.ofCurrentHolder();
		final var events = postEvents(current);
		final var decisions = decisionsOf(current.errands());
		// The drawings first, since a file keeps the category it is first marked with
		Stream.concat(events.stream().filter(AlkolIndex::attachesDrawing).map(EventRow::bifogadfil),
			decisions.stream().map(DecisionRow::bifogadfil).filter(PermitFiles::isDrawing))
			.forEach(name -> files.add(name, ATTACHMENTS, ASSET_CATEGORY_DRAWING));
		postFileNames(product, current).forEach(file -> files.addToPost(file.name(), file.folder()));
		return files;
	}

	/**
	 * @return the notifications themselves and the letters that confirm them, among the files of the registrations and
	 *         their errands, and the drawings
	 */
	private List<FileReference> postFileNames(final Product product, final Registrations registrations) {
		final var events = postEvents(registrations);
		final var decisions = decisionsOf(registrations.errands());
		return Stream.of(
			events.stream().filter(AlkolIndex::attachesDrawing).flatMap(event -> reference(event.bifogadfil(), ATTACHMENTS)),
			decisions.stream().map(DecisionRow::bifogadfil).filter(PermitFiles::isDrawing).flatMap(name -> reference(name, ATTACHMENTS)),
			events.stream().filter(product::concerns).flatMap(event -> reference(event.bifogadfil(), ATTACHMENTS)),
			events.stream().filter(event -> product.isConfirmationTemplate(event.mall())).flatMap(event -> reference(event.worddokument(), DOCUMENTS)),
			decisions.stream().filter(decision -> product.isConfirmationTemplate(decision.mall())).flatMap(decision -> reference(decision.worddokument(), DOCUMENTS)))
			.flatMap(references -> references)
			.toList();
	}

	private static Stream<FileReference> reference(final String name, final FileFolder folder) {
		return Stream.ofNullable(Texts.trimToNull(name)).map(trimmed -> new FileReference(trimmed, folder));
	}

	/**
	 * @return the events of the errands of the registrations, and the registrations' events without one of those errands
	 */
	private List<EventRow> postEvents(final Registrations registrations) {
		final var arendeIds = registrations.errands().stream().map(ErrandRow::arendeId).collect(toSet());
		return Stream.concat(arendeIds.stream().flatMap(arendeId -> index.eventsOfErrand(arendeId).stream()),
			registrations.events().stream().filter(event -> !arendeIds.contains(event.arendeId())))
			.distinct()
			.toList();
	}

	private List<DecisionRow> decisionsOf(final List<ErrandRow> errands) {
		return errands.stream().flatMap(errand -> index.decisionsOfErrand(errand.arendeId()).stream()).toList();
	}

	/**
	 * @return the texts of the notification's errands and events, for its event log: the events and decisions of its
	 *         errands and the place's own events that go to it, as the files do; a notification has no investigation
	 */
	private List<Journal.Entry> log(final Registrations registrations, final List<EventRow> ownEvents) {
		final var errandEntries = registrations.errands().stream().flatMap(errand -> Stream.concat(
			index.eventsOfErrand(errand.arendeId()).stream().filter(event -> event.text() != null).map(event -> index.journalEntry(event, errand.diarieNr())),
			index.decisionsOfErrand(errand.arendeId()).stream().filter(decision -> decision.beslutsText() != null).map(index::journalEntry)));
		final var ownEntries = ownEvents.stream().filter(event -> event.text() != null).map(event -> index.journalEntry(event, null));
		return Stream.concat(errandEntries, ownEntries).toList();
	}

	/**
	 * @return the registrations of the product on the place: its errands, and its events that register or confirm it,
	 *         with or without an errand. Which of the errands and days were a former holder's is told by the errands of the
	 *         product alone ({@link FormerHolders}); an event of an errand goes with its errand.
	 */
	private Registrations registrations(final SalesPlaceRow place, final Product product, final ContactRow owner) {
		final var errands = index.errandsOfPlace(place.fsId()).stream()
			.filter(errand -> product.isErrandType(errand.arendeTyp()))
			.toList();
		final var events = index.eventsOfPlace(place.fsId()).stream()
			.filter(product::concerns)
			.toList();
		final var former = FormerHolders.of(errands, snapshot.applicants(), owner, today);
		final var errandIds = errands.stream().map(ErrandRow::arendeId).collect(toSet());
		final var formerEvents = events.stream()
			.filter(event -> errandIds.contains(event.arendeId()) ? former.errandIds().contains(event.arendeId()) : former.isUpTo(dateOf(event)))
			.collect(toSet());
		return new Registrations(errands, events, former.errandIds(), formerEvents);
	}

	/**
	 * G31 when a former holder registered the product, whose files stay on the errand only, and G19 when no registration is
	 * the current owner's.
	 */
	private static void formerHolderHits(final Registrations registrations, final List<RuleHit> hits) {
		final var former = registrations.ofFormerHolders();
		if (former.errands().isEmpty() && former.events().isEmpty()) {
			return;
		}
		hits.add(RuleHit.of(NOTIFICATION_OF_FORMER_HOLDER, Texts.join(", ", former.errands().stream().map(errand -> CandidateTexts.errand(errand.arendeId(), errand.diarieNr())).toList())));
		final var current = registrations.ofCurrentHolder();
		if (current.errands().isEmpty() && current.events().isEmpty()) {
			hits.add(RuleHit.of(APPLICANT_NOT_OWNER, "anmälan är gjord av en tidigare ägare"));
		}
	}

	private static LocalDate dateOf(final EventRow event) {
		return event.tidpunkt() == null ? null : event.tidpunkt().toLocalDate();
	}

	/**
	 * @param  place   a sales place
	 * @param  product a product the place is marked for
	 * @return         the date the notification of the product on the place has, as below
	 */
	NotifiedDate notifiedDate(final SalesPlaceRow place, final Product product) {
		return notifiedDate(place, registrations(place, product, index.ownerOf(place)));
	}

	/**
	 * @return the date of the notification: the first registration of the product, or, when the product is only a mark on
	 *         the sales place, the earliest date of the place
	 */
	private NotifiedDate notifiedDate(final SalesPlaceRow place, final Registrations registrations) {
		if (registrations.errands().isEmpty() && registrations.events().isEmpty()) {
			final var earliest = earliestDate(place.fsId()).orElse(null);
			return new NotifiedDate(earliest, earliest != null);
		}
		// The current holder's first registration, or the first of all when every one is a former holder's (G19)
		final var current = registrations.ofCurrentHolder();
		final var dated = current.errands().isEmpty() && current.events().isEmpty() ? registrations : current;
		return new NotifiedDate(Stream.concat(dated.errands().stream().map(ErrandRow::openedOrRegistered), dated.events().stream().map(AlkolNotifications::dateOf))
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder())
			.orElse(null), false);
	}

	/**
	 * G22 when the product is only a mark on the sales place, and G23 when the notification has no date at all.
	 */
	private static void notifiedDateHits(final Product product, final Registrations registrations, final NotifiedDate notified, final List<RuleHit> hits) {
		if (registrations.errands().isEmpty() && registrations.events().isEmpty()) {
			hits.add(RuleHit.of(NOTIFICATION_ONLY_FLAG, product.parameterText()));
			if (notified.date() == null) {
				hits.add(RuleHit.of(NOTIFICATION_DATE_MISSING, product.parameterText()));
			}
		} else if (notified.date() == null) {
			hits.add(RuleHit.of(NOTIFICATION_DATE_MISSING, product.parameterText() + ", anmälan saknar datum"));
		}
	}

	/**
	 * @return the diary number of the latest errand of the notification, else of its latest event
	 */
	private static String latestDiaryNumber(final List<ErrandRow> errands, final List<EventRow> events) {
		return errands.stream()
			.filter(errand -> errand.diarieNr() != null)
			.max(Comparator.comparing(ErrandRow::openedOrRegistered, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(ErrandRow::arendeId))
			.map(ErrandRow::diarieNr)
			.or(() -> events.stream()
				.filter(event -> event.diarieNr() != null)
				.max(Comparator.comparing(EventRow::tidpunkt, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(EventRow::handelseId, Comparator.nullsFirst(Comparator.naturalOrder())))
				.map(EventRow::diarieNr))
			.orElse(null);
	}

	/**
	 * Whether a notification of folköl is about serving: the latest notification of folköl decides, an errand or an event
	 * that registers one, not one that confirms it; otherwise the category of the sales place (section 7.4).
	 */
	private boolean isServing(final SalesPlaceRow place, final Product product, final List<ErrandRow> errands, final List<EventRow> events) {
		record Registration(LocalDateTime when, boolean serving) {
		}
		final var latest = Stream.concat(
			errands.stream().map(errand -> new Registration(Optional.ofNullable(errand.openedOrRegistered()).map(LocalDate::atStartOfDay).orElse(null), product.isServingCode(errand.arendeTyp()))),
			events.stream().filter(product::isRegistration).map(event -> new Registration(event.tidpunkt(), product.isServingCode(event.handelseTyp()))))
			.max(Comparator.comparing(Registration::when, Comparator.nullsFirst(Comparator.naturalOrder())));
		if (latest.isPresent()) {
			return latest.get().serving();
		}
		return Optional.ofNullable(snapshot.codes().category(place.kategori()))
			.map(text -> text.toLowerCase(Locale.ROOT).startsWith(SERVING_PLACE_CATEGORY_PREFIX))
			.orElse(false);
	}

	/**
	 * @return the earliest date of the sales place's errands and events
	 */
	private Optional<LocalDate> earliestDate(final Integer fsId) {
		return Stream.concat(
			index.errandsOfPlace(fsId).stream().map(ErrandRow::openedOrRegistered),
			index.eventsOfPlace(fsId).stream().map(AlkolNotifications::dateOf))
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder());
	}

	private static String productText(final ErrandType type) {
		return switch (type) {
			case FOLKOL_SALES_NOTIFICATION -> "försäljning av folköl";
			case FOLKOL_SERVING_NOTIFICATION -> "servering av folköl";
			case ECIGARETTE_NOTIFICATION -> "försäljning av elektroniska cigaretter och påfyllningsbehållare";
			case TOBACCO_FREE_NICOTINE_NOTIFICATION -> "försäljning av tobaksfria nikotinprodukter";
			default -> throw new IllegalArgumentException("Inte en anmälan: " + type);
		};
	}

	/**
	 * The registrations of a product on a sales place.
	 *
	 * @param errands      the errands of the product's types
	 * @param events       the events that register or confirm the product, with or without an errand
	 * @param formerIds    the ArendeID of the errands of former holders
	 * @param formerEvents the events of former holders
	 */
	private record Registrations(List<ErrandRow> errands, List<EventRow> events, Set<Integer> formerIds, Set<EventRow> formerEvents) {

		Registrations ofCurrentHolder() {
			return new Registrations(errands.stream().filter(errand -> !formerIds.contains(errand.arendeId())).toList(),
				events.stream().filter(event -> !formerEvents.contains(event)).toList(), Set.of(), Set.of());
		}

		Registrations ofFormerHolders() {
			return new Registrations(errands.stream().filter(errand -> formerIds.contains(errand.arendeId())).toList(),
				events.stream().filter(formerEvents::contains).toList(), formerIds, formerEvents);
		}
	}

	/**
	 * The date of a notification.
	 *
	 * @param date      the date, null when unknown
	 * @param estimated true when the date is the earliest date of the sales place rather than of the notification
	 */
	record NotifiedDate(LocalDate date, boolean estimated) {

		/**
		 * @return the date as "ÅÅÅÅ-MM-DD", followed by " (uppskattat)" when it is estimated, or null when it is unknown
		 */
		String text() {
			if (date == null) {
				return null;
			}
			return SourceValues.format(date) + (estimated ? " (uppskattat)" : "");
		}
	}
}
