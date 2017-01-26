package org.petapico.npop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

	@com.beust.jcommander.Parameter(names = "-r", description = "Output reused nanopubs")
	private boolean outputReused = false;

	@com.beust.jcommander.Parameter(names = "-n", description = "Output new nanopubs")
	private boolean outputNew = false;

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	@com.beust.jcommander.Parameter(names = "--reuse-format", description = "Format of the nanopubs to be reused: trig, nq, trix, trig.gz, ...")
	private String reuseFormat;

	@com.beust.jcommander.Parameter(names = "--out-format", description = "Format of the output nanopubs: trig, nq, trix, trig.gz, ...")
	private String outFormat;

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
	private Map<String,Nanopub> reusableNanopubs = new HashMap<>();
	private int reusableCount, uniqueReusableCount, inputCount, reuseCount;

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {

		reusableCount = 0;
		uniqueReusableCount = 0;
		inputCount = 0;
		reuseCount = 0;

		// Loading nanopubs to be reused:
		if (reuseFormat != null) {
			rdfReuseFormat = Rio.getParserFormatForFileName("file." + reuseFormat);
		} else {
			rdfReuseFormat = Rio.getParserFormatForFileName(reuseNanopubFile.toString());
		}
		MultiNanopubRdfHandler.process(rdfReuseFormat, reuseNanopubFile, new NanopubHandler() {

			@Override
			public void handleNanopub(Nanopub np) {
				try {
					String fingerprint = Fingerprint.getFingerprint(np);
					reusableNanopubs.put(fingerprint, np);
					reusableCount++;
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				} catch (RDFHandlerException ex) {
					throw new RuntimeException(ex);
				}
			}

		});
		uniqueReusableCount = reusableNanopubs.size();

		// Reuse matching nanopubs:
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

			System.err.println("Reusable count (unique): " + reusableCount + " (" + uniqueReusableCount + ")");
			System.err.println("Input count: " + inputCount);
			System.err.println("Reuse count: " + reuseCount);
		}
	}

	private void process(Nanopub np) throws IOException, RDFHandlerException {
		inputCount++;
		String fingerprint = Fingerprint.getFingerprint(np);
		if (reusableNanopubs.containsKey(fingerprint)) {
			reuseCount++;
			if (outputReused) {
				output(reusableNanopubs.get(fingerprint));
			}
		} else {
			if (outputNew) {
				output(np);
			}
		}
	}

	private void output(Nanopub np) throws IOException, RDFHandlerException {
		NanopubUtils.writeToStream(np, outputStream, rdfOutFormat);
	}

}
