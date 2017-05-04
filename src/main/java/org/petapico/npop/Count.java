package org.petapico.npop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.trustyuri.TrustyUriException;

import org.nanopub.MalformedNanopubException;
import org.nanopub.MultiNanopubRdfHandler;
import org.nanopub.MultiNanopubRdfHandler.NanopubHandler;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Count {

	@com.beust.jcommander.Parameter(description = "input-nanopubs")
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Count obj = new Count();
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

	public static Count getInstance(String args) throws ParameterException {
		NanopubImpl.ensureLoaded();
		if (args == null) args = "";
		Count obj = new Count();
		JCommander jc = new JCommander(obj);
		jc.parse(args.trim().split(" "));
		return obj;
	}

	private RDFFormat rdfInFormat;
	private int npCount, headCount, assertionCount, provCount, pubinfoCount;

	public void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {
		if (inputNanopubs == null || inputNanopubs.isEmpty()) {
			throw new ParameterException("No input files given");
		}
		for (File inputFile : inputNanopubs) {
			npCount = 0;
			headCount = 0;
			assertionCount = 0;
			pubinfoCount = 0;

			if (inFormat != null) {
				rdfInFormat = Rio.getParserFormatForFileName("file." + inFormat);
			} else {
				rdfInFormat = Rio.getParserFormatForFileName(inputFile.toString());
			}

			MultiNanopubRdfHandler.process(rdfInFormat, inputFile, new NanopubHandler() {

				@Override
				public void handleNanopub(Nanopub np) {
					countTriples(np);
				}

			});
			System.out.println("Nanopublications: " + npCount);
			System.out.println("Head triples: " + headCount + " (average: " + ((((float) headCount)) / npCount) + ")");
			System.out.println("Assertion triples: " + assertionCount + " (average: " + ((((float) assertionCount)) / npCount) + ")");
			System.out.println("Provenance triples: " + provCount + " (average: " + ((((float) provCount)) / npCount) + ")");
			System.out.println("Pubinfo triples: " + pubinfoCount + " (average: " + ((((float) pubinfoCount)) / npCount) + ")");
			int t = headCount + assertionCount + provCount + pubinfoCount;
			System.out.println("Total triples: " + t + " (average: " + ((((float) t)) / npCount) + ")");
			System.out.println();
			System.out.println("Table:");
			System.out.println("dataseet,nanopubs,head,assertion,provenance,pubinfo");
			System.out.println(inputFile.getName() + "," + headCount + "," + assertionCount + "," + provCount + "," + pubinfoCount);
			System.out.println();
		}
	}

	public void countTriples(Nanopub np) {
		npCount++;
		headCount += np.getHead().size();
		assertionCount += np.getAssertion().size();
		provCount += np.getProvenance().size();
		pubinfoCount += np.getPubinfo().size();
	}

}
