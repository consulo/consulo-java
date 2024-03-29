/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * User: anna
 * Date: 12-Jan-2010
 */
package com.intellij.java.impl.refactoring.rename.naming;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;

public class AutomaticOverloadsRenamer extends AutomaticRenamer {
  public AutomaticOverloadsRenamer(PsiMethod method, String newName) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass != null) {
      final PsiMethod[] overloads = containingClass.findMethodsByName(method.getName(), false);
      for (PsiMethod overload : overloads) {
        if (overload != method) {
          myElements.add(overload);
          suggestAllNames(overload.getName(), newName);
        }
      }
    }
  }

  @Override
  public String getDialogTitle() {
    return "Rename Overloads";
  }

  @Override
  public String getDialogDescription() {
    return "Rename overloads to:";
  }

  @Override
  public String entityName() {
    return "Overload";
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }
}