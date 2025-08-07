/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.xml.util.XmlUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.ast.TokenSet;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.xml.javaee.ExternalResourceManager;
import consulo.xml.psi.xml.XmlEntityDecl;
import consulo.xml.psi.xml.XmlFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertToBasicLatinAction", categories = {"Java", "I18N"}, fileExtensions = "java")
public class ConvertToBasicLatinAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(ConvertToBasicLatinAction.class);

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, @Nonnull final PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
    final Pair<PsiElement, Handler> pair = findHandler(element);
    if (pair == null) return false;

    final String text = pair.first.getText();
    for (int i = 0; i < text.length(); i++) {
      if (shouldConvert(text.charAt(i))) return true;
    }

    return false;
  }

  @Nonnull
  @Override
  public String getText() {
    return CodeInsightLocalize.intentionConvertToBasicLatin().get();
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, @Nonnull final PsiElement element) throws IncorrectOperationException {
    final Pair<PsiElement, Handler> pair = findHandler(element);
    if (pair == null) return;
    final PsiElement workElement = pair.first;
    final Handler handler = pair.second;

    final String newText = handler.processText(workElement);
    final PsiElement newElement = handler.createReplacement(workElement, newText);
    workElement.replace(newElement);
  }

  @Nullable
  private static Pair<PsiElement, Handler> findHandler(final PsiElement element) {
    for (final Handler handler : ourHandlers) {
      final PsiElement applicable = handler.findApplicable(element);
      if (applicable != null) {
        return Pair.create(applicable, handler);
      }
    }

    return null;
  }

  private static boolean shouldConvert(final char ch) {
    return Character.UnicodeBlock.of(ch) != Character.UnicodeBlock.BASIC_LATIN;
  }

  private static abstract class Handler {
    @Nullable
    public abstract PsiElement findApplicable(final PsiElement element);

    @RequiredReadAction
    public String processText(final PsiElement element) {
      final String text = element.getText();
      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        final char ch = text.charAt(i);
        if (!shouldConvert(ch)) {
          sb.append(ch);
        }
        else {
          convert(sb, ch);
        }
      }
      return sb.toString();
    }

    protected abstract void convert(StringBuilder sb, char ch);

    public abstract PsiElement createReplacement(final PsiElement element, final String newText);
  }

  private static final Handler[] ourHandlers = { new MyLiteralHandler(), new MyDocCommentHandler(), new MyCommentHandler() };

  private static class MyLiteralHandler extends Handler {
    private static final TokenSet LITERALS = TokenSet.create(JavaTokenType.CHARACTER_LITERAL, JavaTokenType.STRING_LITERAL);

    @Override
    public PsiElement findApplicable(final PsiElement element) {
      final PsiElement parent = element.getParent();
      return element instanceof PsiJavaToken javaToken
        && LITERALS.contains(javaToken.getTokenType())
        && parent instanceof PsiLiteralExpression
        ? parent : null;
    }

    @Override
    public PsiElement createReplacement(final PsiElement element, final String newText) {
      return JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(newText, element.getParent());
    }

    @Override
    protected void convert(final StringBuilder sb, final char ch) {
      sb.append(String.format("\\u%04x", (int)ch));
    }
  }

  private static class MyDocCommentHandler extends Handler {
    private static Map<Character, String> ourEntities = null;

    @Override
    public PsiElement findApplicable(final PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, PsiDocComment.class, false);
    }

    @Override
    @RequiredReadAction
    public String processText(final PsiElement element) {
      loadEntities(element.getProject());
      return super.processText(element);
    }

    @Override
    protected void convert(final StringBuilder sb, final char ch) {
      assert ourEntities != null;
      final String entity = ourEntities.get(ch);
      if (entity != null) {
        sb.append('&').append(entity).append(';');
      }
      else {
        sb.append("&#x").append(Integer.toHexString(ch)).append(';');
      }
    }

    @Override
    public PsiElement createReplacement(final PsiElement element, final String newText) {
      return JavaPsiFacade.getElementFactory(element.getProject()).createDocCommentFromText(newText);
    }

    @RequiredReadAction
    private static void loadEntities(final Project project) {
      if (ourEntities != null) return;

      final XmlFile file;
      try {
        final String url = ExternalResourceManager.getInstance().getResourceLocation(XmlUtil.HTML4_LOOSE_URI, project);
        if (url == null) { LOG.error("Namespace not found: " + XmlUtil.HTML4_LOOSE_URI); return; }
        final VirtualFile vFile = VirtualFileUtil.findFileByURL(new URL(url));
        if (vFile == null) { LOG.error("Resource not found: " + url); return; }
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (!(psiFile instanceof XmlFile)) { LOG.error("Unexpected resource: " + psiFile); return; }
        file = (XmlFile)psiFile;
      }
      catch (MalformedURLException e) {
        LOG.error(e); return;
      }

      ourEntities = new HashMap<>();
      final Pattern pattern = Pattern.compile("&#(\\d+);");
      XmlUtil.processXmlElements(
        file,
        element -> {
          if (element instanceof XmlEntityDecl entity) {
            final Matcher m = pattern.matcher(entity.getValueElement().getValue());
            if (m.matches()) {
              final char i = (char) Integer.parseInt(m.group(1));
              if (shouldConvert(i)) {
                ourEntities.put(i, entity.getName());
              }
            }
          }
          return true;
        },
        true
      );
    }
  }

  private static class MyCommentHandler extends MyDocCommentHandler {
    @Override
    public PsiElement findApplicable(final PsiElement element) {
      return element instanceof PsiComment ? element : null;
    }

    @Override
    public PsiElement createReplacement(final PsiElement element, final String newText) {
      return JavaPsiFacade.getElementFactory(element.getProject()).createCommentFromText(newText, element.getParent());
    }
  }
}
