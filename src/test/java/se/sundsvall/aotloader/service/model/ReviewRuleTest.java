package se.sundsvall.aotloader.service.model;

import java.util.EnumSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.HOLDER_NOT_IN_PARTY;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ROWS_ON_PLACE;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.SUBTYPE_MISSING;

class ReviewRuleTest {

	/**
	 * The rules that the exception list cannot lift, as section 7.1.1 lists them: the migration cannot build the errand
	 * without the data they find missing.
	 */
	private static final EnumSet<ReviewRule> NOT_OVERRIDABLE = EnumSet.of(PLACE_MISSING, LEGAL_ID_INVALID, ISSUE_DATE_MISSING, HOLDER_NOT_IN_PARTY, DIARY_NUMBER_MISSING,
		SUBTYPE_MISSING, OWNER_MISSING, NOTIFICATION_DATE_MISSING, PERMIT_ROWS_ON_PLACE, ERRAND_OPEN);

	@ParameterizedTest
	@EnumSource(ReviewRule.class)
	void onlyTheRulesOfTheSolutionDocumentCannotBeLifted(final ReviewRule rule) {
		assertThat(rule.isOverridable()).isEqualTo(!NOT_OVERRIDABLE.contains(rule));
	}
}
