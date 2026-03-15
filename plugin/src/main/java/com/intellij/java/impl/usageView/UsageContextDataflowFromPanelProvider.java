package com.intellij.java.impl.usageView;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.usages.impl.UsageViewImpl;
import consulo.usage.UsageContextPanel;
import consulo.usage.UsageView;


@ExtensionImpl
public class UsageContextDataflowFromPanelProvider extends UsageContextDataflowToPaneProvider {
  @Override
  public UsageContextPanel create(UsageView usageView) {
    return new UsageContextDataflowFromPanel(((UsageViewImpl) usageView).getProject(), usageView.getPresentation());
  }

  @Override
  public String getTabTitle() {
    return "Dataflow from Here";
  }
}
