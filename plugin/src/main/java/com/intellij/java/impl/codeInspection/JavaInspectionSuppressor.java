package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.SuppressManager;
import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.InspectionSuppressor;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 02/12/2022
 */
@ExtensionImpl
public class JavaInspectionSuppressor implements InspectionSuppressor
{
	@Override
	public boolean isSuppressedFor(@Nonnull PsiElement element, String toolId)
	{
		return JavaSuppressionUtil.getElementToolSuppressedIn(element, toolId) != null;
	}

	@Override
	public SuppressQuickFix[] getSuppressActions(@Nonnull PsiElement element, String toolShortName)
	{
		return SuppressManager.getInstance().createBatchSuppressActions(HighlightDisplayKey.find(toolShortName));
	}

	@jakarta.annotation.Nonnull
	@Override
	public Language getLanguage()
	{
		return JavaLanguage.INSTANCE;
	}
}
