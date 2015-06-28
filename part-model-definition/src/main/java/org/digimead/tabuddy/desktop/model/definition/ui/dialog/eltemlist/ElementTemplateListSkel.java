/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.definition.ui.dialog.eltemlist;

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

import swing2swt.layout.FlowLayout;

/**
 * This file is autogenerated by Google WindowBuilder Pro
 *
 * @author ezh
 */
public class ElementTemplateListSkel extends TitleAreaDialog {
	private static final ResourceBundle BUNDLE = getResourceBundle();
	private static final ResourceBundle CORE_BUNDLE = getResourceCoreBundle();
	private static final ResourceBundle LOGIC_BUNDLE = getResourceLogicBundle();
	private TableViewer tableViewer;
	private Composite compositeFooter;
	private TableViewerColumn tableViewerColumnId;
	private TableViewerColumn tableViewerColumnAvailability;
	private TableViewerColumn tableViewerColumnName;

	/**
	 * Get ResourceBundle from Scala environment.
	 *
	 * @return ResourceBundle interface of NLS singleton.
	 */
	private static ResourceBundle getResourceBundle() {
		try {
			return (ResourceBundle) Class.forName("org.digimead.tabuddy.desktop.model.definition.Messages").newInstance();
		} catch (ClassNotFoundException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.messages");
		} catch (IllegalAccessException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.messages");
		} catch (InstantiationException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.model.definition.messages");
		}
	}

	/**
	 * Get ResourceBundle from Scala environment.
	 *
	 * @return ResourceBundle interface of NLS singleton.
	 */
	private static ResourceBundle getResourceCoreBundle() {
		try {
			return (ResourceBundle) Class.forName("org.digimead.tabuddy.desktop.core.Messages").newInstance();
		} catch (ClassNotFoundException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.core.messages");
		} catch (IllegalAccessException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.core.messages");
		} catch (InstantiationException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.core.messages");
		}
	}

	/**
	 * Get ResourceBundle from Scala environment.
	 *
	 * @return ResourceBundle interface of NLS singleton.
	 */
	private static ResourceBundle getResourceLogicBundle() {
		try {
			return (ResourceBundle) Class.forName("org.digimead.tabuddy.desktop.logic.Messages").newInstance();
		} catch (ClassNotFoundException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.logic.messages");
		} catch (IllegalAccessException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.logic.messages");
		} catch (InstantiationException e) {
			return ResourceBundle.getBundle("org.digimead.tabuddy.desktop.logic.messages");
		}
	}

	/**
	 * Create the dialog.
	 *
	 * @param parentShell
	 */
	public ElementTemplateListSkel(Shell parentShell) {
		super(parentShell);
		setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.PRIMARY_MODAL);
	}

	public TableViewer getTableViewer() {
		return tableViewer;
	}

	public Composite getCompositeFooter() {
		return compositeFooter;
	}

	public TableViewerColumn getTableViewerColumnId() {
		return tableViewerColumnId;
	}

	public TableViewerColumn getTableViewerColumnAvailability() {
		return tableViewerColumnAvailability;
	}

	public TableViewerColumn getTableViewerColumnName() {
		return tableViewerColumnName;
	}

	/**
	 * Create contents of the dialog.
	 *
	 * @param parent
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle(BUNDLE.getString("elementTemplateListTitle_text"));
		setMessage(BUNDLE.getString("elementTemplateListDescription_text"));
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(1, true));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label lblElementTemplates = new Label(container, SWT.NONE);
		lblElementTemplates.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblElementTemplates.setText(LOGIC_BUNDLE.getString("elementTemplates_text"));

		tableViewer = new TableViewer(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
		Table table_templates = tableViewer.getTable();
		table_templates.setHeaderVisible(true);
		table_templates.setLinesVisible(true);
		table_templates.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		tableViewerColumnAvailability = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnAvailability = tableViewerColumnAvailability.getColumn();
		tblclmnAvailability.setWidth(100);
		tblclmnAvailability.setText(CORE_BUNDLE.getString("availability_text"));

		tableViewerColumnId = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnId = tableViewerColumnId.getColumn();
		tblclmnId.setWidth(100);
		tblclmnId.setText(CORE_BUNDLE.getString("identificator_text"));

		tableViewerColumnName = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnName = tableViewerColumnName.getColumn();
		tblclmnName.setWidth(100);
		tblclmnName.setText(CORE_BUNDLE.getString("name_text"));

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

}
