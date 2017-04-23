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
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Topic {

	@com.beust.jcommander.Parameter(description = "input-nanopubs")
	private List<File> inputNanopubs = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "--in-format", description = "Format of the input nanopubs: trig, nq, trix, trig.gz, ...")
	private String inFormat;

	@com.beust.jcommander.Parameter(names = "-u", description = "Include the nanopub URI in output")
	private boolean outputNanopubUri = false;

	@com.beust.jcommander.Parameter(names = "-i", description = "Property URIs to ignore, separated by '|' (has no effect if -d is set)")
	private String ignoreProperties;

	@com.beust.jcommander.Parameter(names = "-d", description = "Topic detector class")
	private String detectorClass;

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		Topic obj = new Topic();
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

	public static Topic getInstance(String args) throws ParameterException {
		NanopubImpl.ensureLoaded();
		if (args == null) args = "";
		Topic obj = new Topic();
		JCommander jc = new JCommander(obj);
		jc.parse(args.trim().split(" "));
		return obj;
	}

	private RDFFormat rdfInFormat;
	private OutputStream outputStream = System.out;
	private BufferedWriter writer;
	private Map<String,Boolean> ignore = new HashMap<>();

	public void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {
		if (ignoreProperties != null) {
			for (String s : ignoreProperties.trim().split("\\|")) {
				if (!s.isEmpty()) ignore.put(s, true);
			}
		}
		if (inputNanopubs == null || inputNanopubs.isEmpty()) {
			throw new ParameterException("No input files given");
		}
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

			MultiNanopubRdfHandler.process(rdfInFormat, inputFile, new NanopubHandler() {

				@Override
				public void handleNanopub(Nanopub np) {
					try {
						process(np);
					} catch (RDFHandlerException ex) {
						throw new RuntimeException(ex);
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					} catch (ReflectiveOperationException ex) {
						throw new RuntimeException(ex);
					}
				}

			});

			writer.flush();
			if (outputStream != System.out) {
				writer.close();
			}
		}
	}

	private void process(Nanopub np) throws RDFHandlerException, IOException, ReflectiveOperationException {
		String topic;
		if (detectorClass != null && !detectorClass.isEmpty()) {
			String detectorClassName = detectorClass;
			if (!detectorClass.contains(".")) {
				detectorClassName = "org.petapico.npop.topic." + detectorClass;
			}
			TopicDetector td = (TopicDetector) Class.forName(detectorClassName).newInstance();
			topic = td.getTopic(np);
		} else {
			topic = getTopic(np);
		}
		writer.write(topic);
		if (outputNanopubUri) {
			writer.write(" " + np.getUri());
		}
		writer.write("\n");
	}

	public String getTopic(Nanopub np) {
		Map<Resource,Integer> resourceCount = new HashMap<>();
		for (Statement st : np.getAssertion()) {
			Resource subj = st.getSubject();
			if (subj.equals(np.getUri())) continue;
			if (ignore.containsKey(st.getPredicate().stringValue())) continue;
			if (!resourceCount.containsKey(subj)) resourceCount.put(subj, 0);
			resourceCount.put(subj, resourceCount.get(subj) + 1);
		}
		int max = 0;
		Resource topic = null;
		for (Resource r : resourceCount.keySet()) {
			int c = resourceCount.get(r);
			if (c > max) {
				topic = r;
				max = c;
			} else if (c == max) {
				topic = null;
			}
		}
		return topic + "";
	}


	public interface TopicDetector {

		public String getTopic(Nanopub np);

	}

}
