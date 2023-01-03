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
package com.intellij.java.impl.codeInsight.completion;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.CompletionLocation;
import consulo.language.editor.completion.CompletionPreselectSkipper;
import consulo.language.editor.completion.CompletionType;
import consulo.ide.impl.idea.codeInsight.completion.StatisticsWeigher;
import consulo.language.editor.completion.lookup.LookupElement;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.ide.impl.psi.statistics.StatisticsManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.java.language.module.util.JavaClassNames;

/**
 * @author peter
 */
@ExtensionImpl(id = "skipAbstract")
public class AbstractExpectedTypeSkipper extends CompletionPreselectSkipper {

  private enum Result {
    NON_DEFAULT,
    STRING,
    ABSTRACT,
    ACCEPT
  }

  @Override
  public boolean skipElement(LookupElement element, CompletionLocation location) {
    return skips(element, location);
  }

  public static boolean skips(LookupElement element, CompletionLocation location) {
    return getSkippingStatus(element, location) != Result.ACCEPT;
  }

  private static Result getSkippingStatus(final LookupElement item, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.SMART) return Result.ACCEPT;

    final PsiExpression expression = PsiTreeUtil.getParentOfType(location.getCompletionParameters().getPosition(), PsiExpression.class);
    if (!(expression instanceof PsiNewExpression)) return Result.ACCEPT;

    final Object object = item.getObject();
    if (!(object instanceof PsiClass)) return Result.ACCEPT;

    if (StatisticsManager.getInstance().getUseCount(StatisticsWeigher.getBaseStatisticsInfo(item, location)) > 1)
      return Result.ACCEPT;

    PsiClass psiClass = (PsiClass) object;

    int toImplement = 0;
    for (final PsiMethod method : psiClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        toImplement++;
        if (toImplement > 2) return Result.ABSTRACT;
      }
    }

    toImplement += OverrideImplementUtil.getMethodSignaturesToImplement(psiClass).size();
    if (toImplement > 2) return Result.ABSTRACT;

    final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    boolean isDefaultType = false;
    if (infos != null) {
      final PsiType type = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory().createType(psiClass);
      for (final ExpectedTypeInfo info : infos) {
        final PsiType infoType = TypeConversionUtil.erasure(info.getType().getDeepComponentType());
        final PsiType defaultType = TypeConversionUtil.erasure(info.getDefaultType().getDeepComponentType());
        if (!defaultType.equals(infoType) && infoType.isAssignableFrom(type)) {
          if (!defaultType.isAssignableFrom(type)) return Result.NON_DEFAULT;
          isDefaultType = true;
        }
      }
    }

    if (toImplement > 0) return Result.ACCEPT;

    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return Result.ABSTRACT;
    if (!isDefaultType && JavaClassNames.JAVA_LANG_STRING.equals(psiClass.getQualifiedName())) return Result.STRING;
    if (JavaClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return Result.NON_DEFAULT;

    return Result.ACCEPT;
  }

}
