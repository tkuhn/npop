package org.petapico.npop.topic;

import org.nanopub.Nanopub;
import org.petapico.npop.Topic.TopicHandler;

public class NoTopics implements TopicHandler {

	@Override
	public String getTopic(Nanopub np) {
		return np.getUri().stringValue();
	}

}
