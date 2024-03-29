/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.formatter.java;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.impl.psi.formatter.java.AbstractJavaBlock.newJavaBlock;

public class CallChunkBlockBuilder {

  public static final String FRAGMENT_DEBUG_NAME = "chainFragment";
  public static final String CHAINED_CALL_DEBUG_NAME = "chainedCall";

  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;
  private final FormattingMode myFormattingMode;
  private final Indent mySmartIndent;
  private final boolean myUseRelativeIndents;

  private boolean isFirst = true;

  public CallChunkBlockBuilder(@Nonnull CommonCodeStyleSettings settings, @Nonnull JavaCodeStyleSettings javaSettings,
                               @Nonnull FormattingMode formattingMode) {
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
    myUseRelativeIndents = myIndentSettings != null && myIndentSettings.USE_RELATIVE_INDENTS;
    mySmartIndent = Indent.getSmartIndent(Indent.Type.CONTINUATION, myUseRelativeIndents);
  }

  @Nonnull
  public Block create(@Nonnull final List<? extends ASTNode> subNodes,
                      final Wrap wrap,
                      @Nullable final Alignment alignment,
                      int relativeIndentSize) {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    final ASTNode firstNode = subNodes.get(0);
    if (JavaFormatterUtil.isStartOfCallChunk(mySettings, firstNode)) {
      AlignmentStrategy strategy = AlignmentStrategy.getNullStrategy();
      Indent indent = relativeIndentSize > 0 ? Indent.getSpaceIndent(relativeIndentSize) : Indent.getNoneIndent();
      Block block = newJavaBlock(firstNode, mySettings, myJavaSettings, indent, null, strategy, myFormattingMode);
      subBlocks.add(block);
      if (subNodes.size() > 1) {
        subBlocks.addAll(createJavaBlocks(subNodes.subList(1, subNodes.size())));
      }
      return createSyntheticBlock(subBlocks, getChainedBlockIndent(true), alignment, wrap, CHAINED_CALL_DEBUG_NAME);
    }
    else {
      return createSyntheticBlock(
        createJavaBlocks(subNodes), getChainedBlockIndent(false), alignment, null, FRAGMENT_DEBUG_NAME);
    }
  }

  private Block createSyntheticBlock(@Nonnull List<Block> subBlocks,
                                     @Nonnull Indent chainedBlockIndent,
                                     @Nullable Alignment alignment,
                                     @Nullable Wrap wrap,
                                     @Nonnull final String debugName) {
    return new SyntheticCodeBlock(subBlocks, alignment, mySettings, myJavaSettings, chainedBlockIndent, wrap) {
      @Override
      public String toString() {
        return debugName + ": " + SyntheticCodeBlock.class.getSimpleName();
      }
    };
  }

  private Indent getChainedBlockIndent(boolean isChainedCall) {
    if (isFirst) {
      isFirst = false;
      return Indent.getNoneIndent();
    }
    return isChainedCall ? mySmartIndent : Indent.getContinuationIndent(myUseRelativeIndents);
  }

  @Nonnull
  private List<Block> createJavaBlocks(@Nonnull final List<? extends ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<>();
    for (ASTNode node : subNodes) {
      Indent indent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      result.add(newJavaBlock(node, mySettings, myJavaSettings, indent, null, AlignmentStrategy.getNullStrategy(), myFormattingMode));
    }
    return result;
  }
}