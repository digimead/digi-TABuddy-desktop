package org.digimead.tabuddy.desktop.res;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.ToolItem;

public class test extends Composite {

	/**
	 * Create the composite.
	 * @param parent
	 * @param style
	 */
	public test(Composite parent, int style) {
		super(parent, style);

		ToolBar toolBar = new ToolBar(this, SWT.FLAT | SWT.RIGHT);
		toolBar.setBounds(10, 108, 430, 25);

		ToolItem tltmNewItem = new ToolItem(toolBar, SWT.NONE);
		tltmNewItem.setText(Messages.field_text);

		ToolItem tltmCheckItem = new ToolItem(toolBar, SWT.CHECK);
		tltmCheckItem.setText(Messages.fields_text);

		ToolItem tltmRadioItem = new ToolItem(toolBar, SWT.RADIO);
		tltmRadioItem.setText("Radio Item");

		ToolItem toolItem = new ToolItem(toolBar, SWT.SEPARATOR);

		ToolItem tltmDropdownItem = new ToolItem(toolBar, SWT.DROP_DOWN);
		tltmDropdownItem.setText("DropDown Item");

	}

	@Override
	protected void checkSubclass() {
		// Disable the check that prevents subclassing of SWT components
	}
}
