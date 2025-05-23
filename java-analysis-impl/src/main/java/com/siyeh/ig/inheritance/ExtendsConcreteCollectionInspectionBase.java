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
package com.siyeh.ig.inheritance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import jakarta.annotation.Nonnull;

public abstract class ExtendsConcreteCollectionInspectionBase extends BaseInspection {
  @Override
  @Nonnull
  public String getID() {
    return "ClassExtendsConcreteCollection";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.extendsConcreteCollectionDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass) infos[0];
    final PsiClass aClass = (PsiClass) infos[1];
    return aClass instanceof PsiAnonymousClass
      ? InspectionGadgetsLocalize.anonymousExtendsConcreteCollectionProblemDescriptor(superClass.getQualifiedName()).get()
      : InspectionGadgetsLocalize.extendsConcreteCollectionProblemDescriptor(superClass.getQualifiedName()).get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsConcreteCollectionVisitor();
  }

  private static class ExtendsConcreteCollectionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      final PsiClass superClass = aClass.getSuperClass();
      if (!CollectionUtils.isConcreteCollectionClass(superClass)) {
        return;
      }
      final String qualifiedName = superClass.getQualifiedName();
      if ("java.util.LinkedHashMap".equals(qualifiedName)) {
        final PsiMethod[] methods = aClass.findMethodsByName("removeEldestEntry", false);
        final PsiClassType entryType = TypeUtils.getType(CommonClassNames.JAVA_UTIL_MAP_ENTRY, aClass);
        for (PsiMethod method : methods) {
          if (!PsiType.BOOLEAN.equals(method.getReturnType())) {
            continue;
          }
          final PsiParameterList parameterList = method.getParameterList();
          if (parameterList.getParametersCount() != 1) {
            continue;
          }
          final PsiParameter parameter = parameterList.getParameters()[0];
          if (entryType.isAssignableFrom(parameter.getType())) {
            return;
          }
        }
      }
      registerClassError(aClass, superClass, aClass);
    }
  }
}
