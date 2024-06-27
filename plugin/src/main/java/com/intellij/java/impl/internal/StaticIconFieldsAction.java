/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.java.impl.internal;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.usage.*;
import jakarta.annotation.Nonnull;

public class StaticIconFieldsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(Project.KEY);

    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabName("Statics");
    presentation.setTabText("Statitcs");
    final UsageView view = UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, new Usage[0], presentation);

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching icons usages") {
      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope all = GlobalSearchScope.allScope(project);
        PsiClass allIcons = facade.findClass("com.intellij.icons.AllIcons", all);
        searchFields(allIcons, view, indicator);
        for (PsiClass iconsClass : facade.findPackage("icons").getClasses(all)) {
          searchFields(iconsClass, view, indicator);
        }
      }
    });
  }

  private static void searchFields(PsiClass allIcons, final UsageView view, ProgressIndicator indicator) {
    indicator.setText("Searching for: " + allIcons.getQualifiedName());
    ReferencesSearch.search(allIcons).forEach(reference -> {
      PsiElement elt = reference.getElement();

      while (elt instanceof PsiExpression) elt = elt.getParent();

      if (elt instanceof PsiField) {
        UsageInfo info = new UsageInfo(elt, false);
        view.appendUsage(new UsageInfo2UsageAdapter(info));
      }

      return true;
    });
  }
}

