package se.sundsvall.aotloader.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OmissionsTest {

	@Test
	void aValueWithoutTheFormIsLeftOutAndNotedWithItsPathAndWhyButNotTheValue() {
		final var omissions = new Omissions();

		assertThat(omissions.checked("premises.address.postalCode", " 85230 ", FieldForms::isPostalCode, "har inte fem siffror")).isEqualTo("85230");
		assertThat(omissions.checked("premises.address.postalCode", "S-852", FieldForms::isPostalCode, "har inte fem siffror")).isNull();
		assertThat(omissions.checked("premises.address.postalCode", " ", FieldForms::isPostalCode, "har inte fem siffror")).isNull();
		assertThat(omissions.emailAddress("premises.emailAddress", "kiosken på torget")).isNull();
		assertThat(omissions.emailAddress("premises.emailAddress", "kiosk@example.com")).isEqualTo("kiosk@example.com");
		omissions.omit("servingArea.seatsIndoor", "inte ett heltal");

		assertThat(omissions.list()).containsExactly("premises.address.postalCode: har inte fem siffror", "premises.emailAddress: har inte formen av en e-postadress",
			"servingArea.seatsIndoor: inte ett heltal");
	}
}
