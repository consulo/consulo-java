/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.codeEditor.Editor;
import consulo.language.ast.IElementType;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inject.InjectedEditorManager;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.intention.SuppressIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;

/**
 * User: cdr
 */
class SuppressByCommentOutAction extends SuppressIntentionAction implements SyntheticIntentionAction {
  private final String nonNlsCommentPattern;

  SuppressByCommentOutAction(String nonNlsCommentPattern) {
    this.nonNlsCommentPattern = nonNlsCommentPattern;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    element = findJavaCodeUpThere(element);
    PsiFile file = element.getContainingFile();
    editor = InjectedEditorManager.getInstance(project).openEditorFor(file);
    int endOffset = element.getTextRange().getEndOffset();
    int line = editor.getDocument().getLineNumber(endOffset);
    int lineEndOffset = editor.getDocument().getLineEndOffset(line);

    PsiComment comment = PsiTreeUtil.findElementOfClassAtOffset(file, lineEndOffset - 1, PsiComment.class, false);
    String prefix = "";
    boolean prefixFound = false;
    if (comment != null) {
      IElementType tokenType = comment.getTokenType();
      if (tokenType == JavaTokenType.END_OF_LINE_COMMENT) {
        prefix = StringUtil.trimStart(comment.getText(), "//") + " ";
        prefixFound = true;
      }
    }
    String commentText = "//" + prefix + nonNlsCommentPattern;
    if (prefixFound) {
      PsiComment newcom = JavaPsiFacade.getElementFactory(project).createCommentFromText(commentText, element);
      comment.replace(newcom);
    }
    else {
      editor.getDocument().insertString(lineEndOffset, " " + commentText);
    }
    DaemonCodeAnalyzer.getInstance(project).restart(); //comment replacement not necessarily rehighlights
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (!element.isValid()) {
      return false;
    }
    // find java code up there, going through injections if necessary
    return findJavaCodeUpThere(element) != null;
  }

  private static PsiElement findJavaCodeUpThere(PsiElement element) {
    while (element != null) {
      if (element.getLanguage() == JavaLanguage.INSTANCE) return element;
      element = element.getContext();
    }
    return null;
  }

  @Nonnull
  @Override
  public String getText() {
    return "Suppress with '" + nonNlsCommentPattern + "' comment";
  }
}
