package se.sundsvall.aotloader.source.alkol;

import java.util.Arrays;
import java.util.Set;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;

import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * The kinds of tobacco permit: the column of the view FS_GT_Tobak and the type of the granting decision that give the
 * kind, its text, and the parameter of the post that tells its kind of sale (section 7.4).
 */
enum PermitKind {

	RETAIL("GTDE", "Detaljhandel", AssetParameters.SALES_RETAIL),
	WHOLESALE("GTPH", "Partihandel", AssetParameters.SALES_WHOLESALE),
	DISTANCE("GTDI", "Distanshandel", AssetParameters.SALES_ONLINE);

	/** The types of the decisions that grant a tobacco permit. */
	static final Set<String> GRANTING_DECISIONS = Arrays.stream(values()).map(PermitKind::decision).collect(toUnmodifiableSet());

	private final String decision;
	private final String text;
	private final String salesParameter;

	PermitKind(final String decision, final String text, final String salesParameter) {
		this.decision = decision;
		this.text = text;
		this.salesParameter = salesParameter;
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

	boolean isInView(final PermitViewRow view) {
		return switch (this) {
			case RETAIL -> view.retail();
			case WHOLESALE -> view.wholesale();
			case DISTANCE -> view.distance();
		};
	}
}
