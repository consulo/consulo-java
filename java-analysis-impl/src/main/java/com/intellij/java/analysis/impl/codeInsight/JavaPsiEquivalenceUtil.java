/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight;

import com.intellij.java.language.impl.psi.PsiDiamondTypeImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiDiamondTypeElementImpl;
import com.intellij.java.language.psi.*;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.psi.PsiElement;

import java.util.Comparator;

public class JavaPsiEquivalenceUtil {
  public static boolean areExpressionsEquivalent(PsiExpression expr1, PsiExpression expr2) {
    return PsiEquivalenceUtil.areElementsEquivalent(expr1, expr2, new Comparator<PsiElement>() {
      @Override
      public int compare(PsiElement o1, PsiElement o2) {
        if (o1 instanceof PsiParameter && o2 instanceof PsiParameter && ((PsiParameter) o1).getDeclarationScope() instanceof PsiMethod) {
          return ((PsiParameter) o1).getName().compareTo(((PsiParameter) o2).getName());
        }
        return 1;
      }
    }, new Comparator<PsiElement>() {
      @Override
      public int compare(PsiElement o1, PsiElement o2) {
        if (!o1.textMatches(o2)) return 1;

        if (o1 instanceof PsiDiamondTypeElementImpl && o2 instanceof PsiDiamondTypeElementImpl) {
          final PsiDiamondType.DiamondInferenceResult thisInferenceResult = new PsiDiamondTypeImpl(o1.getManager(), (PsiTypeElement) o1).resolveInferredTypes();
          final PsiDiamondType.DiamondInferenceResult otherInferenceResult = new PsiDiamondTypeImpl(o2.getManager(), (PsiTypeElement) o2).resolveInferredTypes();
          return thisInferenceResult.equals(otherInferenceResult) ? 0 : 1;
        }
        return 0;
      }
    }, null, false);
  }
}
