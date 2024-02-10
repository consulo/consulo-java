package com.intellij.java.impl.usageView;

import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.usages.impl.UsageViewImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.usage.*;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UsageContextDataflowToPaneProvider implements UsageContextPanelProvider {
  @Nonnull
  @Override
  public UsageContextPanel create(@Nonnull UsageView usageView) {
    return new UsageContextDataflowToPanel(((UsageViewImpl) usageView).getProject(), usageView.getPresentation());
  }

  @Override
  public boolean isAvailableFor(@Nonnull UsageView usageView) {
    UsageTarget[] targets = ((UsageViewImpl) usageView).getTargets();
    if (targets.length == 0) {
      return false;
    }
    UsageTarget target = targets[0];
    if (!(target instanceof PsiElementUsageTarget)) {
      return false;
    }
    PsiElement element = ((PsiElementUsageTarget) target).getElement();
    if (element == null || !element.isValid()) {
      return false;
    }
    if (!(element instanceof PsiVariable)) {
      return false;
    }
    PsiFile file = element.getContainingFile();
    return file instanceof PsiJavaFile;
  }

  @Nonnull
  @Override
  public String getTabTitle() {
    return "Dataflow to Here";
  }
}
