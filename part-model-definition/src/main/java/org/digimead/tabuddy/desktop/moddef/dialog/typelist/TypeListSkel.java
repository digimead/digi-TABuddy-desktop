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

package org.digimead.tabuddy.desktop.moddef.dialog.typelist;

import org.digimead.tabuddy.desktop.ResourceManager;
import org.digimead.tabuddy.desktop.moddef.dialog.eltemlist.ElementTemplateListSkel;
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
public class TypeListSkel extends TitleAreaDialog {
	private TableViewer tableViewer;
	private TableViewerColumn tblclmnViewerName;
	private TableViewerColumn tblclmnViewerDescription;
	private Composite compositeFooter;
	private Composite compositeActivator;
	private Text textActiveSchema;
	private Button btnResetSchema;

	/**
	 * Create the dialog.
	 *
	 * @param parentShell
	 */
	public TypeListSkel(Shell parentShell) {
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
		setTitleImage(ResourceManager.getImage(ElementTemplateListSkel.class, "/icons/full/message_info.gif"));
		setTitle(org.digimead.tabuddy.desktop.Messages$.MODULE$.typeListTitle_text()); // $hide$
		setMessage(org.digimead.tabuddy.desktop.Messages$.MODULE$.typeListDescription_text()); // $hide$
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(2, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label lblEnumerations = new Label(container, SWT.NONE);
		lblEnumerations.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 2, 1));
		lblEnumerations.setText(org.digimead.tabuddy.desktop.Messages$.MODULE$.typeSchemas_text()); // $hide$

		Composite compositeHeader = new Composite(container, SWT.NONE);
		compositeHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		compositeHeader.setLayout(new BorderLayout(5, 0));

		Label lblActiveScheme = new Label(compositeHeader, SWT.NONE);
		lblActiveScheme.setLayoutData(BorderLayout.WEST);
		lblActiveScheme.setText(org.digimead.tabuddy.desktop.Messages$.MODULE$.activeSchema_text()); // $hide$

		textActiveSchema = new Text(compositeHeader, SWT.BORDER | SWT.READ_ONLY);
		textActiveSchema.setLayoutData(BorderLayout.CENTER);

		btnResetSchema = new Button(compositeHeader, SWT.NONE);
		btnResetSchema.setEnabled(false);
		btnResetSchema.setLayoutData(BorderLayout.EAST);
		btnResetSchema.setText(org.digimead.tabuddy.desktop.Messages$.MODULE$.reset_text()); // $hide$

		tableViewer = new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
		Table table_enumerations = tableViewer.getTable();
		table_enumerations.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		table_enumerations.setLinesVisible(true);
		table_enumerations.setHeaderVisible(true);

		tblclmnViewerName = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnName = tblclmnViewerName.getColumn();
		tblclmnName.setWidth(100);
		tblclmnName.setText(org.digimead.tabuddy.desktop.Messages$.MODULE$.name_text()); // $hide$

		tblclmnViewerDescription = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnDescription = tblclmnViewerDescription.getColumn();
		tblclmnDescription.setWidth(100);
		tblclmnDescription.setText(org.digimead.tabuddy.desktop.Messages$.MODULE$.description_text()); // $hide$

		compositeActivator = new Composite(container, SWT.NONE);
		compositeActivator.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		compositeFooter = new Composite(container, SWT.NONE);
		compositeFooter.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
		compositeFooter.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));

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

	protected TableViewer getTableViewer() {
		return tableViewer;
	}

	protected TableViewerColumn getTableViewerColumnName() {
		return tblclmnViewerName;
	}

	protected TableViewerColumn getTableViewerColumnDescription() {
		return tblclmnViewerDescription;
	}

	protected Composite getCompositeActivator() {
		return compositeActivator;
	}

	protected Composite getCompositeFooter() {
		return compositeFooter;
	}

	protected Text getTextActiveSchema() {
		return textActiveSchema;
	}

	protected Button getbtnResetSchema() {
		return btnResetSchema;
	}
}
