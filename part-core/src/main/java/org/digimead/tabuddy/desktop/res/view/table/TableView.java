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

package org.digimead.tabuddy.desktop.res.view.table;

import org.digimead.tabuddy.desktop.res.Messages;
import org.eclipse.jface.action.CoolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.Label;

/**
 * This file is autogenerated by Google WindowBuilder Pro
 *
 * @author ezh
 */
public class TableView extends Composite {
	/**
	 * Cool bar manager.
	 */
	private Button btnResetActiveElement;
	private CoolBarManager coolBarManager;
	private SashForm sashForm;
	private StyledText textActiveElement;
	private StyledText textRootElement;

	/**
	 * Create the composite.
	 *
	 * @param parent
	 * @param style
	 */
	public TableView(Composite parent, int style) {
		super(parent, style);
		setLayout(new GridLayout(3, false));

		coolBarManager = new CoolBarManager(SWT.FLAT);
		CoolBar coolBar = coolBarManager.createControl(this);
		coolBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false,
				3, 1));

		sashForm = new SashForm(this, SWT.SMOOTH);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3,
				1));

		Label lblRoot = new Label(this, SWT.NONE);
		lblRoot.setText(Messages.rootElement_text);

		textRootElement = new StyledText(this, SWT.BORDER | SWT.READ_ONLY
				| SWT.WRAP | SWT.SINGLE);
		GridData gd_styledTextRootElement = new GridData(SWT.FILL, SWT.CENTER,
				false, false, 2, 1);
		gd_styledTextRootElement.widthHint = 10;
		textRootElement.setLayoutData(gd_styledTextRootElement);

		Label lblActiveElement = new Label(this, SWT.NONE);
		lblActiveElement.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER,
				false, false, 1, 1));
		lblActiveElement.setText(Messages.activeElement_text);

		textActiveElement = new StyledText(this, SWT.BORDER | SWT.READ_ONLY
				| SWT.WRAP | SWT.SINGLE);
		textActiveElement.setAlignment(SWT.CENTER);
		textActiveElement.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				true, false, 1, 1));

		btnResetActiveElement = new Button(this, SWT.NONE);
		btnResetActiveElement.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				false, false, 1, 1));
		btnResetActiveElement.setText(Messages.reset_text);

	}

	@Override
	protected void checkSubclass() {
		// Disable the check that prevents subclassing of SWT components
	}

	protected CoolBarManager getCoolBarManager() {
		return coolBarManager;
	}

	protected SashForm getSashForm() {
		return sashForm;
	}

	protected StyledText getTextActiveElement() {
		return textActiveElement;
	}

	protected Button getBtnResetActiveElement() {
		return btnResetActiveElement;
	}

	protected StyledText getTextRootElement() {
		return textRootElement;
	}
}
