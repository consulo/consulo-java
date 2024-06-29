/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.generate;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import consulo.logging.Logger;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import com.intellij.java.impl.generate.element.ClassElement;
import com.intellij.java.impl.generate.element.Element;
import com.intellij.java.impl.generate.element.ElementComparator;
import com.intellij.java.impl.generate.element.ElementFactory;
import com.intellij.java.impl.generate.element.ElementUtils;
import com.intellij.java.impl.generate.element.FieldElement;
import com.intellij.java.impl.generate.element.GenerationHelper;
import com.intellij.java.impl.generate.exception.GenerateCodeException;
import com.intellij.java.impl.generate.exception.PluginException;
import org.jetbrains.java.generate.psi.PsiAdapter;
import com.intellij.java.impl.generate.velocity.VelocityFactory;
import com.intellij.java.language.impl.codeInsight.generation.PsiElementClassMember;
import com.intellij.java.impl.codeInsight.generation.PsiFieldMember;
import com.intellij.java.impl.codeInsight.generation.PsiMethodMember;
import consulo.component.ProcessCanceledException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nullable;

public class GenerationUtil
{
	private static final Logger logger = Logger.getInstance(GenerationUtil.class);

	/**
	 * Handles any exception during the executing on this plugin.
	 *
	 * @param project PSI project
	 * @param e       the caused exception.
	 * @throws RuntimeException is thrown for severe exceptions
	 */
	public static void handleException(Project project, Exception e) throws RuntimeException
	{
		logger.info(e);

		if (e instanceof GenerateCodeException)
		{
			// code generation error - display velocity error in error dialog so user can identify problem quicker
			Messages.showMessageDialog(project,
				"Velocity error generating code - see IDEA log for more details (stacktrace should be in idea.log):\n" + e.getMessage(),
				"Warning",
				UIUtil.getWarningIcon()
			);
		}
		else if (e instanceof PluginException)
		{
			// plugin related error - could be recoverable.
			Messages.showMessageDialog(project,
				"A PluginException was thrown while performing the action" +
					" - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(),
				"Warning",
				UIUtil.getWarningIcon()
			);
		}
		else if (e instanceof RuntimeException e1)
		{
			// unknown error (such as NPE) - not recoverable
			Messages.showMessageDialog(
				project,
				"An unrecoverable exception was thrown while performing the action" +
					" - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(),
				"Error",
				UIUtil.getErrorIcon()
			);
			throw e1; // throw to make IDEA alert user
		}
		else
		{
			// unknown error (such as NPE) - not recoverable
			Messages.showMessageDialog(project,
				"An unrecoverable exception was thrown while performing the action" +
					" - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(),
				"Error",
				UIUtil.getErrorIcon()
			);
			throw new RuntimeException(e); // rethrow as runtime to make IDEA alert user
		}
	}

	/**
	 * Combines the two lists into one list of members.
	 *
	 * @param filteredFields  fields to be included in the dialog
	 * @param filteredMethods methods to be included in the dialog
	 * @return the combined list
	 */
	public static PsiElementClassMember[] combineToClassMemberList(PsiField[] filteredFields, PsiMethod[] filteredMethods)
	{
		PsiElementClassMember[] members = new PsiElementClassMember[filteredFields.length + filteredMethods.length];

		// first add fields
		for (int i = 0; i < filteredFields.length; i++)
		{
			members[i] = new PsiFieldMember(filteredFields[i]);
		}

		// then add methods
		for (int i = 0; i < filteredMethods.length; i++)
		{
			members[filteredFields.length + i] = new PsiMethodMember(filteredMethods[i]);
		}

		return members;
	}

	/**
	 * Converts the list of {@link PsiElementClassMember} to {PsiMember} objects.
	 *
	 * @param classMemberList list of {@link PsiElementClassMember}
	 * @return a list of {PsiMember} objects.
	 */
	public static List<PsiMember> convertClassMembersToPsiMembers(@Nullable List<PsiElementClassMember> classMemberList)
	{
		if (classMemberList == null || classMemberList.isEmpty())
		{
			return Collections.emptyList();
		}
		List<PsiMember> psiMemberList = new ArrayList<>();

		for (PsiElementClassMember classMember : classMemberList)
		{
			psiMemberList.add(classMember.getElement());
		}

		return psiMemberList;
	}

	public static void applyJavaDoc(PsiMethod newMethod, String existingJavaDoc, String newJavaDoc)
	{
		String text = newJavaDoc != null ? newJavaDoc : existingJavaDoc; // prefer to use new javadoc
		PsiAdapter.addOrReplaceJavadoc(newMethod, text, true);
	}

