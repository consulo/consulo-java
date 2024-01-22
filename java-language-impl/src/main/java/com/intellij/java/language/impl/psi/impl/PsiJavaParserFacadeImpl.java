/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.parser.DeclarationParser;
import com.intellij.java.language.impl.parser.JavaParser;
import com.intellij.java.language.impl.parser.JavaParserUtil;
import com.intellij.java.language.impl.parser.ReferenceParser;
import com.intellij.java.language.impl.psi.impl.source.JavaDummyElement;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.impl.ast.FileElement;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.impl.psi.DummyHolder;
import consulo.language.impl.psi.DummyHolderFactory;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 */
public class PsiJavaParserFacadeImpl implements PsiJavaParserFacade {
  protected final PsiManager myManager;

  private static final String DUMMY_FILE_NAME = "_Dummy_." + JavaFileType.INSTANCE.getDefaultExtension();

  public PsiJavaParserFacadeImpl(PsiManager manager) {
    myManager = manager;
  }

  private static final JavaParserUtil.ParserWrapper ANNOTATION = builder -> JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);

  private static final JavaParserUtil.ParserWrapper PARAMETER = builder -> JavaParser.INSTANCE.getDeclarationParser().parseParameter(builder, true, false, false);

  private static final JavaParserUtil.ParserWrapper RESOURCE = builder -> JavaParser.INSTANCE.getDeclarationParser().parseResource(builder);

  private static final JavaParserUtil.ParserWrapper TYPE = builder -> JavaParser.INSTANCE.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS |
      ReferenceParser.WILDCARD | ReferenceParser.DISJUNCTIONS | ReferenceParser.VAR_TYPE);

  public static final JavaParserUtil.ParserWrapper REFERENCE = builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);

  public static final JavaParserUtil.ParserWrapper DIAMOND_REF = builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, true);

  public static final JavaParserUtil.ParserWrapper STATIC_IMPORT_REF = builder -> JavaParser.INSTANCE.getReferenceParser().parseImportCodeReference(builder, true);

  private static final JavaParserUtil.ParserWrapper TYPE_PARAMETER = builder -> JavaParser.INSTANCE.getReferenceParser().parseTypeParameter(builder);

  private static final JavaParserUtil.ParserWrapper DECLARATION = builder -> JavaParser.INSTANCE.getDeclarationParser().parse(builder, DeclarationParser.Context.CLASS);

  private static final JavaParserUtil.ParserWrapper CODE_BLOCK = builder -> JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true);

  private static final JavaParserUtil.ParserWrapper STATEMENT = builder -> JavaParser.INSTANCE.getStatementParser().parseStatement(builder);

  private static final JavaParserUtil.ParserWrapper EXPRESSION = builder -> JavaParser.INSTANCE.getExpressionParser().parse(builder);

  private static final JavaParserUtil.ParserWrapper ENUM_CONSTANT = builder -> JavaParser.INSTANCE.getDeclarationParser().parseEnumConstant(builder);

  private static final JavaParserUtil.ParserWrapper MODULE = builder -> JavaParser.INSTANCE.getModuleParser().parse(builder);

  private static final Map<String, PsiPrimitiveType> PRIMITIVE_TYPES;

  static {
    PRIMITIVE_TYPES = new HashMap<>();
    PRIMITIVE_TYPES.put(PsiType.BYTE.getCanonicalText(), PsiType.BYTE);
    PRIMITIVE_TYPES.put(PsiType.CHAR.getCanonicalText(), PsiType.CHAR);
    PRIMITIVE_TYPES.put(PsiType.DOUBLE.getCanonicalText(), PsiType.DOUBLE);
    PRIMITIVE_TYPES.put(PsiType.FLOAT.getCanonicalText(), PsiType.FLOAT);
    PRIMITIVE_TYPES.put(PsiType.INT.getCanonicalText(), PsiType.INT);
    PRIMITIVE_TYPES.put(PsiType.LONG.getCanonicalText(), PsiType.LONG);
    PRIMITIVE_TYPES.put(PsiType.SHORT.getCanonicalText(), PsiType.SHORT);
    PRIMITIVE_TYPES.put(PsiType.BOOLEAN.getCanonicalText(), PsiType.BOOLEAN);
    PRIMITIVE_TYPES.put(PsiType.VOID.getCanonicalText(), PsiType.VOID);
    PRIMITIVE_TYPES.put(PsiType.NULL.getCanonicalText(), PsiType.NULL);
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiAnnotation createAnnotationFromText(@jakarta.annotation.Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ANNOTATION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotation)) {
      throw new IncorrectOperationException("Incorrect annotation '" + text + "'");
    }
    return (PsiAnnotation) element;
  }

  @Nonnull
  @Override
  public PsiDocTag createDocTagFromText(@Nonnull final String text) throws IncorrectOperationException {
    return createDocCommentFromText("/**\n" + text + "\n */").getTags()[0];
  }

  @Nonnull
  @Override
  public PsiDocComment createDocCommentFromText(@Nonnull String docCommentText) throws IncorrectOperationException {
    return createDocCommentFromText(docCommentText, null);
  }

  @Nonnull
  @Override
  public PsiDocComment createDocCommentFromText(@Nonnull String text, @jakarta.annotation.Nullable PsiElement context) throws IncorrectOperationException {
    final PsiMethod method = createMethodFromText(text.trim() + "void m();", context);
    final PsiDocComment comment = method.getDocComment();
    if (comment == null) {
      throw new IncorrectOperationException("Incorrect comment '" + text + "'");
    }
    return comment;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiClass createClassFromText(@Nonnull final String body, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ {\n" + body +"\n}");
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class '" + body + "'");
    }
    return classes[0];
  }

  @Nonnull
  @Override
  public PsiField createFieldFromText(@jakarta.annotation.Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiField)) {
      throw new IncorrectOperationException("Incorrect field '" + text + "'");
    }
    return (PsiField) element;
  }

  @Nonnull
  @Override
  public PsiMethod createMethodFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context, final LanguageLevel level) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiMethod)) {
      throw newException("Incorrect method '" + text + "'", holder);
    }
    return (PsiMethod) element;
  }

  @Nonnull
  @Override
  public final PsiMethod createMethodFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    return createMethodFromText(text, context, LanguageLevel.HIGHEST);
  }

  @Nonnull
  @Override
  public PsiParameter createParameterFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, PARAMETER, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiParameter)) {
      throw new IncorrectOperationException("Incorrect parameter '" + text + "'");
    }
    return (PsiParameter) element;
  }

  @Nonnull
  @Override
  public PsiResourceVariable createResourceFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, RESOURCE, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiResourceVariable)) {
      throw new IncorrectOperationException("Incorrect resource '" + text + "'");
    }
    return (PsiResourceVariable) element;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiType createTypeFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, false);
  }

  @Nonnull
  @Override
  public PsiTypeElement createTypeElementFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final LanguageLevel level = level(context);
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE, level), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeElement)) {
      throw new IncorrectOperationException("Incorrect type '" + text + "' (" + level + ")");
    }
    return (PsiTypeElement) element;
  }

  protected PsiType createTypeInner(final String text, @Nullable final PsiElement context, final boolean markAsCopy) throws IncorrectOperationException {
    final PsiPrimitiveType primitiveType = PRIMITIVE_TYPES.get(text);
    if (primitiveType != null) {
      return primitiveType;
    }

    final PsiTypeElement element = createTypeElementFromText(text, context);
    if (markAsCopy) {
      CodeEditUtil.markGenerated(element.getNode());
    }
    return element.getType();
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiJavaCodeReferenceElement createReferenceFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final boolean isStaticImport = context instanceof PsiImportStaticStatement && !((PsiImportStaticStatement) context).isOnDemand();
    final boolean mayHaveDiamonds = context instanceof PsiNewExpression && PsiUtil.getLanguageLevel(context).isAtLeast(LanguageLevel.JDK_1_7);
    final JavaParserUtil.ParserWrapper wrapper = isStaticImport ? STATIC_IMPORT_REF : mayHaveDiamonds ? DIAMOND_REF : REFERENCE;
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, wrapper, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      throw new IncorrectOperationException("Incorrect reference '" + text + "'");
    }
    return (PsiJavaCodeReferenceElement) element;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiCodeBlock createCodeBlockFromText(@Nonnull final CharSequence text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CODE_BLOCK, level(context), true), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCodeBlock)) {
      throw new IncorrectOperationException("Incorrect code block '" + text + "'");
    }
    return (PsiCodeBlock) element;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiStatement createStatementFromText(@jakarta.annotation.Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, STATEMENT, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiStatement)) {
      throw new IncorrectOperationException("Incorrect statement '" + text + "'");
    }
    return (PsiStatement) element;
  }

  @Nonnull
  @Override
  public PsiExpression createExpressionFromText(@jakarta.annotation.Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, EXPRESSION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiExpression)) {
      throw new IncorrectOperationException("Incorrect expression '" + text + "'");
    }
    return (PsiExpression) element;
  }

  protected PsiJavaFile createDummyJavaFile(@NonNls final String text) {
    final FileType type = JavaFileType.INSTANCE;
    return (PsiJavaFile) PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(DUMMY_FILE_NAME, type, text);
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiTypeParameter createTypeParameterFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE_PARAMETER, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeParameter)) {
      throw new IncorrectOperationException("Incorrect type parameter '" + text + "'");
    }
    return (PsiTypeParameter) element;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiComment createCommentFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile(text);
    for (PsiElement aChildren : aFile.getChildren()) {
      if (aChildren instanceof PsiComment) {
        if (!aChildren.getText().equals(text)) {
          break;
        }
        final PsiComment comment = (PsiComment) aChildren;
        DummyHolderFactory.createHolder(myManager, (TreeElement) SourceTreeToPsiMap.psiElementToTree(comment), context);
        return comment;
      }
    }

    throw new IncorrectOperationException("Incorrect comment '" + text + "'");
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiEnumConstant createEnumConstantFromText(@Nonnull final String text, @jakarta.annotation.Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ENUM_CONSTANT, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiEnumConstant)) {
      throw new IncorrectOperationException("Incorrect enum constant '" + text + "'");
    }
    return (PsiEnumConstant) element;
  }

  @Nonnull
  public PsiType createPrimitiveTypeFromText(@Nonnull String text) throws IncorrectOperationException {
    PsiPrimitiveType primitiveType = getPrimitiveType(text);
    if (primitiveType == null) {
      throw new IncorrectOperationException("Incorrect primitive type '" + text + "'");
    }
    return primitiveType;
  }

  @Nonnull
  @Override
  public PsiJavaModule createModuleFromText(@jakarta.annotation.Nonnull String text, @jakarta.annotation.Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, MODULE, LanguageLevel.JDK_1_9), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiJavaModule)) {
      throw newException("Incorrect module declaration '" + text + "'", holder);
    }
    return (PsiJavaModule) element;
  }

  @Nonnull
  @Override
  public PsiStatement createModuleStatementFromText(@jakarta.annotation.Nonnull String text, @jakarta.annotation.Nullable PsiElement context) throws IncorrectOperationException {
    String template = "module M { " + text + "; }";
    PsiJavaModule module = createModuleFromText(template, context);
    PsiStatement statement = PsiTreeUtil.getChildOfType(module, PsiStatement.class);
    if (statement == null) {
      throw new IncorrectOperationException("Incorrect module statement '" + text + "'");
    }
    return statement;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiJavaModuleReferenceElement createModuleReferenceFromText(@Nonnull String text, @jakarta.annotation.Nullable PsiElement context) throws IncorrectOperationException {
    return createModuleFromText("module " + text + " {}", context).getNameIdentifier();
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiType createPrimitiveType(@jakarta.annotation.Nonnull String text, @jakarta.annotation.Nonnull PsiAnnotation[] annotations) throws IncorrectOperationException {
    return createPrimitiveTypeFromText(text).annotate(TypeAnnotationProvider.Static.create(annotations));
  }

  public static PsiPrimitiveType getPrimitiveType(final String text) {
    return PRIMITIVE_TYPES.get(text);
  }

  protected static LanguageLevel level(@jakarta.annotation.Nullable final PsiElement context) {
    return context != null && context.isValid() ? PsiUtil.getLanguageLevel(context) : LanguageLevel.HIGHEST;
  }

  private static IncorrectOperationException newException(final String msg, final DummyHolder holder) {
    final FileElement root = holder.getTreeElement();
    if (root instanceof JavaDummyElement) {
      final Throwable cause = ((JavaDummyElement) root).getParserError();
      if (cause != null) {
        return new IncorrectOperationException(msg, cause);
      }
    }
    return new IncorrectOperationException(msg);
  }
}