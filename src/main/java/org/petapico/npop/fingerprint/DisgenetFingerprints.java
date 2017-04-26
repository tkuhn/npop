package org.petapico.npop.fingerprint;

import java.util.ArrayList;
import java.util.List;

import net.trustyuri.TrustyUriUtils;
import net.trustyuri.rdf.RdfHasher;
import net.trustyuri.rdf.RdfPreprocessor;

import org.nanopub.Nanopub;
import org.nanopub.NanopubUtils;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.ContextStatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.petapico.npop.Fingerprint.FingerprintHandler;

public class DisgenetFingerprints implements FingerprintHandler {

	private static final URI assertionUriPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/assertion");
	private static final URI provUriPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/provenance");
	private static final URI timestampPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/timestamp");
	private static final URI disgenetVoidPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/disgenet-void");
	private static final URI disgenetGdaPlaceholder = new URIImpl("http://purl.org/nanopub/placeholders/disgenet-gda");

	private static final URI pav1importedOn = new URIImpl("http://purl.org/pav/importedOn");
	private static final URI pav2importedOn = new URIImpl("http://purl.org/pav/2.0/importedOn");

	@Override
	public String getFingerprint(Nanopub np) {
		String artifactCode = TrustyUriUtils.getArtifactCode(np.getUri().toString());
		if (artifactCode == null) {
			throw new RuntimeException("Not a trusty URI: " + np.getUri());
		}
		List<Statement> statements = getNormalizedStatements(np);
		statements = RdfPreprocessor.run(statements, artifactCode);
		String fingerprint = RdfHasher.makeArtifactCode(statements);
		return fingerprint.substring(2);
	}

	private List<Statement> getNormalizedStatements(Nanopub np) {
		List<Statement> statements = NanopubUtils.getStatements(np);
		List<Statement> n = new ArrayList<>();
		for (Statement st : statements) {
			boolean isInAssertion = st.getContext().equals(np.getAssertionUri());
			boolean isInProvenance = st.getContext().equals(np.getProvenanceUri());
			if (!isInProvenance && !isInAssertion) continue;
			URI graphURI;
			if (isInAssertion) {
				graphURI = assertionUriPlaceholder;
			} else {
				graphURI = provUriPlaceholder;
			}
			Resource subj = st.getSubject();
			URI pred = st.getPredicate();
			Value obj = st.getObject();
			if (isInAssertion) {
				String subjS = subj.stringValue();
				if (subjS.startsWith("http://rdf.disgenet.org/resource/gda/DGN") ||
						subjS.startsWith("http://rdf.disgenet.org/gene-disease-association.ttl#DGN")) {
					subj = disgenetGdaPlaceholder;
				}
			} else if (isInProvenance) {
				if (pred.equals(pav1importedOn) || pred.equals(pav2importedOn)) {
					pred = pav2importedOn;
					obj = timestampPlaceholder;
				}
				if (subj.equals(np.getAssertionUri())) {
					subj = assertionUriPlaceholder;
				}
			}
			n.add(new ContextStatementImpl((Resource) transform(subj), (URI) transform(pred), transform(obj), graphURI));
		}
		return n;
	}

	private Value transform(Value v) {
		if (v instanceof URI) {
			String s = ((URI) v).stringValue();
			if (s.startsWith("http://rdf.disgenet.org/v")) {
				return disgenetVoidPlaceholder;
			} else if (s.startsWith("http://purl.obolibrary.org/obo/eco.owl#")) {
				return new URIImpl(s.replace("http://purl.obolibrary.org/obo/eco.owl#", "http://purl.obolibrary.org/obo/"));
			}
		}
		return v;
	}

}
