/*******************************************************************************
 * 
 * Copyright (c) 2008 Thomas Holland (thomas@innot.de) and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Thomas Holland - initial API and implementation
 *     
 * $Id$
 *     
 *******************************************************************************/
package de.innot.avreclipse.core.toolinfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;

import de.innot.avreclipse.AVRPlugin;
import de.innot.avreclipse.core.IMCUProvider;
import de.innot.avreclipse.core.avrdude.AVRDudeException;
import de.innot.avreclipse.core.avrdude.ProgrammerConfig;
import de.innot.avreclipse.core.avrdude.AVRDudeException.Reason;
import de.innot.avreclipse.core.paths.AVRPath;
import de.innot.avreclipse.core.paths.AVRPathProvider;
import de.innot.avreclipse.core.paths.IPathProvider;
import de.innot.avreclipse.core.preferences.AVRDudePreferences;
import de.innot.avreclipse.core.util.AVRMCUidConverter;

/**
 * This class handles all interactions with the avrdude program.
 * <p>
 * It implements the {@link IMCUProvider} Interface to get a list of all MCUs
 * supported by the selected version of AVRDude. Additional methods are
 * available to get a list of all supported Programmers.
 * </p>
 * <p>
 * This class implements the Singleton pattern. Use the {@link #getDefault()}
 * method to get the instance of this class.
 * </p>
 * 
 * @author Thomas Holland
 * @since 2.2
 */
public class AVRDude implements IMCUProvider {

	/** The singleton instance of this class */
	private static AVRDude instance = null;

	/**
	 * A list of all currently supported MCUs (with avrdude MCU id values),
	 * mapped to the ConfigEntry
	 */
	private Map<String, ConfigEntry> fMCUList;

	/**
	 * A list of all currently supported Programmer devices, mapped to the
	 * ConfigEntry
	 */
	private Map<String, ConfigEntry> fProgrammerList;

	/**
	 * Mapping of the Plugin MCU Id values (as keys) to the avrdude mcu id
	 * values (as values)
	 */
	private Map<String, String> fMCUIdMap = null;

	/** The current path to the directory of the avrdude executable */
	private IPath fCurrentPath = null;

	/** The name of the avrdude executable */
	private final static String fCommandName = "avrdude";

	/** The Path provider for the avrdude executable */
	private IPathProvider fPathProvider = new AVRPathProvider(AVRPath.AVRDUDE);

	/**
	 * A cache of one or more avrdude config files. The config files are stored
	 * as List&lt;String&gt; with one entry per line
	 */
	private Map<IPath, List<String>> fConfigFileCache = new HashMap<IPath, List<String>>();

	/**
	 * Get the singleton instance of the AVRDude class.
	 */
	public static AVRDude getDefault() {
		if (instance == null)
			instance = new AVRDude();
		return instance;
	}

	// Prevent Instantiation of the class
	private AVRDude() {
	}

	/**
	 * Returns the name of the AVRDude executable.
	 * <p>
	 * On Windows Systems the ".exe" extension is not included and needs to be
	 * added for access to avrdude other than executing the programm.
	 * </p>
	 * 
	 * @return String with "avrdude"
	 */
	public String getCommandName() {
		return fCommandName;
	}

