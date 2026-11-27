package se.sundsvall.aotloader.service;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileProblemsTest {

	@Test
	void everyLookupResultButFoundIsAProblem() {
		Arrays.stream(LookupResult.values())
			.filter(result -> result != LookupResult.FOUND)
			.forEach(result -> assertThat(FileProblem.of(result).name()).isEqualTo(result.name()));
		assertThatThrownBy(() -> FileProblem.of(LookupResult.FOUND)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fileProblems() {
		final var single = new FileProblems();
		final var several = new FileProblems();
		Arrays.stream(FileProblem.values()).forEach(problem -> {
			single.add(problem, "en.pdf");
			several.add(problem, "ett.pdf");
			several.add(problem, "två.pdf");
		});
		final var singleNotes = new ReportNotes();
		final var severalNotes = new ReportNotes();

		single.report(singleNotes, "50 MB");
		several.report(severalNotes, "50 MB");

		assertThat(singleNotes.result(Outcome.MIGRERAD, null).detail()).isEqualTo(String.join("; ",
			"G14 1 fil saknas på filarean (en.pdf)",
			"G14 1 fil är större än 50 MB och laddas inte upp (en.pdf)",
			"1 länk hoppas över (en.pdf)",
			"G14 1 filreferens har ett ogiltigt namn (en.pdf)",
			"G14 1 fil gick inte att läsa (en.pdf)",
			"G14 1 fil kunde inte kontrolleras, filarean gick inte att läsa (en.pdf)",
			"G14 1 fil togs inte emot av SupportManagement (en.pdf)",
			"G14 1 fil togs inte emot av PartyAssets och ligger bara på ärendet (en.pdf)",
			"G14 1 fil har ett namn som också används på ett annat ställe och laddas inte upp (en.pdf)"));
		assertThat(severalNotes.result(Outcome.MIGRERAD, null).detail()).isEqualTo(String.join("; ",
			"G14 2 filer saknas på filarean (ett.pdf, två.pdf)",
			"G14 2 filer är större än 50 MB och laddas inte upp (ett.pdf, två.pdf)",
			"2 länkar hoppas över (ett.pdf, två.pdf)",
			"G14 2 filreferenser har ett ogiltigt namn (ett.pdf, två.pdf)",
			"G14 2 filer gick inte att läsa (ett.pdf, två.pdf)",
			"G14 2 filer kunde inte kontrolleras, filarean gick inte att läsa (ett.pdf, två.pdf)",
			"G14 2 filer togs inte emot av SupportManagement (ett.pdf, två.pdf)",
			"G14 2 filer togs inte emot av PartyAssets och ligger bara på ärendet (ett.pdf, två.pdf)",
			"G14 2 filer har namn som också används på andra ställen och laddas inte upp (ett.pdf, två.pdf)"));
	}

	@Test
	void fileProblemsListAtMostTwentyNamesWithNumbersMaskedAndLinksAloneAreNoRuleHit() {
		final var many = new FileProblems();
		for (var i = 1; i <= 22; i++) {
			many.add(FileProblem.MISSING, "fil" + i + ".pdf");
		}
		final var masked = new FileProblems();
		masked.add(FileProblem.MISSING, "Intyg 198001011234.pdf");
		final var links = new FileProblems();
		links.add(FileProblem.LINK, "a.url");
		final var manyNotes = new ReportNotes();
		final var maskedNotes = new ReportNotes();
		final var linkNotes = new ReportNotes();
		final var noNotes = new ReportNotes();

		many.report(manyNotes, "50 MB");
		masked.report(maskedNotes, "50 MB");
		links.report(linkNotes, "50 MB");
		new FileProblems().report(noNotes, "50 MB");

		assertThat(manyNotes.result(Outcome.MIGRERAD, null).detail()).startsWith("G14 22 filer saknas på filarean (fil1.pdf, ").endsWith("fil20.pdf och 2 till)");
		assertThat(maskedNotes.result(Outcome.MIGRERAD, null).detail()).isEqualTo("G14 1 fil saknas på filarean (Intyg [nummer].pdf)");
		assertThat(linkNotes.result(Outcome.MIGRERAD, null).rules()).isEmpty();
		assertThat(linkNotes.result(Outcome.MIGRERAD, null).detail()).isEqualTo("1 länk hoppas över (a.url)");
		assertThat(noNotes.result(Outcome.MIGRERAD, null)).isEqualTo(new ItemResult(Outcome.MIGRERAD, List.of(), null, null));
	}
}
