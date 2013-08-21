/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.viewmod.dialog.filterlist;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import swing2swt.layout.FlowLayout;

/**
 * This file is autogenerated by Google WindowBuilder Pro
 *
 * @author ezh
 */
public class FilterListSkel extends TitleAreaDialog {
	private Composite compositeFooter;
	private Table table;
	private TableViewer tableViewer;
	private TableViewerColumn tableViewerColumnDescription;
	private TableViewerColumn tableViewerColumnName;
	private Text textFilter;

	/**
	 * Create the dialog.
	 *
	 * @param parentShell
	 */
	public FilterListSkel(Shell parentShell) {
		super(parentShell);
		setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE);
	}

	/**
	 * Create contents of the dialog.
	 *
	 * @param parent
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		setMessage(org.digimead.tabuddy.desktop.viewmod.Messages$.MODULE$.viewFilterListDescription_text()); // $hide$
		setTitle(org.digimead.tabuddy.desktop.viewmod.Messages$.MODULE$.viewFilterListTitle_text()); // $hide$
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		textFilter = new Text(container, SWT.BORDER);
		textFilter.setToolTipText(org.digimead.tabuddy.desktop.Messages$.MODULE$.lookupFilter_text()); // $hide$
		textFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		tableViewer = new TableViewer(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
		table = tableViewer.getTable();
		table.setLinesVisible(true);
		table.setHeaderVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		tableViewerColumnName = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tableColumnName = tableViewerColumnName.getColumn();
		tableColumnName.setWidth(100);
		tableColumnName.setText(org.digimead.tabuddy.desktop.Messages$.MODULE$.name_text()); // $hide$

		tableViewerColumnDescription = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tableColumnDescription = tableViewerColumnDescription.getColumn();
		tableColumnDescription.setWidth(100);
		tableColumnDescription.setText(org.digimead.tabuddy.desktop.Messages$.MODULE$.description_text()); // $hide$

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
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	protected TableViewerColumn getTableViewerColumnName() {
		return tableViewerColumnName;
	}

	protected TableViewerColumn getTableViewerColumnDescription() {
		return tableViewerColumnDescription;
	}

	protected TableViewer getTableViewer() {
		return tableViewer;
	}

	protected Composite getCompositeFooter() {
		return compositeFooter;
	}

	protected Text getTextFilter() {
		return textFilter;
	}
}
