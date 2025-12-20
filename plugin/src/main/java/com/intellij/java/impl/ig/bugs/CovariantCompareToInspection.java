/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CovariantCompareToInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.covariantComparetoDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.covariantComparetoProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CovariantCompareToVisitor();
  }

  private static class CovariantCompareToVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      String name = method.getName();
      if (!HardcodedMethodConstants.COMPARE_TO.equals(name)) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      PsiParameter[] parameters = parameterList.getParameters();
      PsiType paramType = parameters[0].getType();
      if (TypeUtils.isJavaLangObject(paramType)) {
        return;
      }
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      PsiMethod[] methods = aClass.findMethodsByName(HardcodedMethodConstants.COMPARE_TO, false);
      Project project = method.getProject();
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      GlobalSearchScope scope = method.getResolveScope();
      PsiClass comparableClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_COMPARABLE, scope);
      PsiType substitutedTypeParam = null;
      if (comparableClass != null && comparableClass.getTypeParameters().length == 1) {
        PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(comparableClass, aClass, PsiSubstitutor.EMPTY);
        //null iff aClass is not inheritor of comparableClass
        if (superSubstitutor != null) {
           substitutedTypeParam = superSubstitutor.substitute(comparableClass.getTypeParameters()[0]);
        }
      }
      for (PsiMethod compareToMethod : methods) {
        if (isNonVariantCompareTo(compareToMethod, substitutedTypeParam)) {
          return;
        }
      }
      registerMethodError(method);
    }

    private static boolean isNonVariantCompareTo(PsiMethod method, PsiType substitutedTypeParam) {
      PsiClassType objectType = TypeUtils.getObjectType(method);
      if (MethodUtils.methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.COMPARE_TO, objectType)) {
        return true;
      }
      if (substitutedTypeParam == null) {
        return false;
      }
      return MethodUtils.methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.COMPARE_TO, substitutedTypeParam);
    }
  }
}