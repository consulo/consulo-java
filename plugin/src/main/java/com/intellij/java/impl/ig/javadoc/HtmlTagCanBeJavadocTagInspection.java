/*
 * Copyright 2011-2012 Bas Leijdekkers
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
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocToken;
import com.intellij.java.language.psi.util.PsiUtil;
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
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class HtmlTagCanBeJavadocTagInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.htmlTagCanBeJavadocTagDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.htmlTagCanBeJavadocTagProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        int offset = ((Integer) infos[0]).intValue();
        return new HtmlTagCanBeJavaDocTagFix(offset);
    }

    private static class HtmlTagCanBeJavaDocTagFix extends InspectionGadgetsFix {
        private final int startIndex;

        public HtmlTagCanBeJavaDocTagFix(int startIndex) {
            this.startIndex = startIndex;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.htmlTagCanBeJavadocTagQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiDocComment comment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
            if (comment == null) {
                return;
            }
            @NonNls StringBuilder newCommentText = new StringBuilder();
            buildNewCommentText(comment, element, false, newCommentText);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiDocComment newComment = factory.createDocCommentFromText(newCommentText.toString());
            comment.replace(newComment);
        }

        private boolean buildNewCommentText(
            PsiElement element, PsiElement elementToReplace, boolean missingEndTag,
            @NonNls StringBuilder newCommentText
        ) {
            PsiElement[] children = element.getChildren();
            if (children.length != 0) {
                for (PsiElement child : children) {
                    missingEndTag = buildNewCommentText(child, elementToReplace, missingEndTag, newCommentText);
                }
                return missingEndTag;
            }
            @NonNls String text = element.getText();
            if (element != elementToReplace) {
                if (missingEndTag) {
                    int endIndex = text.indexOf("</code>");
                    if (endIndex >= 0) {
                        String codeText = text.substring(0, endIndex);
                        newCommentText.append(codeText);
                        newCommentText.append('}');
                        newCommentText.append(text.substring(endIndex + 7));
                        return false;
                    }
                }
                newCommentText.append(text);
            }
            else {
                newCommentText.append(text.substring(0, startIndex));
                newCommentText.append("{@code ");
                int endIndex = text.indexOf("</code>", startIndex);
                if (endIndex >= 0) {
                    String codeText = text.substring(startIndex + 6, endIndex);
                    newCommentText.append(codeText);
                    //StringUtil.replace(codeText, "}", "&#125;"));
                    newCommentText.append('}');
                    newCommentText.append(text.substring(endIndex + 7));
                }
                else {
                    String codeText = text.substring(startIndex + 6);
                    newCommentText.append(codeText);
                    //StringUtil.replace(codeText, "}", "&#125;"));
                    return true;
                }
            }
            return missingEndTag;
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new HtmlTagCanBeJavaDocTagVisitor();
    }

    private static class HtmlTagCanBeJavaDocTagVisitor extends BaseInspectionVisitor {

        @Override
        public void visitDocToken(PsiDocToken token) {
            super.visitDocToken(token);
            if (!PsiUtil.isLanguageLevel5OrHigher(token)) {
                return;
            }
            IElementType tokenType = token.getTokenType();
            if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
                return;
            }
            @NonNls String text = token.getText();
            int startIndex = 0;
            while (true) {
                startIndex = text.indexOf("<code>", startIndex);
                if (startIndex < 0) {
                    return;
                }
                if (hasMatchingCloseTag(token, startIndex + 6)) {
                    registerErrorAtOffset(token, startIndex, 6, Integer.valueOf(startIndex));
                }
                startIndex++;
            }
        }

        private static boolean hasMatchingCloseTag(PsiElement element, int offset) {
            String text = element.getText();
            int endOffset1 = text.indexOf("</code>", offset);
            if (endOffset1 >= 0) {
                int startOffset1 = text.indexOf("<code>", offset);
                return startOffset1 < 0 || startOffset1 > endOffset1;
            }
            PsiElement sibling = element.getNextSibling();
            while (sibling != null) {
                String text1 = sibling.getText();
                int endOffset = text1.indexOf("</code>");
                if (endOffset >= 0) {
                    int startOffset = text1.indexOf("<code>");
                    return startOffset < 0 || startOffset > endOffset;
                }
                sibling = sibling.getNextSibling();
            }
            return false;
        }
    }
}
