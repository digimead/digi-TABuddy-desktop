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

package org.digimead.tabuddy.desktop.res.dialog.view;

import org.digimead.tabuddy.desktop.res.Messages;
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

/**
 * This file is autogenerated by Google WindowBuilder Pro
 *
 * @author ezh
 */
public class ViewEditor extends TitleAreaDialog {
	private Button btnCheckAvailability;
	private Composite compositeBody1;
	private Composite compositeBody2;
	private Composite compositeHeader;
	private Table tableFields;
	private Table tableFilters;
	private Table tableProperties;
	private Table tableSortings;
	private TableViewer tableViewerFields;
	private TableViewer tableViewerFilters;
	private TableViewer tableViewerProperties;
	private TableViewer tableViewerSortings;
	private TableViewerColumn tableViewerColumnField;
	private TableViewerColumn tableViewerColumnFilter;
	private TableViewerColumn tableViewerColumnN;
	private TableViewerColumn tableViewerColumnPropertyFrom;
	private TableViewerColumn tableViewerColumnSorting;
	private Text textDescription;
	private Text textFieldFilter;
	private Text textFilterFilter;
	private Text textName;
	private Text textPropertyFilter;
	private Text textSortingFilter;

	/**
	 * Create the dialog.
	 *
	 * @param parentShell
	 */
	public ViewEditor(Shell parentShell) {
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
		setMessage(CustomMessages.viewEditorDescription_text);
		setTitle(CustomMessages.viewEditorTitle_text);
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(6, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		compositeHeader = new Composite(container, SWT.NONE);
		compositeHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 6, 1));
		compositeHeader.setLayout(new GridLayout(2, false));

