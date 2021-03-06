/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.inspection;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.annotation.Nonnull;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.GenerateToStringUtils;
import consulo.java.module.util.JavaClassNames;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ui.CheckBox;

/**
 * Inspection to check if the current class overrides the toString() method.
 * <p/>
 * This inspection will use filter information from the GenerateToString plugin settings to exclude certain fields (eg. constants etc.).
 * Warns if the class has fields to be dumped and does not have a toString method.
 */
public class ClassHasNoToStringMethodInspection extends AbstractToStringInspection
{
	/**
	 * User options for classes to exclude. Must be a regexp pattern
	 */
	public String excludeClassNames = "";  // must be public for JDOMSerialization
	/**
	 * User options for excluded exception classes
	 */
	public boolean excludeException = true; // must be public for JDOMSerialization
	/**
	 * User options for excluded deprecated classes
	 */
	public boolean excludeDeprecated = true; // must be public for JDOMSerialization
	/**
	 * User options for excluded enum classes
	 */
	public boolean excludeEnum = false; // must be public for JDOMSerialization
	/**
	 * User options for excluded abstract classes
	 */
	public boolean excludeAbstract = false; // must be public for JDOMSerialization

	public boolean excludeTestCode = false;

	public boolean excludeInnerClasses = false;

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return "Class does not override 'toString()' method";
	}

	@Override
	@Nonnull
	public String getShortName()
	{
		return "ClassHasNoToStringMethod";
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly)
	{
		return new JavaElementVisitor()
		{
			@Override
			public void visitClass(PsiClass clazz)
			{
				if(AbstractToStringInspection.log.isDebugEnabled())
				{
					AbstractToStringInspection.log.debug("checkClass: clazz=" + clazz);
				}

				// must be a class
				final PsiIdentifier nameIdentifier = clazz.getNameIdentifier();
				if(nameIdentifier == null || clazz.getName() == null)
				{
					return;
				}

				if(excludeException && InheritanceUtil.isInheritor(clazz, JavaClassNames.JAVA_LANG_THROWABLE))
				{
					return;
				}
				if(excludeDeprecated && clazz.isDeprecated())
				{
					return;
				}
				if(excludeEnum && clazz.isEnum())
				{
					return;
				}
				if(excludeAbstract && clazz.hasModifierProperty(PsiModifier.ABSTRACT))
				{
					return;
				}
				if(excludeTestCode && TestFrameworks.getInstance().isTestClass(clazz))
				{
					return;
				}
				if(excludeInnerClasses && clazz.getContainingClass() != null)
				{
					return;
				}

				// if it is an excluded class - then skip
				if(StringUtil.isNotEmpty(excludeClassNames))
				{
					String name = clazz.getName();
					if(name != null && name.matches(excludeClassNames))
					{
						return;
					}
				}

				// must have fields
				PsiField[] fields = clazz.getFields();
				if(fields.length == 0)
				{
					return;
				}

				// get list of fields and getter methods supposed to be dumped in the toString method
				fields = GenerateToStringUtils.filterAvailableFields(clazz, GenerateToStringContext.getConfig().getFilterPattern());
				PsiMethod[] methods = null;
				if(GenerateToStringContext.getConfig().isEnableMethods())
				{
					// okay 'getters in code generation' is enabled so check
					methods = GenerateToStringUtils.filterAvailableMethods(clazz, GenerateToStringContext.getConfig().getFilterPattern());
				}

				// there should be any fields
				if(Math.max(fields.length, methods == null ? 0 : methods.length) == 0)
				{
					return;
				}

				// okay some fields/getter methods are supposed to dumped, does a toString method exist
				final PsiMethod[] toStringMethods = clazz.findMethodsByName("toString", false);
				for(PsiMethod method : toStringMethods)
				{
					final PsiParameterList parameterList = method.getParameterList();
					if(parameterList.getParametersCount() == 0)
					{
						// toString() method found
						return;
					}
				}
				final PsiMethod[] superMethods = clazz.findMethodsByName("toString", true);
				for(PsiMethod method : superMethods)
				{
					final PsiParameterList parameterList = method.getParameterList();
					if(parameterList.getParametersCount() != 0)
					{
						continue;
					}
					if(method.hasModifierProperty(PsiModifier.FINAL))
					{
						// final toString() in super class found
						return;
					}
				}
				holder.registerProblem(nameIdentifier, "Class '" + clazz.getName() + "' does not override 'toString()' method",
						ProblemHighlightType.GENERIC_ERROR_OR_WARNING, GenerateToStringQuickFix.getInstance());
			}
		};
	}

	/**
	 * Creates the options panel in the settings for user changeable options.
	 *
	 * @return the options panel
	 */
	@Override
	public JComponent createOptionsPanel()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();

		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 0.0;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.fill = GridBagConstraints.NONE;
		panel.add(new JLabel("Exclude classes (reg exp):"), constraints);

		final JTextField excludeClassNamesField = new JTextField(excludeClassNames, 40);
		excludeClassNamesField.setMinimumSize(new Dimension(140, 20));
		Document document = excludeClassNamesField.getDocument();
		document.addDocumentListener(new DocumentListener()
		{
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				textChanged();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				textChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				textChanged();
			}

			private void textChanged()
			{
				excludeClassNames = excludeClassNamesField.getText();
			}
		});
		constraints.gridx = 1;
		constraints.gridy = 0;
		constraints.weightx = 1.0;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.fill = GridBagConstraints.NONE;
		panel.add(excludeClassNamesField, constraints);

		final CheckBox excludeExceptionCheckBox = new CheckBox("Ignore exception classes", this, "excludeException");
		constraints.gridx = 0;
		constraints.gridy = 1;
		constraints.gridwidth = 2;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(excludeExceptionCheckBox, constraints);

		final CheckBox excludeDeprecatedCheckBox = new CheckBox("Ignore deprecated classes", this, "excludeDeprecated");
		constraints.gridy = 2;
		panel.add(excludeDeprecatedCheckBox, constraints);

		final CheckBox excludeEnumCheckBox = new CheckBox("Ignore enum classes", this, "excludeEnum");
		constraints.gridy = 3;
		panel.add(excludeEnumCheckBox, constraints);

		final CheckBox excludeAbstractCheckBox = new CheckBox("Ignore abstract classes", this, "excludeAbstract");
		constraints.gridy = 4;
		panel.add(excludeAbstractCheckBox, constraints);

		final CheckBox excludeInTestCodeCheckBox = new CheckBox("Ignore test classes", this, "excludeTestCode");
		constraints.gridy = 5;
		panel.add(excludeInTestCodeCheckBox, constraints);

		final CheckBox excludeInnerClasses = new CheckBox("Ignore inner classes", this, "excludeInnerClasses");
		constraints.gridy = 6;
		constraints.weighty = 1.0;
		panel.add(excludeInnerClasses, constraints);

		return panel;
	}
}
