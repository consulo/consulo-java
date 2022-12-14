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
package com.intellij.java.impl.codeInsight.completion;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.CompletionLocation;
import consulo.ide.impl.idea.codeInsight.completion.CompletionStatistician;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupItem;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.java.impl.psi.statistics.JavaStatisticsManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.ide.impl.psi.statistics.StatisticsInfo;
import consulo.util.collection.ContainerUtil;

import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class JavaCompletionStatistician extends CompletionStatistician {
  @Override
  public consulo.ide.impl.psi.statistics.StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    Object o = element.getObject();

    if (o instanceof PsiLocalVariable || o instanceof PsiParameter || o instanceof PsiThisExpression || o instanceof PsiKeyword) {
      return consulo.ide.impl.psi.statistics.StatisticsInfo.EMPTY;
    }

    LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
    if (item == null) {
      return null;
    }

    PsiType qualifierType = JavaCompletionUtil.getQualifierType(item);

    if (o instanceof PsiMember) {
      final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
      final ExpectedTypeInfo firstInfo = infos != null && infos.length > 0 ? infos[0] : null;
      String key2 = JavaStatisticsManager.getMemberUseKey2((PsiMember) o);
      if (o instanceof PsiClass) {
        PsiType expectedType = firstInfo != null ? firstInfo.getDefaultType() : null;
        return new consulo.ide.impl.psi.statistics.StatisticsInfo(JavaStatisticsManager.getAfterNewKey(expectedType), key2);
      }

      PsiClass containingClass = ((PsiMember) o).getContainingClass();
      if (containingClass != null) {
        String expectedName = firstInfo instanceof ExpectedTypeInfoImpl ? ((ExpectedTypeInfoImpl) firstInfo).getExpectedName() : null;
        String contextPrefix = expectedName == null ? "" : "expectedName=" + expectedName + "###";
        String context = contextPrefix + JavaStatisticsManager.getMemberUseKey2(containingClass);

        if (o instanceof PsiMethod) {
          String memberValue = JavaStatisticsManager.getMemberUseKey2(RecursionWeigher.findDeepestSuper((PsiMethod) o));

          List<consulo.ide.impl.psi.statistics.StatisticsInfo> superMethodInfos = ContainerUtil.newArrayList(new consulo.ide.impl.psi.statistics.StatisticsInfo(contextPrefix + context, memberValue));
          for (PsiClass superClass : InheritanceUtil.getSuperClasses(containingClass)) {
            superMethodInfos.add(new StatisticsInfo(contextPrefix + JavaStatisticsManager.getMemberUseKey2(superClass), memberValue));
          }
          return consulo.ide.impl.psi.statistics.StatisticsInfo.createComposite(superMethodInfos);
        }

        return new consulo.ide.impl.psi.statistics.StatisticsInfo(context, key2);
      }
    }

    if (qualifierType != null) {
      return consulo.ide.impl.psi.statistics.StatisticsInfo.EMPTY;
    }

    return null;
  }

}
