package org.petapico.npop;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Gml {

	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Gml obj = new Gml();
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
	private Map<String,Integer> nodes = new HashMap<>();
	private BufferedWriter writer;
	private int nodeCounter = 0;

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
			writer.write("graph [\n");

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

			writer.write("]\n");
			if (outputStream != System.out) {
				writer.close();
				outputStream.close();
			} else {
				outputStream.flush();
			}
		}
	}

	private void process(Nanopub np) throws RDFHandlerException, IOException {
		for (Statement st : np.getAssertion()) {
			if (!(st.getObject() instanceof URI)) continue;
			String s = st.getSubject().stringValue();
			String p = st.getPredicate().stringValue();
			String o = st.getObject().stringValue();
			int si, oi;
			if (nodes.containsKey(s)) {
				si = nodes.get(s);
			} else {
				si = nodeCounter++;
				nodes.put(s, si);
				writer.write("node [\n");
				writer.write("id N" + si + "N\n");
				writer.write("label \"" + s + "\"\n");
				writer.write("]\n");
			}
			if (nodes.containsKey(o)) {
				oi = nodes.get(o);
			} else {
				oi = nodeCounter++;
				nodes.put(o, oi);
				writer.write("node [\n");
				writer.write("id N" + oi + "N\n");
				writer.write("label \"" + o + "\"\n");
				writer.write("]\n");
			}
			writer.write("edge [\n");
			writer.write("source N" + si + "N\n");
			writer.write("target N" + oi + "N\n");
			writer.write("label \"" + p + "\"\n");
			writer.write("]\n");
		}
	}

}
