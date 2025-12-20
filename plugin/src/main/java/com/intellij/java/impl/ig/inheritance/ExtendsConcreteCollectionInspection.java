/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.inheritance;

import com.intellij.java.impl.ig.fixes.ReplaceInheritanceWithDelegationFix;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.inheritance.ExtendsConcreteCollectionInspectionBase;

public abstract class ExtendsConcreteCollectionInspection extends ExtendsConcreteCollectionInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiClass aClass = (PsiClass) infos[1];
    // skip inheritance with delegation for anonymous classes
    // or better suggest to replace anonymous with inner and then replace with delegation
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    return new ReplaceInheritanceWithDelegationFix();
  }
}