/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeCook;

import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.java.impl.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.java.impl.refactoring.typeCook.deductive.builder.Result;
import com.intellij.java.impl.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.java.impl.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.java.impl.refactoring.typeCook.deductive.resolver.ResolverTree;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class TypeCookProcessor extends BaseRefactoringProcessor {
  private PsiElement[] myElements;
  private final Settings mySettings;
  private Result myResult;

  public TypeCookProcessor(Project project, PsiElement[] elements, Settings settings) {
    super(project);

    myElements = elements;
    mySettings = settings;
  }

  @Nonnull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new TypeCookViewDescriptor(myElements);
  }

  @jakarta.annotation.Nonnull
  protected UsageInfo[] findUsages() {
    final SystemBuilder systemBuilder = new SystemBuilder(myProject, mySettings);

    final ReductionSystem commonSystem = systemBuilder.build(myElements);
    myResult = new Result(commonSystem);

    final ReductionSystem[] systems = commonSystem.isolate();

    for (final ReductionSystem system : systems) {
      if (system != null) {
        final ResolverTree tree = new ResolverTree(system);

        tree.resolve();

        final Binding solution = tree.getBestSolution();

        if (solution != null) {
          myResult.incorporateSolution(solution);
        }
      }
    }

    final HashSet<PsiElement> changedItems = myResult.getCookedElements();
    final UsageInfo[] usages = new UsageInfo[changedItems.size()];

    int i = 0;
    for (final PsiElement element : changedItems) {
      if (!(element instanceof PsiTypeCastExpression)) {
        usages[i++] = new UsageInfo(element) {
          public String getTooltipText() {
            return myResult.getCookedType(element).getCanonicalText();
          }
        };
      }
      else {
        usages[i++] = new UsageInfo(element);
      }
    }

    return usages;
  }

  protected void refreshElements(PsiElement[] elements) {
    myElements = elements;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    final HashSet<PsiElement> victims = new HashSet<PsiElement>();

    for (UsageInfo usage : usages) {
      victims.add(usage.getElement());
    }

    myResult.apply (victims);

    WindowManager.getInstance().getStatusBar(myProject).setInfo(myResult.getReport());
  }

  @Override
  protected boolean isGlobalUndoAction() {
    return true;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("type.cook.command");
  }

  public List<PsiElement> getElements() {
    return Collections.unmodifiableList(Arrays.asList(myElements));
  }
}
