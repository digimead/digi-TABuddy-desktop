/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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
 * that is created or manipulated using TA Buddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TA Buddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TA Buddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.model.definition.dialog.enumed;

import java.util.ResourceBundle;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import swing2swt.layout.BorderLayout;
import swing2swt.layout.FlowLayout;

/**
 * This file is autogenerated by Google WindowBuilder Pro
 *
 * @author ezh
 */
public class EnumerationEditorSkel extends TitleAreaDialog {
	private static final ResourceBundle BUNDLE = getResourceBundle();
	private Text textEnumerationName;
	private Text textEnumerationId;
	private Composite compositeHeader;
	private Composite compositeFooter;
	private TableViewer tableViewer;
	private TableViewerColumn tableViewerColumnValue;
	private TableViewerColumn tableViewerColumnAlias;
	private TableViewerColumn tableViewerColumnDescription;
	private Button btnCheckAvailability;
	private ComboViewer comboViewer;

	/**
	 * Get ResourceBundle from Scala environment.
	 *
	 * @return ResourceBundle interface of NLS singleton.
	 */
	private static ResourceBundle getResourceBundle() {
		try {
			return (ResourceBundle) Class.forName("org.digimead.tabuddy.desktop.core.Messages").newInstance();
		} catch (ClassNotFoundException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.dialog.enumed.messages");
		} catch (IllegalAccessException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.dialog.enumed.messages");
		} catch (InstantiationException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.dialog.enumed.messages");
		}
	}

	/**
	 * Create the dialog.
	 *
	 * @param parentShell
	 */
	public EnumerationEditorSkel(Shell parentShell) {
		super(parentShell);
		setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.PRIMARY_MODAL);
	}

	/**
	 * Create contents of the dialog.
	 *
	 * @param parent
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		setMessage(BUNDLE.getString("enumerationEditorDescription_text"));
		setTitle(BUNDLE.getString("enumerationEditorTitle_text"));
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new BorderLayout(0, 0));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		compositeHeader = new Composite(container, SWT.NONE);
		compositeHeader.setLayoutData(BorderLayout.NORTH);
		compositeHeader.setLayout(new GridLayout(2, false));

		Label lblEnumerationId = new Label(compositeHeader, SWT.NONE);
		lblEnumerationId.setAlignment(SWT.RIGHT);
		lblEnumerationId.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblEnumerationId.setBounds(0, 0, 65, 15);
		lblEnumerationId.setText(BUNDLE.getString("identificator_text"));

		textEnumerationId = new Text(compositeHeader, SWT.BORDER);
		textEnumerationId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textEnumerationId.setText("");

		Label lblEnumerationName = new Label(compositeHeader, SWT.NONE);
		lblEnumerationName.setAlignment(SWT.RIGHT);
		lblEnumerationName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblEnumerationName.setText(BUNDLE.getString("name_text"));

		textEnumerationName = new Text(compositeHeader, SWT.BORDER);
		textEnumerationName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textEnumerationName.setText("");
		new Label(compositeHeader, SWT.NONE);

		btnCheckAvailability = new Button(compositeHeader, SWT.CHECK);
		btnCheckAvailability.setText(BUNDLE.getString("availability_text"));

		Label lblType = new Label(compositeHeader, SWT.NONE);
		lblType.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblType.setText(BUNDLE.getString("type_text"));

		comboViewer = new ComboViewer(compositeHeader, SWT.READ_ONLY);
		Combo combo = comboViewer.getCombo();
		combo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));

		tableViewer = new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
		Table table = tableViewer.getTable();
		table.setLinesVisible(true);
		table.setHeaderVisible(true);

		tableViewerColumnValue = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnValue = tableViewerColumnValue.getColumn();
		tblclmnValue.setWidth(100);
		tblclmnValue.setText(BUNDLE.getString("value_text"));

		tableViewerColumnAlias = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnAlias = tableViewerColumnAlias.getColumn();
		tblclmnAlias.setWidth(100);
		tblclmnAlias.setText(BUNDLE.getString("alias_text"));

		tableViewerColumnDescription = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnDescription = tableViewerColumnDescription.getColumn();
		tblclmnDescription.setWidth(100);
		tblclmnDescription.setText(BUNDLE.getString("description_text"));

		compositeFooter = new Composite(container, SWT.NONE);
		compositeFooter.setLayoutData(BorderLayout.SOUTH);
		compositeFooter.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		return area;
	}

	/**
	 * Create contents of the button bar.
	 *
	 * @param parent
	 */
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		Button button = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		button.setEnabled(false);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	protected Composite getCompositeHeader() {
		return compositeHeader;
	}

	protected Composite getCompositeFooter() {
		return compositeFooter;
	}

	protected TableViewer getTableViewer() {
		return tableViewer;
	}

	protected Text getTextEnumerationName() {
		return textEnumerationName;
	}

	protected Text getTextEnumerationId() {
		return textEnumerationId;
	}

	protected Button getBtnCheckAvailability() {
		return btnCheckAvailability;
	}

	protected TableViewerColumn getTableViewerColumnValue() {
		return tableViewerColumnValue;
	}

	protected TableViewerColumn getTableViewerColumnAlias() {
		return tableViewerColumnAlias;
	}

	protected TableViewerColumn getTableViewerColumnDescription() {
		return tableViewerColumnDescription;
	}

	protected ComboViewer getComboType() {
		return comboViewer;
	}
}
