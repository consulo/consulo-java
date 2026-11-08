// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import consulo.language.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstantInitializer;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;

import static com.intellij.java.language.psi.CommonClassNames.JAVA_LANG_ENUM;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class EnumCompareInliner implements CallInliner {
  private static final CallMatcher ENUM_COMPARE_TO = instanceCall(JAVA_LANG_ENUM, "compareTo").parameterTypes("E");

  @Override
  public boolean tryInlineCall(CFGBuilder builder,
                               PsiMethodCallExpression call) {
    if (!ENUM_COMPARE_TO.matches(call)) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    PsiExpression arg = call.getArgumentList().getExpressions()[0];
    PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
    PsiClass argumentClass = PsiUtil.resolveClassInClassTypeOnly(arg.getType());
    if (qualifierClass instanceof PsiEnumConstantInitializer) {
      qualifierClass = qualifierClass.getSuperClass();
    }
    if (argumentClass instanceof PsiEnumConstantInitializer) {
      argumentClass = argumentClass.getSuperClass();
    }
    if (qualifierClass == null || !qualifierClass.equals(argumentClass)) return false;
    builder.pushExpression(qualifier)
      .unwrap(SpecialField.ENUM_ORDINAL)
      .pushExpression(arg)
      .unwrap(SpecialField.ENUM_ORDINAL)
      .mathOp(LongRangeBinOp.MINUS, call);
    return true;
  }
}
