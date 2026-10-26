package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.source.DocumentationCandidates;
import se.sundsvall.aotloader.source.DocumentationCandidates.PlaceEvent;
import se.sundsvall.aotloader.source.FormerHolders;
import se.sundsvall.aotloader.source.InspectionCandidates;
import se.sundsvall.aotloader.source.InspectionCandidates.InspectionErrand;
import se.sundsvall.aotloader.source.InspectionVisitCandidates;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceEventRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.TextRow;

import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.Source.ALKT;
import static se.sundsvall.aotloader.source.CandidateTexts.UNKNOWN_SERVING_PLACE;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_ERRAND;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_EVENT;

/**
 * Turns what was read from AlkT into candidates: the current serving permits (sections 7.2–7.6), the inspection
 * errands (section 7.7) and the inspection visits (section 7.7.2), with the review rules that AlkT itself shows.
 */
public final class AlktCandidateFactory {

	private final AlktIndex index;
	private final AlktPermits permits;

	public AlktCandidateFactory(final AlktSnapshot snapshot, final LocalDate today, final AotLoaderProperties properties) {
		this.index = new AlktIndex(snapshot, today, properties);
		this.permits = new AlktPermits(index);
	}

	/**
	 * @return the current serving permits, one per place, with a notice for each permit that has no ObjektID and therefore
	 *         no id. A place with several rows (G26) is one item, built from its first row, since the rows would share
	 *         their key.
	 */
	public ReaderResult permits() {
		final var candidates = new ArrayList<Candidate>();
		final var notices = new ArrayList<Notice>();
		final var places = new HashSet<Integer>();
		var withoutId = 0;
		for (final var permit : index.snapshot().permits()) {
			if (permit.objektId() == null) {
				withoutId++;
				notices.add(new Notice(new ItemKey(ALKT, TILLSTAND, ItemKey.WITHOUT_ID + withoutId), null, Outcome.GRANSKAS, PLACE_MISSING.getCode(),
					PLACE_MISSING.getCode() + " Tillståndet saknar ObjektID, diarienummer " + Optional.ofNullable(permit.utfardandeDiarienr()).orElse("saknas")));
			} else if (places.add(permit.objektId())) {
				candidates.add(permits.permit(permit));
			}
		}
		return new ReaderResult(candidates, notices);
	}

	/**
	 * @return the inspection errands of the errand types in the settings, only of places with a current permit when the
	 *         settings say so
	 */
	public ReaderResult inspections() {
		final var inspections = new InspectionCandidates(ErrandType.ALCOHOL_INSPECTION, index.properties(), UNKNOWN_SERVING_PLACE, "tillsynsärendet saknar serveringsställe", index.today());
		final var candidates = index.snapshot().errands().values().stream()
			.filter(errand -> inspections.selects(errand.arendeTyp(), index.hasPermit(errand.objektId())))
			.map(errand -> inspectionErrand(errand, inspections))
			.toList();
		return new ReaderResult(candidates, List.of());
	}

	/**
	 * @return the inspection visits, only of places with a current permit when the settings say so
	 */
	public ReaderResult visits() {
		final var visits = new InspectionVisitCandidates(ErrandType.ALCOHOL_INSPECTION_VISIT, index.properties(), UNKNOWN_SERVING_PLACE, "tillsynsbesöket saknar serveringsställe", index.today());
		final var candidates = index.snapshot().visits().visits().stream()
			.filter(visit -> visits.selects(index.hasPermit(visit.objektId())))
			.map(visit -> visit(visit, visits))
			.toList();
		return new ReaderResult(candidates, List.of());
	}

	/**
	 * @return the documentation errands: the events without an errand of the places with a current permit, those with a
	 *         text or a file, each filed under the permit of its place (decision 71), in the order of their ids
	 */
	public ReaderResult documentation() {
		final var documentation = new DocumentationCandidates(ErrandType.ALCOHOL_DOCUMENTATION, index.properties(), UNKNOWN_SERVING_PLACE, index.today());
		final var candidates = index.snapshot().placeEvents().entrySet().stream()
			.filter(place -> index.hasPermit(place.getKey()))
			.flatMap(place -> {
				final var former = index.formerHolders(place.getKey());
				return place.getValue().stream()
					.filter(event -> DocumentationCandidates.documents(event.text().text(), !event.files().isEmpty()))
					.map(event -> documentationErrand(place.getKey(), event, former, documentation));
			})
			.sorted(Comparator.comparing(candidate -> Integer.valueOf(candidate.key().sourceId())))
			.toList();
		return new ReaderResult(candidates, List.of());
	}

	private Candidate documentationErrand(final Integer objektId, final PlaceEventRow event, final FormerHolders former, final DocumentationCandidates documentation) {
		final var place = index.placeOf(objektId);
		final var row = event.text();
		final var source = new PlaceEvent(ALKT, event.handelseId(), index.snapshot().codeText(CODE_TYPE_EVENT, row.handelseTyp()), row.tidpunkt(), row.text(), row.confidential(),
			row.diarieNr());
		final var files = index.newFileList();
		event.files().forEach(file -> files.add(file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS, file.details()));
		return documentation.builder(source, place == null ? null : index.place(place), index.ownerOf(place).orElse(null), former, index.placePermit(objektId))
			.placeNumber(place == null ? null : place.restaurangBeteckning())
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	private Candidate inspectionErrand(final ErrandRow errand, final InspectionCandidates inspections) {
		final var snapshot = index.snapshot();
		final var place = index.placeOf(errand.objektId());
		final var source = InspectionErrand.of(ALKT, errand, snapshot.codeText(CODE_TYPE_ERRAND, errand.arendeTyp()), snapshot.visits().handlerNames(errand.handlaggare()),
			Stream.concat(snapshot.textsOf(errand.arendeId()).stream().map(TextRow::tidpunkt), index.decisionsOf(errand.arendeId()).stream().map(DecisionRow::beslutad)));
		final var applicant = errand.sokandeId() == null ? null : snapshot.applicants().get(errand.sokandeId());
		final var decisions = index.decisionsOf(errand.arendeId()).stream()
			.sorted((a, b) -> Objects.compare(a.beslutsDatum(), b.beslutsDatum(), Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(decision -> index.decisionText(decision, false))
			.filter(Objects::nonNull)
			.toList();

		final var files = index.errandFiles(List.of(errand.arendeId()), index.newFileList());
		return inspections.builder(source, place == null ? null : index.place(place), applicant, index.ownerOf(place).orElse(null), index.placePermit(errand.objektId()), decisions,
			index.investigationTexts(List.of(errand.arendeId())))
			.placeNumber(place == null ? null : place.restaurangBeteckning())
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	private Candidate visit(final VisitRow visit, final InspectionVisitCandidates visits) {
		final var place = index.placeOf(visit.objektId());
		return visits.builder(visit, index.snapshot().visits(), place == null ? null : index.place(place), index.placePermit(visit.objektId()), index.sharedFileNames())
			.placeNumber(place == null ? null : place.restaurangBeteckning())
			.build();
	}
}
