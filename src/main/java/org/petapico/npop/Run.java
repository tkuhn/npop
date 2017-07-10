package org.petapico.npop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nanopub.NanopubImpl;
import org.openrdf.OpenRDFException;
import org.openrdf.rio.RDFParserRegistry;
import org.openrdf.rio.RDFWriterRegistry;
import org.openrdf.rio.turtle.TurtleParserFactory;
import org.openrdf.rio.turtle.TurtleWriterFactory;

public class Run {

	private Run() {}  // no instances allowed

	public static void main(String[] args) throws IOException, OpenRDFException {
		NanopubImpl.ensureLoaded();
		
		// Not sure why this isnt' done automatically...:
		RDFParserRegistry.getInstance().add(new TurtleParserFactory());
		RDFWriterRegistry.getInstance().add(new TurtleWriterFactory());

		run(args);
	}

	private static List<Class<?>> runnableClasses = new ArrayList<>();
	private static Map<String,Class<?>> runnableClassesByName = new HashMap<>();
	private static Map<String,Class<?>> runnableClassesByShortcut = new HashMap<>();
	private static Map<Class<?>,String> runnableClassNames = new HashMap<>();
	private static Map<Class<?>,String> runnableClassShortcuts = new HashMap<>();

	private static void addRunnableClass(Class<?> c, String shortcut) {
		runnableClasses.add(c);
		runnableClassesByName.put(c.getSimpleName(), c);
		runnableClassNames.put(c, c.getSimpleName());
		if (shortcut != null) {
			runnableClassesByShortcut.put(shortcut, c);
			runnableClassShortcuts.put(c, shortcut);
		}
	}

	static {
		addRunnableClass(Filter.class, "filter");
		addRunnableClass(Extract.class, "extract");
		addRunnableClass(Gml.class, "gml");
		addRunnableClass(Fingerprint.class, "fingerprint");
		addRunnableClass(Topic.class, "topic");
		addRunnableClass(Reuse.class, "reuse");
		addRunnableClass(Count.class, "count");
		addRunnableClass(Decontextualize.class, "decontext");
		addRunnableClass(Union.class, "union");
		addRunnableClass(IndexReuse.class, "ireuse");
		addRunnableClass(ExportJson.class, "exportjson");
	}

	public static void run(String[] command) throws IOException, OpenRDFException {
		if (command.length == 0) {
			System.err.println("ERROR: missing command");
			System.err.println("Run 'npop help' to show all available commands.");
			System.exit(1);
		}
		String cmd = command[0];
		String[] cmdArgs = Arrays.copyOfRange(command, 1, command.length);
		Class<?> runClass = runnableClassesByName.get(cmd);
		if (runClass == null) {
			runClass = runnableClassesByShortcut.get(cmd);
		}
		if (runClass != null) {
			try {
				runClass.getMethod("main", String[].class).invoke(runClass, (Object) cmdArgs);
			} catch (Exception ex) {
				System.err.println("Internal error: " + ex.getMessage());
				ex.printStackTrace(System.err);
				System.exit(1);
			}
		} else if (cmd.equals("help")) {
			System.err.println("Available commands:");
			for (Class<?> c : runnableClasses) {
				String s = runnableClassShortcuts.get(c);
				String n = runnableClassNames.get(c);
				if (s == null) {
					System.err.println("- " + n);
				} else {
					System.err.println("- " + s + " / " + n);
				}
			}
			System.exit(0);
		} else {
			System.err.println("ERROR. Unrecognized command: " + cmd);
			System.err.println("Run 'npop help' to show all available commands.");
			System.exit(1);
		}
	}

}
