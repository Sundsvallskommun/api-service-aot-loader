package se.sundsvall.aotloader.service;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.Assessment.Decision;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.RuleHit;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.FILE_NOT_UPLOADED;
import static se.sundsvall.aotloader.service.model.ReviewRule.HOLDER_NOT_IN_PARTY;
import static se.sundsvall.aotloader.service.model.ReviewRule.NO_ISSUING_ERRAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERIOD_PASSED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ENDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;

class AssessmentTest {

	@Test
	void withoutHitsTheItemIsMigrated() {
		assertThat(Assessment.of(List.of(), Optional.empty())).isEqualTo(new Assessment(Decision.MIGRATE, List.of(), null));
	}

	@Test
	void informationDoesNotStopTheMigration() {
		assertThat(Assessment.of(List.of(RuleHit.of(NO_ISSUING_ERRAND), RuleHit.of(FILE_NOT_UPLOADED)), Optional.empty()).decision()).isEqualTo(Decision.MIGRATE);
	}

	@Test
	void blockingHitsAreReviewed() {
		final var hits = List.of(RuleHit.of(OWNER_DIFFERS), RuleHit.of(PERMIT_ENDED), RuleHit.of(NO_ISSUING_ERRAND));

		final var assessment = Assessment.of(hits, Optional.empty());

		assertThat(assessment.decision()).isEqualTo(Decision.REVIEW);
		assertThat(assessment.reasons()).containsExactly(RuleHit.of(OWNER_DIFFERS));
	}

	@Test
	void anEndedPermitIsNotMigrated() {
		final var assessment = Assessment.of(List.of(RuleHit.of(PERMIT_ENDED, "GTDEU 2022-01-01")), Optional.empty());

		assertThat(assessment.decision()).isEqualTo(Decision.NOT_MIGRATED);
		assertThat(assessment.reasons()).containsExactly(RuleHit.of(PERMIT_ENDED, "GTDEU 2022-01-01"));
	}

	@Test
	void theExceptionListLiftsOverridableRules() {
		final var hits = List.of(RuleHit.of(OWNER_DIFFERS), RuleHit.of(PERIOD_PASSED), RuleHit.of(PERMIT_ENDED));

		assertThat(Assessment.of(hits, Optional.of(ExceptionOutcome.MIGRERA))).isEqualTo(new Assessment(Decision.MIGRATE, List.of(), "Undantagslistan: MIGRERA"));
		assertThat(Assessment.of(hits, Optional.of(ExceptionOutcome.MIGRERA_SOM_UTGANGET)))
			.isEqualTo(new Assessment(Decision.MIGRATE_AS_EXPIRED, List.of(), "Undantagslistan: MIGRERA_SOM_UTGANGET"));
	}

	@Test
	void theExceptionListCannotLiftARuleTheMigrationCannotDoWithout() {
		final var assessment = Assessment.of(List.of(RuleHit.of(OWNER_DIFFERS), RuleHit.of(HOLDER_NOT_IN_PARTY)), Optional.of(ExceptionOutcome.MIGRERA));

		assertThat(assessment.decision()).isEqualTo(Decision.REVIEW);
		assertThat(assessment.reasons()).containsExactly(RuleHit.of(HOLDER_NOT_IN_PARTY));
		assertThat(assessment.note()).isEqualTo("Undantaget i undantagslistan kan inte tillämpas förrän uppgiften är rättad i källan");
	}

	@Test
	void theExceptionListLiftsASharedDiaryNumberAndAnOpenErrandThePermitBuildsOn() {
		final var hits = List.of(RuleHit.of(DIARY_NUMBER_ON_SEVERAL, "IAN-2019-1"), RuleHit.of(UNDERLYING_ERRAND_OPEN, "ArendeID 1"));

		assertThat(Assessment.of(hits, Optional.empty()).decision()).isEqualTo(Decision.REVIEW);
		assertThat(Assessment.of(hits, Optional.of(ExceptionOutcome.MIGRERA)).decision()).isEqualTo(Decision.MIGRATE);
	}

	@Test
	void theExceptionListCannotLiftAnOpenInspectionErrand() {
		final var assessment = Assessment.of(List.of(RuleHit.of(ERRAND_OPEN)), Optional.of(ExceptionOutcome.MIGRERA));

		assertThat(assessment.decision()).isEqualTo(Decision.REVIEW);
		assertThat(assessment.reasons()).containsExactly(RuleHit.of(ERRAND_OPEN));
	}

	@Test
	void ruleHitReportText() {
		assertThat(RuleHit.of(PERIOD_PASSED, "2026-01-01–2026-03-31").toReportText()).isEqualTo("G4 Datumperioden har passerat: 2026-01-01–2026-03-31");
		assertThat(RuleHit.of(PERIOD_PASSED, " ").toReportText()).isEqualTo("G4 Datumperioden har passerat");
		assertThat(RuleHit.of(PERIOD_PASSED).toReportText()).isEqualTo("G4 Datumperioden har passerat");
	}
}
