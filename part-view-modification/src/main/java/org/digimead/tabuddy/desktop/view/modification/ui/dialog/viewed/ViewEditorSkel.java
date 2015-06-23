/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.view.modification.ui.dialog.viewed;

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

/**
 * This file is autogenerated by Google WindowBuilder Pro
 *
 * @author ezh
 */
public class ViewEditorSkel extends TitleAreaDialog {
	private static final ResourceBundle BUNDLE = getResourceBundle();
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
	 * Get ResourceBundle from Scala environment.
	 *
	 * @return ResourceBundle interface of NLS singleton.
	 */
	private static ResourceBundle getResourceBundle() {
		try {
			return (ResourceBundle) Class.forName("org.digimead.tabuddy.desktop.view.modification.Messages").newInstance();
		} catch (ClassNotFoundException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.view.modification.ui.messages");
		} catch (IllegalAccessException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.view.modification.ui.messages");
		} catch (InstantiationException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.view.modification.ui.messages");
		}
	}

	/**
	 * Create the dialog.
	 *
	 * @param parentShell
	 */
	public ViewEditorSkel(Shell parentShell) {
		super(parentShell);
		setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.PRIMARY_MODAL);
	}

	public Text getTextPropertyFilter() {
		return textPropertyFilter;
	}

	public Text getTextSortingFilter() {
		return textSortingFilter;
	}

	public Text getTextFilterFilter() {
		return textFilterFilter;
	}

	public TableViewer getTableViewerProperties() {
		return tableViewerProperties;
	}

	public TableViewer getTableViewerSortings() {
		return tableViewerSortings;
	}

	public TableViewer getTableViewerFilters() {
		return tableViewerFilters;
	}

	public TableViewerColumn getTableViewerColumnSorting() {
		return tableViewerColumnSorting;
	}

	public TableViewerColumn getTableViewerColumnFilter() {
		return tableViewerColumnFilter;
	}

	public Text getTextName() {
		return textName;
	}

	public Text getTextDescription() {
		return textDescription;
	}

	public Button getBtnCheckAvailability() {
		return btnCheckAvailability;
	}

	public TableViewerColumn getTableViewerColumnPropertyFrom() {
		return tableViewerColumnPropertyFrom;
	}

	public Text getTextFieldFilter() {
		return textFieldFilter;
	}

	public Composite getCompositeBody1() {
		return compositeBody1;
	}

	public Composite getCompositeBody2() {
		return compositeBody2;
	}

	public TableViewer getTableViewerFields() {
		return tableViewerFields;
	}

	public TableViewerColumn getTableViewerColumnField() {
		return tableViewerColumnField;
	}

	public TableViewerColumn getTableViewerColumnN() {
		return tableViewerColumnN;
	}

	/**
	 * Create contents of the dialog.
	 *
	 * @param parent
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		setMessage(BUNDLE.getString("viewEditorDescription_text"));
		setTitle(BUNDLE.getString("viewEditorTitle_text"));
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(6, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		compositeHeader = new Composite(container, SWT.NONE);
		compositeHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 6, 1));
		compositeHeader.setLayout(new GridLayout(2, false));

		Label lblViewName = new Label(compositeHeader, SWT.NONE);
		lblViewName.setAlignment(SWT.RIGHT);
		lblViewName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblViewName.setBounds(0, 0, 65, 15);
		lblViewName.setText(BUNDLE.getString("name_text"));

		textName = new Text(compositeHeader, SWT.BORDER);
		textName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textName.setText("");

		Label lblViewDescription = new Label(compositeHeader, SWT.NONE);
		lblViewDescription.setAlignment(SWT.RIGHT);
		lblViewDescription.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblViewDescription.setText(BUNDLE.getString("description_text"));

		textDescription = new Text(compositeHeader, SWT.BORDER);
		textDescription.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textDescription.setText("");
		new Label(compositeHeader, SWT.NONE);

		btnCheckAvailability = new Button(compositeHeader, SWT.CHECK);
		btnCheckAvailability.setText(BUNDLE.getString("availability_text"));

		Label lblProperties = new Label(container, SWT.NONE);
		lblProperties.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblProperties.setAlignment(SWT.CENTER);
		lblProperties.setText(BUNDLE.getString("properties_text"));
		new Label(container, SWT.NONE);

		Label lblFields = new Label(container, SWT.NONE);
		lblFields.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblFields.setAlignment(SWT.CENTER);
		lblFields.setText(BUNDLE.getString("fields_text"));
		new Label(container, SWT.NONE);

		Label lblSortings = new Label(container, SWT.NONE);
		lblSortings.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblSortings.setAlignment(SWT.CENTER);
		lblSortings.setText(BUNDLE.getString("sortings_text"));

		Label lblFilters = new Label(container, SWT.NONE);
		lblFilters.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblFilters.setAlignment(SWT.CENTER);
		lblFilters.setText(BUNDLE.getString("filters_text"));

		textPropertyFilter = new Text(container, SWT.BORDER);
		textPropertyFilter.setToolTipText(BUNDLE.getString("lookupFilter_text"));
		textPropertyFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		new Label(container, SWT.NONE);

		textFieldFilter = new Text(container, SWT.BORDER);
		textFieldFilter.setToolTipText(BUNDLE.getString("lookupFilter_text"));
		textFieldFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		new Label(container, SWT.NONE);

		textSortingFilter = new Text(container, SWT.BORDER);
		textSortingFilter.setToolTipText(BUNDLE.getString("lookupFilter_text"));
		textSortingFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		textFilterFilter = new Text(container, SWT.BORDER);
		textFilterFilter.setToolTipText(BUNDLE.getString("lookupFilter_text"));
		textFilterFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		tableViewerProperties = new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
		tableProperties = tableViewerProperties.getTable();
		tableProperties.setLinesVisible(true);
		tableProperties.setHeaderVisible(true);
		tableProperties.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1));

		tableViewerColumnPropertyFrom = new TableViewerColumn(tableViewerProperties, SWT.NONE);
		TableColumn tableColumnPropertyFrom = tableViewerColumnPropertyFrom.getColumn();
		tableColumnPropertyFrom.setWidth(100);
		tableColumnPropertyFrom.setText(BUNDLE.getString("property_text"));

		compositeBody1 = new Composite(container, SWT.NONE);
		compositeBody1.setLayout(new GridLayout(1, false));

		tableViewerFields = new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
		tableFields = tableViewerFields.getTable();
		tableFields.setLinesVisible(true);
		tableFields.setHeaderVisible(true);
		tableFields.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		tableViewerColumnN = new TableViewerColumn(tableViewerFields, SWT.NONE);
		TableColumn tableColumnN = tableViewerColumnN.getColumn();
		tableColumnN.setWidth(100);
		tableColumnN.setText("N");

		tableViewerColumnField = new TableViewerColumn(tableViewerFields, SWT.NONE);
		TableColumn tableColumnField = tableViewerColumnField.getColumn();
		tableColumnField.setWidth(100);
		tableColumnField.setText(BUNDLE.getString("field_text"));

		compositeBody2 = new Composite(container, SWT.NONE);
		compositeBody2.setLayout(new GridLayout(1, false));

		tableViewerSortings = new TableViewer(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
		tableSortings = tableViewerSortings.getTable();
		tableSortings.setLinesVisible(true);
		tableSortings.setHeaderVisible(true);
		tableSortings.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		tableViewerColumnSorting = new TableViewerColumn(tableViewerSortings, SWT.NONE);
		TableColumn tableColumnSorting = tableViewerColumnSorting.getColumn();
		tableColumnSorting.setWidth(100);
		tableColumnSorting.setText(BUNDLE.getString("sortings_text"));

		tableViewerFilters = new TableViewer(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
		tableFilters = tableViewerFilters.getTable();
		tableFilters.setLinesVisible(true);
		tableFilters.setHeaderVisible(true);
		tableFilters.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		tableViewerColumnFilter = new TableViewerColumn(tableViewerFilters, SWT.NONE);
		TableColumn tableColumnFilter = tableViewerColumnFilter.getColumn();
		tableColumnFilter.setWidth(100);
		tableColumnFilter.setText(BUNDLE.getString("filters_text"));

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

}
