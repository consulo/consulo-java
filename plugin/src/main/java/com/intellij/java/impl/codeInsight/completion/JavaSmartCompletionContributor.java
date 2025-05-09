/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.java.impl.psi.filters.ElementExtractorFilter;
import com.intellij.java.impl.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.java.impl.psi.filters.getters.JavaMembersGetter;
import com.intellij.java.impl.psi.filters.types.AssignableFromFilter;
import com.intellij.java.impl.psi.filters.types.AssignableToFilter;
import com.intellij.java.impl.psi.util.proximity.ReferenceListWeigher;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.filter.OrFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Sets;
import consulo.util.collection.SmartList;
import consulo.util.lang.reflect.ReflectionUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.StandardPatterns.or;

/**
 * @author peter
 */
@ExtensionImpl(id = "javaSmart", order = "last, before default")
public class JavaSmartCompletionContributor extends CompletionContributor {
  private static final HashingStrategy<ExpectedTypeInfo> EXPECTED_TYPE_INFO_STRATEGY = new HashingStrategy<ExpectedTypeInfo>() {
    @Override
    public int hashCode(final ExpectedTypeInfo object) {
      return object.getType().hashCode();
    }

    @Override
    public boolean equals(final ExpectedTypeInfo o1, final ExpectedTypeInfo o2) {
      return o1.getType().equals(o2.getType());
    }
  };

