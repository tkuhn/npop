package org.petapico.npop.topic;

import org.nanopub.Nanopub;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.petapico.npop.Topic.TopicHandler;

public class MetaboliteSpeciesTopics implements TopicHandler {

	@Override
	public String getTopic(Nanopub np) {
		return getPart1a(np) + ">" + getPart1b(np) + ">" + getPart2(np);
	}

	private String getPart1a(Nanopub np) {
		for (Statement st : np.getAssertion()) {
			if (st.getPredicate().stringValue().equals("http://www.wikidata.org/prop/direct/P703")) {
				return st.getSubject().stringValue() + ">" + st.getObject().stringValue();
			}
		}
		return null;
	}

	private String getPart1b(Nanopub np) {
		for (Statement st : np.getAssertion()) {
			if (st.getPredicate().stringValue().equals("http://semanticscience.org/resource/CHEMINF_000399")) {
				return st.getObject().stringValue();
			}
		}
		return null;
	}

	private String getPart2(Nanopub np) {
		for (Statement st : np.getProvenance()) {
			if (st.getPredicate().stringValue().equals("http://semanticscience.org/resource/SIO_000253")) {
				if (!(st.getObject() instanceof URI)) continue;
				if (st.getObject().stringValue().equals("http://www.wikidata.org/entity/Q2013")) continue;
				return st.getObject().stringValue();
			}
		}
		return null;
	}

}
