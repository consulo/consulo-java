// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTag;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTagBody;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTagValue;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.LiteralTextEscaper;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.CharArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PsiSnippetDocTagImpl extends CompositePsiElement implements PsiSnippetDocTag, PsiLanguageInjectionHost {
  public PsiSnippetDocTagImpl() {
    super(JavaDocElementType.DOC_SNIPPET_TAG);
  }

  @Override
  public
  @Nonnull
  String getName() {
    return getNameElement().getText().substring(1);
  }

  @Override
  public PsiDocComment getContainingComment() {
    ASTNode scope = getTreeParent();
    while (scope.getElementType() != JavaDocElementType.DOC_COMMENT) {
      scope = scope.getTreeParent();
    }
    return (PsiDocComment)SourceTreeToPsiMap.treeElementToPsi(scope);
  }

  @Override
  public PsiElement getNameElement() {
    return findPsiChildByType(JavaDocTokenType.DOC_TAG_NAME);
  }

  @Override
  @Nonnull
  public PsiElement[] getDataElements() {
    return getChildrenAsPsiElements(PsiInlineDocTagImpl.VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public
  @Nullable
  PsiSnippetDocTagValue getValueElement() {
    return (PsiSnippetDocTagValue)findPsiChildByType(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTag";
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Contract(pure = true)
  @Nonnull
  public List<TextRange> getContentRanges() {
    final PsiSnippetDocTagValue valueElement = getValueElement();
    if (valueElement == null) return Collections.emptyList();

    final PsiSnippetDocTagBody body = valueElement.getBody();
    if (body == null) return Collections.emptyList();

    final ASTNode colon = getColonElement(body);
    if (colon == null) return Collections.emptyList();

    final int startOffset = colon.getTextRange().getEndOffset();

    final TextRange snippetBodyRange = body.getTextRange();

    final TextRange range = TextRange.create(startOffset, snippetBodyRange.getEndOffset());
    final TextRange snippetBodyTextRangeRelativeToSnippetTag = range.shiftLeft(getStartOffset());

    final String[] lines = snippetBodyTextRangeRelativeToSnippetTag.substring(getText()).split("\n");
    if (lines.length == 0) return Collections.singletonList(snippetBodyTextRangeRelativeToSnippetTag);

    return getRanges(snippetBodyTextRangeRelativeToSnippetTag, lines);
  }

  @Contract(pure = true)
  @Nonnull
  private static List<TextRange> getRanges(@Nonnull TextRange snippetBodyTextRangeRelativeToSnippet,
                                           @Nonnull String[] lines) {
    final int firstLine = getFirstNonEmptyLine(lines);
    final int lastLine = getLastNonEmptyLine(lines);

    int totalMinIndent = getIndent(lines, firstLine, lastLine);

    int startOffset = getStartOffsetOfFirstNonEmptyLine(snippetBodyTextRangeRelativeToSnippet, lines, firstLine);

    final List<TextRange> ranges = new ArrayList<>();
    for (int i = firstLine; i < Math.min(lastLine, lines.length); i++) {
      final String line = lines[i];
      final int size = line.length() + 1;
      final int indentSize = getIndentSize(line, totalMinIndent);

      ranges.add(TextRange.create(0, size - indentSize).shiftRight(startOffset + indentSize));
      startOffset += size;
    }

    final String line = lines[lastLine];
    final int indentSize = getIndentSize(line, totalMinIndent);

    final int endOffset = snippetBodyTextRangeRelativeToSnippet.getEndOffset();
    final int lastLineStartOffset = Math.min(endOffset, startOffset + indentSize);
    final int lastLineEndOffset = startOffset + line.length();

    ranges.add(TextRange.create(lastLineStartOffset, Math.min(endOffset, lastLineEndOffset)));
    return ranges;
  }

  /**
   * Usually leading asterisks of a javadoc are aligned so the common indent for lines in snippet body is obvious,
   * but nevertheless javadoc can have multiple leading asterisks, and they don't have to be aligned.
   * This method either returns the passed indent or, if the passed indent is too short, which will result in leaving some leading
   * asterisks after stripping the indent from the line, the indent that goes after the last leading asterisk.
   *
   * @param line   a line to calculate the indent size for
   * @param indent an indent that is minimal across all the lines in the snippet body
   * @return the indent that is either the passed indent, or a new indent that goes after the last leading asterisk.
   */
  @Contract(pure = true)
  private static int getIndentSize(@Nonnull final String line, int indent) {
    final int ownLineIndent = CharArrayUtil.shiftForward(line, 0, " *");

    final String maxPossibleIndent = line.substring(0, ownLineIndent);
    final int lastAsteriskInIndent = maxPossibleIndent.lastIndexOf('*', ownLineIndent);

    return lastAsteriskInIndent >= indent ? lastAsteriskInIndent + 1 : indent;
  }

  @Contract(pure = true)
  private static int getStartOffsetOfFirstNonEmptyLine(@Nonnull TextRange snippetBodyTextRangeRelativeToSnippet,
                                                       @Nonnull String[] lines,
                                                       int firstLine) {
    int start = snippetBodyTextRangeRelativeToSnippet.getStartOffset();
    for (int i = 0; i < Math.min(firstLine, lines.length); i++) {
      start += lines[i].length() + 1;
    }
    return start;
  }

  @Contract(pure = true)
  private static int getIndent(@Nonnull String[] lines, int firstLine, int lastLine) {
    int minIndent = Integer.MAX_VALUE;
    for (int i = firstLine; i <= lastLine && i < lines.length; i++) {
      String line = lines[i];
      final int indentLength;
      if (isEmptyOrSpacesWithLeadingAsterisksOnly(line)) {
        indentLength = line.length();
      }
      else {
        indentLength = calculateIndent(line);
      }
      if (minIndent > indentLength) minIndent = indentLength;
    }
    if (minIndent == Integer.MAX_VALUE) minIndent = 0;
    return minIndent;
  }

  @Contract(pure = true)
  private static int getLastNonEmptyLine(@Nonnull String[] lines) {
    int lastLine = lines.length - 1;
    while (lastLine > 0 && isEmptyOrSpacesWithLeadingAsterisksOnly(lines[lastLine])) {
      lastLine--;
    }
    return lastLine;
  }

  @Contract(pure = true)
  private static int getFirstNonEmptyLine(@Nonnull String[] lines) {
    int firstLine = 0;
    while (firstLine < lines.length && isEmptyOrSpacesWithLeadingAsterisksOnly(lines[firstLine])) {
      firstLine++;
    }
    return firstLine;
  }

  @Contract(pure = true)
  private static boolean isEmptyOrSpacesWithLeadingAsterisksOnly(@Nonnull String lines) {
    if (lines.isEmpty()) return true;
    return lines.matches("^\\s*\\**\\s*$");
  }

  @Contract(pure = true)
  private static int calculateIndent(@Nonnull String content) {
    if (content.isEmpty()) return 0;
    final String noIndent = content.replaceAll("^\\s*\\*\\s*", "");
    return content.length() - noIndent.length();
  }

  @Contract(pure = true)
  private static
  @Nullable
  ASTNode getColonElement(@Nonnull PsiSnippetDocTagBody snippetBodyBody) {
    final ASTNode[] colonElements = snippetBodyBody.getNode().getChildren(TokenSet.create(JavaDocTokenType.DOC_TAG_VALUE_COLON));
    return colonElements.length == 1 ? colonElements[0] : null;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@Nonnull String text) {
    return new SnippetDocTagManipulator().handleContentChange(this, text);
  }

  @Override
  public
  @Nonnull
  LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new LiteralTextEscaper<PsiSnippetDocTagImpl>(this) {

      private int[] myOffsets;

      @Override
      public boolean decode(@Nonnull TextRange rangeInsideHost, @Nonnull StringBuilder outChars) {
        final List<TextRange> ranges = myHost.getContentRanges();

        final String content = rangeInsideHost.substring(myHost.getText());

        myOffsets = new int[content.length() + 1];
        Arrays.fill(myOffsets, -1);

        int i = 0;
        boolean decoded = false;
        for (TextRange range : ranges) {
          if (!rangeInsideHost.contains(range)) continue;

          for (int j = 0; j < range.getLength(); j++) {
            myOffsets[i++] = range.getStartOffset() + j;
          }

          decoded = true;
          outChars.append(range.substring(myHost.getText()));
        }
        myOffsets[i] = rangeInsideHost.getEndOffset();

        return decoded;
      }

      @Override
      public int getOffsetInHost(int offsetInDecoded, @Nonnull TextRange rangeInsideHost) {
        if (offsetInDecoded >= myOffsets.length) {
          return -1;
        }
        return myOffsets[offsetInDecoded];
      }

      @Override
      public boolean isOneLine() {
        return false;
      }
    };
  }

}
