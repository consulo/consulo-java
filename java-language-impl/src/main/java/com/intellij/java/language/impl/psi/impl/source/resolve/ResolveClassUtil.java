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
package com.intellij.java.language.impl.psi.impl.source.resolve;

import com.intellij.java.language.impl.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ResolveClassUtil {
  private static final Logger LOG = Logger.getInstance(ResolveClassUtil.class);

  @Nullable
  public static PsiClass resolveClass(@Nonnull PsiJavaCodeReferenceElement ref, @Nonnull PsiFile containingFile) {
    if (ref instanceof PsiJavaCodeReferenceElementImpl && ((PsiJavaCodeReferenceElementImpl) ref).getKindEnum(containingFile) == PsiJavaCodeReferenceElementImpl.Kind.CLASS_IN_QUALIFIED_NEW_KIND) {
      PsiElement parent = ref.getParent();
      if (parent instanceof PsiAnonymousClass) {
        parent = parent.getParent();
      }
      PsiExpression qualifier;
      if (parent instanceof PsiNewExpression) {
        qualifier = ((PsiNewExpression) parent).getQualifier();
        LOG.assertTrue(qualifier != null);
      } else if (parent instanceof PsiJavaCodeReferenceElement) {
        return null;
      } else {
        LOG.assertTrue(false);
        return null;
      }

      PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) {
        return null;
      }
      if (!(qualifierType instanceof PsiClassType)) {
        return null;
      }
      PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifierType);
      if (qualifierClass == null) {
        return null;
      }
      String name = ref.getText();
      return qualifierClass.findInnerClassByName(name, true);
    }

    final PsiElement classNameElement = ref.getReferenceNameElement();
    if (!(classNameElement instanceof PsiIdentifier)) {
      return null;
    }
    String className = classNameElement.getText();

    /*
  long time1 = System.currentTimeMillis();
    */

    ClassResolverProcessor processor = new ClassResolverProcessor(className, ref, containingFile);
    PsiScopesUtil.resolveAndWalk(processor, ref, null);


    /*
    long time2 = System.currentTimeMillis();
    Statistics.resolveClassTime += (time2 - time1);
    Statistics.resolveClassCount++;
    */

    return processor.getResult().length == 1 ? (PsiClass) processor.getResult()[0].getElement() : null;
  }
}