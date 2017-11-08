/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.application.options;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.intellij.application.options.codeStyle.CommenterForm;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import consulo.annotations.RequiredDispatchThread;

public class CodeStyleGenerationConfigurable implements Configurable
{
	JPanel myPanel;
	private JTextField myFieldPrefixField;
	private JTextField myStaticFieldPrefixField;
	private JTextField myParameterPrefixField;
	private JTextField myLocalVariablePrefixField;

	private JTextField myFieldSuffixField;
	private JTextField myStaticFieldSuffixField;
	private JTextField myParameterSuffixField;
	private JTextField myLocalVariableSuffixField;

	private JCheckBox myCbPreferLongerNames;

	private final MembersOrderList myMembersOrderList;

	private final CodeStyleSettings mySettings;
	private JCheckBox myCbGenerateFinalParameters;
	private JCheckBox myCbGenerateFinalLocals;
	private JCheckBox myCbUseExternalAnnotations;
	private JCheckBox myInsertOverrideAnnotationCheckBox;
	private JPanel myMembersPanel;
	private JCheckBox myRepeatSynchronizedCheckBox;
	private JPanel myCommentPanel;

	private CommenterForm myCommenterForm;

	public CodeStyleGenerationConfigurable(CodeStyleSettings settings)
	{
		mySettings = settings;
		myMembersOrderList = new MembersOrderList();
		myPanel.setBorder(JBUI.Borders.empty(2));
	}

	@RequiredDispatchThread
	@Override
	public JComponent createComponent()
	{
		final JPanel panel = ToolbarDecorator.createDecorator(myMembersOrderList).disableAddAction().disableRemoveAction().createPanel();
		myMembersPanel.add(panel, BorderLayout.CENTER);
		return myPanel;
	}

	@Override
	public String getDisplayName()
	{
		return ApplicationBundle.message("title.code.generation");
	}

	@Override
	public String getHelpTopic()
	{
		return "reference.settingsdialog.IDE.globalcodestyle.codegen";
	}

	public void reset(CodeStyleSettings settings)
	{
		myCbPreferLongerNames.setSelected(settings.PREFER_LONGER_NAMES);

		myFieldPrefixField.setText(settings.FIELD_NAME_PREFIX);
		myStaticFieldPrefixField.setText(settings.STATIC_FIELD_NAME_PREFIX);
		myParameterPrefixField.setText(settings.PARAMETER_NAME_PREFIX);
		myLocalVariablePrefixField.setText(settings.LOCAL_VARIABLE_NAME_PREFIX);

		myFieldSuffixField.setText(settings.FIELD_NAME_SUFFIX);
		myStaticFieldSuffixField.setText(settings.STATIC_FIELD_NAME_SUFFIX);
		myParameterSuffixField.setText(settings.PARAMETER_NAME_SUFFIX);
		myLocalVariableSuffixField.setText(settings.LOCAL_VARIABLE_NAME_SUFFIX);

		myCommenterForm.reset(settings);

		myCbGenerateFinalLocals.setSelected(settings.GENERATE_FINAL_LOCALS);
		myCbGenerateFinalParameters.setSelected(settings.GENERATE_FINAL_PARAMETERS);
		myMembersOrderList.reset(mySettings);

		myCbUseExternalAnnotations.setSelected(settings.USE_EXTERNAL_ANNOTATIONS);
		myInsertOverrideAnnotationCheckBox.setSelected(settings.INSERT_OVERRIDE_ANNOTATION);
		myRepeatSynchronizedCheckBox.setSelected(settings.REPEAT_SYNCHRONIZED);
	}

	@RequiredDispatchThread
	@Override
	public void reset()
	{
		reset(mySettings);
	}

	public void apply(CodeStyleSettings settings)
	{
		settings.PREFER_LONGER_NAMES = myCbPreferLongerNames.isSelected();

		settings.FIELD_NAME_PREFIX = myFieldPrefixField.getText().trim();
		settings.STATIC_FIELD_NAME_PREFIX = myStaticFieldPrefixField.getText().trim();
		settings.PARAMETER_NAME_PREFIX = myParameterPrefixField.getText().trim();
		settings.LOCAL_VARIABLE_NAME_PREFIX = myLocalVariablePrefixField.getText().trim();

		settings.FIELD_NAME_SUFFIX = myFieldSuffixField.getText().trim();
		settings.STATIC_FIELD_NAME_SUFFIX = myStaticFieldSuffixField.getText().trim();
		settings.PARAMETER_NAME_SUFFIX = myParameterSuffixField.getText().trim();
		settings.LOCAL_VARIABLE_NAME_SUFFIX = myLocalVariableSuffixField.getText().trim();

		myCommenterForm.apply(settings);
		settings.GENERATE_FINAL_LOCALS = myCbGenerateFinalLocals.isSelected();
		settings.GENERATE_FINAL_PARAMETERS = myCbGenerateFinalParameters.isSelected();

		settings.USE_EXTERNAL_ANNOTATIONS = myCbUseExternalAnnotations.isSelected();
		settings.INSERT_OVERRIDE_ANNOTATION = myInsertOverrideAnnotationCheckBox.isSelected();
		settings.REPEAT_SYNCHRONIZED = myRepeatSynchronizedCheckBox.isSelected();

		myMembersOrderList.apply(settings);

		for(Project project : ProjectManager.getInstance().getOpenProjects())
		{
			DaemonCodeAnalyzer.getInstance(project).settingsChanged();
		}
	}

