/*
 * Copyright 2013-2026 consulo.io
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
package consulo.java.impl.spellchecker;

import com.intellij.java.analysis.codeInspection.SuppressManager;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocTagImpl;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiInlineDocTag;
import com.intellij.java.language.psi.util.PsiLiteralUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.TokenSet;
import consulo.language.impl.psi.PsiCommentImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.spellcheker.text.TextContent;
import consulo.language.spellcheker.text.TextContent.Exclusion;
import consulo.language.spellcheker.text.TextContentBuilder;
import consulo.language.spellcheker.text.TextExtractor;
import consulo.language.spellcheker.text.util.HtmlUtils;
import consulo.language.spellcheker.text.util.PsiUtils;
import consulo.language.spellcheker.text.util.Text;
import consulo.util.collection.ContainerUtil;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.intellij.java.language.impl.psi.impl.source.tree.ElementType.JAVA_PLAIN_COMMENT_BIT_SET;
import static com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType.DOC_PARAMETER_REF;
import static com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType.DOC_REFERENCE_HOLDER;
import static com.intellij.java.language.psi.JavaDocTokenType.DOC_COMMENT_END;
import static com.intellij.java.language.psi.JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
import static com.intellij.java.language.psi.JavaDocTokenType.DOC_COMMENT_START;
import static consulo.language.spellcheker.text.TextContent.TextDomain.COMMENTS;
import static consulo.language.spellcheker.text.TextContent.TextDomain.DOCUMENTATION;
import static consulo.language.spellcheker.text.TextContent.TextDomain.LITERALS;
import static consulo.language.spellcheker.text.util.EscapeUtils.replaceBackslashEscapes;

@ExtensionImpl
public class JavaTextExtractor extends TextExtractor {
    private static final TokenSet EXCLUDED =
        TokenSet.create(DOC_COMMENT_START, DOC_COMMENT_LEADING_ASTERISKS, DOC_COMMENT_END, DOC_PARAMETER_REF, DOC_REFERENCE_HOLDER);

    private static final TextContentBuilder javadocBuilder = TextContentBuilder.FromPsi
        .withUnknown(e -> e instanceof PsiInlineDocTag)
        .excluding(e -> EXCLUDED.contains(PsiUtilCore.getElementType(e)))
        .removingIndents(" \t").removingLineSuffixes(" \t");

    @Override
    public List<TextContent> buildTextContents(PsiElement root, Set<TextContent.TextDomain> allowedDomains) {
        if (allowedDomains.contains(DOCUMENTATION)) {
            if (root instanceof PsiDocComment) {
                return HtmlUtils.excludeHtml(
                    javadocBuilder.excluding(e -> e instanceof PsiDocTagImpl)
                        .build(root, DOCUMENTATION)
                );
            }
            if (root instanceof PsiDocTagImpl tag && !tag.getName().equals("author")) {
                return HtmlUtils.excludeHtml(javadocBuilder.build(root, DOCUMENTATION));
            }
        }

        if (root instanceof PsiCommentImpl && allowedDomains.contains(COMMENTS)) {
            if (root.getTextLength() == 2) {
                return List.of();
            }
            List<PsiElement> roots = PsiUtils.getNotSoDistantSimilarSiblings(root, e ->
                JAVA_PLAIN_COMMENT_BIT_SET.contains(PsiUtilCore.getElementType(e)));
            return ContainerUtil.createMaybeSingletonList(
                TextContent.joinWithWhitespace('\n', ContainerUtil.mapNotNull(roots, c ->
                    TextContentBuilder.FromPsi.removingIndents(" \t*/").removingLineSuffixes(" \t").build(c, COMMENTS))));
        }

        if (root instanceof PsiLiteralExpression literalExpression &&
            allowedDomains.contains(LITERALS) &&
            literalExpression.getValue() instanceof String) {
            if (shouldBeIgnored(literalExpression)) {
                return List.of();
            }
            TextContent content = TextContentBuilder.FromPsi.build(root, LITERALS);
            int indent = PsiLiteralUtil.getTextBlockIndent(literalExpression);
            if (indent >= 0 && indent < 1000 && content != null) {
                if (indent > 0) {
                    content = content.excludeRanges(
                        ContainerUtil.map(Text.allOccurrences(Pattern.compile("(?<=\n)" + "\\s{" + indent + "}"), content), Exclusion::exclude));
                }
                content = content.excludeRanges(ContainerUtil.map(Text.allOccurrences(Pattern.compile("\\\\\n"), content), Exclusion::exclude));
                content = replaceBackslashEscapes(content);
                return ContainerUtil.createMaybeSingletonList(content.trimWhitespace());
            }
            if (indent == -1 && content != null) {
                content = replaceBackslashEscapes(content);
            }

            return ContainerUtil.createMaybeSingletonList(content);
        }

        return List.of();
    }

    private static boolean shouldBeIgnored(PsiLiteralExpression literalExpression) {
        return SuppressManager.isSuppressedInspectionName(literalExpression)
            || LiteralExpressionTokenizer.shouldBeIgnored(literalExpression);
    }

    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
