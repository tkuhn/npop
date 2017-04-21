package org.petapico.npop;

import static org.nanopub.SimpleTimestampPattern.isCreationTimeProperty;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import net.trustyuri.TrustyUriException;
import net.trustyuri.TrustyUriUtils;
import net.trustyuri.rdf.RdfHasher;
import net.trustyuri.rdf.RdfPreprocessor;

import org.nanopub.MalformedNanopubException;
import org.nanopub.MultiNanopubRdfHandler;
import org.nanopub.MultiNanopubRdfHandler.NanopubHandler;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.nanopub.NanopubUtils;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.impl.ContextStatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Fingerprint {

	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	@com.beust.jcommander.Parameter(names = "--ignore-head", description = "Ignore the head graph for fingerprint calculation")
	private boolean ignoreHead;

	@com.beust.jcommander.Parameter(names = "--ignore-assertion", description = "Ignore the assertion graph for fingerprint calculation")
	private boolean ignoreAssertion;

	@com.beust.jcommander.Parameter(names = "--ignore-prov", description = "Ignore the provenance graph for fingerprint calculation")
	private boolean ignoreProv;

	@com.beust.jcommander.Parameter(names = "--ignore-pubinfo", description = "Ignore the publication info graph for fingerprint calculation")
	private boolean ignorePubinfo;

	@com.beust.jcommander.Parameter(names = "--test-disgenet", description = "Just for testing...")
	private boolean testDisgenet;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Fingerprint obj = new Fingerprint();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		try {
			obj.run();
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	public static Fingerprint getInstance(String args) throws ParameterException {
		NanopubImpl.ensureLoaded();
		if (args == null) args = "";
		args = args.trim() + " dummy-input-file";
		Fingerprint obj = new Fingerprint();
		JCommander jc = new JCommander(obj);
		jc.parse(args.split(" "));
		return obj;
	}

	private RDFFormat rdfInFormat;
	private OutputStream outputStream = System.out;
	private BufferedWriter writer;

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {
		for (File inputFile : inputNanopubs) {
			if (inFormat != null) {
				rdfInFormat = Rio.getParserFormatForFileName("file." + inFormat);
			} else {
				rdfInFormat = Rio.getParserFormatForFileName(inputFile.toString());
			}
			if (outputFile != null) {
				if (outputFile.getName().endsWith(".gz")) {
					outputStream = new GZIPOutputStream(new FileOutputStream(outputFile));
				} else {
					outputStream = new FileOutputStream(outputFile);
				}
			}

			writer = new BufferedWriter(new OutputStreamWriter(outputStream));

			MultiNanopubRdfHandler.process(rdfInFormat, inputFile, new NanopubHandler() {

				@Override
				public void handleNanopub(Nanopub np) {
					try {
						process(np);
					} catch (RDFHandlerException ex) {
						throw new RuntimeException(ex);
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				}

			});

			writer.flush();
			if (outputStream != System.out) {
				writer.close();
			}
		}
	}

	private void process(Nanopub np) throws RDFHandlerException, IOException {
		writer.write(np.getUri() + " " + getFingerprint(np) + "\n");
	}

	public String getFingerprint(Nanopub np) throws RDFHandlerException, IOException {
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
			boolean isInHead = st.getContext().equals(np.getHeadUri());
			if (isInHead && ignoreHead) continue;
			boolean isInAssertion = st.getContext().equals(np.getAssertionUri());
			if (isInAssertion && ignoreAssertion) continue;
			boolean isInProv = st.getContext().equals(np.getProvenanceUri());
			if (isInProv && ignoreProv) continue;
			boolean isInPubInfo = st.getContext().equals(np.getPubinfoUri());
			if (isInPubInfo && ignorePubinfo) continue;
			Resource subj = st.getSubject();
			if (testDisgenet && (
					subj.stringValue().startsWith("http://rdf.disgenet.org/resource/gda/DGN") ||
					subj.stringValue().startsWith("http://rdf.disgenet.org/gene-disease-association.ttl#DGN"))) {
				subj = new URIImpl("http://rdf.disgenet.org/resource/gda/DGN");
				st = new ContextStatementImpl(subj, st.getPredicate(), st.getObject(), st.getContext());			
			}
			URI pred = st.getPredicate();
//			if (isInPubInfo && options.contains("ignore-version") && 
//					(pred.toString().equals("http://purl.org/pav/2.0/version") || pred.toString().equals("http://purl.org/pav/version"))) {
//				continue;
//			}
//			if (isInProv && options.contains("ignore-imported-on") && 
//					(pred.toString().equals("http://purl.org/pav/2.0/importedOn") || pred.toString().equals("http://purl.org/pav/importedOn"))) {
//				continue;
//			}
			if (isInPubInfo && subj.equals(np.getUri()) && isCreationTimeProperty(pred)) {
				continue;
			}
			n.add(st);
		}
		return n;
	}

}
