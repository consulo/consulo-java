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

/**
 * @author cdr
 */
package consulo.java.properties.impl.i18n;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.document.util.TextRange;
import consulo.java.properties.impl.psi.PropertyCreationHandler;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

public class I18nizeQuickFix implements LocalQuickFix, I18nQuickFixHandler {
  private static final Logger LOG = Logger.getInstance(I18nizeQuickFix.class);
  private TextRange mySelectionRange;

  @Override
  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    // do it later because the fix was called inside writeAction
    project.getApplication().invokeLater(() -> doFix(descriptor, project));
  }

  @Override
  @Nonnull
  public LocalizeValue getName() {
    return CodeInsightLocalize.inspectionI18nQuickfix();
  }

  @Override
  @RequiredReadAction
  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    PsiLiteralExpression literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    if (literalExpression != null) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) return;
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      TextRange textRange = literalExpression.getTextRange();
      if (textRange.contains(start) && textRange.contains(end)) {
        mySelectionRange = new TextRange(start, end);
        return;
      }
    }
    LocalizeValue message = CodeInsightLocalize.i18nizeErrorMessage();
    throw new IncorrectOperationException(message.get());
  }

  @Override
  public void performI18nization(
    final PsiFile psiFile,
    final Editor editor,
    PsiLiteralExpression literalExpression,
    Collection<PropertiesFile> propertiesFiles,
    String key, String value, String i18nizedText,
    PsiExpression[] parameters,
    final PropertyCreationHandler propertyCreationHandler
  ) throws IncorrectOperationException {
    Project project = psiFile.getProject();
    propertyCreationHandler.createProperty(project, propertiesFiles, key, value, parameters);
    try {
      final PsiElement newExpression = doReplacementInJava(psiFile, editor,literalExpression, i18nizedText);
      reformatAndCorrectReferences(newExpression);
    }
    catch (IncorrectOperationException e) {
      Messages.showErrorDialog(
        project,
        CodeInsightLocalize.inspectionI18nExpressionIsInvalidErrorMessage().get(),
        CodeInsightLocalize.inspectionErrorDialogTitle().get()
      );
    }
  }

  @Override
  @RequiredReadAction
  public JavaI18nizeQuickFixDialog createDialog(Project project, Editor editor, PsiFile psiFile) {
    final PsiLiteralExpression literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    return createDialog(project, psiFile, literalExpression);
  }

  @RequiredReadAction
  private void doFix(final ProblemDescriptor descriptor, final Project project) {
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)descriptor.getPsiElement();
    final PsiFile psiFile = literalExpression.getContainingFile();
    if (!JavaI18nizeQuickFixDialog.isAvailable(psiFile)) {
      return;
    }
    final JavaI18nizeQuickFixDialog dialog = createDialog(project, psiFile, literalExpression);
    dialog.show();
    if (!dialog.isOK()) return;
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();

    if (!FileModificationService.getInstance().preparePsiElementForWrite(literalExpression)) return;
    for (PropertiesFile file : propertiesFiles) {
      if (file.findPropertyByKey(dialog.getKey()) == null &&
          !FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile())) return;
    }

    CommandProcessor.getInstance().executeCommand(
      project,
      () -> project.getApplication().runWriteAction(() -> {
        try {
          performI18nization(
            psiFile,
            PsiUtilBase.findEditor(psiFile),
            dialog.getLiteralExpression(),
            propertiesFiles,
            dialog.getKey(),
            dialog.getValue(),
            dialog.getI18nizedText(),
            dialog.getParameters(),
            dialog.getPropertyCreationHandler()
          );
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }),
      CodeInsightLocalize.quickfixI18nCommandName().get(),
      project
    );
  }

  @RequiredReadAction
  protected PsiElement doReplacementInJava(
    @Nonnull final PsiFile psiFile,
    final Editor editor,
    final PsiLiteralExpression literalExpression,
    String i18nizedText
  ) throws IncorrectOperationException {
    return replaceStringLiteral(literalExpression, i18nizedText);
  }

  private static void reformatAndCorrectReferences(PsiElement newExpression) throws IncorrectOperationException {
    final Project project = newExpression.getProject();
    newExpression = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newExpression);
    CodeStyleManager.getInstance(project).reformat(newExpression);
  }

  @RequiredUIAccess
  @RequiredReadAction
  protected JavaI18nizeQuickFixDialog createDialog(final Project project, final PsiFile context, final PsiLiteralExpression literalExpression) {
    String value = (String)literalExpression.getValue();
    if (mySelectionRange != null) {
      TextRange literalRange = literalExpression.getTextRange();
      TextRange intersection = literalRange.intersection(mySelectionRange);
      value = literalExpression.getText().substring(
        intersection.getStartOffset() - literalRange.getStartOffset(),
        intersection.getEndOffset() - literalRange.getStartOffset()
      );
    }
    value = StringUtil.escapeStringCharacters(value);
    return new JavaI18nizeQuickFixDialog(project, context, literalExpression, value, null, true, true);
  }

  @Nullable
  @RequiredReadAction
  private static PsiBinaryExpression breakStringLiteral(PsiLiteralExpression literalExpression, int offset) throws IncorrectOperationException {
    TextRange literalRange = literalExpression.getTextRange();
    PsiElementFactory factory = JavaPsiFacade.getInstance(literalExpression.getProject()).getElementFactory();
    if (literalRange.getStartOffset()+1 < offset && offset < literalRange.getEndOffset()-1) {
      PsiBinaryExpression expression = (PsiBinaryExpression)factory.createExpressionFromText("a + b", literalExpression);
      String value = (String)literalExpression.getValue();
      int breakIndex = offset - literalRange.getStartOffset()-1;
      String lsubstring = value.substring(0, breakIndex);
      expression.getLOperand().replace(factory.createExpressionFromText("\"" + lsubstring + "\"", literalExpression));
      String rsubstring = value.substring(breakIndex);
      expression.getROperand().replace(factory.createExpressionFromText("\"" + rsubstring + "\"", literalExpression));
      return (PsiBinaryExpression)literalExpression.replace(expression);
    }

    return null;
  }

  @RequiredReadAction
  private PsiElement replaceStringLiteral(PsiLiteralExpression literalExpression, String i18nizedText) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(literalExpression.getProject()).getElementFactory();
    if (mySelectionRange != null) {
      try {
        PsiBinaryExpression binaryExpression = breakStringLiteral(literalExpression, mySelectionRange.getEndOffset());
        if (binaryExpression != null) {
          literalExpression = (PsiLiteralExpression)binaryExpression.getLOperand();
        }
        binaryExpression = breakStringLiteral(literalExpression, mySelectionRange.getStartOffset());
        if (binaryExpression != null) {
          literalExpression = (PsiLiteralExpression)binaryExpression.getROperand();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    PsiExpression expression = factory.createExpressionFromText(i18nizedText, literalExpression);
    return literalExpression.replace(expression);
  }
}
