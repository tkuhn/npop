package org.petapico.npop.fingerprint;

import org.nanopub.Nanopub;
import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;

public interface FingerprintHandler {

	public static final URI headUriPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/head");
	public static final URI assertionUriPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/assertion");
	public static final URI provUriPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/provenance");
	public static final URI pubinfoUriPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/pubinfo");
	public static final URI timestampPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/timestamp");
	public static final URI disgenetGdaPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/disgenet-gda");

	public String getFingerprint(Nanopub np);

}