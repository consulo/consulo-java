/*
 * Copyright 2009-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.javadoc;

import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocToken;
import com.intellij.java.language.psi.javadoc.PsiInlineDocTag;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class UnnecessaryInheritDocInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryInheritDocDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessaryInheritDocProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryInheritDocFix();
    }

    private static class UnnecessaryInheritDocFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessaryInheritDocQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiDocTag)) {
                return;
            }
            PsiDocTag docTag = (PsiDocTag) element;
            PsiDocComment docComment = docTag.getContainingComment();
            docComment.delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryInheritDocVisitor();
    }

    private static class UnnecessaryInheritDocVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitDocTag(PsiDocTag tag) {
            if (!(tag instanceof PsiInlineDocTag)) {
                return;
            }
            @NonNls String name = tag.getName();
            if (!"inheritDoc".equals(name)) {
                return;
            }
            PsiDocComment docComment = tag.getContainingComment();
            if (docComment == null) {
                return;
            }
            PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(
                docComment, PsiDocToken.class);
            if (docTokens == null) {
                return;
            }
            for (PsiDocToken docToken : docTokens) {
                IElementType tokenType = docToken.getTokenType();
                if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
                    continue;
                }
                if (!StringUtil.isEmptyOrSpaces(docToken.getText())) {
                    return;
                }
            }
            registerError(tag);
        }
    }
}
