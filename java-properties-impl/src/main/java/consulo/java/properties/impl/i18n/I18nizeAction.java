/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.java.properties.impl.i18n;

import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

@ActionImpl(id = "I18nize", parents = @ActionParentRef(@ActionRef(id = "RefactoringMenu")))
public class I18nizeAction extends AnAction {
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeAction");

    @Override
    @RequiredUIAccess
    public void update(@Nonnull AnActionEvent e) {
        boolean active = getHandler(e) != null;
        if (ActionPlaces.isPopupPlace(e.getPlace())) {
            e.getPresentation().setVisible(active);
        }
        else {
            e.getPresentation().setEnabled(active);
        }
    }

    @Nullable
    @RequiredReadAction
    public static I18nQuickFixHandler getHandler(AnActionEvent e) {
        Editor editor = getEditor(e);
        if (editor == null) {
            return null;
        }

        PsiFile psiFile = e.getData(PsiFile.KEY);
        if (psiFile == null) {
            return null;
        }

        TextRange range = JavaI18nUtil.getSelectedRange(editor, psiFile);
        if (range == null) {
            return null;
        }

        PsiLiteralExpression literalExpression = getEnclosingStringLiteral(psiFile, editor);
        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        if (element == null) {
            return null;
        }
        if (I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(element) != null) {
            return new I18nizeConcatenationQuickFix();
        }
        else if (literalExpression != null && literalExpression.getTextRange().contains(range)) {
            return new I18nizeQuickFix();
        }

        for (I18nizeHandlerProvider handlerProvider : I18nizeHandlerProvider.EP_NAME.getExtensionList()) {
            I18nQuickFixHandler handler = handlerProvider.getHandler(psiFile, editor, range);
            if (handler != null) {
                return handler;
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static PsiLiteralExpression getEnclosingStringLiteral(PsiFile psiFile, Editor editor) {
        PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
        if (psiElement == null) {
            return null;
        }
        PsiLiteralExpression expression = PsiTreeUtil.getParentOfType(psiElement, PsiLiteralExpression.class);
        if (expression == null || !(expression.getValue() instanceof String)) {
            return null;
        }
        return expression;
    }

    private static Editor getEditor(AnActionEvent e) {
        return e.getData(Editor.KEY);
    }

    @RequiredUIAccess
    public static void doI18nSelectedString(
        @Nonnull Project project,
        @Nonnull Editor editor,
        @Nonnull PsiFile psiFile,
        @Nonnull I18nQuickFixHandler handler
    ) {
        try {
            handler.checkApplicability(psiFile, editor);
        }
        catch (IncorrectOperationException ex) {
            CommonRefactoringUtil.showErrorHint(project, editor, ex.getMessage(), CodeInsightLocalize.i18nizeErrorTitle().get(), null);
            return;
        }

        JavaI18nizeQuickFixDialog dialog = handler.createDialog(project, editor, psiFile);
        if (dialog == null) {
            return;
        }
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) {
            return;
        }
        Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
        for (PropertiesFile file : propertiesFiles) {
            if (!FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile())) {
                return;
            }
        }

        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(CodeInsightLocalize.quickfixI18nCommandName())
            .groupId(project)
            .inWriteAction()
            .run(() -> {
                try {
                    handler.performI18nization(
                        psiFile,
                        editor,
                        dialog.getLiteralExpression(),
                        propertiesFiles,
                        dialog.getKey(),
                        StringUtil.unescapeStringCharacters(dialog.getValue()),
                        dialog.getI18nizedText(),
                        dialog.getParameters(),
                        dialog.getPropertyCreationHandler()
                    );
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            });
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Editor editor = getEditor(e);
        Project project = editor.getProject();
        assert project != null;
        PsiFile psiFile = e.getData(PsiFile.KEY);
        if (psiFile == null) {
            return;
        }
        I18nQuickFixHandler handler = getHandler(e);
        if (handler == null) {
            return;
        }

        doI18nSelectedString(project, editor, psiFile, handler);
    }
}
