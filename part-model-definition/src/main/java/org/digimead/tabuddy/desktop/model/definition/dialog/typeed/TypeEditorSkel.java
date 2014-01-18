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

package org.digimead.tabuddy.desktop.model.definition.dialog.typeed;

import java.util.ResourceBundle;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
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
public class TypeEditorSkel extends TitleAreaDialog {
	private static final ResourceBundle BUNDLE = getResourceBundle();
	private Text textSchemaDescription;
	private Text textSchemaName;
	private Composite compositeHeader;
	private Composite compositeFooter;
	private TableViewer tableViewer;
	private TableViewerColumn tableViewerColumnAvailability;
	private TableViewerColumn tableViewerColumnType;
	private TableViewerColumn tableViewerColumnAlias;
	private TableViewerColumn tableViewerColumnView;
	private TableViewerColumn tableViewerColumnLabel;
	private Button btnCheckActive;

	/**
	 * Get ResourceBundle from Scala environment.
	 *
	 * @return ResourceBundle interface of NLS singleton.
	 */
	private static ResourceBundle getResourceBundle() {
		try {
			return (ResourceBundle) Class.forName("org.digimead.tabuddy.desktop.core.Messages").newInstance();
		} catch (ClassNotFoundException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.messages");
		} catch (IllegalAccessException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.messages");
		} catch (InstantiationException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.messages");
		}
	}

	/**
	 * Create the dialog.
	 *
	 * @param parentShell
	 */
	public TypeEditorSkel(Shell parentShell) {
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
		setMessage(BUNDLE.getString("typeEditorDescription_text"));
		setTitle(BUNDLE.getString("typeEditorTitle_text"));
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new BorderLayout(0, 0));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		compositeHeader = new Composite(container, SWT.NONE);
		compositeHeader.setLayoutData(BorderLayout.NORTH);
		compositeHeader.setLayout(new GridLayout(2, false));

		Label lblTemplateName = new Label(compositeHeader, SWT.NONE);
		lblTemplateName.setAlignment(SWT.RIGHT);
		lblTemplateName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblTemplateName.setBounds(0, 0, 65, 15);
		lblTemplateName.setText(BUNDLE.getString("name_text"));

		textSchemaName = new Text(compositeHeader, SWT.BORDER);
		textSchemaName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textSchemaName.setText("");

		Label lblDescription = new Label(compositeHeader, SWT.NONE);
		lblDescription.setAlignment(SWT.RIGHT);
		lblDescription.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblDescription.setText(BUNDLE.getString("description_text"));

		textSchemaDescription = new Text(compositeHeader, SWT.BORDER);
		textSchemaDescription.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textSchemaDescription.setText("");
		new Label(compositeHeader, SWT.NONE);

		btnCheckActive = new Button(compositeHeader, SWT.CHECK);
		btnCheckActive.setText(BUNDLE.getString("active_text"));

		tableViewer = new TableViewer(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
		Table table = tableViewer.getTable();
		table.setLinesVisible(true);
		table.setHeaderVisible(true);

		tableViewerColumnAvailability = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnAvailability = tableViewerColumnAvailability.getColumn();
		tblclmnAvailability.setWidth(100);
		tblclmnAvailability.setText(BUNDLE.getString("availability_text"));

		tableViewerColumnType = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnType = tableViewerColumnType.getColumn();
		tblclmnType.setWidth(100);
		tblclmnType.setText(BUNDLE.getString("type_text"));

		tableViewerColumnAlias = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnAlias = tableViewerColumnAlias.getColumn();
		tblclmnAlias.setWidth(100);
		tblclmnAlias.setText(BUNDLE.getString("alias_text"));

		tableViewerColumnView = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnView = tableViewerColumnView.getColumn();
		tblclmnView.setWidth(100);
		tblclmnView.setText(BUNDLE.getString("view_text"));

		tableViewerColumnLabel = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnDescription = tableViewerColumnLabel.getColumn();
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

	protected Text getTextSchemaDescription() {
		return textSchemaDescription;
	}

	protected Text getTextSchemaName() {
		return textSchemaName;
	}

	protected Button getBtnCheckActive() {
		return btnCheckActive;
	}

	protected TableViewerColumn getTableViewerColumnAvailability() {
		return tableViewerColumnAvailability;
	}

	protected TableViewerColumn getTableViewerColumnType() {
		return tableViewerColumnType;
	}

	protected TableViewerColumn getTableViewerColumnAlias() {
		return tableViewerColumnAlias;
	}

	protected TableViewerColumn getTableViewerColumnView() {
		return tableViewerColumnView;
	}

	protected TableViewerColumn getTableViewerColumnLabel() {
		return tableViewerColumnLabel;
	}
}
