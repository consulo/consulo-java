// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.analysis.impl.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.java.impl.ig.psiutils.HighlightUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;

public class WrapWithMutableCollectionFix implements LocalQuickFix {
    private final String myVariableName;
    private final String myCollectionName;
    private final boolean myOnTheFly;

    public WrapWithMutableCollectionFix(String variableName, String collectionName, boolean onTheFly) {
        myVariableName = variableName;
        myCollectionName = collectionName;
        myOnTheFly = onTheFly;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Nonnull
    @Override
    public String getName() {
        return "Wrap '" + myVariableName + "' with '" + StringUtil.getShortName(myCollectionName) + "'";
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Nonnull
    @Override
    public String getFamilyName() {
        return "Wrap with mutable collection";
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiLocalVariable variable = getVariable(descriptor.getStartElement());
        if (variable == null) {
            return;
        }
        PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
            return;
        }
        PsiClassType type = ObjectUtil.tryCast(variable.getType(), PsiClassType.class);
        if (type == null) {
            return;
        }
        String typeParameters = "";
        if (myCollectionName.equals(CommonClassNames.JAVA_UTIL_HASH_MAP)) {
            PsiType keyParameter = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, false);
            PsiType valueParameter = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);
            if (keyParameter != null && valueParameter != null) {
                typeParameters = "<" + keyParameter.getCanonicalText() + "," + valueParameter.getCanonicalText() + ">";
            }
        }
        else {
            PsiType elementParameter = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_LANG_ITERABLE, 0, false);
            if (elementParameter != null) {
                typeParameters = "<" + elementParameter.getCanonicalText() + ">";
            }
        }
        CommentTracker ct = new CommentTracker();
        PsiElement replacement =
            ct.replaceAndRestoreComments(initializer, "new " + myCollectionName + typeParameters + "(" + ct.text(initializer) + ")");
        RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(replacement);
        if (myOnTheFly) {
            HighlightUtils.highlightElement(replacement);
        }
    }

    @Nullable
    @RequiredReadAction
    public static WrapWithMutableCollectionFix createFix(@Nonnull PsiElement anchor, boolean onTheFly) {
        PsiLocalVariable variable = getVariable(anchor);
        if (variable == null) {
            return null;
        }
        PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
            return null;
        }
        String wrapper = getWrapperByType(variable.getType());
        if (wrapper == null) {
            return null;
        }
        PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
        if (block == null) {
            return null;
        }
        if (!HighlightControlFlowUtil.isEffectivelyFinal(variable, block, null)) {
            return null;
        }
        return new WrapWithMutableCollectionFix(variable.getName(), wrapper, onTheFly);
    }

    @Nullable
    @RequiredReadAction
    private static PsiLocalVariable getVariable(@Nonnull PsiElement anchor) {
        if (anchor.getParent() instanceof PsiReferenceExpression refExpr && refExpr.getParent() instanceof PsiCallExpression) {
            anchor = refExpr.getQualifierExpression();
        }
        return anchor instanceof PsiExpression expression ? ExpressionUtils.resolveLocalVariable(expression) : null;
    }

    @Contract("null -> null")
    @Nullable
    private static String getWrapperByType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return null;
        }
        PsiClass aClass = classType.resolve();
        if (aClass == null) {
            return null;
        }
        String name = aClass.getQualifiedName();
        if (name == null) {
            return null;
        }
        return switch (name) {
            case CommonClassNames.JAVA_LANG_ITERABLE, CommonClassNames.JAVA_UTIL_COLLECTION, CommonClassNames.JAVA_UTIL_LIST ->
                CommonClassNames.JAVA_UTIL_ARRAY_LIST;
            case CommonClassNames.JAVA_UTIL_SET -> CommonClassNames.JAVA_UTIL_HASH_SET;
            case CommonClassNames.JAVA_UTIL_MAP -> CommonClassNames.JAVA_UTIL_HASH_MAP;
            default -> null;
        };
    }
}
