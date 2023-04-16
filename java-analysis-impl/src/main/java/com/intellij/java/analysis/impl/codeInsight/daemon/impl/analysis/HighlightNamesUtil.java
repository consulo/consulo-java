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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.psi.*;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.TextAttributesScheme;
import consulo.content.scope.NamedScope;
import consulo.content.scope.NamedScopesHolder;
import consulo.content.scope.PackageSet;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.packageDependency.DependencyValidationManager;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.editor.rawHighlight.RainbowHighlighter;
import consulo.language.editor.rawHighlight.SeverityRegistrarUtil;
import consulo.language.editor.scope.ScopeAttributesUtil;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.lang.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class HighlightNamesUtil {
  private static final Logger LOG = Logger.getInstance(HighlightNamesUtil.class);

  @Nullable
  public static HighlightInfo highlightMethodName(@Nonnull PsiMethod method, @Nonnull PsiElement elementToHighlight, final boolean isDeclaration, @Nonnull TextAttributesScheme colorsScheme) {
    return highlightMethodName(method, elementToHighlight, elementToHighlight.getTextRange(), colorsScheme, isDeclaration);
  }

  /**
   * @param methodOrClass method to highlight; class is passed instead of implicit constructor
   */
  @Nullable
  public static HighlightInfo highlightMethodName(@Nonnull PsiMember methodOrClass,
                                                  @Nonnull PsiElement elementToHighlight,
                                                  @Nonnull TextRange range,
                                                  @Nonnull TextAttributesScheme colorsScheme,
                                                  final boolean isDeclaration) {
    boolean isInherited = false;

    if (!isDeclaration) {
      if (isCalledOnThis(elementToHighlight)) {
        final PsiClass containingClass = methodOrClass instanceof PsiMethod ? methodOrClass.getContainingClass() : null;
        PsiClass enclosingClass = containingClass == null ? null : PsiTreeUtil.getParentOfType(elementToHighlight, PsiClass.class);
        while (enclosingClass != null) {
          isInherited = enclosingClass.isInheritor(containingClass, true);
          if (isInherited) {
            break;
          }
          enclosingClass = PsiTreeUtil.getParentOfType(enclosingClass, PsiClass.class, true);
        }
      }
    }

    LOG.assertTrue(methodOrClass instanceof PsiMethod || !isDeclaration);
    HighlightInfoType type = methodOrClass instanceof PsiMethod ? getMethodNameHighlightType((PsiMethod) methodOrClass, isDeclaration, isInherited) : JavaHighlightInfoTypes.CONSTRUCTOR_CALL;
    if (type != null) {
      TextAttributes attributes = mergeWithScopeAttributes(methodOrClass, type, colorsScheme);
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
      if (attributes != null) {
        builder.textAttributes(attributes);
      }
      return builder.createUnconditionally();
    }
    return null;
  }

  private static boolean isCalledOnThis(@Nonnull PsiElement elementToHighlight) {
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(elementToHighlight, PsiMethodCallExpression.class);
    if (methodCallExpression != null) {
      PsiElement qualifier = methodCallExpression.getMethodExpression().getQualifier();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        return true;
      }
    }
    return false;
  }

  private static TextAttributes mergeWithScopeAttributes(@Nullable PsiElement element, @Nonnull HighlightInfoType type, @Nonnull TextAttributesScheme colorsScheme) {
    TextAttributes regularAttributes = SeverityRegistrarUtil.getAttributesByType(element, type, colorsScheme);
    if (element == null) {
      return regularAttributes;
    }
    TextAttributes scopeAttributes = getScopeAttributes(element, colorsScheme);
    return TextAttributes.merge(scopeAttributes, regularAttributes);
  }

  @Nonnull
  public static HighlightInfo highlightClassName(@Nullable PsiClass aClass, @Nonnull PsiElement elementToHighlight, @Nonnull TextAttributesScheme colorsScheme) {
    TextRange range = elementToHighlight.getTextRange();
    if (elementToHighlight instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) elementToHighlight;
      PsiElement identifier = referenceElement.getReferenceNameElement();
      if (identifier != null) {
        range = identifier.getTextRange();
      }
    }

    // This will highlight @ sign in annotation as well.
    final PsiElement parent = elementToHighlight.getParent();
    if (parent instanceof PsiAnnotation) {
      final PsiAnnotation psiAnnotation = (PsiAnnotation) parent;
      range = new TextRange(psiAnnotation.getTextRange().getStartOffset(), range.getEndOffset());
    }

    HighlightInfoType type = getClassNameHighlightType(aClass, elementToHighlight);
    TextAttributes attributes = mergeWithScopeAttributes(aClass, type, colorsScheme);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
    if (attributes != null) {
      builder.textAttributes(attributes);
    }
    return builder.createUnconditionally();
  }

  @Nullable
  public static HighlightInfo highlightVariableName(@Nonnull PsiVariable variable, @Nonnull PsiElement elementToHighlight, @Nonnull TextAttributesScheme colorsScheme) {
    HighlightInfoType varType = getVariableNameHighlightType(variable);
    if (varType == null) {
      return null;
    }
    if (variable instanceof PsiField) {
      TextAttributes attributes = mergeWithScopeAttributes(variable, varType, colorsScheme);
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(varType).range(elementToHighlight.getTextRange());
      if (attributes != null) {
        builder.textAttributes(attributes);
      }
      return builder.createUnconditionally();
    }

    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(varType).range(elementToHighlight);
    return RainbowHighlighter.isRainbowEnabledWithInheritance(colorsScheme, JavaLanguage.INSTANCE) ? builder.createUnconditionally() : builder.create();
  }

  @Nullable
  public static HighlightInfo highlightClassNameInQualifier(@Nonnull PsiJavaCodeReferenceElement element, @Nonnull TextAttributesScheme colorsScheme) {
    PsiElement qualifierExpression = element.getQualifier();
    if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement) qualifierExpression).resolve();
      if (resolved instanceof PsiClass) {
        return highlightClassName((PsiClass) resolved, qualifierExpression, colorsScheme);
      }
    }
    return null;
  }

  private static HighlightInfoType getMethodNameHighlightType(@Nonnull PsiMethod method, boolean isDeclaration, boolean isInheritedMethod) {
    if (method.isConstructor()) {
      return isDeclaration ? JavaHighlightInfoTypes.CONSTRUCTOR_DECLARATION : JavaHighlightInfoTypes.CONSTRUCTOR_CALL;
    }
    if (isDeclaration) {
      return JavaHighlightInfoTypes.METHOD_DECLARATION;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return JavaHighlightInfoTypes.STATIC_METHOD;
    }
    if (isInheritedMethod) {
      return JavaHighlightInfoTypes.INHERITED_METHOD;
    }
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return JavaHighlightInfoTypes.ABSTRACT_METHOD;
    }
    return JavaHighlightInfoTypes.METHOD_CALL;
  }

  @Nullable
  private static HighlightInfoType getVariableNameHighlightType(@Nonnull PsiVariable var) {
    if (var instanceof PsiLocalVariable || var instanceof PsiParameter && ((PsiParameter) var).getDeclarationScope() instanceof PsiForeachStatement) {
      return JavaHighlightInfoTypes.LOCAL_VARIABLE;
    }
    if (var instanceof PsiField) {
      return var.hasModifierProperty(PsiModifier.STATIC) ? var.hasModifierProperty(PsiModifier.FINAL) ? JavaHighlightInfoTypes.STATIC_FINAL_FIELD : JavaHighlightInfoTypes.STATIC_FIELD : var
          .hasModifierProperty(PsiModifier.FINAL) ? JavaHighlightInfoTypes.INSTANCE_FINAL_FIELD : JavaHighlightInfoTypes.INSTANCE_FIELD;
    }
    if (var instanceof PsiParameter) {
      return ((PsiParameter) var).getDeclarationScope() instanceof PsiLambdaExpression ? JavaHighlightInfoTypes.LAMBDA_PARAMETER : JavaHighlightInfoTypes.PARAMETER;
    }
    return null;
  }

  @Nonnull
  private static HighlightInfoType getClassNameHighlightType(@Nullable PsiClass aClass, @Nullable PsiElement element) {
    if (element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiAnonymousClass) {
      return JavaHighlightInfoTypes.ANONYMOUS_CLASS_NAME;
    }
    if (aClass != null) {
      if (aClass.isAnnotationType()) {
        return JavaHighlightInfoTypes.ANNOTATION_NAME;
      }
      if (aClass.isInterface()) {
        return JavaHighlightInfoTypes.INTERFACE_NAME;
      }
      if (aClass.isEnum()) {
        return JavaHighlightInfoTypes.ENUM_NAME;
      }
      if (aClass instanceof PsiTypeParameter) {
        return JavaHighlightInfoTypes.TYPE_PARAMETER_NAME;
      }
      final PsiModifierList modList = aClass.getModifierList();
      if (modList != null && modList.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return JavaHighlightInfoTypes.ABSTRACT_CLASS_NAME;
      }
    }
    // use class by default
    return JavaHighlightInfoTypes.CLASS_NAME;
  }

  @Nullable
  public static HighlightInfo highlightReassignedVariable(@Nonnull PsiVariable variable, @Nonnull PsiElement elementToHighlight) {
    if (variable instanceof PsiLocalVariable) {
      return HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.REASSIGNED_LOCAL_VARIABLE).range(elementToHighlight).create();
    }
    if (variable instanceof PsiParameter) {
      return HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.REASSIGNED_PARAMETER).range(elementToHighlight).create();
    }
    return null;
  }

  private static TextAttributes getScopeAttributes(@Nonnull PsiElement element, @Nonnull TextAttributesScheme colorsScheme) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return null;
    }
    TextAttributes result = null;
    DependencyValidationManager validationManager = DependencyValidationManager.getInstance(file.getProject());
    List<Pair<NamedScope, NamedScopesHolder>> scopes = validationManager.getScopeBasedHighlightingCachedScopes();
    for (Pair<NamedScope, NamedScopesHolder> scope : scopes) {
      final NamedScope namedScope = scope.getFirst();
      final TextAttributesKey scopeKey = ScopeAttributesUtil.getScopeTextAttributeKey(namedScope.getName());
      final TextAttributes attributes = colorsScheme.getAttributes(scopeKey);
      if (attributes == null || attributes.isEmpty()) {
        continue;
      }
      final PackageSet packageSet = namedScope.getValue();
      if (packageSet != null && packageSet.contains(file.getVirtualFile(), file.getProject(), scope.getSecond())) {
        result = TextAttributes.merge(attributes, result);
      }
    }
    return result;
  }

  @Nonnull
  public static TextRange getMethodDeclarationTextRange(@Nonnull PsiMethod method) {
    if (method instanceof SyntheticElement) {
      return TextRange.EMPTY_RANGE;
    }
    int start = stripAnnotationsFromModifierList(method.getModifierList());
    final TextRange throwsRange = method.getThrowsList().getTextRange();
    LOG.assertTrue(throwsRange != null, method);
    int end = throwsRange.getEndOffset();
    return new TextRange(start, end);
  }

  @Nonnull
  public static TextRange getFieldDeclarationTextRange(@Nonnull PsiField field) {
    int start = stripAnnotationsFromModifierList(field.getModifierList());
    int end = field.getNameIdentifier().getTextRange().getEndOffset();
    return new TextRange(start, end);
  }

  @Nonnull
  public static TextRange getClassDeclarationTextRange(@Nonnull PsiClass aClass) {
    if (aClass instanceof PsiEnumConstantInitializer) {
      return ((PsiEnumConstantInitializer) aClass).getEnumConstant().getNameIdentifier().getTextRange();
    }
    final PsiElement psiElement = aClass instanceof PsiAnonymousClass ? ((PsiAnonymousClass) aClass).getBaseClassReference() : aClass.getModifierList() == null ? aClass.getNameIdentifier() :
        aClass.getModifierList();
    if (psiElement == null) {
      return new TextRange(aClass.getTextRange().getStartOffset(), aClass.getTextRange().getStartOffset());
    }
    int start = stripAnnotationsFromModifierList(psiElement);
    PsiElement endElement = aClass instanceof PsiAnonymousClass ? ((PsiAnonymousClass) aClass).getBaseClassReference() : aClass.getImplementsList();
    if (endElement == null) {
      endElement = aClass.getNameIdentifier();
    }
    TextRange endTextRange = endElement == null ? TextRange.EMPTY_RANGE : endElement.getTextRange();
    int end = endTextRange == TextRange.EMPTY_RANGE ? start : endTextRange.getEndOffset();
    return new TextRange(start, end);
  }

  private static int stripAnnotationsFromModifierList(@Nonnull PsiElement element) {
    TextRange textRange = element.getTextRange();
    if (textRange == TextRange.EMPTY_RANGE) {
      return 0;
    }
    PsiAnnotation lastAnnotation = null;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof PsiAnnotation) {
        lastAnnotation = (PsiAnnotation) child;
      }
    }
    if (lastAnnotation == null) {
      return textRange.getStartOffset();
    }
    ASTNode node = lastAnnotation.getNode();
    if (node != null) {
      do {
        node = TreeUtil.nextLeaf(node);
      }
      while (node != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(node.getElementType()));
    }
    if (node != null) {
      return node.getTextRange().getStartOffset();
    }
    return textRange.getStartOffset();
  }

  public static HighlightInfo highlightPackage(@Nonnull PsiElement resolved, @Nonnull PsiJavaCodeReferenceElement elementToHighlight, @Nonnull TextAttributesScheme scheme) {
    PsiElement referenceNameElement = elementToHighlight.getReferenceNameElement();
    TextRange range;
    if (referenceNameElement == null) {
      range = elementToHighlight.getTextRange();
    } else {
      PsiElement nextSibling = PsiTreeUtil.nextLeaf(referenceNameElement);
      if (nextSibling != null && nextSibling.getTextRange().isEmpty()) {
        // empty PsiReferenceParameterList
        nextSibling = PsiTreeUtil.nextLeaf(nextSibling);
      }
      if (nextSibling instanceof PsiJavaToken && ((PsiJavaToken) nextSibling).getTokenType() == JavaTokenType.DOT) {
        range = new TextRange(referenceNameElement.getTextRange().getStartOffset(), nextSibling.getTextRange().getEndOffset());
      } else {
        range = referenceNameElement.getTextRange();
      }
    }
    HighlightInfoType type = JavaHighlightInfoTypes.PACKAGE_NAME;
    TextAttributes attributes = mergeWithScopeAttributes(resolved, type, scheme);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
    if (attributes != null) {
      builder.textAttributes(attributes);
    }
    return builder.createUnconditionally();
  }
}
