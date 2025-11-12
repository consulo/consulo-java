/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class InspectionGadgetsFix implements LocalQuickFix {
    public static final InspectionGadgetsFix[] EMPTY_ARRAY = {};

    private boolean myOnTheFly = false;

    @Override
    @RequiredWriteAction
    public final void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement problemElement = descriptor.getPsiElement();
        if (problemElement == null || !problemElement.isValid()) {
            return;
        }
        if (isQuickFixOnReadOnlyFile(problemElement)) {
            return;
        }
        try {
            doFix(project, descriptor);
        }
        catch (IncorrectOperationException e) {
            Class<? extends InspectionGadgetsFix> aClass = getClass();
            String className = aClass.getName();
            Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    @RequiredWriteAction
    protected abstract void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException;

    @RequiredWriteAction
    protected static void deleteElement(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        element.delete();
    }

    @RequiredWriteAction
    protected static void replaceExpression(@Nonnull PsiExpression expression, @Nonnull String newExpressionText)
        throws IncorrectOperationException {
        Project project = expression.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiExpression newExpression = factory.createExpressionFromText(newExpressionText, expression);
        PsiElement replacementExpression = expression.replace(newExpression);
        CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
        styleManager.reformat(replacementExpression);
    }

    @RequiredWriteAction
    protected static void replaceExpressionWithReferenceTo(@Nonnull PsiExpression expression, @Nonnull PsiMember target)
        throws IncorrectOperationException {
        Project project = expression.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiReferenceExpression newExpression = (PsiReferenceExpression) factory.createExpressionFromText("xxx", expression);
        PsiReferenceExpression replacementExpression = (PsiReferenceExpression) expression.replace(newExpression);
        PsiElement element = replacementExpression.bindToElement(target);
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
        styleManager.shortenClassReferences(element);
    }

    @RequiredWriteAction
    protected static void replaceExpressionAndShorten(@Nonnull PsiExpression expression, @Nonnull String newExpressionText)
        throws IncorrectOperationException {
        Project project = expression.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiExpression newExpression = factory.createExpressionFromText(newExpressionText, expression);
        PsiElement replacementExp = expression.replace(newExpression);
        JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
        javaCodeStyleManager.shortenClassReferences(replacementExp);
        CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
        styleManager.reformat(replacementExp);
    }

    @RequiredWriteAction
    protected static void replaceStatement(@Nonnull PsiStatement statement, @Nonnull String newStatementText)
        throws IncorrectOperationException {
        Project project = statement.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiStatement newStatement = factory.createStatementFromText(newStatementText, statement);
        PsiElement replacementExp = statement.replace(newStatement);
        CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
        styleManager.reformat(replacementExp);
    }

    @RequiredWriteAction
    protected static void replaceStatementAndShortenClassNames(@Nonnull PsiStatement statement, @Nonnull String newStatementText)
        throws IncorrectOperationException {
        Project project = statement.getProject();
        CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
        JavaCodeStyleManager javaStyleManager = JavaCodeStyleManager.getInstance(project);
        /*
        if (JspPsiUtil.isInJspFile(statement)) {
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            JspFile file = JspPsiUtil.getJspFile(statement);
            Document document = documentManager.getDocument(file);
            if (document == null) {
                return;
            }
            documentManager.doPostponedOperationsAndUnblockDocument(document);
            TextRange textRange = statement.getTextRange();
            document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), newStatementText);
            documentManager.commitDocument(document);
            JspxFileViewProvider viewProvider = file.getViewProvider();
            PsiElement elementAt = viewProvider.findElementAt(textRange.getStartOffset(), StdLanguages.JAVA);
            if (elementAt == null) {
                return;
            }
            int endOffset = textRange.getStartOffset() + newStatementText.length();
            while (elementAt.getTextRange().getEndOffset() < endOffset || !(elementAt instanceof PsiStatement)) {
                elementAt = elementAt.getParent();
                if (elementAt == null) {
                    LOG.error("Cannot decode statement");
                    return;
                }
            }
            PsiStatement newStatement = (PsiStatement)elementAt;
            javaStyleManager.shortenClassReferences(newStatement);
            TextRange newTextRange = newStatement.getTextRange();
            Language baseLanguage = viewProvider.getBaseLanguage();
            PsiFile element = viewProvider.getPsi(baseLanguage);
            if (element != null) {
                styleManager.reformatRange(element, newTextRange.getStartOffset(), newTextRange.getEndOffset());
            }
        }
        else */
        {
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiElementFactory factory = facade.getElementFactory();
            PsiStatement newStatement = factory.createStatementFromText(newStatementText, statement);
            newStatement = (PsiStatement) statement.replace(newStatement);
            javaStyleManager.shortenClassReferences(newStatement);
            styleManager.reformat(newStatement);
        }
    }

    protected boolean isQuickFixOnReadOnlyFile(PsiElement problemElement) {
        PsiFile containingPsiFile = problemElement.getContainingFile();
        if (containingPsiFile == null) {
            return false;
        }
        VirtualFile virtualFile = containingPsiFile.getVirtualFile();
        Project project = problemElement.getProject();
        ReadonlyStatusHandler handler = ReadonlyStatusHandler.getInstance(project);
        ReadonlyStatusHandler.OperationStatus status = handler.ensureFilesWritable(virtualFile);
        return status.hasReadonlyFiles();
    }

    @RequiredReadAction
    protected static String getElementText(
        @Nonnull PsiElement element,
        @Nullable PsiElement elementToReplace,
        @Nullable String replacement
    ) {
        StringBuilder out = new StringBuilder();
        getElementText(element, elementToReplace, replacement, out);
        return out.toString();
    }

    @RequiredReadAction
    private static void getElementText(
        @Nonnull PsiElement element,
        @Nullable PsiElement elementToReplace,
        @Nullable String replacement,
        @Nonnull StringBuilder out
    ) {
        if (element.equals(elementToReplace)) {
            out.append(replacement);
            return;
        }
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
            out.append(element.getText());
            return;
        }
        for (PsiElement child : children) {
            getElementText(child, elementToReplace, replacement, out);
        }
    }

    public final void setOnTheFly(boolean onTheFly) {
        myOnTheFly = onTheFly;
    }

    public final boolean isOnTheFly() {
        return myOnTheFly;
    }
}