// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.localize.CommonQuickFixLocalize;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class ReplaceWithArgumentFix implements LocalQuickFix {
    private final String myText;
    private final int myArgNum;

    public ReplaceWithArgumentFix(PsiExpression argument, int argNum) {
        myText = PsiExpressionTrimRenderer.render(argument);
        myArgNum = argNum;
    }

    @Override
    @Nonnull
    public LocalizeValue getName() {
        return CommonQuickFixLocalize.fixReplaceWithX(myText);
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
        if (call == null) {
            return;
        }
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length <= myArgNum) {
            return;
        }
        new CommentTracker().replaceAndRestoreComments(call, args[myArgNum]);
    }
}
