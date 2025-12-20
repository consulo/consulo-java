/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.template;

import com.intellij.java.impl.ide.highlighter.JavaFileHighlighter;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import consulo.document.Document;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.template.context.BaseTemplateContextType;
import consulo.language.editor.template.context.TemplateActionContext;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiJavaElement;
import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.StandardPatterns.instanceOf;

public abstract class JavaCodeContextType extends BaseTemplateContextType {

    protected JavaCodeContextType(@Nonnull String id, @Nonnull LocalizeValue presentableName, @Nullable Class<? extends BaseTemplateContextType> baseContextType) {
        super(id, presentableName, baseContextType);
    }

    @Override
    public boolean isInContext(@Nonnull TemplateActionContext templateActionContext) {
        PsiFile file = templateActionContext.getFile();
        int offset = templateActionContext.getStartOffset();

        if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(JavaLanguage.INSTANCE)) {
            PsiElement element = file.findElementAt(offset);
            if (element instanceof PsiWhiteSpace) {
                return false;
            }
            return element != null && isInContext(element);
        }

        return false;
    }

    @Override
    public boolean isInContext(@Nonnull PsiFile file, int offset) {
        if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(JavaLanguage.INSTANCE)) {
            PsiElement element = file.findElementAt(offset);
            if (element instanceof PsiWhiteSpace) {
                return false;
            }
            return element != null && isInContext(element);
        }

        return false;
    }

    protected abstract boolean isInContext(@Nonnull PsiElement element);

    @Nonnull
    @Override
    public SyntaxHighlighter createHighlighter() {
        return new JavaFileHighlighter();
    }

    @Override
    public Document createDocument(CharSequence text, Project project) {
        if (project == null) {
            return super.createDocument(text, project);
        }
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
        JavaCodeFragment fragment = factory.createCodeBlockCodeFragment((String) text, psiFacade.findPackage(""), true);
        DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(fragment, false);
        return PsiDocumentManager.getInstance(project).getDocument(fragment);
    }

    protected static boolean isAfterExpression(PsiElement element) {
        ProcessingContext context = new ProcessingContext();
        if (psiJavaElement().withAncestor(1, instanceOf(PsiExpression.class)).afterLeaf(psiElement().withAncestor(1, psiElement(PsiExpression.class).save("prevExpr"))).accepts(element, context)) {
            PsiExpression prevExpr = (PsiExpression) context.get("prevExpr");
            if (prevExpr.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) {
                return true;
            }
        }

        return false;
    }
}
