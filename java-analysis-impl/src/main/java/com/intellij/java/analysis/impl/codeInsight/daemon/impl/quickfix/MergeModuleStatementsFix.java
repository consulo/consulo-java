/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Pavel.Dolgov
 */
public abstract class MergeModuleStatementsFix<T extends PsiElement> extends LocalQuickFixAndIntentionActionOnPsiElement {
    protected MergeModuleStatementsFix(@Nonnull PsiJavaModule javaModule) {
        super(javaModule);
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        return PsiUtil.isLanguageLevel9OrHigher(file);
    }

    @Override
    @RequiredWriteAction
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nullable Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        if (startElement instanceof PsiJavaModule javaModule) {
            List<T> statementsToMerge = getStatementsToMerge(javaModule);
            LOG.assertTrue(!statementsToMerge.isEmpty());

            String tempModuleText =
                PsiKeyword.MODULE + " " + javaModule.getName() + " {" + getReplacementText(statementsToMerge) + "}";
            PsiJavaModule tempModule = JavaPsiFacade.getInstance(project).getElementFactory().createModuleFromText(tempModuleText);

            List<T> tempStatements = getStatementsToMerge(tempModule);
            LOG.assertTrue(!tempStatements.isEmpty());
            T replacement = tempStatements.get(0);

            T firstStatement = statementsToMerge.get(0);
            CommentTracker commentTracker = new CommentTracker();
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            PsiElement resultingStatement = codeStyleManager.reformat(commentTracker.replace(firstStatement, replacement));

            for (int i = 1; i < statementsToMerge.size(); i++) {
                T statement = statementsToMerge.get(i);
                commentTracker.delete(statement);
            }
            commentTracker.insertCommentsBefore(resultingStatement);

            if (editor != null) {
                int offset = resultingStatement.getTextRange().getEndOffset();
                editor.getCaretModel().moveToOffset(offset);
            }
        }
    }

    @Nonnull
    protected abstract String getReplacementText(List<T> statementsToMerge);

    @Nonnull
    protected abstract List<T> getStatementsToMerge(@Nonnull PsiJavaModule javaModule);

    @Nonnull
    protected static String joinUniqueNames(@Nonnull List<String> names) {
        return names.stream().distinct().collect(Collectors.joining(","));
    }

    @Nullable
    public static MergeModuleStatementsFix createFix(@Nullable PsiElement statement) {
        if (statement instanceof PsiPackageAccessibilityStatement packageAccessibilityStmt) {
            return MergePackageAccessibilityStatementsFix.createFix(packageAccessibilityStmt);
        }
        else if (statement instanceof PsiProvidesStatement providesStmt) {
            return MergeProvidesStatementsFix.createFix(providesStmt);
        }
        return null;
    }
}
