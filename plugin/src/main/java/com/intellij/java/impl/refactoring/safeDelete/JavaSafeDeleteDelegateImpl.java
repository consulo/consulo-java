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
package com.intellij.java.impl.refactoring.safeDelete;

import com.intellij.java.impl.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.usage.UsageInfo;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class JavaSafeDeleteDelegateImpl implements JavaSafeDeleteDelegate {
  @Override
  public void createUsageInfoForParameter(final PsiReference reference,
                                          final List<UsageInfo> usages,
                                          final PsiParameter parameter,
                                          final PsiMethod method) {
    int index = method.getParameterList().getParameterIndex(parameter);
    final PsiElement element = reference.getElement();
    PsiCall call = null;
    if (element instanceof PsiCall) {
      call = (PsiCall)element;
    }
    else if (element.getParent() instanceof PsiCall) {
      call = (PsiCall)element.getParent();
    }
    if (call != null) {
      final PsiExpressionList argList = call.getArgumentList();
      if (argList != null) {
        final PsiExpression[] args = argList.getExpressions();
        if (index < args.length) {
          if (!parameter.isVarArgs()) {
            usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args[index], parameter, true));
          }
          else {
            for (int i = index; i < args.length; i++) {
              usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args[i], parameter, true));
            }
          }
        }
      }
    }
    else if (element instanceof PsiDocMethodOrFieldRef) {
      if (((PsiDocMethodOrFieldRef)element).getSignature() != null) {
        @NonNls final StringBuffer newText = new StringBuffer();
        newText.append("/** @see #").append(method.getName()).append('(');
        final List<PsiParameter> parameters = new ArrayList<PsiParameter>(Arrays.asList(method.getParameterList().getParameters()));
        parameters.remove(parameter);
        newText.append(StringUtil.join(parameters, psiParameter -> parameter.getType().getCanonicalText(), ","));
        newText.append(")*/");
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
          public void deleteElement() throws IncorrectOperationException {
            final PsiDocMethodOrFieldRef.MyReference javadocMethodReference =
              (PsiDocMethodOrFieldRef.MyReference)element.getReference();
            if (javadocMethodReference != null) {
              javadocMethodReference.bindToText(method.getContainingClass(), newText);
            }
          }
        });
      }
    }
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
