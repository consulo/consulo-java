package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.psi.PsiImportHolder;
import com.intellij.java.language.psi.PsiResolveHelper;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.rawHighlight.HighlightVisitor;
import consulo.language.editor.rawHighlight.HighlightVisitorFactory;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiFile;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 25/03/2023
 */
@ExtensionImpl
public class HighlightVisitorFactoryImpl implements HighlightVisitorFactory
{
	private final PsiResolveHelper myResolveHelper;

	@Inject
	public HighlightVisitorFactoryImpl(PsiResolveHelper resolveHelper)
	{
		myResolveHelper = resolveHelper;
	}

	@Override
	public boolean suitableForFile(@Nonnull PsiFile file)
	{
		// both PsiJavaFile and PsiCodeFragment must match
		return file instanceof PsiImportHolder && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
	}

	@Nonnull
	@Override
	public HighlightVisitor createVisitor()
	{
		return new HighlightVisitorImpl(myResolveHelper);
	}
}
