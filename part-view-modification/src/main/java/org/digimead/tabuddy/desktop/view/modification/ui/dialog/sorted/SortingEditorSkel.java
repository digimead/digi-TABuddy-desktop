/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.view.modification.ui.dialog.sorted;

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
public class SortingEditorSkel extends TitleAreaDialog {
	private static final ResourceBundle BUNDLE = getResourceBundle();
	private Button btnCheckAvailability;
	private Composite compositeBody1;
	private Composite compositeBody2;
	private Composite compositeHeader;
	private Table tableProperties;
	private Table tableSortings;
	private TableViewer tableViewerProperties;
	private TableViewer tableViewerSortings;
	private TableViewerColumn tableViewerColumnArgument;
	private TableViewerColumn tableViewerColumnDirection;
	private TableViewerColumn tableViewerColumnN;
	private TableViewerColumn tableViewerColumnProperty;
	private TableViewerColumn tableViewerColumnPropertyFrom;
	private TableViewerColumn tableViewerColumnSorting;
	private TableViewerColumn tableViewerColumnType;
	private TableViewerColumn tableViewerColumnTypeFrom;
	private Text textDescription;
	private Text textFilterProperties;
	private Text textFilterSortings;
	private Text textName;

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
	public SortingEditorSkel(Shell parentShell) {
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
		setMessage(BUNDLE.getString("viewSortingEditorDescription_text"));
		setTitle(BUNDLE.getString("viewSortingEditorTitle_text"));
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(4, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		compositeHeader = new Composite(container, SWT.NONE);
		compositeHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
		compositeHeader.setLayout(new GridLayout(2, false));

		Label lblName = new Label(compositeHeader, SWT.NONE);
		lblName.setAlignment(SWT.RIGHT);
		lblName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblName.setBounds(0, 0, 65, 15);
		lblName.setText(BUNDLE.getString("name_text"));

		textName = new Text(compositeHeader, SWT.BORDER);
		textName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textName.setText("");

		Label lblDescription = new Label(compositeHeader, SWT.NONE);
		lblDescription.setAlignment(SWT.RIGHT);
		lblDescription.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblDescription.setText(BUNDLE.getString("description_text"));

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
		Label lblSortings = new Label(container, SWT.NONE);
		lblSortings.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblSortings.setAlignment(SWT.CENTER);
		lblSortings.setText(BUNDLE.getString("sortings_text"));
		new Label(container, SWT.NONE);

		textFilterProperties = new Text(container, SWT.BORDER);
		textFilterProperties.setToolTipText(BUNDLE.getString("lookupFilter_text"));
		textFilterProperties.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		new Label(container, SWT.NONE);

		textFilterSortings = new Text(container, SWT.BORDER);
		textFilterSortings.setToolTipText(BUNDLE.getString("lookupFilter_text"));
		textFilterSortings.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		new Label(container, SWT.NONE);

		tableViewerProperties = new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
		tableProperties = tableViewerProperties.getTable();
		tableProperties.setLinesVisible(true);
		tableProperties.setHeaderVisible(true);
		tableProperties.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1));

		tableViewerColumnPropertyFrom = new TableViewerColumn(tableViewerProperties, SWT.NONE);
		TableColumn tableColumnPropertyFrom = tableViewerColumnPropertyFrom.getColumn();
		tableColumnPropertyFrom.setWidth(100);
		tableColumnPropertyFrom.setText(BUNDLE.getString("property_text"));

		tableViewerColumnTypeFrom = new TableViewerColumn(tableViewerProperties, SWT.NONE);
		TableColumn tableColumnTypeFrom = tableViewerColumnTypeFrom.getColumn();
		tableColumnTypeFrom.setWidth(100);
		tableColumnTypeFrom.setText(BUNDLE.getString("type_text"));

		compositeBody1 = new Composite(container, SWT.NONE);
		compositeBody1.setLayout(new GridLayout(1, false));

		tableViewerSortings = new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
		tableSortings = tableViewerSortings.getTable();
		tableSortings.setHeaderVisible(true);
		tableSortings.setLinesVisible(true);
		tableSortings.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		tableViewerColumnN = new TableViewerColumn(tableViewerSortings, SWT.NONE);
		TableColumn tableColumnN = tableViewerColumnN.getColumn();
		tableColumnN.setWidth(100);
		tableColumnN.setText("N");

		tableViewerColumnProperty = new TableViewerColumn(tableViewerSortings, SWT.NONE);
		TableColumn tableColumnProperty = tableViewerColumnProperty.getColumn();
		tableColumnProperty.setWidth(100);
		tableColumnProperty.setText(BUNDLE.getString("property_text"));

		tableViewerColumnType = new TableViewerColumn(tableViewerSortings, SWT.NONE);
		TableColumn tableColumnType = tableViewerColumnType.getColumn();
		tableColumnType.setWidth(100);
		tableColumnType.setText(BUNDLE.getString("type_text"));

		tableViewerColumnDirection = new TableViewerColumn(tableViewerSortings, SWT.NONE);
		TableColumn tableColumnDirection = tableViewerColumnDirection.getColumn();
		tableColumnDirection.setWidth(100);
		tableColumnDirection.setText(BUNDLE.getString("direction_text"));

		tableViewerColumnSorting = new TableViewerColumn(tableViewerSortings, SWT.NONE);
		TableColumn tableColumnSorting = tableViewerColumnSorting.getColumn();
		tableColumnSorting.setWidth(100);
		tableColumnSorting.setText(BUNDLE.getString("sorting_text"));

		tableViewerColumnArgument = new TableViewerColumn(tableViewerSortings, SWT.NONE);
		TableColumn tableColumnArgument = tableViewerColumnArgument.getColumn();
		tableColumnArgument.setWidth(100);
		tableColumnArgument.setText(BUNDLE.getString("argument_text"));

		compositeBody2 = new Composite(container, SWT.NONE);
		compositeBody2.setLayout(new GridLayout(1, false));

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

	protected TableViewer getTableViewerSortings() {
		return tableViewerSortings;
	}

	protected TableViewer getTableViewerProperties() {
		return tableViewerProperties;
	}

	protected TableViewerColumn getTableViewerColumnProperty() {
		return tableViewerColumnProperty;
	}

	protected TableViewerColumn getTableViewerColumnType() {
		return tableViewerColumnType;
	}

	protected TableViewerColumn getTableViewerColumnDirection() {
		return tableViewerColumnDirection;
	}

	protected TableViewerColumn getTableViewerColumnArgument() {
		return tableViewerColumnArgument;
	}

	protected TableViewerColumn getTableViewerColumnSorting() {
		return tableViewerColumnSorting;
	}

	protected Text getTextDescription() {
		return textDescription;
	}

	protected Text getTextName() {
		return textName;
	}

	protected Button getBtnCheckAvailability() {
		return btnCheckAvailability;
	}

	protected Composite getCompositeBody2() {
		return compositeBody2;
	}

	protected Composite getCompositeBody1() {
		return compositeBody1;
	}

	protected TableViewerColumn getTableViewerColumnPropertyFrom() {
		return tableViewerColumnPropertyFrom;
	}

	protected TableViewerColumn getTableViewerColumnTypeFrom() {
		return tableViewerColumnTypeFrom;
	}

	protected Text getTextFilterProperties() {
		return textFilterProperties;
	}

	protected Text getTextFilterSortings() {
		return textFilterSortings;
	}

	protected TableViewerColumn getTableViewerColumnN() {
		return tableViewerColumnN;
	}
}
