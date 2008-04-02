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
package de.innot.avreclipse.core.preferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import de.innot.avreclipse.AVRPlugin;

/**
 * Container for the Project Properties of an AVR Project.
 * <p>
 * This class maintains the global project settings (which currently is only the
 * "per config" flag) and a list of {@link AVRProjectProperties} objects which
 * contain all other project properties which can be either global for the
 * project or for each build configuration.
 * </p>
 * <p>
 * For read access instantiate this class for a project and call
 * {@link #getConfigurationProperties(IConfiguration)}. This method will return
 * either the properties for the given <code>IConfiguration</code> or the
 * project properties if the "per config" flag has not been set (by the user).
 * </p>
 * <p>
 * To modify the properties either the
 * {@link #getPropsForConfig(IConfiguration, boolean)} or the
 * {@link #getProjectProperties()} methods can be used to get the properties for
 * a
 * <code>IConfiguration</code< (regardless of the "per config" flag), respectively the project
 * properties.</p><p>
 * All modifications, including the current state of the "per config" flag are persisted with a call to {@link #save()}.
 * </p>
 * 
 * 
 * @author Thomas Holland
 * @since 2.2
 *
 */
public class ProjectPropertyManager {

	private static final String CLASSNAME = "avrtarget";
	private static final String QUALIFIER = AVRPlugin.PLUGIN_ID + "/" + CLASSNAME;

	public static final String KEY_PER_CONFIG = "perConfig";
	private static final boolean DEFAULT_PER_CONFIG = false;

	private static Map<IProject, ProjectPropertyManager> fsProjectMap = new HashMap<IProject, ProjectPropertyManager>();

	public static ProjectPropertyManager getPropertyManager(IProject project) {
		if (fsProjectMap.containsKey(project)) {
			return fsProjectMap.get(project);
		}
		ProjectPropertyManager newmanager = new ProjectPropertyManager(project);
		fsProjectMap.put(project, newmanager);

		// TODO add some kind of listener to remove projects if necessary

		return newmanager;
	}

	/**
	 * "per config" flag. If <code>true</code>, the project uses separate
	 * properties for each build configuration.
	 * 
	 */
	private boolean fPerConfig;

	/** The project this description is for */
	private final IProject fProject;

	/** Map of properties elements for each build configuration */
	private final Map<String, AVRProjectProperties> fConfigProperties = new HashMap<String, AVRProjectProperties>();

	/** Global project properties (used when "per config" flag is false = default */
	private AVRProjectProperties fProjectProps;

	/**
	 * Instantiate Properties Description Object for the given Project.
	 * 
	 * @param project
	 */
	private ProjectPropertyManager(IProject project) {
		Assert.isNotNull(project);

		fProject = project;

		reload();
	}

	/**
	 * Set the "per config" flag.
	 * <p>
	 * If set to <code>true</code> the project will use separate properties
	 * for each build configuration of the project. If set to <code>false</code>
	 * (the default value), only the global project properties will be used for
	 * all build configurations.
	 * </p>
	 * 
	 * @param flag
	 */
	public void setPerConfig(boolean flag) {
		fPerConfig = flag;
	}

	/**
	 * @return The current state of the "per config" flag.
	 */
	public boolean isPerConfig() {
		return fPerConfig;
	}

	/**
	 * Get the Properties for the active build configuration or the global
	 * project properties if the "per config" flag is false.
	 * <p>
	 * if no properties for the active configuration exists the global project
	 * properties are used as a fallback.
	 * </p>
	 * 
	 * @return <code>AVRProjectProperies</code> with the requested properties.
	 */
	public AVRProjectProperties getActiveProperties() {
		if (fPerConfig) {
			// Get the active IConfiguration from our IProject
			ICProjectDescription projdesc = CoreModel.getDefault().getProjectDescription(fProject,
			        false);
			ICConfigurationDescription cfgdesc = projdesc.getActiveConfiguration();
			IConfiguration buildcfg = ManagedBuildManager.getConfigurationForDescription(cfgdesc);
			return getConfigurationProperties(buildcfg);
		} else {
			// Project settings only
			return getProjectProperties();
		}
	}

	/**
	 * Get the properties for the given <code>IConfiguration</code> or the
	 * global project properties if the "per config" flag is false.
	 * <p>
	 * if no properties for the given configuration exists the global project
	 * properties are used as a fallback.
	 * </p>
	 * 
	 * @param buildcfg
	 *            <code>IConfiguration</code> for which the properties are
	 *            requested.
	 * @return <code>AVRProjectProperies</code> with the requested properties.
	 */
	public AVRProjectProperties getConfigurationProperties(IConfiguration buildcfg) {
		return getConfigurationProperties(buildcfg, false, false);
	}