		Label lblViewName = new Label(compositeHeader, SWT.NONE);
		lblViewName.setAlignment(SWT.RIGHT);
		lblViewName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false,
				false, 1, 1));
		lblViewName.setBounds(0, 0, 65, 15);
		lblViewName.setText(Messages.name_text);

		textName = new Text(compositeHeader, SWT.BORDER);
		textName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false,
				1, 1));
		textName.setText("");

		Label lblViewDescription = new Label(compositeHeader, SWT.NONE);
		lblViewDescription.setAlignment(SWT.RIGHT);
		lblViewDescription.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER,
				false, false, 1, 1));
		lblViewDescription.setText(Messages.description_text);

		textDescription = new Text(compositeHeader, SWT.BORDER);
		textDescription.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 1, 1));
		textDescription.setText("");
		new Label(compositeHeader, SWT.NONE);

		btnCheckAvailability = new Button(compositeHeader, SWT.CHECK);
		btnCheckAvailability.setText(Messages.availability_text);

		Label lblProperties = new Label(container, SWT.NONE);
		lblProperties.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false,
				false, 1, 1));
		lblProperties.setAlignment(SWT.CENTER);
		lblProperties.setText(Messages.properties_text);
		new Label(container, SWT.NONE);

		Label lblFields = new Label(container, SWT.NONE);
		lblFields.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false,
				false, 1, 1));
		lblFields.setAlignment(SWT.CENTER);
		lblFields.setText(Messages.fields_text);
		new Label(container, SWT.NONE);

		Label lblSortings = new Label(container, SWT.NONE);
		lblSortings.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false,
				false, 1, 1));
		lblSortings.setAlignment(SWT.CENTER);
		lblSortings.setText(Messages.sortings_text);

		Label lblFilters = new Label(container, SWT.NONE);
		lblFilters.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false,
				false, 1, 1));
		lblFilters.setAlignment(SWT.CENTER);
		lblFilters.setText(Messages.filters_text);

		textPropertyFilter = new Text(container, SWT.BORDER);
		textPropertyFilter.setToolTipText(Messages.lookupFilter_text);
		textPropertyFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				true, false, 1, 1));

		new Label(container, SWT.NONE);

		textFieldFilter = new Text(container, SWT.BORDER);
		textFieldFilter.setToolTipText(Messages.lookupFilter_text);
		textFieldFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 1, 1));

		new Label(container, SWT.NONE);

		textSortingFilter = new Text(container, SWT.BORDER);
		textSortingFilter.setToolTipText(Messages.lookupFilter_text);
		textSortingFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				true, false, 1, 1));

		textFilterFilter = new Text(container, SWT.BORDER);
		textFilterFilter.setToolTipText(Messages.lookupFilter_text);
		textFilterFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 1, 1));

		tableViewerProperties = new TableViewer(container, SWT.BORDER
				| SWT.FULL_SELECTION);
		tableProperties = tableViewerProperties.getTable();
		tableProperties.setLinesVisible(true);
		tableProperties.setHeaderVisible(true);
		tableProperties.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false,
				true, 1, 1));

		tableViewerColumnPropertyFrom = new TableViewerColumn(
				tableViewerProperties, SWT.NONE);
		TableColumn tableColumnPropertyFrom = tableViewerColumnPropertyFrom
				.getColumn();
		tableColumnPropertyFrom.setWidth(100);
		tableColumnPropertyFrom.setText(Messages.property_text);

		compositeBody1 = new Composite(container, SWT.NONE);
		compositeBody1.setLayout(new GridLayout(1, false));

		tableViewerFields = new TableViewer(container, SWT.BORDER
				| SWT.FULL_SELECTION);
		tableFields = tableViewerFields.getTable();
		tableFields.setLinesVisible(true);
		tableFields.setHeaderVisible(true);
		tableFields.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true,
				1, 1));

		tableViewerColumnN = new TableViewerColumn(tableViewerFields, SWT.NONE);
		TableColumn tableColumnN = tableViewerColumnN.getColumn();
		tableColumnN.setWidth(100);
		tableColumnN.setText("N");

		tableViewerColumnField = new TableViewerColumn(tableViewerFields,
				SWT.NONE);
		TableColumn tableColumnField = tableViewerColumnField.getColumn();
		tableColumnField.setWidth(100);
		tableColumnField.setText(Messages.field_text);

		compositeBody2 = new Composite(container, SWT.NONE);
		compositeBody2.setLayout(new GridLayout(1, false));

		tableViewerSortings = new TableViewer(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
		tableSortings = tableViewerSortings.getTable();
		tableSortings.setLinesVisible(true);
		tableSortings.setHeaderVisible(true);
		tableSortings.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true,
				true, 1, 1));

		tableViewerColumnSorting = new TableViewerColumn(tableViewerSortings,
				SWT.NONE);
		TableColumn tableColumnSorting = tableViewerColumnSorting.getColumn();
		tableColumnSorting.setWidth(100);
		tableColumnSorting.setText(Messages.sortings_text);

		tableViewerFilters = new TableViewer(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
		tableFilters = tableViewerFilters.getTable();
		tableFilters.setLinesVisible(true);
		tableFilters.setHeaderVisible(true);
		tableFilters.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true,
				1, 1));

		tableViewerColumnFilter = new TableViewerColumn(tableViewerFilters,
				SWT.NONE);
		TableColumn tableColumnFilter = tableViewerColumnFilter.getColumn();
		tableColumnFilter.setWidth(100);
		tableColumnFilter.setText(Messages.filters_text);

		return area;
	}

	/**
	 * Create contents of the button bar.
	 *
	 * @param parent
	 */
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		Button button = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
				true);
		button.setEnabled(false);
		createButton(parent, IDialogConstants.CANCEL_ID,
				IDialogConstants.CANCEL_LABEL, false);
	}

	protected Text getTextPropertyFilter() {
		return textPropertyFilter;
	}

	protected Text getTextSortingFilter() {
		return textSortingFilter;
	}

	protected Text getTextFilterFilter() {
		return textFilterFilter;
	}

	protected TableViewer getTableViewerProperties() {
		return tableViewerProperties;
	}

	protected TableViewer getTableViewerSortings() {
		return tableViewerSortings;
	}

	protected TableViewer getTableViewerFilters() {
		return tableViewerFilters;
	}

	protected TableViewerColumn getTableViewerColumnSorting() {
		return tableViewerColumnSorting;
	}

	protected TableViewerColumn getTableViewerColumnFilter() {
		return tableViewerColumnFilter;
	}

	protected Text getTextName() {
		return textName;
	}

	public Text getTextDescription() {
		return textDescription;
	}

	protected Button getBtnCheckAvailability() {
		return btnCheckAvailability;
	}
	protected TableViewerColumn getTableViewerColumnPropertyFrom() {
		return tableViewerColumnPropertyFrom;
	}
	protected Text getTextFieldFilter() {
		return textFieldFilter;
	}
	protected Composite getCompositeBody1() {
		return compositeBody1;
	}
	protected Composite getCompositeBody2() {
		return compositeBody2;
	}
	protected TableViewer getTableViewerFields() {
		return tableViewerFields;
	}
	protected TableViewerColumn getTableViewerColumnField() {
		return tableViewerColumnField;
	}
	protected TableViewerColumn getTableViewerColumnN() {
		return tableViewerColumnN;
	}
}