  private static final ElementExtractorFilter THROWABLES_FILTER = new ElementExtractorFilter(new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE));
  public static final ElementPattern<PsiElement> AFTER_NEW = psiElement().afterLeaf(psiElement().withText(PsiKeyword.NEW).andNot(psiElement().afterLeaf(psiElement().withText(PsiKeyword.THROW))));
  static final ElementPattern<PsiElement> AFTER_THROW_NEW = psiElement().afterLeaf(psiElement().withText(PsiKeyword.NEW).afterLeaf(PsiKeyword.THROW));
  public static final ElementPattern<PsiElement> INSIDE_EXPRESSION = or(psiElement().withParent(PsiExpression.class).andNot(psiElement().withParent(PsiLiteralExpression.class)).andNot(psiElement()
      .withParent(PsiMethodReferenceExpression.class)), psiElement().inside(PsiClassObjectAccessExpression.class), psiElement().inside(PsiThisExpression.class), psiElement().inside
      (PsiSuperExpression.class));
  static final ElementPattern<PsiElement> INSIDE_TYPECAST_EXPRESSION = psiElement().withParent(psiElement(PsiReferenceExpression.class).afterLeaf(psiElement().withText(")").withParent
      (PsiTypeCastExpression.class)));

  @Nullable
  private static ElementFilter getClassReferenceFilter(final PsiElement element, final boolean inRefList) {
    //throw new foo
    if (AFTER_THROW_NEW.accepts(element)) {
      return THROWABLES_FILTER;
    }

    //new xxx.yyy
    if (psiElement().afterLeaf(psiElement().withText(".")).withSuperParent(2, psiElement(PsiNewExpression.class)).accepts(element)) {
      if (((PsiNewExpression) element.getParent().getParent()).getClassReference() == element.getParent()) {
        PsiType[] types = ExpectedTypesGetter.getExpectedTypes(element, false);
        return new OrFilter(ContainerUtil.map2Array(types, ElementFilter.class, (Function<PsiType, ElementFilter>) type -> new AssignableFromFilter(type)));
      }
    }

    // extends/implements/throws
    if (inRefList) {
      return new ElementExtractorFilter(new ElementFilter() {
        @Override
        public boolean isAcceptable(Object aClass, @Nullable PsiElement context) {
          return aClass instanceof PsiClass && ReferenceListWeigher.INSTANCE.getApplicability((PsiClass) aClass, element) != ReferenceListWeigher.ReferenceListApplicability.inapplicable;
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      });
    }

    return null;
  }

  public JavaSmartCompletionContributor() {
    extend(CompletionType.SMART, SmartCastProvider.TYPECAST_TYPE_CANDIDATE, new SmartCastProvider());

    extend(CompletionType.SMART, SameSignatureCallParametersProvider.IN_CALL_ARGUMENT, new SameSignatureCallParametersProvider());

    extend(CompletionType.SMART, MethodReturnTypeProvider.IN_METHOD_RETURN_TYPE, new MethodReturnTypeProvider());

    extend(CompletionType.SMART, InstanceofTypeProvider.AFTER_INSTANCEOF, new InstanceofTypeProvider());

    extend(CompletionType.SMART, psiElement(), new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull final CompletionParameters parameters, final ProcessingContext context, @Nonnull final CompletionResultSet result) {
        if (SmartCastProvider.shouldSuggestCast(parameters)) {
          return;
        }

        final PsiElement element = parameters.getPosition();
        final PsiJavaCodeReferenceElement reference = PsiTreeUtil.findElementOfClassAtOffset(element.getContainingFile(), parameters.getOffset(), PsiJavaCodeReferenceElement.class, false);
        if (reference != null) {
          boolean inRefList = ReferenceListWeigher.INSIDE_REFERENCE_LIST.accepts(element);
          ElementFilter filter = getClassReferenceFilter(element, inRefList);
          if (filter != null) {
            final List<ExpectedTypeInfo> infos = Arrays.asList(getExpectedTypes(parameters));
            for (LookupElement item : completeReference(element, reference, filter, true, false, parameters, result.getPrefixMatcher())) {
              if (item.getObject() instanceof PsiClass) {
                if (!inRefList) {
                  item = LookupElementDecorator.withInsertHandler(item, ConstructorInsertHandler.SMART_INSTANCE);
                }
                result.addElement(decorate(item, infos));
              }
            }
          } else if (INSIDE_TYPECAST_EXPRESSION.accepts(element)) {
            final PsiTypeCastExpression cast = PsiTreeUtil.getContextOfType(element, PsiTypeCastExpression.class, true);
            if (cast != null && cast.getCastType() != null) {
              filter = new AssignableToFilter(cast.getCastType().getType());
              for (final LookupElement item : completeReference(element, reference, filter, false, true, parameters, result.getPrefixMatcher())) {
                result.addElement(item);
              }
            }
          }
        }
      }
    });

    extend(CompletionType.SMART, INSIDE_EXPRESSION, new ExpectedTypeBasedCompletionProvider() {
      @Override
      protected void addCompletions(final CompletionParameters params, final CompletionResultSet result, final Collection<ExpectedTypeInfo> _infos) {
        if (SmartCastProvider.shouldSuggestCast(params)) {
          return;
        }

        Consumer<LookupElement> noTypeCheck = decorateWithoutTypeCheck(result, _infos);

        Set<ExpectedTypeInfo> mergedInfos = Sets.newHashSet(_infos, EXPECTED_TYPE_INFO_STRATEGY);
        List<Runnable> chainedEtc = new ArrayList<>();
        for (final ExpectedTypeInfo info : mergedInfos) {
          Runnable slowContinuation = ReferenceExpressionCompletionContributor.fillCompletionVariants(new JavaSmartCompletionParameters(params, info), noTypeCheck);
          ContainerUtil.addIfNotNull(chainedEtc, slowContinuation);
        }
        addExpectedTypeMembers(params, mergedInfos, true, noTypeCheck);

        PsiElement parent = params.getPosition().getParent();
        if (parent instanceof PsiReferenceExpression) {
          CollectConversion.addCollectConversion((PsiReferenceExpression) parent, mergedInfos, noTypeCheck);
        }

        for (final ExpectedTypeInfo info : mergedInfos) {
          BasicExpressionCompletionContributor.fillCompletionVariants(new JavaSmartCompletionParameters(params, info), lookupElement ->
          {
            final PsiType psiType = JavaCompletionUtil.getLookupElementType(lookupElement);
            if (psiType != null && info.getType().isAssignableFrom(psiType)) {
              result.addElement(decorate(lookupElement, _infos));
            }
          }, result.getPrefixMatcher());

        }

        for (Runnable runnable : chainedEtc) {
          runnable.run();
        }


        final boolean searchInheritors = params.getInvocationCount() > 1;
        if (searchInheritors) {
          addExpectedTypeMembers(params, mergedInfos, false, noTypeCheck);
        }
      }
    });

    extend(CompletionType.SMART, ExpectedAnnotationsProvider.ANNOTATION_ATTRIBUTE_VALUE, new ExpectedAnnotationsProvider());

    extend(CompletionType.SMART, CatchTypeProvider.CATCH_CLAUSE_TYPE, new CatchTypeProvider());

    extend(CompletionType.SMART, TypeArgumentCompletionProvider.IN_TYPE_ARGS, new TypeArgumentCompletionProvider(true, null));

    extend(CompletionType.SMART, AFTER_NEW, new JavaInheritorsGetter(ConstructorInsertHandler.SMART_INSTANCE));

    extend(CompletionType.SMART, LabelReferenceCompletion.LABEL_REFERENCE, new LabelReferenceCompletion());

    extend(CompletionType.SMART, psiElement(), new FunctionalExpressionCompletionProvider());
    extend(CompletionType.SMART, psiElement(), new MethodReferenceCompletionProvider());
  }

  @Nonnull
  private static Consumer<LookupElement> decorateWithoutTypeCheck(final CompletionResultSet result, final Collection<ExpectedTypeInfo> infos) {
    return lookupElement -> result.addElement(decorate(lookupElement, infos));
  }

  private static void addExpectedTypeMembers(CompletionParameters params, Set<ExpectedTypeInfo> mergedInfos, boolean quick, Consumer<LookupElement> consumer) {
    PsiElement position = params.getPosition();
    if (!JavaKeywordCompletion.AFTER_DOT.accepts(position)) {
      for (ExpectedTypeInfo info : mergedInfos) {
        new JavaMembersGetter(info.getType(), params).addMembers(!quick, consumer);
        if (!info.getDefaultType().equals(info.getType())) {
          new JavaMembersGetter(info.getDefaultType(), params).addMembers(!quick, consumer);
        }
      }
    }
  }

  @Override
  public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet result) {
    if (parameters.getPosition() instanceof PsiComment) {
      return;
    }

    super.fillCompletionVariants(parameters, JavaCompletionSorting.addJavaSorting(parameters, result));
  }

  public static SmartCompletionDecorator decorate(LookupElement lookupElement, Collection<ExpectedTypeInfo> infos) {
    return new SmartCompletionDecorator(lookupElement, infos);
  }

  @Nonnull
  public static ExpectedTypeInfo[] getExpectedTypes(final CompletionParameters parameters) {
    return getExpectedTypes(parameters.getPosition(), parameters.getCompletionType() == CompletionType.SMART);
  }

  @Nonnull
  public static ExpectedTypeInfo[] getExpectedTypes(PsiElement position, boolean voidable) {
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(position)) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(position.getProject()).getElementFactory();
      final PsiClassType classType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, position.getResolveScope());
      final List<ExpectedTypeInfo> result = new SmartList<>();
      result.add(new ExpectedTypeInfoImpl(classType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, classType, TailType.SEMICOLON, null, ExpectedTypeInfoImpl.NULL));
      final PsiMethod method = PsiTreeUtil.getContextOfType(position, PsiMethod.class, true);
      if (method != null) {
        for (final PsiClassType type : method.getThrowsList().getReferencedTypes()) {
          result.add(new ExpectedTypeInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.SEMICOLON, null, ExpectedTypeInfoImpl.NULL));
        }
      }
      return result.toArray(new ExpectedTypeInfo[result.size()]);
    }

    PsiExpression expression = PsiTreeUtil.getContextOfType(position, PsiExpression.class, true);
    if (expression == null) {
      return ExpectedTypeInfo.EMPTY_ARRAY;
    }

    return ExpectedTypesProvider.getExpectedTypes(expression, true, voidable, false);
  }

  static Set<LookupElement> completeReference(final PsiElement element,
                                              PsiJavaCodeReferenceElement reference,
                                              final ElementFilter filter,
                                              final boolean acceptClasses,
                                              final boolean acceptMembers,
                                              CompletionParameters parameters,
                                              final PrefixMatcher matcher) {
    ElementFilter checkClass = new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return filter.isAcceptable(element, context);
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        if (ReflectionUtil.isAssignable(PsiClass.class, hintClass)) {
          return acceptClasses;
        }

        if (ReflectionUtil.isAssignable(PsiVariable.class, hintClass) || ReflectionUtil.isAssignable(PsiMethod.class, hintClass) || ReflectionUtil.isAssignable(CandidateInfo.class, hintClass)) {
          return acceptMembers;
        }
        return false;
      }
    };
    JavaCompletionProcessor.Options options = JavaCompletionProcessor.Options.DEFAULT_OPTIONS.withFilterStaticAfterInstance(parameters.getInvocationCount() <= 1);
    return JavaCompletionUtil.processJavaReference(element, reference, checkClass, options, matcher, parameters);
  }

  @Override
  public void beforeCompletion(@Nonnull CompletionInitializationContext context) {
    if (context.getCompletionType() != CompletionType.SMART) {
      return;
    }

    if (!context.getEditor().getSelectionModel().hasSelection()) {
      final PsiFile file = context.getFile();
      PsiElement element = file.findElementAt(context.getStartOffset());
      if (element instanceof PsiIdentifier) {
        element = element.getParent();
        while (element instanceof PsiJavaCodeReferenceElement || element instanceof PsiCall || element instanceof PsiThisExpression || element instanceof PsiSuperExpression || element
            instanceof PsiTypeElement || element instanceof PsiClassObjectAccessExpression) {
          int newEnd = element.getTextRange().getEndOffset();
          if (element instanceof PsiMethodCallExpression) {
            newEnd = ((PsiMethodCallExpression) element).getMethodExpression().getTextRange().getEndOffset();
          } else if (element instanceof PsiNewExpression) {
            final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) element).getClassReference();
            if (classReference != null) {
              newEnd = classReference.getTextRange().getEndOffset();
            }
          }
          context.setReplacementOffset(newEnd);
          element = element.getParent();
        }
      }
    }

    PsiElement lastElement = context.getFile().findElementAt(context.getStartOffset() - 1);
    if (lastElement != null && lastElement.getText().equals("(") && lastElement.getParent() instanceof PsiParenthesizedExpression) {
      // don't trim dummy identifier or we won't be able to determine the type of the expression after '('
      // which is needed to insert correct cast
      return;
    }
    context.setDummyIdentifier(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
