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

import com.intellij.java.analysis.impl.generate.config.Config;
import com.intellij.java.analysis.impl.generate.config.DuplicationPolicy;
import com.intellij.java.analysis.impl.generate.config.InsertWhere;
import com.intellij.java.impl.generate.config.*;
import com.intellij.java.impl.generate.exception.GenerateCodeException;
import com.intellij.java.impl.generate.template.TemplateResource;
import com.intellij.java.impl.generate.view.MethodExistsDialog;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.VisualPosition;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.hint.HintManager;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.psi.PsiAdapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author max
 */
public class GenerateToStringWorker
{
	private static final Logger logger = Logger.getInstance(GenerateToStringWorker.class);

	private final Editor editor;
	private final PsiClass clazz;
	private final Config config;
	private final boolean hasOverrideAnnotation;

	public GenerateToStringWorker(PsiClass clazz, Editor editor, boolean insertAtOverride)
	{
		this.clazz = clazz;
		this.editor = editor;
		this.config = GenerateToStringContext.getConfig();
		this.hasOverrideAnnotation = insertAtOverride;
	}

	/**
	 * Creates the <code>toString</code> method.
	 *
	 * @param selectedMembers the selected members as both {@link PsiField} and {@link PsiMethod}.
	 * @param policy          conflict resolution policy
	 * @param params          additional parameters stored with key/value in the map.
	 * @param template        the template to use
	 * @return the created method, null if the method is not created due the user cancels this operation
	 * @throws GenerateCodeException       is thrown when there is an error generating the javacode.
	 * @throws IncorrectOperationException is thrown by IDEA.
	 */
	@Nullable
	private PsiMethod createToStringMethod(Collection<PsiMember> selectedMembers,
			ConflictResolutionPolicy policy,
			Map<String, String> params,
			TemplateResource template) throws IncorrectOperationException, GenerateCodeException
	{
		// generate code using velocity
		String body = GenerationUtil.velocityGenerateCode(clazz, selectedMembers, params, template.getMethodBody(), config.getSortElements(), config.isUseFullyQualifiedName());
		if(logger.isDebugEnabled())
		{
			logger.debug("Method body generated from Velocity:\n" + body);
		}

		// fix weird linebreak problem in IDEA #3296 and later
		body = StringUtil.convertLineSeparators(body);

		// create psi newMethod named toString()
		final JVMElementFactory topLevelFactory = JVMElementFactories.getFactory(clazz.getLanguage(), clazz.getProject());
		if(topLevelFactory == null)
		{
			return null;
		}
		PsiMethod newMethod;
		try
		{
			newMethod = topLevelFactory.createMethodFromText(template.getMethodSignature() + " { " + body + " }", clazz);
			CodeStyleManager.getInstance(clazz.getProject()).reformat(newMethod);
		}
		catch(IncorrectOperationException ignore)
		{
			HintManager.getInstance().showErrorHint(editor, "'toString()' method could not be created from template '" +
					template.getFileName() + '\'');
			return null;
		}

		// insertNewMethod conflict resolution policy (add/replace, duplicate, cancel)
		PsiMethod existingMethod = clazz.findMethodBySignature(newMethod, false);
		PsiMethod toStringMethod = policy.applyMethod(clazz, existingMethod, newMethod, editor);
		if(toStringMethod == null)
		{
			return null; // user cancelled so return null
		}

		if(hasOverrideAnnotation)
		{
			toStringMethod.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
		}

		// applyJavaDoc conflict resolution policy (add or keep existing)
		String existingJavaDoc = params.get("existingJavaDoc");
		String newJavaDoc = template.getJavaDoc();
		if(existingJavaDoc != null || newJavaDoc != null)
		{
			// generate javadoc using velocity
			newJavaDoc = GenerationUtil.velocityGenerateCode(clazz, selectedMembers, params, newJavaDoc, config.getSortElements(), config.isUseFullyQualifiedName());
			if(logger.isDebugEnabled())
			{
				logger.debug("JavaDoc body generated from Velocity:\n" + newJavaDoc);
			}

			GenerationUtil.applyJavaDoc(toStringMethod, existingJavaDoc, newJavaDoc);
		}

		// return the created method
		return toStringMethod;
	}

	public void execute(Collection<PsiMember> members, TemplateResource template) throws IncorrectOperationException, GenerateCodeException
	{
		// decide what to do if the method already exists
		ConflictResolutionPolicy resolutionPolicy = exitsMethodDialog(template);
		// what insert policy should we use?
		resolutionPolicy.setNewMethodStrategy(getStrategy(config.getInsertNewMethodInitialOption()));

		// user didn't click cancel so go on
		Map<String, String> params = new HashMap<String, String>();

		// before
		beforeCreateToStringMethod(params, template);

		// generate method
		PsiMethod method = createToStringMethod(members, resolutionPolicy, params, template);

		// after, if method was generated (not cancel policy)
		if(method != null)
		{
			afterCreateToStringMethod(method, params, template);
		}
	}

