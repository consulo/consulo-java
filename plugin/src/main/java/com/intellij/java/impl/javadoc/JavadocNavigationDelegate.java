/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.javadoc;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.EditorNavigationDelegate;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Holds javadoc-specific navigation logic.
 *
 * @author Denis Zhdanov
 * @since 5/26/11 5:22 PM
 */
@ExtensionImpl
public class JavadocNavigationDelegate implements EditorNavigationDelegate {

  private static final JavadocHelper ourHelper = JavadocHelper.getInstance();

  /**
   * Improves navigation in case of incomplete javadoc parameter descriptions.
   * <p>
   * Example:
   * <pre>
   *   /**
   *    * @param i[caret]
   *    * @param secondArgument
   *    *&#47;
   *    abstract void test(int i, int secondArgument);
   * </pre>
   * <p>
   * We expect the caret to be placed in position of parameter description start then (code style is condifured to
   * <b>align</b> parameter descriptions):
   * <pre>
   *   /**
   *    * @param i                 [caret]
   *    * @param secondArgument
   *    *&#47;
   *    abstract void test(int i, int secondArgument);
   * </pre>
   * <p>
   * or this one for non-aligned descriptions:
   * <pre>
   *   /**
   *    * @param i    [caret]
   *    * @param secondArgument
   *    *&#47;
   *    abstract void test(int i, int secondArgument);
   * </pre>
   *
   * @param editor current editor
   * @return processing result
   */
  @Nonnull
  @Override
  public Result navigateToLineEnd(@Nonnull Editor editor, @Nonnull DataContext dataContext) {
    if (!CodeInsightSettings.getInstance().SMART_END_ACTION) {
      return Result.CONTINUE;
    }

    Project project = dataContext.getData(Project.KEY);
    if (project == null) {
      return Result.CONTINUE;
    }

    Document document = editor.getDocument();
    PsiFile psiFile = dataContext.getData(PsiFile.KEY);
    if (psiFile == null) {
      psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    }
    if (psiFile == null) {
      return Result.CONTINUE;
    }

    return navigateToLineEnd(editor, psiFile);
  }

  public static Result navigateToLineEnd(@Nonnull Editor editor, @Nonnull PsiFile psiFile) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    int offset = caretModel.getOffset();

    CharSequence text = document.getCharsSequence();
    int line = caretModel.getLogicalPosition().line;
    int endLineOffset = document.getLineEndOffset(line);
    LogicalPosition endLineLogicalPosition = editor.offsetToLogicalPosition(endLineOffset);

    // Stop processing if there are non-white space symbols after the current caret position.
    int lastNonWsSymbolOffset = CharArrayUtil.shiftBackward(text, endLineOffset, " \t");
    if (lastNonWsSymbolOffset > offset || caretModel.getLogicalPosition().column > endLineLogicalPosition.column) {
      return Result.CONTINUE;
    }

    Pair<JavadocHelper.JavadocParameterInfo, List<JavadocHelper.JavadocParameterInfo>> pair = ourHelper.parse(psiFile, editor, offset);
    if (pair.first == null || pair.first.parameterDescriptionStartPosition != null) {
      return Result.CONTINUE;
    }

    LogicalPosition position = ourHelper.calculateDescriptionStartPosition(psiFile, pair.second, pair.first);
    ourHelper.navigate(position, editor, psiFile.getProject());
    return Result.STOP;
  }
}
