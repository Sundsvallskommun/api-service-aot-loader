package se.sundsvall.aotloader.source;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import se.sundsvall.aotloader.service.model.RuleHit;

import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;

/**
 * The review rules both sources check the same way on the rows of an item.
 */
public final class SourceRules {

	private SourceRules() {}

	/**
	 * Ä1 for every errand that is not closed in the source and that the permit or notification waits for: the errands it
	 * builds on (decisions 16 and 23), and the other errands of the place whose decisions may change it (decision 115),
	 * since an item already migrated is not changed again (decision 11).
	 *
	 * @param underlying the errands the permit or notification builds on
	 * @param others     the errands of the place that may change it; those it builds on are left out of them
	 */
	public static void openErrandHits(final Collection<? extends SourceErrand> underlying, final Collection<? extends SourceErrand> others, final List<RuleHit> hits) {
		underlying.stream()
			.filter(errand -> errand.avslutad() == null)
			.forEach(errand -> hits.add(RuleHit.of(UNDERLYING_ERRAND_OPEN, CandidateTexts.errand(errand.arendeId(), errand.diarieNr()))));
		final var told = new HashSet<SourceErrand>(underlying);
		others.stream()
			.filter(errand -> errand.avslutad() == null && !told.contains(errand))
			.forEach(errand -> hits.add(RuleHit.of(PLACE_ERRAND_OPEN, CandidateTexts.errand(errand.arendeId(), errand.diarieNr()))));
	}
}
