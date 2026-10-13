package se.sundsvall.aotloader.source;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;

/**
 * Puts the results of a reader together, in the order a run takes them.
 */
public final class ReaderResults {

	private static final Comparator<Candidate> ORDER = Comparator
		.comparing((Candidate candidate) -> candidate.key().kind())
		.thenComparing(candidate -> leadingNumber(candidate.key().sourceId()))
		.thenComparing(candidate -> candidate.key().sourceId());

	private ReaderResults() {}

	/**
	 * @return the candidates and notices of all results, the candidates by kind and then by id
	 */
	public static ReaderResult combine(final List<ReaderResult> results) {
		final var candidates = new ArrayList<Candidate>();
		final var notices = new ArrayList<Notice>();
		results.forEach(result -> {
			candidates.addAll(result.candidates());
			notices.addAll(result.notices());
		});
		candidates.sort(ORDER);
		return new ReaderResult(candidates, notices);
	}

	private static BigInteger leadingNumber(final String sourceId) {
		final var digits = sourceId.replaceFirst("^(\\d*).*$", "$1");
		return digits.isEmpty() ? BigInteger.ZERO : new BigInteger(digits);
	}
}
