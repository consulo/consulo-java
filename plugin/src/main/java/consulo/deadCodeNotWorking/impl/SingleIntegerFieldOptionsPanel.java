/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.deadCodeNotWorking.impl;

import consulo.language.editor.inspection.InspectionTool;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.Document;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;

@Deprecated
public class SingleIntegerFieldOptionsPanel extends JPanel
{

	public SingleIntegerFieldOptionsPanel(String labelString, final InspectionTool owner, @NonNls final String property)
	{
		this(labelString, owner, property, 2);
	}

	public SingleIntegerFieldOptionsPanel(String labelString, final InspectionTool owner, @NonNls final String property, int integerFieldColumns)
	{
		super(new GridBagLayout());
		final JLabel label = new JLabel(labelString);
		final JFormattedTextField valueField = createIntegerFieldTrackingValue(owner, property, integerFieldColumns);
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets.right = 10;
		constraints.weightx = 0.0;
		constraints.anchor = GridBagConstraints.BASELINE_LEADING;
		constraints.fill = GridBagConstraints.NONE;
		add(label, constraints);
		constraints.gridx = 1;
		constraints.gridy = 0;
		constraints.weightx = 1.0;
		constraints.weighty = 1.0;
		constraints.insets.right = 0;
		constraints.anchor = GridBagConstraints.BASELINE_LEADING;
		constraints.fill = GridBagConstraints.NONE;
		add(valueField, constraints);
	}

	public static JFormattedTextField createIntegerFieldTrackingValue(@Nonnull InspectionTool owner, @Nonnull String property, int integerFieldColumns)
	{
		JFormattedTextField valueField = new JFormattedTextField();
		valueField.setColumns(integerFieldColumns);
		setupIntegerFieldTrackingValue(valueField, owner, property);
		return valueField;
	}

	/**
	 * Sets integer number format to JFormattedTextField instance,
	 * sets value of JFormattedTextField instance to object's field value,
	 * synchronizes object's field value with the value of JFormattedTextField instance.
	 *
	 * @param textField JFormattedTextField instance
	 * @param owner     an object whose field is synchronized with {@code textField}
	 * @param property  object's field name for synchronization
	 */
	public static void setupIntegerFieldTrackingValue(final JFormattedTextField textField, final InspectionTool owner, final String property)
	{
		NumberFormat formatter = NumberFormat.getIntegerInstance();
		formatter.setParseIntegerOnly(true);
		textField.setFormatterFactory(new DefaultFormatterFactory(new NumberFormatter(formatter)));
		textField.setValue(getPropertyValue(owner, property));
		final Document document = textField.getDocument();
		document.addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			public void textChanged(DocumentEvent e)
			{
				try
				{
					textField.commitEdit();
					setPropertyValue(owner, property, ((Number) textField.getValue()).intValue());
				}
				catch(ParseException e1)
				{
					// No luck this time
				}
			}
		});
	}

	private static void setPropertyValue(InspectionTool owner, String property, int value)
	{
		try
		{
			owner.getClass().getField(property).setInt(owner, value);
		}
		catch(Exception e)
		{
			// OK
		}
	}

	private static int getPropertyValue(InspectionTool owner, String property)
	{
		try
		{
			return owner.getClass().getField(property).getInt(owner);
		}
		catch(Exception e)
		{
			return 0;
		}
	}
}