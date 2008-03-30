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
package de.innot.avreclipse.mbs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.cdtvariables.CdtVariableException;
import org.eclipse.cdt.core.cdtvariables.ICdtVariable;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.macros.BuildMacroException;
import org.eclipse.cdt.managedbuilder.macros.IBuildMacro;
import org.eclipse.cdt.managedbuilder.macros.IBuildMacroStatus;

/**
 * Implementation of the {@link IBuildMacro} interface.
 * <p>
 * Each instance of this class represents a single build macro within the CDT
 * managed build system.
 * </p>
 * <p>
 * This class is mostly a container for the {@link BuildVariableValues} Enum,
 * which handles the actual macro value.
 * </p>
 * <p>
 * This class supports only simple String macros. List type macros are currently
 * not supported.
 * </p>
 * 
 * @author Thomas Holland
 * @since 2.2
 */
public class BuildMacro implements IBuildMacro {

	/** The value handler of this macro */
	private BuildVariableValues fValueHandler;

	/** The configuration for this macro */
	private IConfiguration fConfiguration;

	/**
	 * Constructs a new build macro for the given build configuration.
	 * <p>
	 * The name of the macro must be one of those returned by the
	 * {@link #getMacroNames()} method, otherwise an unchecked Exception is
	 * thrown.
	 * </p>
	 * 
	 * @see BuildVariableValues
	 * 
	 * @param name
	 *            <code>String</code> with the macro name.
	 * @param buildcfg
	 *            <code>IConfiguration</code> scope for the macro.
	 * @throws <code>IllegalArgumentException</code> if the name is not valid.
	 */
	public BuildMacro(String name, IConfiguration buildcfg) {
		fValueHandler = BuildVariableValues.valueOf(name);
		fConfiguration = buildcfg;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.cdt.managedbuilder.macros.IBuildMacro#getStringListValue()
	 */
	public String[] getStringListValue() throws BuildMacroException {
		// List type macros are not supported by this class
		throw new BuildMacroException(new CdtVariableException(
		        IBuildMacroStatus.TYPE_MACRO_NOT_STRINGLIST, fValueHandler.name(), null, null));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.cdt.managedbuilder.macros.IBuildMacro#getStringValue()
	 */
	public String getStringValue() throws BuildMacroException {
		return fValueHandler.getValue(fConfiguration);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.cdt.core.cdtvariables.ICdtVariable#getName()
	 */
	public String getName() {
		return fValueHandler.name();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.cdt.managedbuilder.macros.IBuildMacro#getMacroValueType()
	 */
	public int getMacroValueType() {
		return ICdtVariable.VALUE_TEXT; // we only need simple text macros
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.cdt.core.cdtvariables.ICdtVariable#getValueType()
	 */
	public int getValueType() {
		return ICdtVariable.VALUE_TEXT; // we only need simple text macros
	}

	/**
	 * @return a <code>List&lt;String&gt;</code> of all supported macro names.
	 */
	public static List<String> getMacroNames() {
		List<String> allmacronames = new ArrayList<String>();
		BuildVariableValues[] allnames = BuildVariableValues.values();
		for (BuildVariableValues mvar : allnames) {
			if (mvar.isMacro()) {
				allmacronames.add(mvar.name());
			}
		}
		return allmacronames;
	}

}
