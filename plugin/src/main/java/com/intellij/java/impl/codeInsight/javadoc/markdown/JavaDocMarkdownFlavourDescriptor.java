// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.javadoc.markdown;

import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.markdown.documentation.flavour.CodeTagParser;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor;
import org.intellij.markdown.flavours.gfm.GFMTokenTypes;
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser;
import org.intellij.markdown.html.GeneratingProvider;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.html.TransparentInlineHolderProvider;
import org.intellij.markdown.html.TrimmingInlineHolderProvider;
import org.intellij.markdown.parser.LinkMap;
import org.intellij.markdown.parser.MarkerProcessorFactory;
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager;
import org.intellij.markdown.parser.sequentialparsers.impl.AutolinkParser;
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser;
import org.intellij.markdown.parser.sequentialparsers.impl.EmphStrongDelimiterParser;
import org.intellij.markdown.parser.sequentialparsers.impl.ImageParser;
import org.intellij.markdown.parser.sequentialparsers.impl.InlineLinkParser;
import org.intellij.markdown.parser.sequentialparsers.impl.MathParser;
import org.intellij.markdown.parser.sequentialparsers.impl.ReferenceLinkParser;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flavour descriptor which describes how to generate html text from markdown documentation
 */
public class JavaDocMarkdownFlavourDescriptor extends GFMFlavourDescriptor {
  public static final IElementType RAW_TYPE = CodeTagParser.RAW_TYPE;

  public JavaDocMarkdownFlavourDescriptor() {
    super(true, false, true);
  }

  @Override
  public SequentialParserManager getSequentialParserManager() {
    return new SequentialParserManager() {
      @Override
      public List<SequentialParser> getParserSequence() {
        return List.of(
          new AutolinkParser(Arrays.asList(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
          new BacktickParser(),
          new CodeTagParser(), // Custom parser excluding <code> blocks content to be processed as markdown
          new MathParser(),
          new ImageParser(),
          new InlineLinkParser(),
          new ReferenceLinkParser(),
          new EmphasisLikeParser(new EmphStrongDelimiterParser(), new StrikeThroughDelimiterParser())
        );
      }
    };
  }

  @Override
  public Map<IElementType, GeneratingProvider> createHtmlGeneratingProviders(LinkMap linkMap, @Nullable URI baseURI) {
    Map<IElementType, GeneratingProvider> result = new HashMap<>(super.createHtmlGeneratingProviders(linkMap, baseURI));

    // We don't generate a body
    result.remove(MarkdownElementTypes.MARKDOWN_FILE);

    // Paragraphs may need to be inlined
    result.put(MarkdownElementTypes.PARAGRAPH, new JavaDocParagraphProvider());

    // Custom type, no processing
    result.put(RAW_TYPE, new TransparentInlineHolderProvider());

    return result;
  }

  @Override
  public MarkerProcessorFactory getMarkerProcessorFactory() {
    return new JavaDocMarkerProcessorFactory();
  }

  private static class JavaDocParagraphProvider extends TrimmingInlineHolderProvider {
    private static final CharSequence[] EMPTY_ARRAY = new CharSequence[0];

    /** @return Whether or no the paragraph is small and thus should be rendered inline */
    private static boolean isSmallParagraph(ASTNode node) {
      for (ASTNode child : node.getChildren(null)) {
        if (child.getElementType() == MarkdownTokenTypes.EOL) {
          return false;
        }
      }

      ASTNode parent = node.getTreeParent();
      if (parent != null && parent.getElementType() == MarkdownElementTypes.MARKDOWN_FILE) {
        if (hasEolSiblings(node, 1, true)) return false;
        if (hasEolSiblings(node, 2, false)) return false;
      }
      return true;
    }
    
    /// Returns whether we have eol siblings, skipping over whitespaces. 
    /// @param count The desired number of EOL siblings
    /// @param forward Whether to search forward or backward
    private static boolean hasEolSiblings(ASTNode node, int count, boolean forward) throws IllegalArgumentException {
      if (count <= 0) throw new IllegalArgumentException("count must be positive, was " + count);
      ASTNode parent = node.getTreeParent();
      if (parent == null) return false;
       
      List<ASTNode> siblings = Arrays.asList(parent.getChildren(null));
      int nodeChildIndex = siblings.indexOf(node);
      int direction = forward ? 1 : -1;

      for (int i = nodeChildIndex + direction; i < siblings.size() && i >= 0; i += direction) {
        if (siblings.get(i).getElementType() == MarkdownTokenTypes.EOL) {
          count--;
          if (count == 0) return true;
          continue;
        }
        if (siblings.get(i).getElementType() == MarkdownTokenTypes.WHITE_SPACE) continue;
        
        return false;
      }
      return false;
    }

    @Override
    public void closeTag(HtmlGenerator.HtmlGeneratingVisitor visitor, String text, ASTNode node) {
      if (isSmallParagraph(node)) return;
      visitor.consumeTagClose("p");
    }

    @Override
    public void openTag(HtmlGenerator.HtmlGeneratingVisitor visitor, String text, ASTNode node) {
      if (isSmallParagraph(node)) return;
      visitor.consumeTagOpen(node, "p", false, EMPTY_ARRAY);
    }
  }
}
