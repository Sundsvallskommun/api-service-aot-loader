package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import se.sundsvall.aotloader.api.model.ReviewException;
import se.sundsvall.aotloader.api.model.ReviewExceptionRequest;
import se.sundsvall.aotloader.integration.db.ReviewExceptionRepository;
import se.sundsvall.aotloader.integration.db.model.ReviewExceptionEntity;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.source.alkol.AlkolCandidateFactory;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The exception list: what the business decided for items that cannot be corrected in the source (section 7.1.1). The
 * list holds for every municipality.
 */
@Service
public class ReviewExceptionService {

	// The ids the sources give their items: a number, with the product after it for a notification
	private static final Pattern ITEM_ID = Pattern.compile("[1-9]\\d*");
	private static final Pattern NOTIFICATION_ID = Pattern.compile("[1-9]\\d*-(" + String.join("|", AlkolCandidateFactory.productCodes()) + ")");

	private final MigrationRunner runner;
	private final ReviewExceptionRepository exceptionRepository;
	private final Clock clock;

	ReviewExceptionService(final MigrationRunner runner, final ReviewExceptionRepository exceptionRepository, final Clock clock) {
		this.runner = runner;
		this.exceptionRepository = exceptionRepository;
		this.clock = clock;
	}

	public List<ReviewException> getExceptions() {
		return exceptionRepository.findAllByOrderBySourceAscKindAscSourceIdAsc().stream().map(ReviewExceptionService::toReviewException).toList();
	}

	/**
	 * Adds the item to the exception list, or changes what it says.
	 *
	 * @throws se.sundsvall.dept44.problem.ThrowableProblem 400 when no item can have the key: a kind its source has no
	 *                                                      items of, or an id the source never gives an item, e.g. one
	 *                                                      with a leading zero, or a notification of an unknown product
	 */
	public ReviewException putException(final ItemKey key, final ReviewExceptionRequest request) {
		if (!runner.supportedKinds().getOrDefault(key.source(), Set.of()).contains(key.kind())) {
			throw Problem.valueOf(BAD_REQUEST, "%s har inga ärenden av sorten %s".formatted(key.source().getDisplayName(), key.kind()));
		}
		if (!(key.kind() == Kind.ANMALAN ? NOTIFICATION_ID : ITEM_ID).matcher(key.sourceId()).matches()) {
			throw Problem.valueOf(BAD_REQUEST, "%s är inte ett id som %s ger ett ärende av sorten %s".formatted(key.sourceId(), key.source().getDisplayName(), key.kind()));
		}
		final var entity = exceptionRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId()).orElseGet(() -> {
			final var created = new ReviewExceptionEntity();
			created.setSource(key.source());
			created.setKind(key.kind());
			created.setSourceId(key.sourceId());
			created.setCreated(OffsetDateTime.now(clock));
			return created;
		});
		entity.setOutcome(request.outcome());
		entity.setDecidedBy(request.decidedBy().strip());
		entity.setDecidedAt(Optional.ofNullable(request.decidedAt()).orElse(LocalDate.now(clock)));
		entity.setComment(request.comment());
		return toReviewException(exceptionRepository.save(entity));
	}

	/**
	 * @throws se.sundsvall.dept44.problem.ThrowableProblem 404 when the item is not in the exception list
	 */
	public void deleteException(final ItemKey key) {
		final var entity = exceptionRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId())
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Ärendet %s finns inte i undantagslistan".formatted(key)));
		exceptionRepository.delete(entity);
	}

	private static ReviewException toReviewException(final ReviewExceptionEntity entity) {
		return new ReviewException(entity.getSource(), entity.getKind(), entity.getSourceId(), entity.getOutcome(), entity.getDecidedBy(), entity.getDecidedAt(), entity.getComment());
	}
}
