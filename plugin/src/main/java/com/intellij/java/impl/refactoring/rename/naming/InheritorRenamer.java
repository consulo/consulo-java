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
package com.intellij.java.impl.refactoring.rename.naming;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;

/**
 * @author dsl
 */
public class InheritorRenamer extends AutomaticRenamer {
  public InheritorRenamer(PsiClass aClass, String newClassName) {
    for (final PsiClass inheritor : ClassInheritorsSearch.search(aClass, true).findAll()) {
      if (inheritor.getName() != null) {
        myElements.add(inheritor);
      }
    }

    suggestAllNames(aClass.getName(), newClassName);
  }

  public String getDialogTitle() {
    return RefactoringLocalize.renameInheritorsTitle().get();
  }

  public String getDialogDescription() {
    return RefactoringLocalize.renameInheritorsWithTheFollowingNamesTo().get();
  }

  public String entityName() {
    return RefactoringLocalize.entityNameInheritor().get();
  }
}
