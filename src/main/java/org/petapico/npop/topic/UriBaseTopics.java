package org.petapico.npop.topic;

import org.nanopub.Nanopub;
import org.petapico.npop.Topic.TopicHandler;

public class UriBaseTopics implements TopicHandler {

	@Override
	public String getTopic(Nanopub np) {
		String s = np.getUri().stringValue();
		s = s.replaceFirst("RA.{43}$", "");
		s = s.replaceFirst("#.*$", "");
		s = s.replaceFirst("[\\./]$", "");
		return s;
	}

}
