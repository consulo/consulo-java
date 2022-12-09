/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.java.impl.lang.java;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.psi.formatter.java.AbstractJavaBlock;
import com.intellij.java.impl.psi.impl.source.codeStyle.PsiBasedFormatterModelWithShiftIndentInside;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.ide.impl.psi.formatter.FormattingDocumentModelImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.*;
import consulo.language.impl.ast.FileElement;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ExtensionImpl
public class JavaFormattingModelBuilder implements FormattingModelBuilderEx {
  private static final Logger LOG = Logger.getInstance(JavaFormattingModelBuilder.class);

  @Override
  @Nonnull
  public FormattingModel createModel(FormattingContext context) {
    CodeStyleSettings settings = context.getCodeStyleSettings();
    PsiElement element = context.getPsiElement();
    FormattingMode formattingMode = context.getFormattingMode();
    final FileElement fileElement = TreeUtil.getFileElement((TreeElement) SourceTreeToPsiMap.psiElementToTree(element));
    LOG.assertTrue(fileElement != null, "File element should not be null for " + element);
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    JavaCodeStyleSettings customJavaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    Block block = AbstractJavaBlock.newJavaBlock(fileElement, commonSettings, customJavaSettings, formattingMode);
    FormattingDocumentModelImpl model = FormattingDocumentModelImpl.createOn(element.getContainingFile());
    return new PsiBasedFormatterModelWithShiftIndentInside(element.getContainingFile(), block, model);
  }

  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return doGetRangeAffectingIndent(elementAtOffset);
  }

  @Nullable
  public static TextRange doGetRangeAffectingIndent(final ASTNode elementAtOffset) {
    ASTNode current = elementAtOffset;
    current = findNearestExpressionParent(current);
    if (current == null) {
      if (elementAtOffset.getElementType() == TokenType.WHITE_SPACE) {
        ASTNode prevElement = elementAtOffset.getTreePrev();
        if (prevElement == null) {
          return elementAtOffset.getTextRange();
        } else {
          ASTNode prevExpressionParent = findNearestExpressionParent(prevElement);
          if (prevExpressionParent == null) {
            return elementAtOffset.getTextRange();
          } else {
            return new TextRange(prevExpressionParent.getTextRange().getStartOffset(), elementAtOffset.getTextRange().getEndOffset());
          }
        }
      } else {
        // Look at IDEA-65777 for example of situation when it's necessary to expand element range in case of invalid syntax.
        return combineWithErrorElementIfPossible(elementAtOffset);
      }
    } else {
      return current.getTextRange();
    }
  }

  /**
   * Checks if previous non-white space leaf of the given node is error element and combines formatting range relevant for it
   * with the range of the given node.
   *
   * @param node target node
   * @return given node range if there is no error-element before it; combined range otherwise
   */
  @Nullable
  private static TextRange combineWithErrorElementIfPossible(@Nonnull ASTNode node) {
    if (node.getElementType() == TokenType.ERROR_ELEMENT) {
      return node.getTextRange();
    }
    final ASTNode prevLeaf = FormatterUtil.getPreviousLeaf(node, TokenType.WHITE_SPACE);
    if (prevLeaf == null || prevLeaf.getElementType() != TokenType.ERROR_ELEMENT) {
      return node.getTextRange();
    }

    final TextRange range = doGetRangeAffectingIndent(prevLeaf);
    if (range == null) {
      return node.getTextRange();
    } else {
      return new TextRange(range.getStartOffset(), node.getTextRange().getEndOffset());
    }
  }

  @Nullable
  private static ASTNode findNearestExpressionParent(final ASTNode current) {
    ASTNode result = current;
    while (result != null) {
      PsiElement psi = result.getPsi();
      if (psi instanceof PsiExpression && !(psi.getParent() instanceof PsiExpression)) {
        break;
      }
      result = result.getTreeParent();
    }
    return result;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}