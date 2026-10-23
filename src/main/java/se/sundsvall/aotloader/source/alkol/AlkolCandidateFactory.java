package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.source.InspectionCandidates;
import se.sundsvall.aotloader.source.InspectionCandidates.InspectionErrand;
import se.sundsvall.aotloader.source.InspectionVisitCandidates;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.source.CandidateTexts.UNKNOWN_SALES_PLACE;
import static se.sundsvall.aotloader.source.alkol.AlkolIndex.DECISION_ORDER;

/**
 * Turns what was read from AlkOL, the database of tobacco, folköl, e-cigarettes and tobacco-free nicotine products,
 * into candidates: the tobacco permits of the view FS_GT_Tobak, one notification per active sales place and product,
 * and the inspection errands and visits.
 */
public final class AlkolCandidateFactory {

	/** The notice for an active sales place without any notified product and without a tobacco permit. */
	static final String NOTICE_WITHOUT_PRODUCT = "Ö1";
	/** The notice for an active sales place marked for tobacco but without a current tobacco permit. */
	static final String NOTICE_TOBACCO_WITHOUT_PERMIT = "Ö2";

	// The product part of the id of the notice for a sales place without any notified product
	private static final String NO_PRODUCT = "INGEN";

	private final AlkolIndex index;
	private final AlkolPermits permits;
	private final AlkolNotifications notifications;

	public AlkolCandidateFactory(final AlkolSnapshot snapshot, final LocalDate today, final AotLoaderProperties properties) {
		this.index = new AlkolIndex(snapshot, today, properties);
		this.permits = new AlkolPermits(index);
		this.notifications = new AlkolNotifications(index);
	}

	/**
	 * @return the codes of the products in the ids of the notifications, e.g. FOLKOL in 1234-FOLKOL
	 */
	public static Set<String> productCodes() {
		return Arrays.stream(Product.values()).map(Product::code).collect(toUnmodifiableSet());
	}

	/**
	 * @return the tobacco permits of the sales places in the view FS_GT_Tobak, and a notice for every active sales place
	 *         marked for tobacco that is not in the view
	 */
	public ReaderResult permits() {
		final var permitView = index.snapshot().permitView();
		final var candidates = permitView.keySet().stream()
			.map(fsId -> permits.permit(fsId, index.salesPlace(fsId)))
			.toList();
		final var notices = index.places().stream()
			.filter(place -> !place.upphort() && place.tobak() && !permitView.containsKey(place.fsId()))
			.map(place -> new Notice(new ItemKey(ALKOL, TILLSTAND, String.valueOf(place.fsId())), place.namn(), Outcome.MIGRERAS_INTE, NOTICE_TOBACCO_WITHOUT_PERMIT,
				NOTICE_TOBACCO_WITHOUT_PERMIT + " Aktivt försäljningsställe är markerat för tobak men har inget gällande tobakstillstånd"))
			.toList();
		return new ReaderResult(candidates, notices);
	}

	/**
	 * @return one notification per active sales place and notified product, and a notice for every active sales place
	 *         without any product and without a tobacco permit
	 */
	public ReaderResult notifications() {
		final var candidates = new ArrayList<Candidate>();
		final var notices = new ArrayList<Notice>();
		index.places().stream().filter(place -> !place.upphort()).forEach(place -> {
			Product.notifiedOn(place).forEach(product -> candidates.add(notifications.notification(place, product)));
			if (!Product.hasNotification(place) && !place.tobak() && !index.hasPermit(place.fsId())) {
				notices.add(new Notice(new ItemKey(ALKOL, ANMALAN, AlkolNotifications.notificationId(place, NO_PRODUCT)), place.namn(), Outcome.MIGRERAS_INTE, NOTICE_WITHOUT_PRODUCT,
					NOTICE_WITHOUT_PRODUCT + " Aktivt försäljningsställe utan produkttyp migreras inte"));
			}
		});
		return new ReaderResult(candidates, notices);
	}

	/**
	 * @return the inspection errands of the errand types in the settings, only of places with a current permit when the
	 *         settings say so, and never of a sales place that has ceased (section 7.7). A permit is current when it is in
	 *         the view FS_GT_Tobak and no decision ended it after its latest grant (G18, decision 29).
	 */
	public ReaderResult inspections() {
		final var inspections = new InspectionCandidates(ErrandType.TOBACCO_INSPECTION, index.properties(), UNKNOWN_SALES_PLACE, "tillsynsärendet saknar försäljningsställe", index.today());
		return new ReaderResult(index.snapshot().errands().stream()
			.filter(errand -> !index.hasCeased(errand.fsId()))
			.filter(errand -> inspections.selects(errand.arendeTyp(), index.hasCurrentPermit(errand.fsId())))
			.map(errand -> inspectionErrand(errand, inspections))
			.toList(), List.of());
	}

	/**
	 * @return the inspection visits, only of places with a current permit, as for the inspection errands, when the settings
	 *         say so, and never of a sales place that has ceased (section 7.7.2)
	 */
	public ReaderResult visits() {
		final var visits = new InspectionVisitCandidates(ErrandType.TOBACCO_INSPECTION_VISIT, index.properties(), UNKNOWN_SALES_PLACE, "tillsynsbesöket saknar försäljningsställe", index.today());
		return new ReaderResult(index.snapshot().visits().visits().stream()
			.filter(visit -> !index.hasCeased(visit.objektId()))
			.filter(visit -> visits.selects(index.hasCurrentPermit(visit.objektId())))
			.map(visit -> visit(visit, visits))
			.toList(), List.of());
	}

	private Candidate inspectionErrand(final ErrandRow errand, final InspectionCandidates inspections) {
		final var snapshot = index.snapshot();
		final var place = index.salesPlace(errand.fsId());
		final var source = InspectionErrand.of(ALKOL, errand, snapshot.codes().errandType(errand.arendeTyp()), snapshot.visits().handlerNames(errand.handlaggare()),
			Stream.concat(index.eventsOfErrand(errand.arendeId()).stream().map(EventRow::tidpunkt), index.decisionsOfErrand(errand.arendeId()).stream().map(DecisionRow::beslutad)));
		final var applicant = errand.sokandeId() == null ? null : snapshot.applicants().get(errand.sokandeId());
		final var decisions = index.decisionsOfErrand(errand.arendeId()).stream()
			.sorted(DECISION_ORDER)
			.map(index::decisionText)
			.filter(Objects::nonNull)
			.toList();

		final var files = index.addErrandFiles(List.of(errand.arendeId()), index.newFileList());
		return inspections.builder(source, place == null ? null : index.place(place), applicant, index.ownerOf(place), index.placePermit(errand.fsId()), decisions,
			index.investigationTexts(errand))
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	private Candidate visit(final VisitRow visit, final InspectionVisitCandidates visits) {
		final var place = index.salesPlace(visit.objektId());
		return visits.builder(visit, index.snapshot().visits(), place == null ? null : index.place(place), index.placePermit(visit.objektId()), index.sharedFileNames())
			.build();
	}
}