	private static InsertNewMethodStrategy getStrategy(InsertWhere option)
	{
		switch(option)
		{
			case AFTER_EQUALS_AND_HASHCODE:
				return InsertAfterEqualsHashCodeStrategy.getInstance();
			case AT_CARET:
				return InsertAtCaretStrategy.getInstance();
			case AT_THE_END_OF_A_CLASS:
				return InsertLastStrategy.getInstance();
		}

		return InsertLastStrategy.getInstance();
	}

	/**
	 * This method gets the choice if there is an existing <code>toString</code> method.
	 * <br/> 1) If there is a settings to always override use this.
	 * <br/> 2) Prompt a dialog and let the user decide.
	 *
	 * @param template the chosen template to use
	 * @return the policy the user selected (never null)
	 */
	protected ConflictResolutionPolicy exitsMethodDialog(TemplateResource template)
	{
		final DuplicationPolicy dupPolicy = config.getReplaceDialogInitialOption();
		if(dupPolicy == DuplicationPolicy.ASK)
		{
			PsiMethod existingMethod = PsiAdapter.findMethodByName(clazz, template.getTargetMethodName());
			if(existingMethod != null)
			{
				return MethodExistsDialog.showDialog(template.getTargetMethodName());
			}
		}
		else if(dupPolicy == DuplicationPolicy.REPLACE)
		{
			return ReplacePolicy.getInstance();
		}

		// If there is no conflict, duplicate policy will do the trick
		return DuplicatePolicy.getInstance();
	}

	/**
	 * This method is executed just before the <code>toString</code> method is created or updated.
	 *
	 * @param params   additional parameters stored with key/value in the map.
	 * @param template the template to use
	 */
	private void beforeCreateToStringMethod(Map<String, String> params, TemplateResource template)
	{
		PsiMethod existingMethod = PsiAdapter.findMethodByName(clazz, template.getTargetMethodName()); // find the existing method
		if(existingMethod != null && existingMethod.getDocComment() != null)
		{
			PsiDocComment doc = existingMethod.getDocComment();
			if(doc != null)
			{
				params.put("existingJavaDoc", doc.getText());
			}
		}
	}


	/**
	 * This method is executed just after the <code>toString</code> method is created or updated.
	 *
	 * @param method   the newly created/updated <code>toString</code> method.
	 * @param params   additional parameters stored with key/value in the map.
	 * @param template the template to use
	 * @throws IncorrectOperationException is thrown by IDEA
	 */
	private void afterCreateToStringMethod(PsiMethod method, Map<String, String> params, TemplateResource template)
	{
		PsiFile containingFile = clazz.getContainingFile();
		if(containingFile instanceof PsiJavaFile)
		{
			final PsiJavaFile javaFile = (PsiJavaFile) containingFile;
			if(params.get("autoImportPackages") != null)
			{
				// keep this for old user templates
				autoImportPackages(javaFile, params.get("autoImportPackages"));
			}
		}
		method = (PsiMethod) JavaCodeStyleManager.getInstance(clazz.getProject()).shortenClassReferences(method);

		// jump to method
		if(!config.isJumpToMethod() || editor == null)
		{
			return;
		}
		int offset = method.getTextOffset();
		if(offset <= 2)
		{
			return;
		}
		VisualPosition vp = editor.offsetToVisualPosition(offset);
		if(logger.isDebugEnabled())
		{
			logger.debug("Moving/Scrolling caret to " + vp + " (offset=" + offset + ")");
		}
		editor.getCaretModel().moveToVisualPosition(vp);
		editor.getScrollingModel().scrollToCaret(ScrollType.CENTER_DOWN);
	}

	/**
	 * Automatic import the packages.
	 *
	 * @param packageNames names of packages (must end with .* and be separated by ; or ,)
	 * @throws IncorrectOperationException error adding imported package
	 */
	private static void autoImportPackages(PsiJavaFile psiJavaFile, String packageNames) throws IncorrectOperationException
	{
		StringTokenizer tok = new StringTokenizer(packageNames, ",");
		while(tok.hasMoreTokens())
		{
			String packageName = tok.nextToken().trim(); // trim in case of space
			if(logger.isDebugEnabled())
			{
				logger.debug("Auto importing package: " + packageName);
			}
			PsiAdapter.addImportStatement(psiJavaFile, packageName);
		}
	}

	/**
	 * Generates the toString() code for the specified class and selected
	 * fields, doing the work through a WriteAction ran by a CommandProcessor.
	 *
	 * @param selectedMembers  list of members selected
	 * @param template         the chosen template to use
	 * @param insertAtOverride
	 */
	public static void executeGenerateActionLater(final PsiClass clazz,
			final Editor editor,
			final Collection<PsiMember> selectedMembers,
			final TemplateResource template,
			final boolean insertAtOverride)
	{
		Runnable writeCommand = new Runnable()
		{
			public void run()
			{
				ApplicationManager.getApplication().runWriteAction(new Runnable()
				{
					public void run()
					{
						try
						{
							new GenerateToStringWorker(clazz, editor, insertAtOverride).execute(selectedMembers, template);
						}
						catch(Exception e)
						{
							GenerationUtil.handleException(clazz.getProject(), e);
						}
					}
				});
			}
		};

		CommandProcessor.getInstance().executeCommand(clazz.getProject(), writeCommand, "GenerateToString", null);
	}
}
