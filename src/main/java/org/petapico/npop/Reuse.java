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
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import net.trustyuri.TrustyUriException;

import org.nanopub.MalformedNanopubException;
import org.nanopub.MultiNanopubRdfHandler;
import org.nanopub.MultiNanopubRdfHandler.NanopubHandler;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.nanopub.NanopubUtils;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Reuse {

	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-x", description = "Nanopubs to be reused", required = true)
	private File reuseNanopubFile;

	@com.beust.jcommander.Parameter(names = "-n", description = "Output new nanopubs")
	private boolean outputNew = false;

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file (requires option -r and/or -n to be set)")
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
	private String fingerprintingOptions;

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
		try {
			obj.run();
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	private RDFFormat rdfInFormat, rdfReuseFormat, rdfOutFormat;
	private OutputStream outputStream = System.out;
	private PrintStream uriStream = null;
	private Map<String,String> reusableNanopubs = new HashMap<>();
	private int reusableCount, uniqueReusableCount, inputCount, reuseCount;
	private Set<String> fpOptions = Fingerprint.parseFingerprintingOptions(fingerprintingOptions);

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {

		reusableCount = 0;
		uniqueReusableCount = 0;
		inputCount = 0;
		reuseCount = 0;

		// Loading nanopubs to be reused:
		if (reuseNanopubFile.getName().endsWith(".txt")) {
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
						String fingerprint = Fingerprint.getFingerprint(np, fpOptions);
						reusableNanopubs.put(fingerprint, np.getUri().toString());
						reusableCount++;
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
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					} catch (RDFHandlerException ex) {
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

			System.err.println("Reusable count (unique): " + reusableCount + " (" + uniqueReusableCount + ")");
			System.err.println("Input count: " + inputCount);
			System.err.println("Reuse count: " + reuseCount);
		}
	}

	private void process(Nanopub np) throws IOException, RDFHandlerException {
		inputCount++;
		String fingerprint = Fingerprint.getFingerprint(np, fpOptions);
		String uri = np.getUri().toString();
		if (reusableNanopubs.containsKey(fingerprint)) {
			reuseCount++;
			uri = reusableNanopubs.get(fingerprint);
		} else {
			if (outputNew) {
				output(np);
			}
		}
		if (uriStream != null) {
			uriStream.println(uri + " " + fingerprint);
		}
	}

	private void output(Nanopub np) throws IOException, RDFHandlerException {
		NanopubUtils.writeToStream(np, outputStream, rdfOutFormat);
	}

}
