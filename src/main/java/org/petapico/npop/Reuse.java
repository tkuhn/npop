package org.petapico.npop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import net.trustyuri.TrustyUriException;

import org.nanopub.MalformedNanopubException;
import org.nanopub.MultiNanopubRdfHandler;
import org.nanopub.MultiNanopubRdfHandler.NanopubHandler;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.nanopub.NanopubRdfHandler;
import org.nanopub.NanopubUtils;
import org.nanopub.trusty.FixTrustyNanopub;
import org.openrdf.model.URI;
import org.openrdf.model.impl.ContextStatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Reuse {

	public static final URI SUPERSEDES = new URIImpl("http://purl.org/nanopub/x/supersedes");


	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-x", description = "Nanopubs to be reused", required = true)
	private File reuseNanopubFile;

	@com.beust.jcommander.Parameter(names = "-n", description = "Output new nanopubs")
	private boolean outputNew = false;

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file (requires option -n to be set)")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "-u", description = "Output text file with URIs and fingerprints (can afterwards be used as "+
			"a reuse file for argument -x or to create an index)")
	private File uriFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	@com.beust.jcommander.Parameter(names = "--reuse-format", description = "Format of the nanopubs to be reused: trig, nq, trix, trig.gz, ...")
	private String reuseFormat;

	@com.beust.jcommander.Parameter(names = "--out-format", description = "Format of the output nanopubs: trig, nq, trix, trig.gz, ...")
	private String outFormat;

	@com.beust.jcommander.Parameter(names = "-f", description = "Fingerprinting options")
	private String fingerprintOptions;

	@com.beust.jcommander.Parameter(names = "-s", description = "Add npx:supersedes backlinks for changed nanopublications")
	private boolean addSupersedesBacklinks = false;

	@com.beust.jcommander.Parameter(names = "-t", description = "Topic options")
	private String topicOptions;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Reuse obj = new Reuse();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		obj.init();
		try {
			obj.run();
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	private static final String multipleNanopubs = "MULTI";
	private static final String matchedNanopub = "MATCHED";

	private RDFFormat rdfInFormat, rdfReuseFormat, rdfOutFormat;
	private OutputStream outputStream = System.out;
	private PrintStream uriStream = null;
	private Map<String,String> reusableNanopubs = new HashMap<>();
	private Map<String,String> existingTopics = new HashMap<>();
	private int reusableCount, uniqueReusableCount, inputCount, reuseCount, inTopicDuplCount, outTopicDuplCount, topicMatchErrors, topicMatchCount;
	private Fingerprint fingerprint;
	private Topic topic;

	private void init() {
		try {
			fingerprint = Fingerprint.getInstance(fingerprintOptions);
		} catch (ParameterException ex) {
			System.err.println(ex);
		}
		try {
			topic = Topic.getInstance(topicOptions);
		} catch (ParameterException ex) {
			System.err.println(ex);
		}
	}

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {

		reusableCount = 0;
		uniqueReusableCount = 0;
		inputCount = 0;
		reuseCount = 0;
		inTopicDuplCount = 0;
		topicMatchCount = 0;
		topicMatchErrors = 0;

		// TODO: first dataset creation

		// Loading nanopubs to be reused:
		if (reuseNanopubFile.getName().endsWith(".txt")) {
			System.err.println("zzz");
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(reuseNanopubFile));
			    String line;
			    while ((line = br.readLine()) != null) {
			    	line = line.trim();
			    	if (line.isEmpty()) continue;
			    	String[] columns = line.split(" ");
			    	String uri = columns[0];
			    	String fingerprint = columns[1];
			    	reusableNanopubs.put(fingerprint, uri);
					reusableCount++;
					// TODO: consider topics here too
			    }
			} finally {
				if (br != null) br.close();
			}
		} else {
			if (reuseFormat != null) {
				rdfReuseFormat = Rio.getParserFormatForFileName("file." + reuseFormat);
			} else {
				rdfReuseFormat = Rio.getParserFormatForFileName(reuseNanopubFile.toString());
			}
			MultiNanopubRdfHandler.process(rdfReuseFormat, reuseNanopubFile, new NanopubHandler() {
	
				@Override
				public void handleNanopub(Nanopub np) {
					try {
						String fp = fingerprint.getFingerprint(np);
						reusableNanopubs.put(fp, np.getUri().toString());
						reusableCount++;
						if (addSupersedesBacklinks) {
							String t = topic.getTopic(np);
							if (existingTopics.containsKey(t)) {
								existingTopics.put(t, multipleNanopubs);
								inTopicDuplCount++;
								topicMatchErrors++;
							} else {
								existingTopics.put(t, np.getUri().toString());
							}
						}
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					} catch (RDFHandlerException ex) {
						throw new RuntimeException(ex);
					}
				}
	
			});
		}
		uniqueReusableCount = reusableNanopubs.size();

		// Reuse matching nanopubs:
		if (uriFile != null) {
			uriStream = new PrintStream(uriFile);
		}
		for (File inputFile : inputNanopubs) {
			if (inFormat != null) {
				rdfInFormat = Rio.getParserFormatForFileName("file." + inFormat);
			} else {
				rdfInFormat = Rio.getParserFormatForFileName(inputFile.toString());
			}
			if (outputFile == null) {
				if (outFormat == null) {
					outFormat = "trig";
				}
				rdfOutFormat = Rio.getParserFormatForFileName("file." + outFormat);
			} else {
				rdfOutFormat = Rio.getParserFormatForFileName(outputFile.getName());
				if (outputFile.getName().endsWith(".gz")) {
					outputStream = new GZIPOutputStream(new FileOutputStream(outputFile));
				} else {
					outputStream = new FileOutputStream(outputFile);
				}
			}

			MultiNanopubRdfHandler.process(rdfInFormat, inputFile, new NanopubHandler() {

				@Override
				public void handleNanopub(Nanopub np) {
					try {
						process(np);
					} catch (Exception ex) {
						throw new RuntimeException(ex);
					}
				}

			});

			outputStream.flush();
			if (outputStream != System.out) {
				outputStream.close();
			}
			if (uriStream != null) {
				uriStream.flush();
				uriStream.close();
			}

			System.err.println("Older dataset count (unique): " + reusableCount + " (" + uniqueReusableCount + ")");
			System.err.println("Newer dataset count: " + inputCount);
			System.err.println("Reuse count: " + reuseCount);
			if (addSupersedesBacklinks) {
				System.err.println("Topic match count: " + topicMatchCount);
				System.err.println("Duplicate topics in older dataset: " + inTopicDuplCount);
				System.err.println("Duplicate topics in newer dataset: " + outTopicDuplCount);
				System.err.println("Total topic matching errors: " + topicMatchErrors);
			}
		}
	}

	private void process(Nanopub np) throws IOException, RDFHandlerException, MalformedNanopubException, TrustyUriException {
		inputCount++;
		String fp = fingerprint.getFingerprint(np);
		String t = null;
		if (addSupersedesBacklinks) {
			t = topic.getTopic(np);
		}
		String uri = np.getUri().toString();
		if (reusableNanopubs.containsKey(fp)) {
			reuseCount++;
			uri = reusableNanopubs.get(fp);
			if (addSupersedesBacklinks) {
				String et = existingTopics.get(t);
				if (et == multipleNanopubs || et == matchedNanopub) {
					topicMatchErrors++;
				}
				existingTopics.put(t, matchedNanopub);
			}
		} else {
			if (addSupersedesBacklinks) {
				if (existingTopics.containsKey(t)) {
					String et = existingTopics.get(t);
					if (et == multipleNanopubs) {
						topicMatchErrors++;
					} else if (et == matchedNanopub) {
						topicMatchErrors++;
						outTopicDuplCount++;
					} else {
						topicMatchCount++;
						String oldNpUri = existingTopics.get(t);
						existingTopics.put(t, matchedNanopub);
						np = addSupersedesBacklink(np, new URIImpl(oldNpUri));
						uri = np.getUri().toString();
					}
				}
			}
			if (outputNew) {
				output(np);
			}
		}
		if (uriStream != null) {
			uriStream.println(uri + " " + fp);
		}
	}

	private void output(Nanopub np) throws IOException, RDFHandlerException {
		NanopubUtils.writeToStream(np, outputStream, rdfOutFormat);
	}

	private Nanopub addSupersedesBacklink(final Nanopub newNp, final URI oldUri)
			throws RDFHandlerException, MalformedNanopubException, TrustyUriException {
		SupersedesLinkAdder linkAdder = new SupersedesLinkAdder(oldUri, newNp);
		NanopubUtils.propagateToHandler(newNp, linkAdder);
		return FixTrustyNanopub.fix(linkAdder.getNanopub());
	}

	
	private class SupersedesLinkAdder extends NanopubRdfHandler {

		private URI oldUri;
		private Nanopub newNp;

		public SupersedesLinkAdder(URI oldUri, Nanopub newNp) {
			this.oldUri = oldUri;
			this.newNp = newNp;
		}

		@Override
		public void endRDF() throws RDFHandlerException {
			handleStatement(new ContextStatementImpl(
					newNp.getUri(), SUPERSEDES, oldUri, newNp.getPubinfoUri()));
			super.endRDF();
		}
	}

}