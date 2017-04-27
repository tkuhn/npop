package org.petapico.npop.fingerprint;

import static org.nanopub.SimpleTimestampPattern.isCreationTimeProperty;

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
import org.petapico.npop.Fingerprint.FingerprintHandler;

public class DefaultFingerprints implements FingerprintHandler {

	private boolean ignoreHead;
	private boolean ignoreProv;
	private boolean ignorePubinfo;

	public DefaultFingerprints(boolean ignoreHead, boolean ignoreProv, boolean ignorePubinfo) {
		this.ignoreHead = ignoreHead;
		this.ignoreProv = ignoreProv;
		this.ignorePubinfo = ignorePubinfo;
	}

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

	protected List<Statement> getNormalizedStatements(Nanopub np) {
		List<Statement> statements = NanopubUtils.getStatements(np);
		List<Statement> n = new ArrayList<>();
		for (Statement st : statements) {
			boolean isInHead = st.getContext().equals(np.getHeadUri());
			if (isInHead && ignoreHead) continue;
			boolean isInProv = st.getContext().equals(np.getProvenanceUri());
			if (isInProv && ignoreProv) continue;
			boolean isInPubInfo = st.getContext().equals(np.getPubinfoUri());
			if (isInPubInfo && ignorePubinfo) continue;
			Resource subj = st.getSubject();
			URI pred = st.getPredicate();
			if (isInPubInfo && subj.equals(np.getUri()) && isCreationTimeProperty(pred)) {
				continue;
			}
			n.add(st);
		}
		return n;
	}

}
