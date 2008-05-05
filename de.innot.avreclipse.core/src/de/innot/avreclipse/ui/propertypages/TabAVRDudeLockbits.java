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
package de.innot.avreclipse.ui.propertypages;

import de.innot.avreclipse.core.avrdude.AVRDudeException;
import de.innot.avreclipse.core.avrdude.AbstractBytes;
import de.innot.avreclipse.core.properties.AVRDudeProperties;
import de.innot.avreclipse.core.toolinfo.AVRDude;
import de.innot.avreclipse.core.toolinfo.fuses.ByteValues;

/**
 * The AVRDude Lockbits Tab page.
 * <p>
 * On this tab, the following properties are edited:
 * <ul>
 * <li>Upload of the Lockbits</li>
 * </ul>
 * The lockbit values can either be entered directly, or a lockbits file can be selected which
 * provides the lockbit values.
 * </p>
 * 
 * @author Thomas Holland
 * @since 2.2
 * 
 */
public class TabAVRDudeLockbits extends AbstractTabAVRDudeBytes {

	/** Max number of Lockbit bytes */
	private final static int		MAX_LOCKBYTES	= 1;

	private final static String[]	LABELS			= new String[] { "Lockbits", "lockbits" };

	/** The file extensions for lockbits files. Used by the file selector. */
	public final static String[]	LOCKBITS_EXTS	= new String[] { "*.locks" };

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.ui.propertypages.AbstractTabAVRDudeBytes#getByteCount(java.lang.String)
	 */
	@Override
	protected int getByteCount(String mcuid) {
		// As far as I can see all AVR MCUs have one and only one Lockbits byte.
		return MAX_LOCKBYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.ui.propertypages.AbstractTabAVRDudeBytes#getMaxBytes()
	 */
	@Override
	protected int getMaxBytes() {
		return MAX_LOCKBYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.ui.propertypages.AbstractTabAVRDudeBytes#getByteEditorLabel(int)
	 */
	@Override
	protected String getByteEditorLabel(int index) {
		// don't use a label for the lockbits byte value editor
		return "";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.ui.propertypages.AbstractTabAVRDudeBytes#getByteProps(de.innot.avreclipse.core.properties.AVRDudeProperties)
	 */
	@Override
	protected AbstractBytes getByteProps(AVRDudeProperties avrdudeprops) {
		return avrdudeprops.getLockbitBytes();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.ui.propertypages.AbstractTabAVRDudeBytes#getByteValues(de.innot.avreclipse.core.properties.AVRDudeProperties)
	 */
	@Override
	protected ByteValues getByteValues(AVRDudeProperties avrdudeprops) throws AVRDudeException {
		return AVRDude.getDefault().getLockbits(avrdudeprops.getProgrammer());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.ui.propertypages.AbstractTabAVRDudeBytes#getFileExtensions()
	 */
	@Override
	protected String[] getFileExtensions() {
		return LOCKBITS_EXTS;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.innot.avreclipse.ui.propertypages.AbstractTabAVRDudeBytes#getLabelString(int)
	 */
	@Override
	protected String[] getLabels() {
		return LABELS;
	}

}