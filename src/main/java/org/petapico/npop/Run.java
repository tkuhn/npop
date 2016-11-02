package org.petapico.npop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import net.trustyuri.TrustyUriException;
import net.trustyuri.TrustyUriResource;

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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Run {

	@com.beust.jcommander.Parameter(description = "input-nanopubs", required = true)
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-f", description = "Filter by URI or literal")
	private String filter = null;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Run obj = new Run();
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

	private RDFFormat format;
	private OutputStream out;

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {
		for (File inputFile : inputNanopubs) {
			File outFile = new File(inputFile.getParent(), "op." + inputFile.getName());
			if (inputFile.getName().matches(".*\\.(gz|gzip)")) {
				out = new GZIPOutputStream(new FileOutputStream(outFile));
			} else {
				out = new FileOutputStream(outFile);
			}
			format = new TrustyUriResource(inputFile).getFormat(RDFFormat.TRIG);
			MultiNanopubRdfHandler.process(format, inputFile, new NanopubHandler() {

				@Override
				public void handleNanopub(Nanopub np) {
					try {
						process(np);
					} catch (RDFHandlerException ex) {
						throw new RuntimeException(ex);
					}
				}

			});
			out.close();
		}
	}

	private void process(Nanopub np) throws RDFHandlerException {
		if (filter != null) {
			boolean keep = false;
			for (Statement st : NanopubUtils.getStatements(np)) {
				if (st.getSubject().stringValue().equals(filter)) {
					keep = true;
					break;
				}
				if (st.getPredicate().stringValue().equals(filter)) {
					keep = true;
					break;
				}
				if (st.getObject().stringValue().equals(filter)) {
					keep = true;
					break;
				}
				if (st.getContext().stringValue().equals(filter)) {
					keep = true;
					break;
				}
			}
			if (!keep) return;
		}
		NanopubUtils.writeToStream(np, out, format);
	}

}
