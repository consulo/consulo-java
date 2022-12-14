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
import com.intellij.java.impl.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PatternCondition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;
import static com.intellij.java.impl.codeInsight.completion.JavaCompletionContributor.isInJavaContext;
import static com.intellij.java.language.patterns.PsiJavaPatterns.*;
import static consulo.language.pattern.StandardPatterns.or;

/**
 * @author Pavel.Dolgov
 */
@ExtensionImpl(id = "javaReflection", order = "last, before javaLegacy")
public class JavaReflectionCompletionContributor extends CompletionContributor {
  private static final String CONSTRUCTOR = "getConstructor";
  private static final String DECLARED_CONSTRUCTOR = "getDeclaredConstructor";
  private static final String ANNOTATION = "getAnnotation";
  private static final String DECLARED_ANNOTATION = "getDeclaredAnnotation";
  private static final String ANNOTATIONS_BY_TYPE = "getAnnotationsByType";
  private static final String DECLARED_ANNOTATIONS_BY_TYPE = "getDeclaredAnnotationsByType";
  private static final String ANNOTATED_ELEMENT = "java.lang.reflect.AnnotatedElement";

  private static final Set<String> DECLARED_NAMES = Set.of(DECLARED_CONSTRUCTOR, DECLARED_ANNOTATION, DECLARED_ANNOTATIONS_BY_TYPE);
  private static final ElementPattern<? extends PsiElement> CONSTRUCTOR_ARGUMENTS = psiElement(PsiExpressionList.class).withParent(psiExpression().methodCall(psiMethod().withName(CONSTRUCTOR,
      DECLARED_CONSTRUCTOR).definedInClass(JavaClassNames.JAVA_LANG_CLASS)));

  private static final ElementPattern<? extends PsiElement> ANNOTATION_ARGUMENTS = psiElement(PsiExpressionList.class).withParent(psiExpression().methodCall(psiMethod().withName(ANNOTATION,
      DECLARED_ANNOTATION, ANNOTATIONS_BY_TYPE, DECLARED_ANNOTATIONS_BY_TYPE).with(new MethodDefinedInInterfacePatternCondition(ANNOTATED_ELEMENT))));

  private static final ElementPattern<PsiElement> BEGINNING_OF_CONSTRUCTOR_ARGUMENTS = beginningOfArguments(CONSTRUCTOR_ARGUMENTS);

  private static final ElementPattern<PsiElement> BEGINNING_OF_ANNOTATION_ARGUMENTS = beginningOfArguments(ANNOTATION_ARGUMENTS);

  private static ElementPattern<PsiElement> beginningOfArguments(ElementPattern<? extends PsiElement> argumentsPattern) {
    return psiElement().afterLeaf("(").withParent(or(psiExpression().withParent(argumentsPattern), psiElement().withParent(PsiTypeElement.class) // special case for getConstructor(int.class)
        // because 'int' is a keyword
        .withSuperParent(2, PsiClassObjectAccessExpression.class).withSuperParent(3, argumentsPattern)));
  }

  @Override
  public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }

    final PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) {
      return;
    }

    if (BEGINNING_OF_ANNOTATION_ARGUMENTS.accepts(position)) {
      addVariants(position, (psiClass, isDeclared) -> addAnnotationClasses(psiClass, isDeclared, result));
      //TODO handle annotations on fields and methods
    } else if (BEGINNING_OF_CONSTRUCTOR_ARGUMENTS.accepts(position)) {
      addVariants(position, (psiClass, isDeclared) -> addConstructorParameterTypes(psiClass, isDeclared, result));
    }
  }

  private static void addVariants(PsiElement position, BiConsumer<PsiClass, Boolean> variantAdder) {
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      JavaReflectionReferenceUtil.ReflectiveClass reflectiveClass = getReflectiveClass(methodCall.getMethodExpression().getQualifierExpression());
      if (reflectiveClass != null) {
        String methodName = methodCall.getMethodExpression().getReferenceName();
        if (methodName != null) {
          variantAdder.accept(reflectiveClass.getPsiClass(), DECLARED_NAMES.contains(methodName));
        }
      }
    }
  }

  private static void addAnnotationClasses(@Nonnull PsiClass psiClass, boolean isDeclared, @Nonnull CompletionResultSet result) {
    Set<PsiAnnotation> declaredAnnotations = isDeclared ? Set.of(AnnotationUtil.getAllAnnotations(psiClass, false, null, false)) : null;

    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(psiClass, true, null, false);
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        PsiElement resolved = referenceElement.resolve();
        if (resolved instanceof PsiClass) {
          PsiClass annotationClass = (PsiClass) resolved;
          String className = annotationClass.getName();
          if (className != null) {
            LookupElement lookupElement = LookupElementBuilder.createWithIcon(annotationClass).withPresentableText(className + ".class").withInsertHandler
                (JavaReflectionCompletionContributor::handleAnnotationClassInsertion);
            if (isDeclared) {
              lookupElement = withPriority(lookupElement, declaredAnnotations.contains(annotation));
            }
            result.addElement(lookupElement);
          }
        }
      }
    }
  }

  private static void addConstructorParameterTypes(@Nonnull PsiClass psiClass, boolean isDeclared, @Nonnull CompletionResultSet result) {
    PsiMethod[] constructors = psiClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      LookupElement lookupElement = JavaLookupElementBuilder.forMethod(constructor, PsiSubstitutor.EMPTY).withInsertHandler
          (JavaReflectionCompletionContributor::handleConstructorSignatureInsertion);
      if (isDeclared) {
        lookupElement = withPriority(lookupElement, isPublic(constructor));
      }
      result.addElement(lookupElement);
    }
  }

  private static void handleAnnotationClassInsertion(@Nonnull InsertionContext context, @Nonnull LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiClass) {
      String className = ((PsiClass) object).getQualifiedName();
      if (className != null) {
        handleParametersInsertion(context, className + ".class");
      }
    }
  }

  private static void handleConstructorSignatureInsertion(@Nonnull InsertionContext context, @Nonnull LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiMethod) {
      String text = getParameterTypesText((PsiMethod) object);
      if (text != null) {
        handleParametersInsertion(context, text);
      }
    }
  }

  private static void handleParametersInsertion(@Nonnull InsertionContext context, @Nonnull String text) {
    PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    PsiExpressionList parameterList = PsiTreeUtil.getParentOfType(newElement, PsiExpressionList.class);
    if (parameterList != null) {
      final TextRange range = parameterList.getTextRange();
      context.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "(" + text + ")");
      context.commitDocument();
      shortenArgumentsClassReferences(context);
    }
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  private static class MethodDefinedInInterfacePatternCondition extends PatternCondition<PsiMethod> {
    private final String myInterfaceName;

    public MethodDefinedInInterfacePatternCondition(@Nonnull String interfaceName) {
      super("definedInInterface");
      myInterfaceName = interfaceName;
    }

    @Override
    public boolean accepts(@Nonnull PsiMethod method, ProcessingContext context) {
      PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, false, myInterfaceName);
    }
  }
}
