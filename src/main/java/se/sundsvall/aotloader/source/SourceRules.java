package se.sundsvall.aotloader.source;

import java.util.Collection;
import java.util.List;
import se.sundsvall.aotloader.service.model.RuleHit;

import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;

/**
 * The review rules both sources check the same way on the rows of an item.
 */
public final class SourceRules {

	private SourceRules() {}

	/**
	 * Ä1 for every errand the permit or notification builds on that is not closed in the source (decisions 16 and 23).
	 */
	public static void openErrandHits(final Collection<? extends SourceErrand> errands, final List<RuleHit> hits) {
		errands.stream()
			.filter(errand -> errand.avslutad() == null)
			.forEach(errand -> hits.add(RuleHit.of(UNDERLYING_ERRAND_OPEN, CandidateTexts.errand(errand.arendeId(), errand.diarieNr()))));
	}
}
