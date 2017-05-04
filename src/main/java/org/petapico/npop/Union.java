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

public class Union {

	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	@com.beust.jcommander.Parameter(names = "--out-format", description = "Format of the output nanopubs: trig, nq, trix, trig.gz, ...")
	private String outFormat;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Union obj = new Union();
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

	private RDFFormat rdfInFormat, rdfOutFormat;
	private OutputStream outputStream = System.out;
	private Map<String,Boolean> seen = new HashMap<String,Boolean>();
	private int duplicates = 0;

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {
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

		for (File inputFile : inputNanopubs) {
			if (inFormat != null) {
				rdfInFormat = Rio.getParserFormatForFileName("file." + inFormat);
			} else {
				rdfInFormat = Rio.getParserFormatForFileName(inputFile.toString());
			}

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

		}

		System.err.println(duplicates + " duplicates eliminated");
		outputStream.flush();
		if (outputStream != System.out) {
			outputStream.close();
		}
	}

	private void process(Nanopub np) throws RDFHandlerException {
		String u = np.getUri().stringValue();
		if (seen.containsKey(u)) {
			duplicates++;
		} else {
			NanopubUtils.writeToStream(np, outputStream, rdfOutFormat);
			seen.put(u, true);
		}
	}

}
