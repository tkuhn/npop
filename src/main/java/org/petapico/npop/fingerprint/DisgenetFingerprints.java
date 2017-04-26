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
import org.openrdf.model.impl.ContextStatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.petapico.npop.Fingerprint.FingerprintHandler;

public class DisgenetFingerprints implements FingerprintHandler {

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
			if (!isInAssertion) continue;
			Resource subj = st.getSubject();
			if (subj.stringValue().startsWith("http://rdf.disgenet.org/resource/gda/DGN") ||
					subj.stringValue().startsWith("http://rdf.disgenet.org/gene-disease-association.ttl#DGN")) {
				subj = new URIImpl("http://rdf.disgenet.org/resource/gda/DGN");
				st = new ContextStatementImpl(subj, st.getPredicate(), st.getObject(), st.getContext());			
			}
			n.add(st);
		}
		return n;
	}

}
