package se.sundsvall.aotloader.source;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;

/**
 * Puts the results of a reader together, in the order a run takes them.
 */
public final class ReaderResults {

	private static final Comparator<ItemKey> ORDER = Comparator
		.comparing(ItemKey::kind)
		.thenComparing(key -> leadingNumber(key.sourceId()))
		.thenComparing(ItemKey::sourceId);

	private ReaderResults() {}

	/**
	 * @return the candidates and notices of all results, each by kind and then by id
	 */
	public static ReaderResult combine(final List<ReaderResult> results) {
		final var candidates = new ArrayList<Candidate>();
		final var notices = new ArrayList<Notice>();
		results.forEach(result -> {
			candidates.addAll(result.candidates());
			notices.addAll(result.notices());
		});
		candidates.sort(Comparator.comparing(Candidate::key, ORDER));
		notices.sort(Comparator.comparing(Notice::key, ORDER));
		return new ReaderResult(candidates, notices);
	}

	private static BigInteger leadingNumber(final String sourceId) {
		var end = 0;
		while (end < sourceId.length() && sourceId.charAt(end) >= '0' && sourceId.charAt(end) <= '9') {
			end++;
		}
		return end == 0 ? BigInteger.ZERO : new BigInteger(sourceId.substring(0, end));
	}
}
