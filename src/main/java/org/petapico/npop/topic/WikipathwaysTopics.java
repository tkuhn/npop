package org.petapico.npop.topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nanopub.Nanopub;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.vocabulary.RDF;
import org.petapico.npop.Topic.TopicHandler;

public class WikipathwaysTopics implements TopicHandler {

	@Override
	public String getTopic(Nanopub np) {
		Map<Resource,Integer> resourceCount = new HashMap<>();
		List<String> organismNames = new ArrayList<>();
		for (Statement st : np.getAssertion()) {
			Resource subj = st.getSubject();
			if (subj.equals(np.getUri())) continue;
			String ps = st.getPredicate().stringValue();
			if (ps.equals(RDF.TYPE.stringValue())) continue;
			if (ps.equals("http://vocabularies.wikipathways.org/wp#pathwayOntologyTag")) continue;
			if (ps.equals("http://vocabularies.wikipathways.org/wp#organismName")) {
				organismNames.add(st.getObject().stringValue().replace(" ", "_"));
			}
			if (!resourceCount.containsKey(subj)) resourceCount.put(subj, 0);
			resourceCount.put(subj, resourceCount.get(subj) + 1);
		}
		int max = 0;
		Resource topic = null;
		for (Resource r : resourceCount.keySet()) {
			int c = resourceCount.get(r);
			if (c > max) {
				topic = r;
				max = c;
			} else if (c == max) {
				topic = null;
			}
		}
		String organisms = "";
		if (!organismNames.isEmpty()) {
			Collections.sort(organismNames);
			for (String s : organismNames) {
				organisms += "|" + s;
			}
		}
		return topic + organisms;
	}

}
