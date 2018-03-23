package org.petapico.npop;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import net.trustyuri.TrustyUriException;

import org.nanopub.MalformedNanopubException;
import org.nanopub.MultiNanopubRdfHandler;
import org.nanopub.MultiNanopubRdfHandler.NanopubHandler;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Namespaces {

	@com.beust.jcommander.Parameter(description = "input-nanopubs")
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-h", description = "Output file for namespaces used in head graph")
	private File headOutputFile;

	@com.beust.jcommander.Parameter(names = "-a", description = "Output file for namespaces used in assertion graph")
	private File assertionOutputFile;

	@com.beust.jcommander.Parameter(names = "-p", description = "Output file for namespaces used in provenance graph")
	private File provOutputFile;

	@com.beust.jcommander.Parameter(names = "-i", description = "Output file for namespaces used in pub info graph")
	private File pubinfoOutputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Namespaces obj = new Namespaces();
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

	public static Namespaces getInstance(String args) throws ParameterException {
		NanopubImpl.ensureLoaded();
		if (args == null) args = "";
		Namespaces obj = new Namespaces();
		JCommander jc = new JCommander(obj);
		jc.parse(args.trim().split(" "));
		obj.init();
		return obj;
	}

	private RDFFormat rdfInFormat;
	private BufferedWriter headWriter, assertionWriter, provWriter, pubinfoWriter;

	private void init() {
	}

	public void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {
		if (inputNanopubs == null || inputNanopubs.isEmpty()) {
			throw new ParameterException("No input files given");
		}
		for (File inputFile : inputNanopubs) {
			if (inFormat != null) {
				rdfInFormat = Rio.getParserFormatForFileName("file." + inFormat);
			} else {
				rdfInFormat = Rio.getParserFormatForFileName(inputFile.toString());
			}
			headWriter = makeWriter(headOutputFile);
			assertionWriter = makeWriter(assertionOutputFile);
			provWriter = makeWriter(provOutputFile);
			pubinfoWriter = makeWriter(pubinfoOutputFile);

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

			closeWriter(headWriter);
			closeWriter(assertionWriter);
			closeWriter(provWriter);
			closeWriter(pubinfoWriter);
		}
	}

	public void process(Nanopub np) throws RDFHandlerException, IOException {
		writeNamespaces(np.getHead(), headWriter);
		writeNamespaces(np.getAssertion(), assertionWriter);
		writeNamespaces(np.getProvenance(), provWriter);
		writeNamespaces(np.getPubinfo(), pubinfoWriter);
	}

	private void writeNamespaces(Set<Statement> statements, BufferedWriter w) throws IOException {
		if (w == null) return;
		Set<String> namespaces = new HashSet<>();
		for (Statement st : statements) {
			namespaces.add(getNamespace(st.getPredicate()));
		}
		for (String n : namespaces) {
			w.write(n + "\n");
		}
	}

	private String getNamespace(URI uri) {
		return uri.toString().replaceFirst("[A-Za-z0-9_.-]*.$", "");
	}

	private BufferedWriter makeWriter(File f) throws IOException {
		if (f == null) return null;
		OutputStream stream = null;
		if (f.getName().endsWith(".gz")) {
			stream = new GZIPOutputStream(new FileOutputStream(f));
		} else {
			stream = new FileOutputStream(f);
		}
		return new BufferedWriter(new OutputStreamWriter(stream));
	}

	private void closeWriter(Writer w) throws IOException {
		if (w == null) return;
		w.flush();
		w.close();
	}

}