	/**
	 * Get the properties for the given <code>IConfiguration</code>.
	 * <p>
	 * The force flag determines whether the "per config" flag is taken into
	 * account.
	 * <ul>
	 * <li>force = <code>true</code>: Return the properties for the build
	 * configuration, regardless of the "per config" flag.</li>
	 * <li>force = <code>false</code>: Return the global project properties
	 * if the "per config" flag is also <code>false</code>.</li>
	 * </ul>
	 * <p>
	 * If no properties for the given build configuration exist, the project
	 * settings are copied.
	 * </p>
	 * 
	 * @param buildcfg
	 *            <code>IConfiguration</code> for which the properties are
	 *            requested.
	 * @param force
	 *            Set to <code>true</code> to disregard the "per config" flag.
	 * @param nocache
	 *            Return fresh properties, not from the cache.
	 * @return <code>AVRProjectProperies</code> with the requested properties.
	 */
	public AVRProjectProperties getConfigurationProperties(IConfiguration buildcfg, boolean force,
	        boolean nocache) {

		// Test if the configuration belongs to this project
		IProject cfgproj = (IProject) buildcfg.getOwner();

		if (!fProject.equals(cfgproj)) {
			throw new IllegalArgumentException("Configuration " + buildcfg.getId()
			        + " does not belong to project " + fProject.getName());
		}

		if (fPerConfig || force) {
			if (!nocache && fConfigProperties.containsKey(buildcfg.getId())) {
				return fConfigProperties.get(buildcfg.getId());
			}
			BuildConfigurationScope scope = new BuildConfigurationScope(buildcfg);

			// Test if the node for the configuration already exists. If no, we
			// create a new node by copying all values from the project settings
			boolean copyproject = !scope.configExists(QUALIFIER, buildcfg);

			IEclipsePreferences cfgprefs = getConfigurationPreferences(buildcfg);
			AVRProjectProperties newconfigprops;
			if (copyproject) {
				newconfigprops = new AVRProjectProperties(cfgprefs, getProjectProperties());
			} else {
				newconfigprops = new AVRProjectProperties(cfgprefs);
			}

			fConfigProperties.put(buildcfg.getId(), newconfigprops);
			return newconfigprops;
		} else {
			// global project settings
			return getProjectProperties();
		}
	}

	/**
	 * Get the global project properties.
	 * 
	 * @return <code>AVRProjectProperies</code> with the requested properties.
	 */
	public AVRProjectProperties getProjectProperties() {
		if (fProjectProps == null) {
			fProjectProps = new AVRProjectProperties(getProjectPreferences(fProject));
		}
		return fProjectProps;
	}

	/**
	 * Get the default properties.
	 * <p>
	 * Unlike the other get???Properties() methods, the properties returned by
	 * this call are not backed with a storage. Calls to save() will have no
	 * effect. It should only be used to extract the default values.
	 * </p>
	 * 
	 * @return
	 */
	public static AVRProjectProperties getDefaultProperties() {
		return new AVRProjectProperties(getDefaultPreferences());
	}

	/**
	 * Reloads all Properties from the property storage.
	 * <p>
	 * Called after a Cancel event to delete any pending modifications.
	 * </p>
	 */
	public void reload() {
		IEclipsePreferences projectprefs = getProjectPreferences(fProject);
		fPerConfig = projectprefs.getBoolean(KEY_PER_CONFIG, DEFAULT_PER_CONFIG);

		if (fProjectProps != null) {
			fProjectProps.loadData();
		}

		for (AVRProjectProperties props : fConfigProperties.values()) {
			props.loadData();
		}
	}

	/**
	 * Save all modified properties.
	 * <p>
	 * This will save the current "per config" flag, the global project
	 * properties (if they have been requested) and the properties for all build
	 * configurations that have been requested.
	 * </p>
	 * <p>
	 * Also this method will synchronize our properties with the currently
	 * existing build configurations. If build configurations have been removed,
	 * this method will remove any associated properties.
	 * </p>
	 * 
	 * @throws BackingStoreException
	 * 
	 * @throws BackingStoreException
	 *             on any errors writing to the backing store.
	 */
	public void save() throws BackingStoreException {

		savePerConfigFlag();

		// Save the global project properties
		if (fProjectProps != null) {
			fProjectProps.save();
		}

		// save all known configuration properties
		for (AVRProjectProperties prop : fConfigProperties.values()) {
			prop.save();
		}
	}

	/**
	 * Save the current value of the "per config" flag.
	 * 
	 * @throws BackingStoreException
	 */
	public void savePerConfigFlag() throws BackingStoreException {
		// Save the "per config" flag
		IEclipsePreferences projectprefs = getProjectPreferences(fProject);
		projectprefs.putBoolean(KEY_PER_CONFIG, fPerConfig);
		projectprefs.flush();
	}

	/**
	 * Remove all configuration properties for which the referenced build
	 * configuration does not exist anymore.
	 */
	public void sync(List<String> allcfgids) {
		// TODO This method does not work yet.
		// Calling it will cause some exceptions later on for reasons unknown.
		// For now do nothing

		if (false)
			return;

		// get list of all our configuration properties
		IEclipsePreferences projprops = getProjectPreferences(fProject);
		try {
			String[] allcfgprops = projprops.childrenNames();
			for (String cfgname : allcfgprops) {
				// nodes not starting with "de.innot.avreclipse" cannot be
				// configuration property nodes
				if (!cfgname.startsWith("de.innot.avreclipse")) {
					break;
				}
				if (!allcfgids.contains(cfgname)) {
					// The configuration does not exist anymore
					// remove the node from the preferences
					Preferences removenode = projprops.node(cfgname);
					removenode.removeNode();
					projprops.flush();
				}
			}
		} catch (BackingStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static IEclipsePreferences getDefaultPreferences() {
		IScopeContext scope = new DefaultScope();
		return scope.getNode(QUALIFIER);
	}

	private static IEclipsePreferences getProjectPreferences(IProject project) {
		IScopeContext scope = new ProjectScope(project);
		return scope.getNode(QUALIFIER);
	}

	private static IEclipsePreferences getConfigurationPreferences(IConfiguration buildcfg) {
		IScopeContext scope = new BuildConfigurationScope(buildcfg);
		return scope.getNode(QUALIFIER);
	}
}
