package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.PermitLink.Basis;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

class PermitLinkTest {

	private static final ItemKey PERMIT = new ItemKey(Source.ALKT, Kind.TILLSTAND, "1");
	private static final LegalId HOLDER = new LegalId("5566778899", ENTERPRISE);

	@Test
	void theHolderOfAnInspectionErrandDecidesWhateverItsDay() {
		final var permit = new PlacePermit(PERMIT, LocalDate.of(2019, 5, 1), null, true);

		// An errand of the same holder before the permit was issued, for instance before a new permit of the same company
		assertThat(new PermitLink(permit, HOLDER, HOLDER, LocalDate.of(2018, 1, 1)).basis()).isEqualTo(Basis.SAME_HOLDER);
		assertThat(new PermitLink(permit, HOLDER, new LegalId("5566778800", ENTERPRISE), LocalDate.of(2020, 1, 1)).basis()).isEqualTo(Basis.OTHER_HOLDER);
		// The numbers decide, also between a person and a company with the same digits
		assertThat(new PermitLink(permit, HOLDER, new LegalId("5566778899", PRIVATE), null).basis()).isEqualTo(Basis.SAME_HOLDER);
		assertThat(new PermitLink(permit, null, HOLDER, LocalDate.of(2020, 1, 1)).basis()).isEqualTo(Basis.NO_PERMIT_HOLDER);
		assertThat(new PermitLink(null, HOLDER, HOLDER, null).basis()).isEqualTo(Basis.NO_PERMIT);
		assertThat(new PermitLink(null, HOLDER, HOLDER, null).permitKey()).isNull();
		assertThat(new PermitLink(permit, null, null, null).permitKey()).isEqualTo(PERMIT);
	}

	@Test
	void aDocumentationErrandIsFiledUnderThePermitWhateverItsHolderAndDay() {
		final var permit = new PlacePermit(PERMIT, LocalDate.of(2019, 5, 1), LocalDate.of(2020, 1, 1), false);

		// Neither the holder nor the day decides, nor that the permit may have been issued to another (decision 71)
		assertThat(PermitLink.filedUnder(permit).basis()).isEqualTo(Basis.FILED);
		assertThat(PermitLink.filedUnder(permit).filed()).isTrue();
		assertThat(PermitLink.filedUnder(null).basis()).isEqualTo(Basis.NO_PERMIT);
		assertThat(new PermitLink(permit, HOLDER, HOLDER, null).filed()).isFalse();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		2023-11-04 | 2019-05-01 | -          | true  | AFTER_ISSUE
		2019-05-01 | 2019-05-01 | -          | true  | AFTER_ISSUE
		2019-04-30 | 2019-05-01 | -          | true  | BEFORE_ISSUE
		2026-02-12 | 2019-05-01 | 2026-02-12 | true  | AFTER_ISSUE
		2026-02-13 | 2019-05-01 | 2026-02-12 | true  | AFTER_END
		2023-11-04 | 2019-05-01 | -          | false | ISSUED_TO_OTHER
		2023-11-04 | -          | -          | true  | NO_ISSUE_DATE
		2023-11-04 | -          | -          | false | ISSUED_TO_OTHER
		-          | 2019-05-01 | -          | true  | NO_DATE
		""")
	void theDayOfAnInspectionWithoutAKnownHolderDecides(final LocalDate inspected, final LocalDate issued, final LocalDate ended, final boolean issuedToOwner, final Basis basis) {
		final var link = new PermitLink(new PlacePermit(PERMIT, issued, ended, issuedToOwner), HOLDER, null, inspected);

		assertThat(link.basis()).isEqualTo(basis);
		assertThat(link.basis().relates()).isEqualTo(basis == Basis.AFTER_ISSUE);
	}
}
