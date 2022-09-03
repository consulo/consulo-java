/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import java.util.ArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import consulo.application.ApplicationManager;
import consulo.language.editor.WriteCommandAction;
import consulo.undoRedo.util.UndoUtil;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.psi.GenericsUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import consulo.usage.UsageViewUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

public class VariableTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement
{
	static final Logger LOG = Logger.getInstance(VariableTypeFix.class);

	private final PsiType myReturnType;
	protected final String myName;

	public VariableTypeFix(@Nonnull PsiVariable variable, PsiType toReturn)
	{
		super(variable);
		myReturnType = GenericsUtil.getVariableTypeByExpressionType(toReturn);
		myName = variable.getName();
	}

	@Nonnull
	@Override
	public String getText()
	{
		return JavaQuickFixBundle.message("fix.variable.type.text", UsageViewUtil.getType(getStartElement()), myName, getReturnType().getCanonicalText());
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("fix.variable.type.family");
	}

	@Override
	public boolean startInWriteAction()
	{
		return false;
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		final PsiVariable myVariable = (PsiVariable) startElement;
		return myVariable.isValid() && myVariable.getTypeElement() != null && myVariable.getManager().isInProject(myVariable) && getReturnType() != null && getReturnType().isValid() &&
				!TypeConversionUtil.isNullType(getReturnType()) && !TypeConversionUtil.isVoidType(getReturnType());
	}

	@Override
	public void invoke(@Nonnull final Project project,
			@Nonnull final PsiFile file,
			@Nullable Editor editor,
			@Nonnull PsiElement startElement,
			@Nonnull PsiElement endElement)
	{
		final PsiVariable myVariable = (PsiVariable) startElement;
		if(changeMethodSignatureIfNeeded(myVariable))
		{
			return;
		}
		if(!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile()))
		{
			return;
		}
		new WriteCommandAction.Simple(project, getText(), file)
		{

			@Override
			protected void run() throws Throwable
			{
				try
				{
					myVariable.normalizeDeclaration();
					final PsiTypeElement typeElement = myVariable.getTypeElement();
					LOG.assertTrue(typeElement != null, myVariable.getClass());
					final PsiTypeElement newTypeElement = JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createTypeElement(getReturnType());
					typeElement.replace(newTypeElement);
					JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
					UndoUtil.markPsiFileForUndo(file);
				}
				catch(IncorrectOperationException e)
				{
					LOG.error(e);
				}
			}
		}.execute();
	}

	private boolean changeMethodSignatureIfNeeded(PsiVariable myVariable)
	{
		if(myVariable instanceof PsiParameter)
		{
			final PsiElement scope = ((PsiParameter) myVariable).getDeclarationScope();
			if(scope instanceof PsiMethod)
			{
				final PsiMethod method = (PsiMethod) scope;
				final PsiMethod psiMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
				if(psiMethod == null)
				{
					return true;
				}
				final int parameterIndex = method.getParameterList().getParameterIndex((PsiParameter) myVariable);
				if(!FileModificationService.getInstance().prepareFileForWrite(psiMethod.getContainingFile()))
				{
					return true;
				}
				final ArrayList<ParameterInfoImpl> infos = new ArrayList<ParameterInfoImpl>();
				int i = 0;
				for(PsiParameter parameter : psiMethod.getParameterList().getParameters())
				{
					final boolean changeType = i == parameterIndex;
					infos.add(new ParameterInfoImpl(i++, parameter.getName(), changeType ? getReturnType() : parameter.getType()));
				}

				if(!ApplicationManager.getApplication().isUnitTestMode())
				{
					final JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(psiMethod.getProject(), psiMethod, false, myVariable);
					dialog.setParameterInfos(infos);
					dialog.show();
				}
				else
				{
					ChangeSignatureProcessor processor = new ChangeSignatureProcessor(psiMethod.getProject(), psiMethod, false, null, psiMethod.getName(), psiMethod.getReturnType(),
							infos.toArray(new ParameterInfoImpl[infos.size()]));
					processor.run();
				}
				return true;
			}
		}
		return false;
	}

	protected PsiType getReturnType()
	{
		return myReturnType;
	}
}