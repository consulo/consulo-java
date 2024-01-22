/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import consulo.content.scope.NamedScope;
import consulo.content.scope.NamedScopesHolder;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.undoRedo.BasicUndoableAction;
import consulo.undoRedo.ProjectUndoManager;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SuppressForTestsScopeFix extends InspectionGadgetsFix {

  private final AbstractBaseJavaLocalInspectionTool myInspection;

  private SuppressForTestsScopeFix(AbstractBaseJavaLocalInspectionTool inspection) {
    myInspection = inspection;
  }

  @Nullable
  public static SuppressForTestsScopeFix build(AbstractBaseJavaLocalInspectionTool inspection, PsiElement context) {
    if (!TestUtils.isInTestSourceContent(context)) {
      return null;
    }
    return new SuppressForTestsScopeFix(inspection);
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("suppress.for.tests.scope.quickfix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void doFix(final Project project, ProblemDescriptor descriptor) {
    addRemoveTestsScope(project, true);
    final VirtualFile vFile = descriptor.getPsiElement().getContainingFile().getVirtualFile();
    ProjectUndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        addRemoveTestsScope(project, false);
      }

      @Override
      public void redo() {
        addRemoveTestsScope(project, true);
      }
    });
  }

  private void addRemoveTestsScope(Project project, boolean add) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final String shortName = myInspection.getShortName();
    final InspectionToolWrapper tool = profile.getInspectionTool(shortName, project);
    if (tool == null) {
      return;
    }
    if (add) {
      final NamedScope namedScope = NamedScopesHolder.getScope(project, "Tests");
      final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      final HighlightDisplayLevel level = profile.getErrorLevel(key, namedScope, project);
      profile.addScope(tool, namedScope, level, false, project);
    }
    else {
      profile.removeScope(shortName, "Tests", project);
    }
    profile.scopesChanged();
  }
}
