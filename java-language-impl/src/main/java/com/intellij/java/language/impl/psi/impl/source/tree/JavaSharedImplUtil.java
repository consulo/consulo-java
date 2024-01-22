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
package com.intellij.java.language.impl.psi.impl.source.tree;

import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.impl.ast.*;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.CharTable;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.collection.SmartList;
import consulo.util.collection.Stack;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.List;

public class JavaSharedImplUtil {
  private static final Logger LOG = Logger.getInstance(JavaSharedImplUtil.class);

  private static final TokenSet BRACKETS = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);

  private JavaSharedImplUtil() {
  }

  public static PsiType getType(@jakarta.annotation.Nonnull PsiTypeElement typeElement, @jakarta.annotation.Nonnull PsiElement anchor) {
    return getType(typeElement, anchor, null);
  }

  public static PsiType getType(@jakarta.annotation.Nonnull PsiTypeElement typeElement, @jakarta.annotation.Nonnull PsiElement anchor, @Nullable PsiAnnotation stopAt) {
    PsiType type = typeElement.getType();

    List<PsiAnnotation[]> allAnnotations = collectAnnotations(anchor, stopAt);
    if (allAnnotations == null) {
      return null;
    }
    for (PsiAnnotation[] annotations : allAnnotations) {
      type = type.createArrayType().annotate(TypeAnnotationProvider.Static.create(annotations));
    }

    return type;
  }

  // collects annotations bound to C-style arrays
  private static List<PsiAnnotation[]> collectAnnotations(PsiElement anchor, PsiAnnotation stopAt) {
    List<PsiAnnotation[]> annotations = new SmartList<>();

    List<PsiAnnotation> current = null;
    boolean found = (stopAt == null), stop = false;
    for (PsiElement child = anchor.getNextSibling(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiComment || child instanceof PsiWhiteSpace) {
        continue;
      }

      if (child instanceof PsiAnnotation) {
        if (current == null) {
          current = new SmartList<>();
        }
        current.add((PsiAnnotation) child);
        if (child == stopAt) {
          found = stop = true;
        }
        continue;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        annotations.add(ContainerUtil.toArray(current, PsiAnnotation.ARRAY_FACTORY));
        current = null;
        if (stop) {
          return annotations;
        }
      } else if (!PsiUtil.isJavaToken(child, JavaTokenType.RBRACKET)) {
        break;
      }
    }

    // annotation is misplaced (either located before the anchor or has no following brackets)
    return !found || stop ? null : annotations;
  }

  @jakarta.annotation.Nonnull
  public static PsiType applyAnnotations(@jakarta.annotation.Nonnull PsiType type, @jakarta.annotation.Nullable PsiModifierList modifierList) {
    if (modifierList != null) {
      PsiAnnotation[] annotations = modifierList.getAnnotations();
      if (annotations.length > 0) {
        TypeAnnotationProvider original = modifierList.getParent() instanceof PsiMethod ? type.getAnnotationProvider() : TypeAnnotationProvider.EMPTY;
        TypeAnnotationProvider provider = new FilteringTypeAnnotationProvider(annotations, original);
        if (type instanceof PsiArrayType) {
          Stack<PsiArrayType> types = new Stack<>();
          do {
            types.push((PsiArrayType) type);
            type = ((PsiArrayType) type).getComponentType();
          }
          while (type instanceof PsiArrayType);
          type = type.annotate(provider);
          while (!types.isEmpty()) {
            PsiArrayType t = types.pop();
            type = t instanceof PsiEllipsisType ? new PsiEllipsisType(type, t.getAnnotations()) : new PsiArrayType(type, t.getAnnotations());
          }
          return type;
        } else if (type instanceof PsiDisjunctionType) {
          List<PsiType> components = ContainerUtil.newArrayList(((PsiDisjunctionType) type).getDisjunctions());
          components.set(0, components.get(0).annotate(provider));
          return ((PsiDisjunctionType) type).newDisjunctionType(components);
        } else {
          return type.annotate(provider);
        }
      }
    }

    return type;
  }

  public static void normalizeBrackets(@Nonnull PsiVariable variable) {
    CompositeElement variableElement = (CompositeElement) variable.getNode();

    PsiTypeElement typeElement = variable.getTypeElement();
    PsiIdentifier nameElement = variable.getNameIdentifier();
    LOG.assertTrue(typeElement != null && nameElement != null);

    ASTNode type = typeElement.getNode();
    ASTNode name = nameElement.getNode();

    ASTNode firstBracket = null;
    ASTNode lastBracket = null;
    int arrayCount = 0;
    ASTNode element = name;
    while (element != null) {
      element = PsiImplUtil.skipWhitespaceAndComments(element.getTreeNext());
      if (element == null || element.getElementType() != JavaTokenType.LBRACKET) {
        break;
      }
      if (firstBracket == null) {
        firstBracket = element;
      }
      lastBracket = element;
      arrayCount++;

      element = PsiImplUtil.skipWhitespaceAndComments(element.getTreeNext());
      if (element == null || element.getElementType() != JavaTokenType.RBRACKET) {
        break;
      }
      lastBracket = element;
    }

    if (firstBracket != null) {
      element = firstBracket;
      while (true) {
        ASTNode next = element.getTreeNext();
        variableElement.removeChild(element);
        if (element == lastBracket) {
          break;
        }
        element = next;
      }

      CompositeElement newType = (CompositeElement) type.clone();
      for (int i = 0; i < arrayCount; i++) {
        CompositeElement newType1 = ASTFactory.composite(JavaElementType.TYPE);
        newType1.rawAddChildren(newType);

        newType1.rawAddChildren(ASTFactory.leaf(JavaTokenType.LBRACKET, "["));
        newType1.rawAddChildren(ASTFactory.leaf(JavaTokenType.RBRACKET, "]"));
        newType = newType1;
        CodeEditUtil.markGenerated(newType);
      }
      newType.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(type));
      variableElement.replaceChild(type, newType);
    }
  }

  public static void setInitializer(PsiVariable variable, PsiExpression initializer) throws IncorrectOperationException {
    PsiExpression oldInitializer = variable.getInitializer();
    if (oldInitializer != null) {
      oldInitializer.delete();
    }
    if (initializer == null) {
      return;
    }
    CompositeElement variableElement = (CompositeElement) variable.getNode();
    ASTNode eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    if (eq == null) {
      final CharTable charTable = SharedImplUtil.findCharTableByTree(variableElement);
      eq = Factory.createSingleLeafElement(JavaTokenType.EQ, "=", 0, 1, charTable, variable.getManager());
      PsiElement identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      ASTNode node = PsiImplUtil.skipWhitespaceCommentsAndTokens(identifier.getNode().getTreeNext(), BRACKETS);
      variableElement.addInternal((TreeElement) eq, eq, node, Boolean.TRUE);
      eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
      assert eq != null : variable;
    }
    variable.addAfter(initializer, eq.getPsi());
  }

  @jakarta.annotation.Nonnull
  public static PsiType createTypeFromStub(@jakarta.annotation.Nonnull PsiModifierListOwner owner, @Nonnull TypeInfo typeInfo) {
    String typeText = TypeInfo.createTypeText(typeInfo);
    assert typeText != null : owner;
    PsiType type = JavaPsiFacade.getInstance(owner.getProject()).getParserFacade().createTypeFromText(typeText, owner);
    type = applyAnnotations(type, owner.getModifierList());
    return typeInfo.getTypeAnnotations().applyTo(type, owner);
  }

  @jakarta.annotation.Nonnull
  public static PsiElement getPatternVariableDeclarationScope(@Nonnull PsiPatternVariable variable) {
    PsiElement parent = variable.getPattern().getParent();
    if (!(parent instanceof PsiInstanceOfExpression) && !(parent instanceof PsiCaseLabelElementList) && !(parent instanceof PsiPattern)
      && !(parent instanceof PsiDeconstructionList)) {
      return parent;
    }
    return getInstanceOfPartDeclarationScope(parent);
  }

  @jakarta.annotation.Nullable
  public static PsiElement getPatternVariableDeclarationScope(@jakarta.annotation.Nonnull PsiInstanceOfExpression instanceOfExpression) {
    return getInstanceOfPartDeclarationScope(instanceOfExpression);
  }

  private static PsiElement getInstanceOfPartDeclarationScope(@Nonnull PsiElement parent) {
    boolean negated = false;
    for (PsiElement nextParent = parent.getParent(); ; parent = nextParent, nextParent = parent.getParent()) {
      if (nextParent instanceof PsiParenthesizedExpression) continue;
      if (nextParent instanceof PsiForeachStatementBase ||
        nextParent instanceof PsiConditionalExpression && parent == ((PsiConditionalExpression)nextParent).getCondition()) {
        return nextParent;
      }
      if (nextParent instanceof PsiPrefixExpression &&
        ((PsiPrefixExpression)nextParent).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        negated = !negated;
        continue;
      }
      if (nextParent instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)nextParent).getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) && !negated || tokenType.equals(JavaTokenType.OROR) && negated) continue;
      }
      if (nextParent instanceof PsiIfStatement) {
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      if (nextParent instanceof PsiConditionalLoopStatement) {
        if (!negated) return nextParent;
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      if (nextParent instanceof PsiSwitchLabelStatementBase) {
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      if (nextParent instanceof PsiPattern || nextParent instanceof PsiCaseLabelElementList ||
        (parent instanceof PsiPattern && nextParent instanceof PsiInstanceOfExpression) ||
        (parent instanceof PsiPattern && nextParent instanceof PsiDeconstructionList)) {
        continue;
      }
      return parent;
    }
  }

  private static class FilteringTypeAnnotationProvider implements TypeAnnotationProvider {
    private final PsiAnnotation[] myCandidates;
    private final TypeAnnotationProvider myOriginalProvider;
    private volatile PsiAnnotation[] myCache;

    private FilteringTypeAnnotationProvider(PsiAnnotation[] candidates, TypeAnnotationProvider originalProvider) {
      myCandidates = candidates;
      myOriginalProvider = originalProvider;
    }

    @Nonnull
    @Override
    public PsiAnnotation[] getAnnotations() {
      PsiAnnotation[] result = myCache;
      if (result == null) {
        List<PsiAnnotation> filtered = JBIterable.of(myCandidates).filter(annotation -> AnnotationTargetUtil.isTypeAnnotation(annotation)).append(myOriginalProvider.getAnnotations())
            .toList();
        myCache = result = filtered.isEmpty() ? PsiAnnotation.EMPTY_ARRAY : filtered.toArray(new PsiAnnotation[filtered.size()]);
      }
      return result;
    }
  }
}