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
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl;

import com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.java.impl.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.patterns.PsiJavaElementPattern;
import com.intellij.java.language.patterns.PsiMethodPattern;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;
import static com.intellij.java.impl.codeInsight.completion.JavaCompletionContributor.isInJavaContext;
import static com.intellij.java.language.patterns.PsiJavaPatterns.*;
import static consulo.language.pattern.StandardPatterns.or;

/**
 * @author Pavel.Dolgov
 */
@ExtensionImpl(id = "javaMethodHandle", order = "last, before javaLegacy")
public class JavaMethodHandleCompletionContributor extends CompletionContributor {

  // MethodHandle for constructors and methods
  private static final Set<String> METHOD_HANDLE_FACTORY_NAMES = Set.of(FIND_CONSTRUCTOR, FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL);

  private static final PsiJavaElementPattern.Capture<PsiElement> METHOD_TYPE_ARGUMENT_PATTERN = psiJavaElement().afterLeaf(",").withParent(or(psiExpression().methodCallParameter(1, methodPattern
      (FIND_CONSTRUCTOR)), psiExpression().methodCallParameter(2, methodPattern(FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL))));


  // VarHandle for fields and synthetic MethodHandle for field getters/setters
  private static final Set<String> FIELD_HANDLE_FACTORY_NAMES = Set.of(FIND_GETTER, FIND_SETTER, FIND_STATIC_GETTER, FIND_STATIC_SETTER, FIND_VAR_HANDLE,
      FIND_STATIC_VAR_HANDLE);

  private static final PsiJavaElementPattern.Capture<PsiElement> FIELD_TYPE_ARGUMENT_PATTERN = psiJavaElement().afterLeaf(",").withParent(psiExpression().methodCallParameter(2, methodPattern(ArrayUtil
      .toStringArray(FIELD_HANDLE_FACTORY_NAMES))));


  @Nonnull
  private static PsiMethodPattern methodPattern(String... methodNames) {
    return psiMethod().withName(methodNames).definedInClass(JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP);
  }

  @Override
  public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) {
      return;
    }

    if (METHOD_TYPE_ARGUMENT_PATTERN.accepts(position)) {
      addMethodHandleVariants(position, result);
    } else if (FIELD_TYPE_ARGUMENT_PATTERN.accepts(position)) {
      addFieldHandleVariants(position, result);
    }
  }

  private static void addMethodHandleVariants(@Nonnull PsiElement position, @Nonnull Consumer<LookupElement> result) {
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      String methodName = methodCall.getMethodExpression().getReferenceName();
      if (methodName != null && METHOD_HANDLE_FACTORY_NAMES.contains(methodName)) {
        PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
        ReflectiveClass reflectiveClass = arguments.length != 0 ? getReflectiveClass(arguments[0]) : null;
        if (reflectiveClass != null) {
          switch (methodName) {
            case FIND_CONSTRUCTOR:
              addConstructorSignatures(reflectiveClass.getPsiClass(), position, result);
              break;
            case FIND_VIRTUAL:
            case FIND_STATIC:
            case FIND_SPECIAL:
              String name = arguments.length > 1 ? computeConstantExpression(arguments[1], String.class) : null;
              if (!StringUtil.isEmpty(name)) {
                addMethodSignatures(reflectiveClass.getPsiClass(), name, FIND_STATIC.equals(methodName), position, result);
              }
              break;
          }
        }
      }
    }
  }

  private static void addConstructorSignatures(@Nonnull PsiClass psiClass, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result) {
    String className = psiClass.getName();
    if (className != null) {
      PsiMethod[] constructors = psiClass.getConstructors();
      if (constructors.length != 0) {
        lookupMethodTypes(Arrays.stream(constructors), context, result);
      } else {
        result.accept(lookupSignature(ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE, context));
      }
    }
  }

  private static void addMethodSignatures(@Nonnull PsiClass psiClass, @Nonnull String methodName, boolean isStaticExpected, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result) {
    PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
    if (methods.length != 0) {
      Stream<PsiMethod> methodStream = Arrays.stream(methods).filter(method -> method.hasModifierProperty(PsiModifier.STATIC) == isStaticExpected);
      lookupMethodTypes(methodStream, context, result);
    }
  }

  private static void lookupMethodTypes(@Nonnull Stream<PsiMethod> methods, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result) {
    methods.map(JavaReflectionReferenceUtil::getMethodSignature).filter(Objects::nonNull).sorted(ReflectiveSignature::compareTo).map(signature -> lookupSignature(signature, context)).forEach
        (result::accept);
  }

  @Nonnull
  private static LookupElement lookupSignature(@Nonnull ReflectiveSignature signature, @Nonnull PsiElement context) {
    String expressionText = getMethodTypeExpressionText(signature);
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiExpression expression = factory.createExpressionFromText(expressionText, context);

    String shortTypes = signature.getText(true, type -> PsiNameHelper.getShortClassName(type) + ".class");
    String presentableText = PsiNameHelper.getShortClassName(JAVA_LANG_INVOKE_METHOD_TYPE) + "." + METHOD_TYPE + shortTypes;
    String lookupText = METHOD_TYPE + signature.getText(true, PsiNameHelper::getShortClassName);

    return lookupExpression(expression, AllIcons.Nodes.Method, presentableText, lookupText);
  }

  private static void addFieldHandleVariants(@Nonnull PsiElement position, @Nonnull Consumer<LookupElement> result) {
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      String methodName = methodCall.getMethodExpression().getReferenceName();
      if (methodName != null && FIELD_HANDLE_FACTORY_NAMES.contains(methodName)) {
        PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
        if (arguments.length > 2) {
          String fieldName = computeConstantExpression(arguments[1], String.class);
          if (!StringUtil.isEmpty(fieldName)) {
            ReflectiveClass reflectiveClass = getReflectiveClass(arguments[0]);
            if (reflectiveClass != null) {
              addFieldType(reflectiveClass.getPsiClass(), fieldName, position, result);
            }
          }
        }
      }
    }
  }

  private static void addFieldType(@Nonnull PsiClass psiClass, @Nonnull String fieldName, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result) {
    PsiField field = psiClass.findFieldByName(fieldName, false);
    if (field != null) {
      String typeText = getTypeText(field.getType());
      PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
      PsiExpression expression = factory.createExpressionFromText(typeText + ".class", context);

      String shortType = PsiNameHelper.getShortClassName(typeText);
      result.accept(lookupExpression(expression, AllIcons.Nodes.Class, shortType + ".class", shortType));
    }
  }

  @Nonnull
  private static LookupElement lookupExpression(@Nonnull PsiExpression expression, @Nullable Image icon, @Nonnull String presentableText, @Nonnull String lookupText) {
    LookupElement element = new ExpressionLookupItem(expression, icon, presentableText, lookupText) {
      @Override
      public void handleInsert(InsertionContext context) {
        context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        context.commitDocument();
        replaceText(context, getObject().getText());
      }
    };
    return PrioritizedLookupElement.withPriority(element, 1);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
