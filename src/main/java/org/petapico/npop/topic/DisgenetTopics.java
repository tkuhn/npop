package org.petapico.npop.topic;

import org.nanopub.Nanopub;

public class DisgenetTopics extends DefaultTopics {

	public DisgenetTopics() {
		super(null);
	}

	@Override
	public String getTopic(Nanopub np) {
		String t = super.getTopic(np);
		t = t.replaceFirst("^http://rdf.disgenet.org/gene-disease-association.ttl#", "http://rdf.disgenet.org/resource/gda/");
		return t;
	}

}