	@RequiredDispatchThread
	@Override
	public void apply()
	{
		apply(mySettings);
	}

	public boolean isModified(CodeStyleSettings settings)
	{
		boolean isModified = isModified(myCbPreferLongerNames, settings.PREFER_LONGER_NAMES);

		isModified |= isModified(myFieldPrefixField, settings.FIELD_NAME_PREFIX);
		isModified |= isModified(myStaticFieldPrefixField, settings.STATIC_FIELD_NAME_PREFIX);
		isModified |= isModified(myParameterPrefixField, settings.PARAMETER_NAME_PREFIX);
		isModified |= isModified(myLocalVariablePrefixField, settings.LOCAL_VARIABLE_NAME_PREFIX);

		isModified |= isModified(myFieldSuffixField, settings.FIELD_NAME_SUFFIX);
		isModified |= isModified(myStaticFieldSuffixField, settings.STATIC_FIELD_NAME_SUFFIX);
		isModified |= isModified(myParameterSuffixField, settings.PARAMETER_NAME_SUFFIX);
		isModified |= isModified(myLocalVariableSuffixField, settings.LOCAL_VARIABLE_NAME_SUFFIX);

		isModified |= myCommenterForm.isModified(settings);

		isModified |= isModified(myCbGenerateFinalLocals, settings.GENERATE_FINAL_LOCALS);
		isModified |= isModified(myCbGenerateFinalParameters, settings.GENERATE_FINAL_PARAMETERS);

		isModified |= isModified(myCbUseExternalAnnotations, settings.USE_EXTERNAL_ANNOTATIONS);
		isModified |= isModified(myInsertOverrideAnnotationCheckBox, settings.INSERT_OVERRIDE_ANNOTATION);
		isModified |= isModified(myRepeatSynchronizedCheckBox, settings.REPEAT_SYNCHRONIZED);

		isModified |= myMembersOrderList.isModified(settings);

		return isModified;
	}

	@RequiredDispatchThread
	@Override
	public boolean isModified()
	{
		return isModified(mySettings);
	}

	private static boolean isModified(JCheckBox checkBox, boolean value)
	{
		return checkBox.isSelected() != value;
	}

	private static boolean isModified(JTextField textField, String value)
	{
		return !textField.getText().trim().equals(value);
	}

	private static class MembersOrderList extends JBList
	{
		private static abstract class PropertyManager
		{

			public final String myName;

			protected PropertyManager(String nameKey)
			{
				myName = ApplicationBundle.message(nameKey);
			}

			abstract void apply(CodeStyleSettings settings, int value);

			abstract int getValue(CodeStyleSettings settings);
		}

		private static final Map<String, PropertyManager> PROPERTIES = new HashMap<String, PropertyManager>();

		static
		{
			init();
		}

		private final DefaultListModel myModel;

		public MembersOrderList()
		{
			myModel = new DefaultListModel();
			setModel(myModel);
			setVisibleRowCount(PROPERTIES.size());
		}

		public void reset(final CodeStyleSettings settings)
		{
			myModel.removeAllElements();
			for(String string : getPropertyNames(settings))
			{
				myModel.addElement(string);
			}

			setSelectedIndex(0);
		}

