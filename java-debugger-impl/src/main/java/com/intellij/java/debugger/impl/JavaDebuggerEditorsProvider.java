package com.intellij.java.debugger.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.language.impl.JavaFileType;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.evaluation.EvaluationMode;
import consulo.language.Language;
import consulo.document.Document;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.evaluation.XDebuggerEditorsProviderBase;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XExpressionImpl;

public class JavaDebuggerEditorsProvider extends XDebuggerEditorsProviderBase
{
	@Nonnull
	@Override
	public FileType getFileType()
	{
		return JavaFileType.INSTANCE;
	}

	@Override
	protected PsiFile createExpressionCodeFragment(@Nonnull Project project, @Nonnull String text, @Nullable PsiElement context, boolean isPhysical)
	{
		return JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, context, null, isPhysical);
	}

	@Nonnull
	@Override
	public Collection<Language> getSupportedLanguages(@Nonnull Project project, @Nullable XSourcePosition sourcePosition)
	{
		if(sourcePosition != null)
		{
			PsiElement context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
			Collection<Language> res = new ArrayList<Language>();
			for(CodeFragmentFactory factory : DebuggerUtilsEx.getCodeFragmentFactories(context))
			{
				res.add(factory.getFileType().getLanguage());
			}
			return res;
		}
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public XExpression createExpression(@Nonnull Project project, @Nonnull Document document, @Nullable Language language, @Nonnull EvaluationMode mode)
	{
		PsiDocumentManager.getInstance(project).commitDocument(document);
		PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
		if(psiFile != null)
		{
			return new consulo.ide.impl.idea.xdebugger.impl.breakpoints.XExpressionImpl(psiFile.getText(), language, ((JavaCodeFragment) psiFile).importsToString(), mode);
		}
		return super.createExpression(project, document, language, mode);
	}

	@Override
	protected PsiFile createExpressionCodeFragment(@Nonnull Project project, @Nonnull XExpression expression, @Nullable PsiElement context, boolean isPhysical)
	{
		TextWithImports text = TextWithImportsImpl.fromXExpression(expression);
		if(text != null && context != null)
		{
			CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, context);
			JavaCodeFragment codeFragment = factory.createPresentationCodeFragment(text, context, project);
			codeFragment.forceResolveScope(GlobalSearchScope.allScope(project));

			final PsiClass contextClass = PsiTreeUtil.getNonStrictParentOfType(context, PsiClass.class);
			if(contextClass != null)
			{
				final PsiClassType contextType = JavaPsiFacade.getInstance(codeFragment.getProject()).getElementFactory().createType(contextClass);
				codeFragment.setThisType(contextType);
			}
			return codeFragment;
		}
		else
		{
			return super.createExpressionCodeFragment(project, expression, context, isPhysical);
		}
	}
}
