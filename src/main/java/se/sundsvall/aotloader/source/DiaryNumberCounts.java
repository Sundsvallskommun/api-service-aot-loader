package se.sundsvall.aotloader.source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.support.DiaryNumbers;

import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;

/**
 * The permits of a source by their diary number, compared as {@link DiaryNumbers} does, for G15 and G16. The permits of
 * one place with the same number count once, and a permit without a place counts on its own.
 */
public final class DiaryNumberCounts {

	private final Map<String, Integer> counts = new HashMap<>();
	private final Set<String> numbersOfPlaces = new HashSet<>();

	/**
	 * Counts the permit, unless it has no diary number.
	 *
	 * @param place the place of the permit, null when it has none
	 */
	public void add(final String diaryNumber, final Integer place) {
		Optional.ofNullable(DiaryNumbers.normalize(diaryNumber))
			.filter(number -> place == null || numbersOfPlaces.add(number + "/" + place))
			.ifPresent(number -> counts.merge(number, 1, Integer::sum));
	}

	/**
	 * G15 for a permit without a diary number, and G16 for one whose number another permit has too.
	 *
	 * @param diaryNumber   the diary number as the source has it, null when there is none
	 * @param missingDetail the detail of G15, null for none
	 */
	public void hits(final String diaryNumber, final String missingDetail, final List<RuleHit> hits) {
		final var number = DiaryNumbers.normalize(diaryNumber);
		if (number == null) {
			hits.add(RuleHit.of(DIARY_NUMBER_MISSING, missingDetail));
		} else if (counts.getOrDefault(number, 0) > 1) {
			hits.add(RuleHit.of(DIARY_NUMBER_ON_SEVERAL, diaryNumber));
		}
	}
}
