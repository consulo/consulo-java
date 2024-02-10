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
package com.intellij.java.language.impl.psi.impl.cache;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaDocLexer;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.language.ast.LightTreeUtil;
import consulo.language.ast.IElementType;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import consulo.language.ast.LighterASTTokenNode;
import consulo.language.psi.stub.StubElement;
import consulo.language.util.CharTable;

import jakarta.annotation.Nonnull;

/**
 * @author max
 */
public class RecordUtil {
  private static final String DEPRECATED_ANNOTATION_NAME = "Deprecated";
  private static final String DEPRECATED_TAG = "@deprecated";

  private RecordUtil() {
  }

  public static boolean isDeprecatedByAnnotation(@Nonnull LighterAST tree, @Nonnull LighterASTNode modList) {
    for (final LighterASTNode child : tree.getChildren(modList)) {
      if (child.getTokenType() == JavaElementType.ANNOTATION) {
        final LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, child, JavaElementType.JAVA_CODE_REFERENCE);
        if (ref != null) {
          final LighterASTNode id = LightTreeUtil.firstChildOfType(tree, ref, JavaTokenType.IDENTIFIER);
          if (id != null) {
            final String name = intern(tree.getCharTable(), id);
            if (DEPRECATED_ANNOTATION_NAME.equals(name)) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  public static boolean isDeprecatedByDocComment(@Nonnull LighterAST tree, @Nonnull LighterASTNode comment) {
    String text = LightTreeUtil.toFilteredString(tree, comment, null);
    if (text.contains(DEPRECATED_TAG)) {
      JavaDocLexer lexer = new JavaDocLexer(LanguageLevel.HIGHEST);
      lexer.start(text);
      IElementType token;
      while ((token = lexer.getTokenType()) != null) {
        if (token == JavaDocTokenType.DOC_TAG_NAME && DEPRECATED_TAG.equals(lexer.getTokenText())) {
          return true;
        }
        lexer.advance();
      }
    }

    return false;
  }

  public static int packModifierList(@Nonnull LighterAST tree, @Nonnull LighterASTNode modList) {
    int packed = 0;
    for (final LighterASTNode child : tree.getChildren(modList)) {
      packed |= ModifierFlags.KEYWORD_TO_MODIFIER_FLAG_MAP.getInt(child.getTokenType());
    }
    return packed;
  }

  @Nonnull
  public static String intern(@Nonnull CharTable table, @Nonnull LighterASTNode node) {
    assert node instanceof LighterASTTokenNode : node;
    return table.intern(((LighterASTTokenNode) node).getText()).toString();
  }

  public static boolean isStaticNonPrivateMember(@Nonnull StubElement<?> stub) {
    StubElement<PsiModifierList> type = stub.findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    if (!(type instanceof PsiModifierListStub)) {
      return false;
    }

    int mask = ((PsiModifierListStub) type).getModifiersMask();
    if (ModifierFlags.hasModifierProperty(PsiModifier.PRIVATE, mask)) {
      return false;
    }

    if (ModifierFlags.hasModifierProperty(PsiModifier.STATIC, mask)) {
      return true;
    }

    return stub instanceof PsiFieldStub && stub.getStubType() == JavaElementType.ENUM_CONSTANT || stub.getParentStub() instanceof PsiClassStub && ((PsiClassStub) stub.getParentStub()).isInterface();
  }
}