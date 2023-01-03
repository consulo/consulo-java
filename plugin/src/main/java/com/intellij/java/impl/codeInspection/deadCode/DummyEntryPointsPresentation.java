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
package com.intellij.java.impl.codeInspection.deadCode;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.ex.EntryPointsManager;
import com.intellij.java.analysis.impl.codeInspection.util.RefFilter;
import consulo.ide.impl.idea.codeInspection.ex.GlobalInspectionContextImpl;
import consulo.ide.impl.idea.codeInspection.ex.InspectionRVContentProvider;
import consulo.ide.impl.idea.codeInspection.ex.QuickFixAction;
import consulo.ide.impl.idea.codeInspection.ui.InspectionNode;
import consulo.ide.impl.idea.codeInspection.ui.InspectionTreeNode;
import consulo.language.editor.inspection.HTMLComposerImpl;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;

import javax.annotation.Nonnull;

public class DummyEntryPointsPresentation extends UnusedDeclarationPresentation {
  private static final RefEntryPointFilter myFilter = new RefEntryPointFilter();
  private QuickFixAction[] myQuickFixActions;

  public DummyEntryPointsPresentation(@Nonnull InspectionToolWrapper toolWrapper,
                                      @Nonnull GlobalInspectionContextImpl context) {
    super(toolWrapper, context);
  }

  @Override
  public RefFilter getFilter() {
    return myFilter;
  }

  @Override
  public QuickFixAction[] getQuickFixes(@Nonnull final RefEntity[] refElements) {
    if (myQuickFixActions == null) {
      myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious(getToolWrapper())};
    }
    return myQuickFixActions;
  }

  @Override
  protected String getSeverityDelegateName() {
    return UnusedDeclarationInspection.SHORT_NAME;
  }

  private class MoveEntriesToSuspicious extends QuickFixAction {
    private MoveEntriesToSuspicious(@Nonnull InspectionToolWrapper toolWrapper) {
      super(InspectionsBundle.message("inspection.dead.code.remove.from.entry.point.quickfix"), null, null,
          toolWrapper);
    }

    @Override
    protected boolean applyFix(@Nonnull RefEntity[] refElements) {
      final EntryPointsManager entryPointsManager = getContext().getExtension(GlobalJavaInspectionContext
          .CONTEXT).getEntryPointsManager(getContext().getRefManager());
      for (RefEntity refElement : refElements) {
        if (refElement instanceof RefElement) {
          entryPointsManager.removeEntryPoint((RefElement) refElement);
        }
      }

      return true;
    }
  }

  @Nonnull
  @Override
  public InspectionNode createToolNode(@Nonnull GlobalInspectionContextImpl context,
                                       @Nonnull InspectionNode node,
                                       @Nonnull InspectionRVContentProvider provider,
                                       @Nonnull InspectionTreeNode parentNode,
                                       boolean showStructure) {
    return node;
  }

  @Override
  @Nonnull
  public HTMLComposerImpl getComposer() {
    return new DeadHTMLComposer(this);
  }
}
