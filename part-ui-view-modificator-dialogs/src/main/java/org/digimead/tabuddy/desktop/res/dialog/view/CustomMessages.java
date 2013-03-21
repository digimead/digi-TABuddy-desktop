/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
 * BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
 * THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using TABuddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TABuddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TABuddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.res.dialog.view;

import java.lang.reflect.Field;

import org.eclipse.osgi.util.NLS;

/**
 * This file is autogenerated by Google WindowBuilder Pro
 *
 * @author ezh
 */
public class CustomMessages extends NLS {
	private static final String BUNDLE_NAME = "org.digimead.tabuddy.desktop.res.dialog.view.messages"; //$NON-NLS-1$

	public static String viewEditorDescription_text;
	public static String viewEditorDialog_text;
	public static String viewEditorTitle_text;
	public static String viewFilterEditorDescription_text;
	public static String viewFilterEditorDialog_text;
	public static String viewFilterEditorTitle_text;
	public static String viewFilterListDescription_text;
	public static String viewFilterListDialog_text;
	public static String viewFilterListTitle_text;
	public static String viewListDescription_text;
	public static String viewListDialog_text;
	public static String viewListTitle_text;
	public static String viewSortingEditorDescription_text;
	public static String viewSortingEditorDialog_text;
	public static String viewSortingEditorTitle_text;
	public static String viewSortingListDescription_text;
	public static String viewSortingListDialog_text;
	public static String viewSortingListTitle_text;

	// //////////////////////////////////////////////////////////////////////////
	//
	// Class initialization
	//
	// //////////////////////////////////////////////////////////////////////////
	static {
		// load message values from bundle file
		NLS.initializeMessages(BUNDLE_NAME, CustomMessages.class);
		final Field[] fieldArray = CustomMessages.class.getDeclaredFields();
		final int len = fieldArray.length;
		for (int i = 0; i < len; i++) {
			final Field field = (Field) fieldArray[i];
			if (field.getType() == java.lang.String.class) {
				if (!field.isAccessible())
					field.setAccessible(true);
				try {
					final String rawValue = (String) field.get(null);
					field.set(
							null,
							new String(rawValue.getBytes("ISO-8859-1"), "UTF-8")
									.replaceAll("\\\\n", "\n").replaceAll(
											"\\\\t", "\t"));
				} catch (Exception e) {
					// skip field modification
				}
			}
		}
	}

	// //////////////////////////////////////////////////////////////////////////
	//
	// Constructor
	//
	// //////////////////////////////////////////////////////////////////////////
	private CustomMessages() {
		// do not instantiate
	}
}
