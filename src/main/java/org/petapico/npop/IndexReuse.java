package org.petapico.npop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
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
import org.nanopub.extra.index.IndexUtils;
import org.nanopub.extra.index.NanopubIndex;
import org.nanopub.extra.index.NanopubIndexCreator;
import org.nanopub.extra.index.SimpleIndexCreator;
import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class IndexReuse {

	@com.beust.jcommander.Parameter(description = "input-nanopub-cache", required = true)
	private List<File> inputNanopubCache = new ArrayList<File>();

	@com.beust.jcommander.Parameter(names = "-x", description = "Index nanopubs to be reused (need to be sorted; no subindex supported)")
	private File reuseIndexFile;

	@com.beust.jcommander.Parameter(names = "-o", description = "Output file of new index nanopublications")
	private File outputFile;

	@com.beust.jcommander.Parameter(names = "-a", description = "Output file of all index nanopublications")
	private File allOutputFile;

	@com.beust.jcommander.Parameter(names = "-r", description = "Append line to this table file")
	private File tableFile;

	@com.beust.jcommander.Parameter(names = "--reuse-format", description = "Format of the nanopubs to be reused: trig, nq, trix, trig.gz, ...")
	private String reuseFormat;

	@com.beust.jcommander.Parameter(names = "--out-format", description = "Format of the output nanopubs: trig, nq, trix, trig.gz, ...")
	private String outFormat;

	@com.beust.jcommander.Parameter(names = "-U", description = "Base URI for index nanopubs")
	private String baseUri = "http://purl.org/np/";

	@com.beust.jcommander.Parameter(names = "-T", description = "Title of index")
	private String iTitle;

	@com.beust.jcommander.Parameter(names = "-D", description = "Description of index")
	private String iDesc;

	@com.beust.jcommander.Parameter(names = "-C", description = "Creator of index")
	private List<String> iCreators = new ArrayList<>();

	@com.beust.jcommander.Parameter(names = "-A", description = "'See also' resources")
	private List<String> seeAlso = new ArrayList<>();

	public static void main(String[] args) {
		NanopubImpl.ensureLoaded();
		IndexReuse obj = new IndexReuse();
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

	private URI lastIndexUri = null;
	private RDFFormat rdfReuseFormat, rdfOutFormat;
	private PrintStream outputStream = System.out;
	private PrintStream allOutputStream;
	private List<String> contentNanopubList = new ArrayList<>();
	private Map<String,Boolean> contentNanopubMap = new HashMap<>();
	private int reuseCount;
	private boolean reuseStopped = false;
	private NanopubIndexCreator indexCreator = null;

	private void run() throws IOException, RDFParseException, RDFHandlerException,
			MalformedNanopubException, TrustyUriException {

		reuseCount = 0;

		for (File inputFile : inputNanopubCache) {
			if (outputFile == null) {
				if (outFormat == null) {
					outFormat = "trig";
				}
				rdfOutFormat = Rio.getParserFormatForFileName("file." + outFormat);
			} else {
				rdfOutFormat = Rio.getParserFormatForFileName(outputFile.getName());
				if (outputFile.getName().endsWith(".gz")) {
					outputStream = new PrintStream(new GZIPOutputStream(new FileOutputStream(outputFile)));
				} else {
					outputStream = new PrintStream(new FileOutputStream(outputFile));
				}
			}
			if (allOutputFile != null) {
				if (allOutputFile.getName().endsWith(".gz")) {
					allOutputStream = new PrintStream(new GZIPOutputStream(new FileOutputStream(allOutputFile)));
				} else {
					allOutputStream = new PrintStream(new FileOutputStream(allOutputFile));
				}
			}

			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(inputFile));
			    String line;
			    while ((line = br.readLine()) != null) {
			    	line = line.trim();
			    	if (line.isEmpty()) continue;
			    	String[] columns = line.split(" ");
			    	String uri = columns[0];
					contentNanopubList.add(uri);
					contentNanopubMap.put(uri, true);
			    }
			} finally {
				if (br != null) br.close();
			}

			if (reuseIndexFile != null) {
				if (reuseFormat != null) {
					rdfReuseFormat = Rio.getParserFormatForFileName("file." + reuseFormat);
				} else {
					rdfReuseFormat = Rio.getParserFormatForFileName(reuseIndexFile.toString());
				}
				MultiNanopubRdfHandler.process(rdfReuseFormat, reuseIndexFile, new NanopubHandler() {
		
					@Override
					public void handleNanopub(Nanopub np) {
						try {
							if (!reuseStopped) {
								processIndexNanopub(np);
							}
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						} catch (RDFHandlerException ex) {
							throw new RuntimeException(ex);
						} catch (MalformedNanopubException ex) {
							throw new RuntimeException(ex);
						}
					}
		
				});
			}

			indexCreator = new IndexCreator(lastIndexUri);

			for (String npUri : contentNanopubList) {
				if (!contentNanopubMap.containsKey(npUri)) continue;
				indexCreator.addElement(new URIImpl(npUri));
			}
			indexCreator.finalizeNanopub();

			outputStream.flush();
			if (outputStream != System.out) {
				outputStream.close();
			}
			if (allOutputStream != null) {
				allOutputStream.flush();
				allOutputStream.close();
			}

			System.err.println("Index reuse count: " + reuseCount);
			if (tableFile != null) {
				PrintStream st = new PrintStream(new FileOutputStream(tableFile, true));
				st.println(inputFile.getName() + "," + reuseCount);
				st.close();
			}
		}
	}

	private void processIndexNanopub(Nanopub np) throws IOException, RDFHandlerException, MalformedNanopubException {
		NanopubIndex npi = IndexUtils.castToIndex(np);
		if (!npi.getSubIndexes().isEmpty()) {
			throw new RuntimeException("Subindexes are not supported");
		}
		if (lastIndexUri == null && npi.getAppendedIndex() != null) {
			throw new RuntimeException("Starting index nanopub expected first in file");
		} else if (lastIndexUri != null && npi.getAppendedIndex() == null) {
			throw new RuntimeException("Non-appending index nanopub found after first position");
		}
		boolean canBeReused = true;
		for (URI c : npi.getElements()) {
			if (!contentNanopubMap.containsKey(c.stringValue())) {
				canBeReused = false;
				break;
			}
		}
		if (canBeReused && !npi.getElements().isEmpty()) {
			reuseCount++;
			outputOld(npi);
			for (URI c : npi.getElements()) {
				contentNanopubMap.remove(c.stringValue());
			}
			lastIndexUri = npi.getUri();
		} else {
			reuseStopped = true;
		}
	}

	private void output(NanopubIndex npi) {
		try {
			outputStream.print(NanopubUtils.writeToString(npi, rdfOutFormat) + "\n\n");
			if (allOutputStream != null) {
				allOutputStream.print(NanopubUtils.writeToString(npi, rdfOutFormat) + "\n\n");
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void outputOld(NanopubIndex npi) {
		try {
			if (allOutputStream != null) {
				allOutputStream.print(NanopubUtils.writeToString(npi, rdfOutFormat) + "\n\n");
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}


	private class IndexCreator extends SimpleIndexCreator {

		public IndexCreator(URI previousIndexUri) {
			super(previousIndexUri);

			setBaseUri(baseUri);
			if (iTitle != null) {
				setTitle(iTitle);
			}
			if (iDesc != null) {
				setDescription(iDesc);
			}
			for (String creator : iCreators) {
				addCreator(creator);
			}
			for (String sa : seeAlso) {
				addSeeAlsoUri(new URIImpl(sa));
			}
		}

		@Override
		public void handleIncompleteIndex(NanopubIndex npi) {
			output(npi);
		}

		@Override
		public void handleCompleteIndex(NanopubIndex npi) {
			System.out.println("Index URI: " + npi.getUri());
			output(npi);
		}

	}

}
