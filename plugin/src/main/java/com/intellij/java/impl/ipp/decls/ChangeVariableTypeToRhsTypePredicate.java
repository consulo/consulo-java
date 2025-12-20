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
package com.intellij.java.impl.ipp.decls;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

/**
 * @author Bas Leijdekkers
 */
class ChangeVariableTypeToRhsTypePredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiTypeElement)) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiVariable)) {
      return false;
    }
    PsiVariable variable = (PsiVariable)parent;
    PsiExpression initializer = variable.getInitializer();
    if (!(initializer instanceof PsiNewExpression)) {
      return false;
    }
    PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    PsiType initializerType = initializer.getType();
    if (!(initializerType instanceof PsiClassType)) {
      return false;
    }
    PsiClassType initializerClassType = (PsiClassType)initializerType;
    PsiClass initializerClass = initializerClassType.resolve();
    if (initializerClass == null) {
      return false;
    }
    PsiClassType classType = (PsiClassType)type;
    PsiClass variableClass = classType.resolve();
    if (variableClass == null) {
      return false;
    }
    return initializerClass.isInheritor(variableClass, true);
  }
}
