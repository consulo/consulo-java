package com.intellij.java.impl.usageView;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.usages.impl.UsageViewImpl;
import consulo.usage.UsageContextPanel;
import consulo.usage.UsageView;

import javax.annotation.Nonnull;

@ExtensionImpl
public class UsageContextDataflowFromPanelProvider extends UsageContextDataflowToPaneProvider {
  @Nonnull
  @Override
  public UsageContextPanel create(@Nonnull UsageView usageView) {
    return new UsageContextDataflowFromPanel(((UsageViewImpl) usageView).getProject(), usageView.getPresentation());
  }

  @Nonnull
  @Override
  public String getTabTitle() {
    return "Dataflow from Here";
  }
}
