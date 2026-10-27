package se.sundsvall.aotloader.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCsvTest {

	@Test
	void write() {
		final var csv = ReportCsv.write(List.of(
			new ReportLine(Source.ALKT, Kind.TILLSTAND, "1", "12345678", "Krogen \"Ankan\"", Outcome.FEL, "G13,G14", "Steg Ärende: 502\nmer", "ALKT-2026-1"),
			new ReportLine(null, null, null, null, "=SUMMA(A1)", null, null, null, null)));

		assertThat(csv).startsWith("﻿" + ReportCsv.HEADER + "\r\n")
			.contains("\"ALKT\";\"TILLSTAND\";\"1\";\"12345678\";\"Krogen \"\"Ankan\"\"\";\"FEL\";\"G13,G14\";\"Steg Ärende: 502 | mer\";\"ALKT-2026-1\"\r\n")
			.contains("\"\";\"\";\"\";\"\";\"'=SUMMA(A1)\";\"\";\"\";\"\";\"\"\r\n");
	}

	@Test
	void cellsThatExcelWouldReadAsFormulas() {
		assertThat(ReportCsv.cell("+1")).isEqualTo("\"'+1\"");
		assertThat(ReportCsv.cell("-1")).isEqualTo("\"'-1\"");
		assertThat(ReportCsv.cell("@x")).isEqualTo("\"'@x\"");
		assertThat(ReportCsv.cell("a=b")).isEqualTo("\"a=b\"");
	}
}