	/**
	 * Returns the full path to the AVRDude executable.
	 * <p>
	 * Note: On Windows Systems the returned path does not include the ".exe"
	 * extension.
	 * </p>
	 * 
	 * @return <code>IPath</code> to the avrdude executable
	 */
	public IPath getToolPath() {
		IPath path = fPathProvider.getPath();
		return path.append(getCommandName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.core.IMCUProvider#getMCUInfo(java.lang.String)
	 */
	public String getMCUInfo(String mcuid) {
		Map<String, String> internalmap;
		try {
			internalmap = loadMCUList();
		} catch (AVRDudeException e) {
			// Something went wrong when avrdude was called. The exception has
			// already been logged, so just return null
			return null;
		}
		return internalmap.get(mcuid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.core.IMCUProvider#getMCUList()
	 */
	public Set<String> getMCUList() {
		Map<String, String> internalmap;
		try {
			internalmap = loadMCUList();
		} catch (AVRDudeException e) {
			// Something went wrong when avrdude was called. The exception has
			// already been logged, so just return an empty list.
			return new HashSet<String>();
		}
		Set<String> idset = internalmap.keySet();
		return new HashSet<String>(idset);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.core.IMCUProvider#hasMCU(java.lang.String)
	 */
	public boolean hasMCU(String mcuid) {
		Map<String, String> internalmap;
		try {
			internalmap = loadMCUList();
		} catch (AVRDudeException e) {
			// Something went wrong when avrdude was called. The exception has
			// already been logged, so just return false.
			return false;
		}
		return internalmap.containsKey(mcuid);
	}

	/**
	 * Returns a Set of all currently supported Programmer devices.
	 * 
	 * @return <code>Set&lt;String&gt</code> with the avrdude id values.
	 * @throws AVRDudeException
	 */
	public Set<String> getProgrammersList() throws AVRDudeException {
		Map<String, ConfigEntry> internalmap = loadProgrammersList();
		Set<String> idset = internalmap.keySet();
		return new HashSet<String>(idset);
	}

	/**
	 * Returns the {@link ConfigEntry} for the given Programmer device.
	 * 
	 * @param programmerid
	 *            <code>String</code> with the avrdude id of the programmer
	 * @return <code>ConfigEntry</code> containing all known information
	 *         extracted from the avrdude executable
	 * @throws AVRDudeException
	 */
	public ConfigEntry getProgrammerInfo(String programmerid)
			throws AVRDudeException {
		Map<String, ConfigEntry> internalmap = loadProgrammersList();
		return internalmap.get(programmerid);
	}

	/**
	 * Returns the section of the avrdude.conf configuration file describing the
	 * the given ConfigEntry.
	 * <p>
	 * The extract is returned as a multiline <code>String</code> that can be
	 * used directly in an Text Control in the GUI.
	 * </p>
	 * <p>
	 * Note: The first call to this method may take some time, as the complete
	 * avrdude.conf file is read and and split into lines (currently around 450
	 * Kbyte). This method is Synchronized, so it is safe to call it multiple
	 * times.
	 * 
	 * @param entry
	 *            The <code>ConfigEntry</code> for which to get the
	 *            avrdude.conf entry.
	 * @return A <code>String</code> with the relevant lines, separated with
	 *         '\n'.
	 * @throws IOException
	 *             Any Exception reading the configuration file.
	 */
	public synchronized String getConfigDetailInfo(ConfigEntry entry)
			throws IOException {

		List<String> configcontent = null;
		// Test if we have already loaded the config file
		IPath configpath = entry.configfile;
		if (fConfigFileCache.containsKey(configpath)) {
			configcontent = fConfigFileCache.get(configpath);
		} else {
			// Load the config file
			configcontent = loadConfigFile(configpath);
			fConfigFileCache.put(configpath, configcontent);
		}

		// make a string, starting from the given line until the first line that
		// does not start with a whitespace
		StringBuffer result = new StringBuffer();

		// TODO This still-in-section matcher is probably to simple, maybe try
		// to find the ";" marking the end of a section (which requires parsing
		// the subsections)
		Pattern section = Pattern.compile("\\s+.*");
		Matcher m;

		int index = entry.linenumber;
		while (true) {
			String line = configcontent.get(index++);
			m = section.matcher(line);
			if (!m.matches()) {
				break;
			}
			result.append(line.trim()).append('\n');
		}
		return result.toString();
	}

	/**
	 * Return the MCU id value of the device currently attached to the given
	 * Programmer.
	 * 
	 * @param config
	 *            <code>ProgrammerConfig</code> with the Programmer to query.
	 * @return <code>String</code> with the id of the attached MCU, or
	 *         <code>null</code> if the id could not be read (e.g. no
	 *         programmer attached).
	 * @throws AVRDudeException
	 */
	public String getAttachedMCU(ProgrammerConfig config)
			throws AVRDudeException {

		List<String> configoptions = config.getArguments();
		configoptions.add("-pm16");

		List<String> stdout = runCommand(configoptions);
		if (stdout == null) {
			return null;
		}

		// Parse the output and look for a line "avrdude: Device signature =
		// 0x123456"
		Pattern mcuPat = Pattern.compile(".+signature.+(0x[\\da-fA-F]{6})");
		Matcher m;

		for (String line : stdout) {
			m = mcuPat.matcher(line);
			if (!m.matches()) {
				continue;
			}
			// pattern matched. Get the Signature and convert it to a mcu id
			String mcuid = Signatures.getDefault().getMCU(m.group(1));
			return mcuid;
		}
		// Signature not found
		return null;
	}

	/**
	 * Internal method to read the config file with the given path and split it
	 * into lines.
	 * 
	 * @param path
	 *            <code>IPath</code> to a configuration file.
	 * @return A <code>List&lt;String&gt;</code> with all lines of the given
	 *         configuration file
	 * @throws IOException
	 *             Any Exception reading the configuration file.
	 */
	private List<String> loadConfigFile(IPath path) throws IOException {

		// The default avrdude.conf file has some 12.000+ lines, however custom
		// avrdude.conf files might be much smaller, so we start with 100 lines
		// and let the ArrayList grow as required
		List<String> content = new ArrayList<String>(100);

		BufferedReader br = null;

		try {
			File configfile = path.toFile();
			br = new BufferedReader(new FileReader(configfile));

			String line;
			while ((line = br.readLine()) != null) {
				content.add(line);
			}

		} finally {
			if (br != null)
				br.close();
		}
		return content;
	}

	/**
	 * @return Map&lt;mcu id, avrdude id&gt; of all supported MCUs
	 * @throws AVRDudeException
	 */
	private Map<String, String> loadMCUList() throws AVRDudeException {

		if (!getToolPath().equals(fCurrentPath)) {
			// toolpath has changed, reload the list
			fMCUList = null;
			fMCUIdMap = null;
			fCurrentPath = getToolPath();
		}

		if (fMCUIdMap != null) {
			// return stored map
			return fMCUIdMap;
		}

		fMCUList = new HashMap<String, ConfigEntry>();
		// Execute avrdude with the "-p?" to get a list of all supported mcus.
		readAVRDudeConfigOutput(fMCUList, "-p?");

		// The returned list has avrdude mcu id values, which are not the same
		// as the ones used in this Plugin. Instead the returned name is
		// converted into an Pluin mcu id value.
		fMCUIdMap = new HashMap<String, String>(fMCUList.size());
		Collection<ConfigEntry> allentries = fMCUList.values();
		for (ConfigEntry entry : allentries) {
			String mcuid = AVRMCUidConverter.name2id(entry.description);
			fMCUIdMap.put(mcuid, entry.avrdudeid);
		}

		return fMCUIdMap;
	}

	/**
	 * @return Map&lt;mcu id, avrdude id&gt; of all supported Programmer
	 *         devices.
	 * @throws AVRDudeException
	 */
	private Map<String, ConfigEntry> loadProgrammersList()
			throws AVRDudeException {

		if (!getToolPath().equals(fCurrentPath)) {
			// toolpath has changed, reload the list
			fProgrammerList = null;
			fCurrentPath = getToolPath();
		}

		if (fProgrammerList != null) {
			// return stored list
			return fProgrammerList;
		}
		fProgrammerList = new HashMap<String, ConfigEntry>();
		// Execute avrdude with the "-c?" to get a list of all supported
		// programmers.
		readAVRDudeConfigOutput(fProgrammerList, "-c?");
		return fProgrammerList;
	}

	/**
	 * Internal method to execute avrdude and parse the output as ConfigEntries.
	 * 
	 * @see #loadMCUList()
	 * @see #loadProgrammersList()
	 * 
	 * @param resultmap
	 * @param arguments
	 * @throws AVRDudeException
	 */
	private void readAVRDudeConfigOutput(Map<String, ConfigEntry> resultmap,
			String... arguments) throws AVRDudeException {

		List<String> stdout = runCommand(arguments);
		if (stdout == null) {
			return;
		}

		// Avrdude output for configuration items looks like:
		// " id = description [pathtoavrdude.conf:line]"
		// The following pattern splits this into the four groups:
		// id / description / path / line
		Pattern mcuPat = Pattern
				.compile("\\s*(\\w+)\\s*=\\s*(.+?)\\s*\\[(.+):(\\d+)\\]\\.*");
		Matcher m;

		for (String line : stdout) {
			m = mcuPat.matcher(line);
			if (!m.matches()) {
				continue;
			}
			ConfigEntry entry = new ConfigEntry();
			entry.avrdudeid = m.group(1);
			entry.description = m.group(2);
			entry.configfile = new Path(m.group(3));
			entry.linenumber = Integer.valueOf(m.group(4));

			resultmap.put(entry.avrdudeid, entry);
		}
	}

	/**
	 * Get the command name and the current version of avrdude.
	 * <p>
	 * The name is defined in {@link #fCommandName}. The version is gathered by
	 * executing with the "-v" option and parsing the output.
	 * </p>
	 * 
	 * @return <code>String</code> with the command name and version
	 * @throws AVRDudeException
	 */
	public String getNameAndVersion() throws AVRDudeException {

		// Execute avrdude with the "-v" option and parse the
		// output
		List<String> stdout = runCommand("-v");
		if (stdout == null) {
			// Return default name on failures
			return getCommandName() + " n/a";
		}

		// look for a line matching "*Version TheVersionNumber *"
		Pattern mcuPat = Pattern.compile(".*Version\\s+([\\d\\.]+).*");
		Matcher m;
		for (String line : stdout) {
			m = mcuPat.matcher(line);
			if (!m.matches()) {
				continue;
			}
			return getCommandName() + " " + m.group(1);
		}

		// could not read the version from the output, probably the regex has a
		// mistake. Return a reasonable default.
		return getCommandName() + " ?.?";
	}

	/**
	 * Runs avrdude with the given arguments.
	 * <p>
	 * The Output of stdout and stderr are merged and returned in a
	 * <code>List&lt;String&gt;</code>.
	 * </p>
	 * <p>
	 * If the command fails to execute an entry is written to the log and an
	 * {@link AVRDudeException} with the reason is thrown.
	 * </p>
	 * 
	 * @param arguments
	 *            Zero or more arguments for avrdude
	 * @return A list of all output lines, or <code>null</code> if the command
	 *         could not be launched.
	 * @throws AVRDudeException
	 */
	private List<String> runCommand(String... arguments)
			throws AVRDudeException {

		List<String> arglist = new ArrayList<String>(1);
		for (String arg : arguments) {
			arglist.add(arg);
		}

		return runCommand(arglist);
	}

	/**
	 * Runs avrdude with the given arguments.
	 * <p>
	 * The Output of stdout and stderr are merged and returned in a
	 * <code>List&lt;String&gt;</code>.
	 * </p>
	 * <p>
	 * If the command fails to execute an entry is written to the log and an
	 * {@link AVRDudeException} with the reason is thrown.
	 * </p>
	 * 
	 * @param arguments
	 *            <code>List&lt;String&gt;</code> with the arguments
	 * @return A list of all output lines, or <code>null</code> if the command
	 *         could not be launched.
	 * @throws AVRDudeException
	 *             when avrdude cannot be started or when avrdude returned an
	 *             error errors.
	 */
	private List<String> runCommand(List<String> arglist)
			throws AVRDudeException {

		String command = getToolPath().toOSString();

		// Check if the user has a custom configuration file
		IPreferenceStore avrdudeprefs = AVRDudePreferences.getPreferenceStore();
		boolean usecustomconfig = avrdudeprefs
				.getBoolean(AVRDudePreferences.KEY_USECUSTOMCONFIG);
		if (usecustomconfig) {
			String newconfigfile = avrdudeprefs
					.getString(AVRDudePreferences.KEY_CONFIGFILE);
			arglist.add("-C" + newconfigfile);
		}

		// Set up the External Command
		ExternalCommandLauncher avrdude = new ExternalCommandLauncher(command,
				arglist);
		avrdude.redirectErrorStream(true);

		IProgressMonitor monitor = new NullProgressMonitor();
		ICommandOutputListener outputlistener = new OutputListener(monitor);
		avrdude.setCommandOutputListener(outputlistener);

		// Run avrdude
		try {
			fAbortReason = null;
			avrdude.launch(monitor);
		} catch (IOException e) {
			// Something didn't work while running the external command
			IStatus status = new Status(Status.ERROR, AVRPlugin.PLUGIN_ID,
					"Could not start " + command, e);
			AVRPlugin.getDefault().log(status);
			throw new AVRDudeException(e);
		}

		// Test if avrdude was aborted
		if (fAbortReason != null) {
			throw new AVRDudeException(fAbortReason, fAbortLine);
		}

		// Everything was fine: get the ooutput from avrdude and return it to
		// the caller
		List<String> stdout = avrdude.getStdOut();

		return stdout;
	}

	/**
	 * The Reason code why avrdude was aborted (or <code>null</code> if
	 * avrdude finished normally)
	 */
	protected Reason fAbortReason;

	/** The line from the avrdude output that caused the abort */
	protected String fAbortLine;

	/**
	 * Internal class to listen to the output of avrdude and cancel avrdude if
	 * the certain key Strings appears in the output.
	 * <p>
	 * They are:
	 * <ul>
	 * <li><code>timeout</code></li>
	 * <li><code>Can't open device</code></li>
	 * <li><code>can't open config file</code></li>
	 * <li><code>Can't find programmer id</code></li>
	 * <li><code>AVR Part ???? not found</code></li>
	 * </ul>
	 * </p>
	 * <p>
	 * Once any of these Strings is found in the output the associated Reason is
	 * set and avrdude is aborted via the ProgressMonitor.
	 * </p>
	 */
	private class OutputListener implements ICommandOutputListener {

		private IProgressMonitor fProgressMonitor;

		public OutputListener(IProgressMonitor monitor) {
			fProgressMonitor = monitor;
		}

		public void handleLine(String line, StreamSource source) {

			boolean abort = false;

			if (line.contains("timeout")) {
				abort = true;
				fAbortReason = Reason.TIMEOUT;
			} else if (line.contains("can't open device")) {
				abort = true;
				fAbortReason = Reason.PORT_BLOCKED;
			} else if (line.contains("can't open config file")) {
				abort = true;
				fAbortReason = Reason.CONFIG_NOT_FOUND;
			} else if (line.contains("Can't find programmer id")) {
				abort = true;
				fAbortReason = Reason.UNKNOWN_PROGRAMMER;
			} else if (line.matches("AVR Part.+not found")) {
				abort = true;
				fAbortReason = Reason.UNKNOWN_MCU;
			}

			if (abort) {
				fProgressMonitor.setCanceled(true);
				fAbortLine = line;
			}
		}

	}

	/**
	 * Container class for AVRDude configuration entries.
	 * <p>
	 * This class is stores the four informations that avrdude supplies about a
	 * Programming device or a MCU part:
	 * </p>
	 * <ul>
	 * <li>{@link #avrdudeid} = AVRDude internal id</li>
	 * <li>{@link #description} = Human readable description</li>
	 * <li>{@link #configfile} = Path to the avrdude configuration file which
	 * declares this programmer or part</li>
	 * <li>{@link #linenumber} = Line number within the configuration file
	 * where the definition starts</li>
	 * </ul>
	 * 
	 */
	public static class ConfigEntry {
		/** AVRDude internal id for this entry */
		public String avrdudeid;

		/** (Human readable) description of this entry */
		public String description;

		/** Path to the configuration file which contains the definition */
		public IPath configfile;

		/** line number of the start of the definition */
		public int linenumber;
	}
}
