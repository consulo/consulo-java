package com.intellij.java.impl.psi.formatter.java;

import consulo.document.util.TextRange;
import consulo.language.codeStyle.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * from kotlin
 */
public record TextLineBlock(TextRange range, Alignment aligment, Indent indent, Spacing spacing) implements Block {
  @Nonnull
  @Override
  public TextRange getTextRange() {
    return range();
  }

  @Nonnull
  @Override
  public List<Block> getSubBlocks() {
    return List.of();
  }

  @Nullable
  @Override
  public Wrap getWrap() {
    return null;
  }

  @Nullable
  @Override
  public Indent getIndent() {
    return indent();
  }

  @Nullable
  @Override
  public Alignment getAlignment() {
    return aligment();
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block block, @Nonnull Block block1) {
    return spacing();
  }

  @Nonnull
  @Override
  public ChildAttributes getChildAttributes(int i) {
    return new ChildAttributes(null, null);
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }
}
