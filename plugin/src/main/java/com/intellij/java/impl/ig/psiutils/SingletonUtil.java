/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import jakarta.annotation.Nonnull;

public class SingletonUtil {

  private SingletonUtil() {
  }

  public static boolean isSingleton(@Nonnull PsiClass aClass) {
    if (aClass.isInterface() || aClass.isEnum() ||
        aClass.isAnnotationType()) {
      return false;
    }
    if (aClass instanceof PsiTypeParameter ||
        aClass instanceof PsiAnonymousClass) {
      return false;
    }
    PsiMethod[] constructors = getIfOnlyInvisibleConstructors(aClass);
    if (constructors.length == 0) {
      return false;
    }
    PsiField selfInstance = getIfOneStaticSelfInstance(aClass);
    if (selfInstance == null) {
      return false;
    }
    return newOnlyAssignsToStaticSelfInstance(constructors[0], selfInstance);
  }

  private static PsiField getIfOneStaticSelfInstance(PsiClass aClass) {
    PsiField[] fields = aClass.getFields();
    PsiField result = null;
    for (PsiField field : fields) {
      String className = aClass.getQualifiedName();
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      PsiType type = field.getType();
      String fieldTypeName = type.getCanonicalText();
      if (!fieldTypeName.equals(className)) {
        continue;
      }
      if (result != null) {
        return null;
      }
      result = field;
    }
    return result;
  }

  private static PsiMethod[] getIfOnlyInvisibleConstructors(PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return PsiMethod.EMPTY_ARRAY;
    }
    for (PsiMethod constructor : constructors) {
      if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        return PsiMethod.EMPTY_ARRAY;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE) &&
          !constructor.hasModifierProperty(PsiModifier.PROTECTED)) {
        return PsiMethod.EMPTY_ARRAY;
      }
    }
    return constructors;
  }

  private static boolean newOnlyAssignsToStaticSelfInstance(
    PsiMethod method, PsiField field) {
    Query<PsiReference> search =
      MethodReferencesSearch.search(method, field.getUseScope(),
                                    false);
    NewOnlyAssignedToFieldProcessor processor =
      new NewOnlyAssignedToFieldProcessor(field);
    search.forEach(processor);
    return processor.isNewOnlyAssignedToField();
  }

  private static class NewOnlyAssignedToFieldProcessor
    implements Processor<PsiReference> {

    private boolean newOnlyAssignedToField = true;
    private final PsiField field;

    public NewOnlyAssignedToFieldProcessor(PsiField field) {
      this.field = field;
    }

    public boolean process(PsiReference reference) {
      PsiElement element = reference.getElement();
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      PsiElement grandParent = parent.getParent();
      if (field.equals(grandParent)) {
        return true;
      }
      if (!(grandParent instanceof PsiAssignmentExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)grandParent;
      PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lhs;
      PsiElement target = referenceExpression.resolve();
      if (!field.equals(target)) {
        newOnlyAssignedToField = false;
        return false;
      }
      return true;
    }

    public boolean isNewOnlyAssignedToField() {
      return newOnlyAssignedToField;
    }
  }
}
