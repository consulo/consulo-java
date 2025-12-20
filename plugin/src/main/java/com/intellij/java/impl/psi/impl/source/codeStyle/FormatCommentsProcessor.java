// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.psi.impl.source.codeStyle;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.PreFormatProcessor;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class FormatCommentsProcessor implements PreFormatProcessor {
  @Nonnull
  @Override
  public TextRange process(@Nonnull ASTNode element, @Nonnull TextRange range) {
    PsiElement e = SourceTreeToPsiMap.treeElementToPsi(element);
    assert e != null && e.isValid();
    PsiFile file = e.getContainingFile();
    Project project = e.getProject();
    if (!CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING ||
        element.getPsi().getContainingFile().getLanguage() != JavaLanguage.INSTANCE
        || InjectedLanguageManager.getInstance(project).isInjectedFragment(element.getPsi().getContainingFile())) {
      return range;
    }
    return formatCommentsInner(project, element, range);
  }

  /**
   * Formats PsiDocComments of current ASTNode element and all his children PsiDocComments
   */
  @Nonnull
  private static TextRange formatCommentsInner(@Nonnull Project project, @Nonnull ASTNode element, @Nonnull TextRange markedRange) {
    TextRange resultTextRange = markedRange;
    PsiElement elementPsi = element.getPsi();
    assert elementPsi.isValid();
    PsiFile file = elementPsi.getContainingFile();
    boolean shouldFormat = markedRange.contains(element.getTextRange());

    if (shouldFormat) {

      ASTNode rangeAnchor;
      // There are two possible cases:
      //   1. Given element correspond to comment's owner (e.g. field or method);
      //   2. Given element corresponds to comment itself;
      // However, doc comment formatter replaces old comment with the new one, hence, old element becomes invalid. That's why we need
      // to calculate text length delta not for the given comment element (it's invalid because removed from the AST tree) but for
      // its parent.
      if (elementPsi instanceof PsiDocComment) {
        rangeAnchor = element.getTreeParent();
      } else {
        rangeAnchor = element;
      }
      TextRange before = rangeAnchor.getTextRange();
      new CommentFormatter(file).processComment(element);
      int deltaRange = rangeAnchor.getTextRange().getLength() - before.getLength();
      resultTextRange = new TextRange(markedRange.getStartOffset(), markedRange.getEndOffset() + deltaRange);
    }


    // If element is Psi{Method, Field, DocComment} and was formatted there is no reason to continue - we formatted all possible javadocs.
    // If element is out of range its children are also out of range. So in both cases formatting is finished. It's just for optimization.
    if ((shouldFormat && (elementPsi instanceof PsiMethod || elementPsi instanceof PsiField || elementPsi instanceof PsiDocComment))
        || markedRange.getEndOffset() < element.getStartOffset()) {
      return resultTextRange;
    }

    ASTNode current = element.getFirstChildNode();
    while (current != null) {
      // When element is PsiClass its PsiDocComment is formatted up to this moment, so we didn't need to format it again.
      if (!(shouldFormat && current.getPsi() instanceof PsiDocComment && elementPsi instanceof PsiClass)) {
        resultTextRange = formatCommentsInner(project, current, resultTextRange);
      }
      current = current.getTreeNext();
    }

    return resultTextRange;
  }
}
