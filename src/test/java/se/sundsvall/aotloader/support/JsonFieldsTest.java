package se.sundsvall.aotloader.support;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFieldsTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void aFieldWithoutAValueIsLeftOut() {
		final var fields = JsonFields.object()
			.text("blank", " ")
			.text("missing", null)
			.text("stripped", " text ")
			.date("day", LocalDate.of(2019, 5, 1))
			.date("noDay", null)
			.flag("no", false)
			.flag("unknown", null)
			.number("seats", 80)
			.number("noSeats", null)
			.texts("lines", Arrays.asList("a", " ", null, " b "))
			.texts("noLines", List.of(" "))
			.texts("nothing", null)
			.object("empty", JsonFields.object().text("blank", ""))
			.object("absent", null)
			.object("inner", JsonFields.object().text("name", "x"))
			.objects("entries", Arrays.asList(JsonFields.object(), null, JsonFields.object().flag("yes", true)))
			.objects("noEntries", List.of(JsonFields.object()));

		assertThat(JSON.writeValueAsString(fields.toNode())).isEqualTo("""
			{"stripped":"text","day":"2019-05-01","no":false,"seats":80,"lines":["a","b"],"inner":{"name":"x"},"entries":[{"yes":true}]}""");
		assertThat(fields.isEmpty()).isFalse();
		assertThat(JsonFields.object().isEmpty()).isTrue();
	}

	@Test
	void theNodeIsACopyAndTheValuesAreNotShown() {
		final var fields = JsonFields.object().text("name", "Tolvan Tolvansson");
		final var node = fields.toNode();
		node.put("changed", true);

		assertThat(fields.toNode().has("changed")).isFalse();
		assertThat(fields).hasToString("JsonFields[name]");
	}
}