		private static void init()
		{
			PropertyManager staticFieldManager = new PropertyManager("listbox.members.order.static.fields")
			{
				@Override
				void apply(CodeStyleSettings settings, int value)
				{
					settings.STATIC_FIELDS_ORDER_WEIGHT = value;
				}

				@Override
				int getValue(CodeStyleSettings settings)
				{
					return settings.STATIC_FIELDS_ORDER_WEIGHT;
				}
			};
			PROPERTIES.put(staticFieldManager.myName, staticFieldManager);

			PropertyManager instanceFieldManager = new PropertyManager("listbox.members.order.fields")
			{
				@Override
				void apply(CodeStyleSettings settings, int value)
				{
					settings.FIELDS_ORDER_WEIGHT = value;
				}

				@Override
				int getValue(CodeStyleSettings settings)
				{
					return settings.FIELDS_ORDER_WEIGHT;
				}
			};
			PROPERTIES.put(instanceFieldManager.myName, instanceFieldManager);

			PropertyManager constructorManager = new PropertyManager("listbox.members.order.constructors")
			{
				@Override
				void apply(CodeStyleSettings settings, int value)
				{
					settings.CONSTRUCTORS_ORDER_WEIGHT = value;
				}

				@Override
				int getValue(CodeStyleSettings settings)
				{
					return settings.CONSTRUCTORS_ORDER_WEIGHT;
				}
			};
			PROPERTIES.put(constructorManager.myName, constructorManager);

			PropertyManager staticMethodManager = new PropertyManager("listbox.members.order.static.methods")
			{
				@Override
				void apply(CodeStyleSettings settings, int value)
				{
					settings.STATIC_METHODS_ORDER_WEIGHT = value;
				}

				@Override
				int getValue(CodeStyleSettings settings)
				{
					return settings.STATIC_METHODS_ORDER_WEIGHT;
				}
			};
			PROPERTIES.put(staticMethodManager.myName, staticMethodManager);

			PropertyManager instanceMethodManager = new PropertyManager("listbox.members.order.methods")
			{
				@Override
				void apply(CodeStyleSettings settings, int value)
				{
					settings.METHODS_ORDER_WEIGHT = value;
				}

				@Override
				int getValue(CodeStyleSettings settings)
				{
					return settings.METHODS_ORDER_WEIGHT;
				}
			};
			PROPERTIES.put(instanceMethodManager.myName, instanceMethodManager);

			PropertyManager staticInnerClassManager = new PropertyManager("listbox.members.order.inner.static.classes")
			{
				@Override
				void apply(CodeStyleSettings settings, int value)
				{
					settings.STATIC_INNER_CLASSES_ORDER_WEIGHT = value;
				}

				@Override
				int getValue(CodeStyleSettings settings)
				{
					return settings.STATIC_INNER_CLASSES_ORDER_WEIGHT;
				}
			};
			PROPERTIES.put(staticInnerClassManager.myName, staticInnerClassManager);

			PropertyManager innerClassManager = new PropertyManager("listbox.members.order.inner.classes")
			{
				@Override
				void apply(CodeStyleSettings settings, int value)
				{
					settings.INNER_CLASSES_ORDER_WEIGHT = value;
				}

				@Override
				int getValue(CodeStyleSettings settings)
				{
					return settings.INNER_CLASSES_ORDER_WEIGHT;
				}
			};
			PROPERTIES.put(innerClassManager.myName, innerClassManager);
		}

		private static Iterable<String> getPropertyNames(final CodeStyleSettings settings)
		{
			List<String> result = new ArrayList<String>(PROPERTIES.keySet());
			Collections.sort(result, new Comparator<String>()
			{
				@Override
				public int compare(String o1, String o2)
				{
					int weight1 = getWeight(o1);
					int weight2 = getWeight(o2);
					return weight1 - weight2;
				}

				private int getWeight(String o)
				{
					PropertyManager propertyManager = PROPERTIES.get(o);
					if(propertyManager == null)
					{
						throw new IllegalArgumentException("unexpected " + o);
					}
					return propertyManager.getValue(settings);
				}
			});
			return result;
		}

		public void apply(CodeStyleSettings settings)
		{
			for(int i = 0; i < myModel.size(); i++)
			{
				Object o = myModel.getElementAt(i);
				if(o == null)
				{
					throw new IllegalArgumentException("unexpected " + o);
				}
				PropertyManager propertyManager = PROPERTIES.get(o.toString());
				if(propertyManager == null)
				{
					throw new IllegalArgumentException("unexpected " + o);
				}
				propertyManager.apply(settings, i + 1);
			}
		}

		public boolean isModified(CodeStyleSettings settings)
		{
			Iterable<String> oldProperties = getPropertyNames(settings);
			int i = 0;
			for(String property : oldProperties)
			{
				if(i >= myModel.size() || !property.equals(myModel.getElementAt(i)))
				{
					return true;
				}
				i++;
			}
			return false;
		}
	}

	private void createUIComponents()
	{
		myCommenterForm = new CommenterForm(JavaLanguage.INSTANCE);
		myCommentPanel = myCommenterForm.getCommenterPanel();
	}
}
