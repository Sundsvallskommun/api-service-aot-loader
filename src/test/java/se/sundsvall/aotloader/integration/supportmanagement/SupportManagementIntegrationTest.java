package se.sundsvall.aotloader.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.AttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Config;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.Labels;
import generated.se.sundsvall.supportmanagement.PageErrand;
import generated.se.sundsvall.supportmanagement.UpdateErrandAttachmentRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

@ExtendWith(MockitoExtension.class)
class SupportManagementIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "AOT";
	private static final byte[] CONTENT = {
		1, 2, 3
	};

	@Mock
	private SupportManagementClient client;

	private SupportManagementIntegration integration;

	@BeforeEach
	void setUp() {
		integration = new SupportManagementIntegration(client, TestData.properties());
	}

	@Test
	void findErrandsByExternalTagWithDraftsIncluded() {
		final var filter = "externalTags.key:'migreringsid' and externalTags.value:'ALKT-TILLSTAND-1' and (lifecycle:'DRAFT' or lifecycle:'ACTIVE')";
		final var matching = new Errand().id("a").externalTags(Set.of(new ExternalTag().key("migreringsid").value("ALKT-TILLSTAND-1")));
		final var notMatching = new Errand().id("b").externalTags(Set.of(new ExternalTag().key("migreringsid").value("x"), new ExternalTag().key("y").value("ALKT-TILLSTAND-1")));
		final var withoutTags = new Errand().id("c");
		final var fullPage = IntStream.range(0, SupportManagementIntegration.PAGE_SIZE).mapToObj(i -> new Errand().id("p" + i)).toList();
		when(client.findErrands(MUNICIPALITY_ID, NAMESPACE, filter, 0, 100)).thenReturn(new PageErrand().content(fullPage).last(false));
		when(client.findErrands(MUNICIPALITY_ID, NAMESPACE, filter, 1, 100)).thenReturn(new PageErrand().content(List.of(matching, notMatching, withoutTags)).last(true));

		assertThat(integration.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).containsExactly(matching);
	}

	@Test
	void findErrandsStopsAtAnEmptyAnswerAndRefusesQuotes() {
		when(client.findErrands(eq(MUNICIPALITY_ID), eq(NAMESPACE), anyString(), eq(0), eq(100))).thenReturn(null);

		assertThat(integration.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "A")).isEmpty();
		assertThatThrownBy(() -> integration.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "x' or '1'='1"))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Den externa taggen innehåller tecken som inte går att söka på");
	}

	@Test
	void errandsAttachmentsAndLabels() {
		final var errand = new Errand().id("a");
		when(client.getErrand(MUNICIPALITY_ID, NAMESPACE, "a")).thenReturn(errand);
		when(client.getErrand(MUNICIPALITY_ID, NAMESPACE, "gone")).thenThrow(Problem.valueOf(NOT_FOUND, "Finns inte"));
		when(client.getErrand(MUNICIPALITY_ID, NAMESPACE, "broken")).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		when(client.createErrand(MUNICIPALITY_ID, NAMESPACE, errand)).thenReturn(ResponseEntity.created(URI.create("/2281/AOT/errands/new-id")).build());
		when(client.updateErrand(MUNICIPALITY_ID, NAMESPACE, "a", errand)).thenReturn(errand);
		when(client.getAttachments(MUNICIPALITY_ID, NAMESPACE, "a")).thenReturn(List.of(new ErrandAttachment()));
		when(client.getAttachments(MUNICIPALITY_ID, NAMESPACE, "b")).thenReturn(null);
		when(client.getLabels(MUNICIPALITY_ID, NAMESPACE)).thenReturn(new Labels().labelStructure(List.of(new Label().id("l"))), new Labels());
		when(client.getActionConfigs(MUNICIPALITY_ID, NAMESPACE)).thenReturn(List.of(new Config().name("SEND_EMAIL"))).thenReturn(null);
		assertThat(integration.getErrand(MUNICIPALITY_ID, "a")).contains(errand);
		assertThat(integration.getErrand(MUNICIPALITY_ID, "gone")).isEmpty();
		assertThatThrownBy(() -> integration.getErrand(MUNICIPALITY_ID, "broken")).isInstanceOf(ThrowableProblem.class);
		assertThat(integration.createErrand(MUNICIPALITY_ID, errand)).isEqualTo("new-id");
		assertThat(integration.updateErrand(MUNICIPALITY_ID, "a", errand)).isSameAs(errand);
		assertThat(integration.getAttachments(MUNICIPALITY_ID, "a")).hasSize(1);
		assertThat(integration.getAttachments(MUNICIPALITY_ID, "b")).isEmpty();
		assertThat(integration.getLabelTree(MUNICIPALITY_ID)).hasSize(1);
		assertThat(integration.getLabelTree(MUNICIPALITY_ID)).isEmpty();
		assertThat(integration.getActionConfigs(MUNICIPALITY_ID)).extracting(Config::getName).containsExactly("SEND_EMAIL");
		assertThat(integration.getActionConfigs(MUNICIPALITY_ID)).isEmpty();
	}

	@Test
	void attachmentsAreUploadedWithTheTimeTheyCameInAndGetTheirPurpose() {
		when(client.createAttachment(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq("a"), any(), any()))
			.thenReturn(ResponseEntity.created(URI.create("/2281/AOT/errands/a/attachments/att-1")).build(), ResponseEntity.ok().build());
		when(client.getAttachmentPurposes(MUNICIPALITY_ID, NAMESPACE)).thenReturn(List.of(new AttachmentPurpose().id("p").name("BUDGET")), null);

		assertThat(integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, OffsetDateTime.parse("2019-06-01T09:30+02:00"))).contains("att-1");
		// In UTC, since a + in the query would be read as a blank
		verify(client).createAttachment(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq("a"), eq("2019-06-01T07:30:00Z"),
			argThat(file -> "errandAttachment".equals(file.getName()) && "beslut.pdf".equals(file.getOriginalFilename())));
		assertThatThrownBy(() -> integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, null))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Svaret när bilagan skapades saknade id i Location");
		verify(client).createAttachment(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq("a"), isNull(), any());

		integration.updateAttachment(MUNICIPALITY_ID, "a", "att-1", "purpose-1", null);
		integration.updateAttachment(MUNICIPALITY_ID, "a", "att-2", null, OffsetDateTime.parse("2019-06-01T09:30+02:00"));
		verify(client).updateAttachment(MUNICIPALITY_ID, NAMESPACE, "a", "att-1", new UpdateErrandAttachmentRequest().purpose(new ErrandAttachmentPurpose().id("purpose-1")));
		verify(client).updateAttachment(MUNICIPALITY_ID, NAMESPACE, "a", "att-2", new UpdateErrandAttachmentRequest().received(OffsetDateTime.parse("2019-06-01T09:30+02:00")));

		assertThat(integration.getAttachmentPurposes(MUNICIPALITY_ID)).extracting(AttachmentPurpose::getName).containsExactly("BUDGET");
		assertThat(integration.getAttachmentPurposes(MUNICIPALITY_ID)).isEmpty();
	}

	@Test
	void investigations() {
		final var investigation = new Investigation().status("COMPLETED");
		when(client.getInvestigations(MUNICIPALITY_ID, NAMESPACE, "a")).thenReturn(List.of(new Investigation().id("i")), null);
		when(client.createInvestigation(MUNICIPALITY_ID, NAMESPACE, "a", investigation))
			.thenReturn(ResponseEntity.created(URI.create("/2281/AOT/errands/a/investigations/i-1")).build());

		assertThat(integration.getInvestigations(MUNICIPALITY_ID, "a")).extracting(Investigation::getId).containsExactly("i");
		assertThat(integration.getInvestigations(MUNICIPALITY_ID, "a")).isEmpty();
		assertThat(integration.createInvestigation(MUNICIPALITY_ID, "a", investigation)).isEqualTo("i-1");
	}

	@Test
	void aFileRefusedBySupportManagementIsToldApartFromOtherFailures() {
		when(client.createAttachment(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq("a"), any(), any())).thenThrow(
			Problem.valueOf(BAD_REQUEST, "Ogiltig fil"),
			Problem.valueOf(CONTENT_TOO_LARGE, "För stor"),
			Problem.valueOf(UNSUPPORTED_MEDIA_TYPE, "Filtypen tas inte emot"),
			Problem.valueOf(UNPROCESSABLE_CONTENT, "Ogiltig"),
			Problem.valueOf(BAD_GATEWAY, "Tjänsten svarar inte"),
			Problem.valueOf(FORBIDDEN, "Ingen behörighet"));

		assertThat(integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, null)).isEmpty();
		assertThat(integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, null)).isEmpty();
		assertThat(integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, null)).isEmpty();
		assertThat(integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, null)).isEmpty();
		assertThatThrownBy(() -> integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, null)).isInstanceOf(ThrowableProblem.class).hasMessageContaining("Tjänsten svarar inte");
		assertThatThrownBy(() -> integration.createAttachment(MUNICIPALITY_ID, "a", "beslut.pdf", CONTENT, null)).isInstanceOf(ThrowableProblem.class).hasMessageContaining("Ingen behörighet");
	}
}
