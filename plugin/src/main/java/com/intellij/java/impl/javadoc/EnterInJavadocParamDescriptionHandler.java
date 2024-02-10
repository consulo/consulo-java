package com.intellij.java.impl.javadoc;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.EnterHandlerDelegateAdapter;
import consulo.dataContext.DataContext;
import consulo.application.ApplicationManager;
import consulo.codeEditor.CaretModel;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.util.lang.CharArrayUtil;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 5/30/11 2:08 PM
 */
@ExtensionImpl
public class EnterInJavadocParamDescriptionHandler extends EnterHandlerDelegateAdapter {

  private final JavadocHelper myHelper = JavadocHelper.getInstance();

  @Override
  public Result postProcessEnter(@Nonnull final PsiFile file, @Nonnull Editor editor, @Nonnull DataContext dataContext) {
    if (!CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER
        || !CodeStyleSettingsManager.getSettings(file.getProject()).JD_ALIGN_PARAM_COMMENTS)
    {
      return Result.Continue;
    }
    final CaretModel caretModel = editor.getCaretModel();
    final int caretOffset = caretModel.getOffset();
    final Pair<JavadocHelper.JavadocParameterInfo,List<JavadocHelper.JavadocParameterInfo>> pair
      = myHelper.parse(file, editor, caretOffset);
    if (pair.first == null || pair.first.parameterDescriptionStartPosition == null) {
      return Result.Continue;
    }

    final LogicalPosition caretPosition = caretModel.getLogicalPosition();
    final LogicalPosition nameEndPosition = pair.first.parameterNameEndPosition;
    if (nameEndPosition.line == caretPosition.line && caretPosition.column <= nameEndPosition.column) {
      return Result.Continue;
    }
    
    final int descriptionStartColumn = pair.first.parameterDescriptionStartPosition.column;
    final LogicalPosition desiredPosition = new LogicalPosition(caretPosition.line, descriptionStartColumn);
    final Document document = editor.getDocument();
    final CharSequence text = document.getCharsSequence();
    final int offsetAfterLastWs = CharArrayUtil.shiftForward(text, caretOffset, " \t");
    if (editor.offsetToLogicalPosition(offsetAfterLastWs).column < desiredPosition.column) {
      final int lineStartOffset = document.getLineStartOffset(desiredPosition.line);
      final String toInsert = StringUtil.repeat(" ", desiredPosition.column - (offsetAfterLastWs - lineStartOffset));
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          document.insertString(caretOffset, toInsert);
          PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        }
      });
    } 

    myHelper.navigate(desiredPosition, editor, file.getProject());
    return Result.Stop;
  }
}
