// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import consulo.language.dataFlow.interpreter.UnsatisfiedConditionProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiTypes;
import consulo.util.lang.ObjectUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;

public class IndexOfInliner implements CallInliner {
  private static final CallMatcher INDEX_OF = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "indexOf", "lastIndexOf")
    .parameterCount(1);

  @Override
  public boolean tryInlineCall(CFGBuilder builder, PsiMethodCallExpression call) {
    if (!INDEX_OF.test(call)) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    PsiExpression arg = call.getArgumentList().getExpressions()[0];
    DfaVariableValue res = builder.createTempVariable(PsiTypes.intType());
    RelationType relationType = RelationType.LT;
    if (TypeUtils.isJavaLangString(arg.getType())) {
       String strArg = ObjectUtil.tryCast(ExpressionUtils.computeConstantExpression(arg), String.class);
      if (strArg == null || strArg.isEmpty()) {
        // a.lastIndexOf(b) could equal to a.length() if b is an empty string
        // also, a.indexOf(b) could be equal to a.length() if both a and b are empty
        relationType = RelationType.LE;
      }
    }
    builder.pushExpression(qualifier)
      .dup()
      .pushExpression(arg)
      .call(call)
      .assignTo(res)
      .splice(2, 0, 0, 1)
      .unwrap(SpecialField.STRING_LENGTH)
      .compare(relationType)
      .ensure(RelationType.EQ, DfTypes.TRUE, new UnsatisfiedConditionProblem() {
      }, null)
      .pop();
    return true;
  }
}
