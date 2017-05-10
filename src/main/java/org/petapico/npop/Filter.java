package org.petapico.npop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
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
import org.openrdf.model.Statement;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Filter {

	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-f", description = "Filter by URI or literal")
	private String filter = null;

	@com.beust.jcommander.Parameter(names = "-F", description = "Filter by URI or literal read from file")
	private File filterFile = null;

	@com.beust.jcommander.Parameter(names = "--split", description = "Treat blanks in filter string as OR connectives")
	private boolean splitFilter = false;

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	@com.beust.jcommander.Parameter(names = "--out-format", description = "Format of the output nanopubs: trig, nq, trix, trig.gz, ...")
	private String outFormat;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Filter obj = new Filter();
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
	private Map<String,Boolean> filterComponents;

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {
		if (splitFilter) {
			for (String s : filter.split(" ")) {
				filterComponents.put(s, true);
			}
		} else {
			filterComponents.put(filter, true);
		}
		if (filterFile != null) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(filterFile));
			    String line;
			    while ((line = br.readLine()) != null) {
			    	line = line.trim();
			    	if (line.isEmpty()) continue;
			    	filterComponents.put(line, true);
			    }
			} finally {
				if (br != null) br.close();
			}
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
					} catch (RDFHandlerException ex) {
						throw new RuntimeException(ex);
					}
				}

			});

			outputStream.flush();
			if (outputStream != System.out) {
				outputStream.close();
			}
		}
	}

	private void process(Nanopub np) throws RDFHandlerException {
		if (matchesFilter(np)) {
			NanopubUtils.writeToStream(np, outputStream, rdfOutFormat);
		}
	}

	private boolean matchesFilter(Nanopub np) {
		for (Statement st : NanopubUtils.getStatements(np)) {
			if (filterComponents.containsKey(st.getSubject().stringValue())) {
				return true;
			}
			if (filterComponents.containsKey(st.getPredicate().stringValue())) {
				return true;
			}
			if (filterComponents.containsKey(st.getObject().stringValue())) {
				return true;
			}
			if (filterComponents.containsKey(st.getContext().stringValue())) {
				return true;
			}
		}
		return false;
	}

}
