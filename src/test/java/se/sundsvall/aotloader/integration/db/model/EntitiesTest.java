package se.sundsvall.aotloader.integration.db.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static com.google.code.beanmatchers.BeanMatchers.registerValueGenerator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

class EntitiesTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> LocalDate.now().plusDays(new Random().nextInt(1000)), LocalDate.class);
		registerValueGenerator(() -> OffsetDateTime.now().plusDays(new Random().nextInt(1000)), OffsetDateTime.class);
	}

	@ParameterizedTest
	@ValueSource(classes = {
		MigrationRunEntity.class, MigrationItemEntity.class, RunItemEntity.class, ReviewExceptionEntity.class, MigrationTargetEntity.class
	})
	void bean(final Class<?> entity) {
		assertThat(entity, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanToString()));
	}

	@Test
	void equalsAndHashCodeFollowTheId() {
		final var run1 = new MigrationRunEntity();
		run1.setId("a");
		final var run2 = new MigrationRunEntity();
		run2.setId("a");
		final var item1 = new MigrationItemEntity();
		item1.setId(1L);
		final var item2 = new MigrationItemEntity();
		item2.setId(1L);
		final var line1 = new RunItemEntity();
		line1.setId(1L);
		final var line2 = new RunItemEntity();
		line2.setId(1L);
		final var exception1 = new ReviewExceptionEntity();
		exception1.setId(1L);
		final var exception2 = new ReviewExceptionEntity();
		exception2.setId(1L);
		final var target1 = new MigrationTargetEntity();
		target1.setId(1);
		final var target2 = new MigrationTargetEntity();
		target2.setId(1);

		assertThat(run1).isEqualTo(run2).isEqualTo(run1).hasSameHashCodeAs(run2).isNotEqualTo(new MigrationRunEntity()).isNotEqualTo("a");
		assertThat(item1).isEqualTo(item2).isEqualTo(item1).hasSameHashCodeAs(item2).isNotEqualTo(new MigrationItemEntity()).isNotEqualTo("a");
		assertThat(line1).isEqualTo(line2).isEqualTo(line1).hasSameHashCodeAs(line2).isNotEqualTo(new RunItemEntity()).isNotEqualTo("a");
		assertThat(exception1).isEqualTo(exception2).isEqualTo(exception1).hasSameHashCodeAs(exception2).isNotEqualTo(new ReviewExceptionEntity()).isNotEqualTo("a");
		assertThat(target1).isEqualTo(target2).isEqualTo(target1).hasSameHashCodeAs(target2).isNotEqualTo(new MigrationTargetEntity()).isNotEqualTo("a");
	}
}
