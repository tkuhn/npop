package org.petapico.npop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import net.trustyuri.TrustyUriException;

import org.nanopub.MalformedNanopubException;
import org.nanopub.MultiNanopubRdfHandler;
import org.nanopub.MultiNanopubRdfHandler.NanopubHandler;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.nanopub.NanopubUtils;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.ContextStatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;
import org.petapico.npop.fingerprint.FingerprintHandler;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Decontextualize {

	public static final URI graphPlaceholer = new URIImpl("http://purl.org/nanopub/placeholders/graph");

	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Decontextualize obj = new Decontextualize();
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

	private RDFFormat rdfInFormat;
	private OutputStream outputStream = System.out;
	private RDFWriter writer;

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

			writer = Rio.createWriter(RDFFormat.NQUADS, new OutputStreamWriter(outputStream, Charset.forName("UTF-8")));
			writer.startRDF();

			MultiNanopubRdfHandler.process(rdfInFormat, inputFile, new NanopubHandler() {

				@Override
				public void handleNanopub(Nanopub np) {
					try {
						process(np);
					} catch (RDFHandlerException ex) {
						throw new RuntimeException(ex);
					}
				}

			});

			writer.endRDF();
			outputStream.flush();
			if (outputStream != System.out) {
				outputStream.close();
			}
		}
	}

	private void process(Nanopub np) throws RDFHandlerException {
		for (Statement st : getNormalizedStatements(np)) {
			writer.handleStatement(st);
		}
	}

	private List<Statement> getNormalizedStatements(Nanopub np) {
		List<Statement> statements = NanopubUtils.getStatements(np);
		List<Statement> n = new ArrayList<>();
		for (Statement st : statements) {
			boolean isInHead = st.getContext().equals(np.getHeadUri());
			if (isInHead) continue;
			boolean isInProvenance = st.getContext().equals(np.getProvenanceUri());
			boolean isInPubinfo = st.getContext().equals(np.getPubinfoUri());
			URI toBeReplacedUri = null;
			URI replacementUri = null;
			if (isInProvenance) {
				toBeReplacedUri = np.getAssertionUri();
				replacementUri = FingerprintHandler.assertionUriPlaceholder;
			} else if (isInPubinfo) {
				toBeReplacedUri = np.getUri();
				replacementUri = FingerprintHandler.nanopubUriPlaceholder;
			}
			Resource subj = st.getSubject();
			URI pred = st.getPredicate();
			Value obj = st.getObject();
			n.add(new ContextStatementImpl(
					(Resource) transform(subj, toBeReplacedUri, replacementUri),
					(URI) transform(pred, toBeReplacedUri, replacementUri),
					transform(obj, toBeReplacedUri, replacementUri),
					graphPlaceholer));
		}
		return n;
	}

	private Value transform(Value v, URI toBeReplacedUri, URI replacementUri) {
		if (toBeReplacedUri == null) return v;
		if (v.equals(toBeReplacedUri)) {
			return replacementUri;
		}
		return v;
	}

}