	/**
	 * Generates the code using Velocity.
	 * <p/>
	 * This is used to create the <code>toString</code> method body and it's javadoc.
	 *
	 * @param clazz
	 * @param selectedMembers       the selected members as both {@link PsiField} and {@link PsiMethod}.
	 * @param params                additional parameters stored with key/value in the map.
	 * @param templateMacro         the velocity macro template
	 * @param sortElements
	 * @param useFullyQualifiedName @return code (usually javacode). Returns null if templateMacro is null.
	 * @throws GenerateCodeException is thrown when there is an error generating the javacode.
	 */
	public static String velocityGenerateCode(PsiClass clazz,
			Collection<? extends PsiMember> selectedMembers,
			Map<String, String> params,
			String templateMacro,
			int sortElements,
			boolean useFullyQualifiedName) throws GenerateCodeException
	{
		return velocityGenerateCode(clazz, selectedMembers, Collections.<PsiMember>emptyList(), params, Collections.<String, Object>emptyMap(), templateMacro, sortElements, useFullyQualifiedName,
				false);
	}

	/**
	 * Generates the code using Velocity.
	 * <p/>
	 * This is used to create the <code>toString</code> method body and it's javadoc.
	 *
	 * @param selectedMembers the selected members as both {@link PsiField} and {@link PsiMethod}.
	 * @param params          additional parameters stored with key/value in the map.
	 * @param templateMacro   the velocity macro template
	 * @param useAccessors    if true, accessor property for FieldElement bean would be assigned to field getter name append with ()
	 * @return code (usually javacode). Returns null if templateMacro is null.
	 * @throws GenerateCodeException is thrown when there is an error generating the javacode.
	 */
	public static String velocityGenerateCode(@Nullable PsiClass clazz,
			Collection<? extends PsiMember> selectedMembers,
			Collection<? extends PsiMember> selectedNotNullMembers,
			Map<String, String> params,
			Map<String, Object> contextMap,
			String templateMacro,
			int sortElements,
			boolean useFullyQualifiedName,
			boolean useAccessors) throws GenerateCodeException
	{
		if (templateMacro == null)
		{
			return null;
		}

		StringWriter sw = new StringWriter();
		try
		{
			VelocityContext vc = new VelocityContext();

			// field information
			logger.debug("Velocity Context - adding fields");
			final List<FieldElement> fieldElements = ElementUtils.getOnlyAsFieldElements(selectedMembers, selectedNotNullMembers, useAccessors);
			vc.put("fields", fieldElements);
			if (fieldElements.size() == 1)
			{
				vc.put("field", fieldElements.get(0));
			}

			PsiMember member = clazz != null ? clazz : ContainerUtil.getFirstItem(selectedMembers);

			// method information
			logger.debug("Velocity Context - adding methods");
			vc.put("methods", ElementUtils.getOnlyAsMethodElements(selectedMembers));

			// element information (both fields and methods)
			logger.debug("Velocity Context - adding members (fields and methods)");
			List<Element> elements = ElementUtils.getOnlyAsFieldAndMethodElements(selectedMembers, selectedNotNullMembers, useAccessors);
			// sort elements if enabled and not using chooser dialog
			if (sortElements != 0)
			{
				Collections.sort(elements, new ElementComparator(sortElements));
			}
			vc.put("members", elements);

			// class information
			if (clazz != null)
			{
				ClassElement ce = ElementFactory.newClassElement(clazz);
				vc.put("class", ce);
				if (logger.isDebugEnabled())
				{
					logger.debug("Velocity Context - adding class: " + ce);
				}

				// information to keep as it is to avoid breaking compatibility with prior releases
				vc.put("classname", useFullyQualifiedName ? ce.getQualifiedName() : ce.getName());
				vc.put("FQClassname", ce.getQualifiedName());
			}

			if (member != null)
			{
				vc.put("java_version", PsiAdapter.getJavaVersion(member));
				final Project project = member.getProject();
				vc.put("settings", CodeStyleSettingsManager.getSettings(project));
				vc.put("project", project);
			}

			vc.put("helper", GenerationHelper.class);
			vc.put("StringUtil", StringUtil.class);

			for (String paramName : contextMap.keySet())
			{
				vc.put(paramName, contextMap.get(paramName));
			}

			if (logger.isDebugEnabled())
			{
				logger.debug("Velocity Macro:\n" + templateMacro);
			}

			// velocity
			VelocityEngine velocity = VelocityFactory.getVelocityEngine();
			logger.debug("Executing velocity +++ START +++");
			velocity.evaluate(vc, sw, GenerateToStringWorker.class.getName(), templateMacro);
			logger.debug("Executing velocity +++ END +++");

			// any additional packages to import returned from velocity?
			if (vc.get("autoImportPackages") != null)
			{
				params.put("autoImportPackages", (String) vc.get("autoImportPackages"));
			}
		}
		catch (ProcessCanceledException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new GenerateCodeException("Error in Velocity code generator", e);
		}

		return StringUtil.convertLineSeparators(sw.getBuffer().toString());
	}
}