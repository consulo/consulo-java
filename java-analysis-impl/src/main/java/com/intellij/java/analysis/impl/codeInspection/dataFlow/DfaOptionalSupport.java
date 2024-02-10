// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.impl.codeInspection.util.OptionalUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.CommonQuickFixBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author anet, peter
 */
public final class DfaOptionalSupport {

  @Nullable
  public static LocalQuickFix registerReplaceOptionalOfWithOfNullableFix(@Nonnull PsiExpression qualifier) {
    final PsiMethodCallExpression call = findCallExpression(qualifier);
    final PsiMethod method = call == null ? null : call.resolveMethod();
    final PsiClass containingClass = method == null ? null : method.getContainingClass();
    if (containingClass != null && "of".equals(method.getName())) {
      final String qualifiedName = containingClass.getQualifiedName();
      if (CommonClassNames.JAVA_UTIL_OPTIONAL.equals(qualifiedName)) {
        return new ReplaceOptionalCallFix("ofNullable", false);
      }
      if (OptionalUtil.GUAVA_OPTIONAL.equals(qualifiedName)) {
        return new ReplaceOptionalCallFix("fromNullable", false);
      }
    }
    return null;
  }

  private static PsiMethodCallExpression findCallExpression(@Nonnull PsiElement anchor) {
    final PsiElement argList = PsiUtil.skipParenthesizedExprUp(anchor).getParent();
    if (argList instanceof PsiExpressionList) {
      final PsiElement parent = argList.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return (PsiMethodCallExpression) parent;
      }
    }
    return null;
  }

  @Nullable
  public static LocalQuickFix createReplaceOptionalOfNullableWithEmptyFix(@Nonnull PsiElement anchor) {
    final PsiMethodCallExpression parent = findCallExpression(anchor);
    if (parent == null)
      return null;
    boolean jdkOptional = OptionalUtil.JDK_OPTIONAL_OF_NULLABLE.test(parent);
    return new ReplaceOptionalCallFix(jdkOptional ? "empty" : "absent", true);
  }

  @Nullable
  public static LocalQuickFix createReplaceOptionalOfNullableWithOfFix(@Nonnull PsiElement anchor) {
    final PsiMethodCallExpression parent = findCallExpression(anchor);
    if (parent == null)
      return null;
    return new ReplaceOptionalCallFix("of", false);
  }

  /**
   * Creates a DfType which represents present or absent optional (non-null)
   *
   * @param present whether the value should be present
   * @return a DfType representing an Optional
   */
  @Nonnull
  public static DfType getOptionalValue(boolean present) {
    DfType valueType = present ? DfTypes.NOT_NULL_OBJECT : DfTypes.NULL;
    return SpecialField.OPTIONAL_VALUE.asDfType(valueType);
  }

  private static class ReplaceOptionalCallFix implements LocalQuickFix {
    private final String myTargetMethodName;
    private final boolean myClearArguments;

    ReplaceOptionalCallFix(final String targetMethodName, boolean clearArguments) {
      myTargetMethodName = targetMethodName;
      myClearArguments = clearArguments;
    }

    @Nonnull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "." + myTargetMethodName + "()");
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      final PsiMethodCallExpression
          methodCallExpression = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (methodCallExpression != null) {
        ExpressionUtils.bindCallTo(methodCallExpression, myTargetMethodName);
        if (myClearArguments) {
          PsiExpressionList argList = methodCallExpression.getArgumentList();
          PsiExpression[] args = argList.getExpressions();
          if (args.length > 0) {
            argList.deleteChildRange(args[0], args[args.length - 1]);
          }
        }
      }
    }
  }
}
