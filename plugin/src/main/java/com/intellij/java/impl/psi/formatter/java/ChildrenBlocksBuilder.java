/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.*;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;

import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildrenBlocksBuilder {
  private final Config myConfig;

  private ChildrenBlocksBuilder(Config builder) {
    myConfig = builder;
  }

  public List<Block> buildNodeChildBlocks(ASTNode node, BlockFactory factory) {
    List<Block> blocks = ContainerUtil.newArrayList();

    for (ASTNode child : node.getChildren(null)) {
      if (FormatterUtil.isWhitespaceOrEmpty(child) || child.getTextLength() == 0) {
        continue;
      }

      Alignment alignment = myConfig.getAlignment(child);

      IElementType type = child.getElementType();
      Indent indent = myConfig.getIndent(type);
      Wrap wrap = myConfig.getWrap(type);

      blocks.add(factory.createBlock(child, indent, alignment, wrap, factory.getFormattingMode()));
    }

    return blocks;
  }

  public static class Config {
    private static final Alignment NO_ALIGNMENT = Alignment.createAlignment();
    private static final Wrap NO_WRAP = Wrap.createWrap(0, false);

    private final Map<IElementType, Alignment> myAlignments = new HashMap<>();
    private final Map<IElementType, Indent> myIndents = new HashMap<>();
    private final Map<IElementType, Wrap> myWraps = new HashMap<>();

    private final Map<IElementType, Condition<ASTNode>> myNoneAlignmentCondition = new HashMap<>();

    private Alignment myDefaultAlignment;
    private Indent myDefaultIndent;
    private Wrap myDefaultWrap;
    private FormattingMode myFormattingMode;

    public ChildrenBlocksBuilder createBuilder() {
      return new ChildrenBlocksBuilder(this);
    }

    public Config setDefaultAlignment(Alignment alignment) {
      myDefaultAlignment = alignment;
      return this;
    }

    public Config setDefaultWrap(Wrap wrap) {
      myDefaultWrap = wrap;
      return this;
    }

    public Config setDefaultIndent(Indent indent) {
      myDefaultIndent = indent;
      return this;
    }

    public Config setAlignment(@Nonnull IElementType elementType, @Nonnull Alignment alignment) {
      myAlignments.put(elementType, alignment);
      return this;
    }

    public Config setNoAlignment(IElementType elementType) {
      myAlignments.put(elementType, NO_ALIGNMENT);
      return this;
    }

    public Config setNoAlignmentIf(IElementType elementType, Condition<ASTNode> applyAlignCondition) {
      myNoneAlignmentCondition.put(elementType, applyAlignCondition);
      return this;
    }

    public Config setIndent(IElementType elementType, Indent indent) {
      myIndents.put(elementType, indent);
      return this;
    }

    private Indent getIndent(IElementType elementType) {
      Indent indent = myIndents.get(elementType);
      return indent != null ? indent : myDefaultIndent;
    }

    private Alignment getAlignment(ASTNode node) {
      IElementType elementType = node.getElementType();

      Condition<ASTNode> noneAlignmentCondition = myNoneAlignmentCondition.get(elementType);
      if (noneAlignmentCondition != null && noneAlignmentCondition.value(node)) {
        return null;
      }

      Alignment alignment = myAlignments.get(elementType);
      if (alignment == null) {
        return myDefaultAlignment;
      }
      return alignment == NO_ALIGNMENT ? null : alignment;
    }

    private Wrap getWrap(IElementType elementType) {
      Wrap wrap = myWraps.get(elementType);
      if (wrap == NO_WRAP) {
        return null;
      }
      return wrap != null ? wrap : myDefaultWrap;
    }

    public Config setNoWrap(IElementType elementType) {
      myWraps.put(elementType, NO_WRAP);
      return this;
    }

    public FormattingMode getFormattingMode() {
      return myFormattingMode;
    }

    public Config setFormattingMode(FormattingMode formattingMode) {
      myFormattingMode = formattingMode;
      return this;
    }
  }
}
