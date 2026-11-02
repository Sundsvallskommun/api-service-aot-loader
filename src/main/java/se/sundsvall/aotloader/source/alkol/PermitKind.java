package se.sundsvall.aotloader.source.alkol;

import java.util.Arrays;
import java.util.Set;
import se.sundsvall.aotloader.source.CertificateParameters;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;

import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * The kinds of tobacco permit: the column of the view FS_GT_Tobak and the type of the granting decision that give the
 * kind, its text, the parameter of the certificate that tells its kind of sale, and the kind of sale of the tobacco
 * schema (section 7.4, decision 87).
 */
enum PermitKind {

	RETAIL("GTDE", "Detaljhandel", CertificateParameters.SALES_RETAIL, "RETAIL"),
	WHOLESALE("GTPH", "Partihandel", CertificateParameters.SALES_WHOLESALE, "WHOLESALE"),
	DISTANCE("GTDI", "Distanshandel", CertificateParameters.SALES_ONLINE, "DISTANCE_SELLING");

	/** The types of the decisions that grant a tobacco permit. */
	static final Set<String> GRANTING_DECISIONS = Arrays.stream(values()).map(PermitKind::decision).collect(toUnmodifiableSet());

	private final String decision;
	private final String text;
	private final String salesParameter;
	private final String salesType;

	PermitKind(final String decision, final String text, final String salesParameter, final String salesType) {
		this.decision = decision;
		this.text = text;
		this.salesParameter = salesParameter;
		this.salesType = salesType;
	}

	String decision() {
		return decision;
	}

	String text() {
		return text;
	}

	String salesParameter() {
		return salesParameter;
	}

	String salesType() {
		return salesType;
	}

	boolean isInView(final PermitViewRow view) {
		return switch (this) {
			case RETAIL -> view.retail();
			case WHOLESALE -> view.wholesale();
			case DISTANCE -> view.distance();
		};
	}
}
