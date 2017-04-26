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

	@com.beust.jcommander.Parameter(names = "-i", description = "Property URIs to ignore, separated by '|' (has no effect if -d is set)")
	private String ignoreProperties;

	@com.beust.jcommander.Parameter(names = "-h", description = "Topic handler class")
	private String handlerClass;

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
		obj.init();
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
		obj.init();
		return obj;
	}

	private RDFFormat rdfInFormat;
	private OutputStream outputStream = System.out;
	private BufferedWriter writer;
	private Map<String,Boolean> ignore = new HashMap<>();
	private TopicHandler topicHandler;

	private void init() {
		if (handlerClass != null && !handlerClass.isEmpty()) {
			String detectorClassName = handlerClass;
			if (!handlerClass.contains(".")) {
				detectorClassName = "org.petapico.npop.topic." + handlerClass;
			}
			try {
				topicHandler = (TopicHandler) Class.forName(detectorClassName).newInstance();
			} catch (ReflectiveOperationException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

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
						writer.write(np.getUri() + " " + getTopic(np) + "\n");
					} catch (IOException ex) {
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

	public String getTopic(Nanopub np) {
		if (topicHandler != null) {
			// Get topic via handler class
			return topicHandler.getTopic(np);
		} else {
			// Calculate topic directly
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
	}


	public interface TopicHandler {

		public String getTopic(Nanopub np);

	}

}
