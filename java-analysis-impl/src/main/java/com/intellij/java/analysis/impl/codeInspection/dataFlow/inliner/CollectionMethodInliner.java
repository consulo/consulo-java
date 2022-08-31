// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.inliner;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.CFGBuilder;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.SpecialField;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.siyeh.ig.callMatcher.CallMatcher;

import javax.annotation.Nonnull;

import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_COLLECTION;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_MAP;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class CollectionMethodInliner implements CallInliner {
  private static final CallMatcher CLEAR = anyOf(instanceCall(JAVA_UTIL_COLLECTION, "clear").parameterCount(0),
      instanceCall(JAVA_UTIL_MAP, "clear").parameterCount(0));

  @Override
  public boolean tryInlineCall(@Nonnull CFGBuilder builder, @Nonnull PsiMethodCallExpression call) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null)
      return false;
    if (CLEAR.matches(call)) {
      inlineClear(builder, qualifier);
      return true;
    }
    return false;
  }

  private static void inlineClear(@Nonnull CFGBuilder builder, @Nonnull PsiExpression qualifier) {
    DfaValueFactory factory = builder.getFactory();
    builder
        .pushExpression(qualifier)
        .unwrap(SpecialField.COLLECTION_SIZE)
        .push(factory.getInt(0))
        .assign()
        .pop()
        .pushUnknown();
  }
}
