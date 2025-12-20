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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.java.impl.codeInsight.lookup.LookupItemUtil;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.impl.psi.filters.ElementExtractorFilter;
import com.intellij.java.impl.psi.filters.classes.AssignableFromContextFilter;
import com.intellij.java.impl.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.java.impl.psi.filters.getters.JavaMembersGetter;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.filters.classes.AnnotationTypeFilter;
import com.intellij.java.language.impl.psi.filters.element.ModifierFilter;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiLabelReference;
import com.intellij.java.language.impl.psi.scope.ElementClassFilter;
import com.intellij.java.language.patterns.PsiJavaElementPattern;
import com.intellij.java.language.patterns.PsiNameValuePairPattern;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.DocumentUtil;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.impl.idea.codeInsight.completion.CodeCompletionFeatures;
import consulo.ide.impl.idea.codeInsight.completion.LegacyCompletionContributor;
import consulo.ide.impl.idea.codeInsight.completion.WordCompletionContributor;
import consulo.language.LangBundle;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.*;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PatternCondition;
import consulo.language.psi.*;
import consulo.language.psi.filter.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.action.IdeActions;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.java.language.patterns.PsiJavaPatterns.*;
import static consulo.util.lang.ObjectUtil.assertNotNull;

/**
 * @author peter
 */
@ExtensionImpl(id = "javaLegacy", order = "last, before legacy, before default, before javaClassName")
public class JavaCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(JavaCompletionContributor.class);

  public static final ElementPattern<PsiElement> ANNOTATION_NAME = psiElement().
      withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class).afterLeaf("@");
  private static final PsiJavaElementPattern.Capture<PsiElement> UNEXPECTED_REFERENCE_AFTER_DOT = psiJavaElement().afterLeaf(".").insideStarting(psiExpressionStatement());

  private static final PsiNameValuePairPattern NAME_VALUE_PAIR = psiNameValuePair().withSuperParent(2, psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME = or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR), psiElement().afterLeaf("(").withParent
      (psiReferenceExpression().withParent(NAME_VALUE_PAIR)));

  private static final ElementPattern<PsiElement> IN_VARIABLE_TYPE = psiElement()
    .withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiDeclarationStatement.class)
    .afterLeaf(psiElement().inside(psiAnnotation()));

  private static final ElementPattern SWITCH_LABEL = psiElement().withSuperParent(2, psiElement(PsiSwitchLabelStatement.class).withSuperParent(2, psiElement(PsiSwitchStatement.class).with(new
                                                                                                                                                                                                PatternCondition<PsiSwitchStatement>("enumExpressionType") {
                                                                                                                                                                                                  @Override
                                                                                                                                                                                                  public boolean accepts(@Nonnull PsiSwitchStatement psiSwitchStatement, ProcessingContext context) {
                                                                                                                                                                                                    PsiExpression expression = psiSwitchStatement.getExpression();
                                                                                                                                                                                                    if (expression == null) {
                                                                                                                                                                                                      return false;
                                                                                                                                                                                                    }
                                                                                                                                                                                                    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
                                                                                                                                                                                                    return aClass != null && aClass.isEnum();
                                                                                                                                                                                                  }
                                                                                                                                                                                                })));
  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL = psiElement().afterLeaf(psiElement().withElementType(elementType().oneOf(JavaTokenType.DOUBLE_LITERAL, JavaTokenType
      .LONG_LITERAL, JavaTokenType.FLOAT_LITERAL, JavaTokenType.INTEGER_LITERAL)));
  private static final ElementPattern<PsiElement> IMPORT_REFERENCE = psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiImportStatementBase.class));
  private static final ElementPattern<PsiElement> CATCH_OR_FINALLY = psiElement().afterLeaf(psiElement().withText("}").withParent(psiElement(PsiCodeBlock.class).afterLeaf(PsiKeyword.TRY)));
  private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiMethod().constructor(true));

  @Nullable
  public static ElementFilter getReferenceFilter(PsiElement position) {
    // Completion after extends in interface, type parameter and implements in class
    PsiClass containingClass = PsiTreeUtil.getParentOfType(position, PsiClass.class, false, PsiCodeBlock.class, PsiMethod.class, PsiExpressionList.class, PsiVariable.class, PsiAnnotation
        .class);
    if (containingClass != null && psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS, ",", "&").accepts(position)) {
      return new AndFilter(ElementClassFilter.CLASS, new NotFilter(new AssignableFromContextFilter()));
    }

    if (ANNOTATION_NAME.accepts(position)) {
      return new AnnotationTypeFilter();
    }

    if (JavaKeywordCompletion.isDeclarationStart(position) ||
      JavaKeywordCompletion.isInsideParameterList(position) ||
      isInsideAnnotationName(position) ||
      PsiTreeUtil.getParentOfType(position, PsiReferenceParameterList.class, false, PsiAnnotation.class) != null ||
      IN_VARIABLE_TYPE.accepts(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE);
    }

    if (psiElement().afterLeaf(PsiKeyword.INSTANCEOF).accepts(position)) {
      return new ElementExtractorFilter(ElementClassFilter.CLASS);
    }

    if (JavaKeywordCompletion.VARIABLE_AFTER_FINAL.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (CATCH_OR_FINALLY.accepts(position) || JavaKeywordCompletion.START_SWITCH.accepts(position) || JavaKeywordCompletion.isInstanceofPlace(position) || JavaKeywordCompletion
        .isAfterPrimitiveOrArrayType(position)) {
      return null;
    }

    if (JavaKeywordCompletion.START_FOR.accepts(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.VARIABLE);
    }

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiReferenceParameterList.class).accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiAnnotationParameterList.class).accepts(position)) {
      return createAnnotationFilter(position);
    }

    PsiVariable var = PsiTreeUtil.getParentOfType(position, PsiVariable.class, false, PsiClass.class);
    if (var != null && PsiTreeUtil.isAncestor(var.getInitializer(), position, false)) {
      return new ExcludeFilter(var);
    }

    if (SWITCH_LABEL.accepts(position)) {
      return new ClassFilter(PsiField.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiEnumConstant;
        }
      };
    }

    PsiForeachStatement loop = PsiTreeUtil.getParentOfType(position, PsiForeachStatement.class);
    if (loop != null && PsiTreeUtil.isAncestor(loop.getIteratedValue(), position, false)) {
      return new ExcludeFilter(loop.getIterationParameter());
    }

    return TrueFilter.INSTANCE;
  }

  private static boolean isInsideAnnotationName(PsiElement position) {
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class, true, PsiMember.class);
    return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), position, true);
  }

  private static ElementFilter createAnnotationFilter(PsiElement position) {
    OrFilter orFilter = new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE, new AndFilter(new ClassFilter(PsiField.class), new ModifierFilter(PsiModifier.STATIC,
        PsiModifier.FINAL)));
    if (psiElement().insideStarting(psiNameValuePair()).accepts(position)) {
      orFilter.addFilter(new ClassFilter(PsiAnnotationMethod.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiAnnotationMethod && PsiUtil.isAnnotationMethod((PsiElement) element);
        }
      });
    }
    return orFilter;
  }

  @Override
  public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet _result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }

    PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) {
      return;
    }

    if (AFTER_NUMBER_LITERAL.accepts(position) || UNEXPECTED_REFERENCE_AFTER_DOT.accepts(position)) {
      _result.stopHere();
      return;
    }

    CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);
    JavaCompletionSession session = new JavaCompletionSession(result);

    if (ANNOTATION_ATTRIBUTE_NAME.accepts(position) && !JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position)) {
      addExpectedTypeMembers(parameters, result);
      JavaKeywordCompletion.addPrimitiveTypes(result, position, session);
      completeAnnotationAttributeName(result, position, parameters);
      result.stopHere();
      return;
    }

    PrefixMatcher matcher = result.getPrefixMatcher();
    PsiElement parent = position.getParent();

    if (JavaModuleCompletion.isModuleFile(parameters.getOriginalFile())) {
      JavaModuleCompletion.addVariants(position, parameters, result);
      result.stopHere();
      return;
    }

    if (JavaKeywordCompletion.addWildcardExtendsSuper(result, position)) {
      return;
    }

    if (position instanceof PsiIdentifier) {
      addIdentifierVariants(parameters, position, result, session, matcher);
    }

    MultiMap<CompletionResultSet, LookupElement> referenceVariants = addReferenceVariants(parameters, result, session);
    Set<String> usedWords = ContainerUtil.map2Set(referenceVariants.values(), LookupElement::getLookupString);
    for (Map.Entry<CompletionResultSet, Collection<LookupElement>> entry : referenceVariants.entrySet()) {
      session.registerBatchItems(entry.getKey(), entry.getValue());
    }

    session.flushBatchItems();

    if (psiElement().inside(PsiLiteralExpression.class).accepts(position)) {
      PsiReference reference = position.getContainingFile().findReferenceAt(parameters.getOffset());
      if (reference == null || reference.isSoft()) {
        WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
      }
    }

    if (position instanceof PsiIdentifier) {
      JavaGenerateMemberCompletionContributor.fillCompletionVariants(parameters, result);
    }

    addAllClasses(parameters, result, session);

    if (position instanceof PsiIdentifier) {
      FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, true, result.getPrefixMatcher(), result);
    }

    if (position instanceof PsiIdentifier && parent instanceof PsiReferenceExpression && !((PsiReferenceExpression) parent).isQualified() && parameters.isExtendedCompletion() && StringUtil
        .isNotEmpty(matcher.getPrefix())) {
      new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
    }
  }

  private static void addIdentifierVariants(@Nonnull CompletionParameters parameters, PsiElement position, CompletionResultSet result, JavaCompletionSession session, PrefixMatcher matcher) {
    session.registerBatchItems(result, getFastIdentifierVariants(parameters, position, matcher, position.getParent(), session));

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      session.flushBatchItems();
      new JavaInheritorsGetter(ConstructorInsertHandler.BASIC_INSTANCE).generateVariants(parameters, matcher, session::addClassItem);
    }

    suggestSmartCast(parameters, session, false, result);
  }

  private static void suggestSmartCast(CompletionParameters parameters, JavaCompletionSession session, boolean quick, Consumer<LookupElement> result) {
    if (SmartCastProvider.shouldSuggestCast(parameters)) {
      session.flushBatchItems();
      SmartCastProvider.addCastVariants(parameters, session.getMatcher(), element ->
      {
        registerClassFromTypeElement(element, session);
        result.accept(PrioritizedLookupElement.withPriority(element, 1));
      }, quick);
    }
  }

  private static List<LookupElement> getFastIdentifierVariants(@Nonnull CompletionParameters parameters,
                                                               PsiElement position,
                                                               PrefixMatcher matcher,
                                                               PsiElement parent,
                                                               @Nonnull JavaCompletionSession session) {
    List<LookupElement> items = new ArrayList<>();
    if (TypeArgumentCompletionProvider.IN_TYPE_ARGS.accepts(position)) {
      new TypeArgumentCompletionProvider(false, session).addTypeArgumentVariants(parameters, items::add, matcher);
    }

    FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, false, matcher, items::add);

    if (MethodReturnTypeProvider.IN_METHOD_RETURN_TYPE.accepts(position)) {
      MethodReturnTypeProvider.addProbableReturnTypes(parameters, element ->
      {
        registerClassFromTypeElement(element, session);
        items.add(element);
      });
    }

    suggestSmartCast(parameters, session, true, items::add);

    if (parent instanceof PsiReferenceExpression) {
      List<ExpectedTypeInfo> expected = Arrays.asList(ExpectedTypesProvider.getExpectedTypes((PsiExpression) parent, true));
      CollectConversion.addCollectConversion((PsiReferenceExpression) parent, expected, lookupElement -> items.add(JavaSmartCompletionContributor.decorate(lookupElement, expected)));
    }

    if (IMPORT_REFERENCE.accepts(position)) {
      items.add(LookupElementBuilder.create("*"));
    }

    items.addAll(new JavaKeywordCompletion(parameters, session).getResults());

    addExpressionVariants(parameters, position, items::add);
    return items;
  }

  private static void registerClassFromTypeElement(LookupElement element, JavaCompletionSession session) {
    PsiType type = assertNotNull(element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY)).getType();
    if (type instanceof PsiPrimitiveType) {
      session.registerKeyword(type.getCanonicalText(false));
      return;
    }

    PsiClass aClass = type instanceof PsiClassType && ((PsiClassType) type).getParameterCount() == 0 ? ((PsiClassType) type).resolve() : null;
    if (aClass != null) {
      session.registerClass(aClass);
    }
  }

  private static void addExpressionVariants(@Nonnull CompletionParameters parameters, PsiElement position, Consumer<LookupElement> result) {
    if (JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) && !JavaKeywordCompletion.AFTER_DOT.accepts(position) && !SmartCastProvider.shouldSuggestCast(parameters)) {
      addExpectedTypeMembers(parameters, result);
      if (SameSignatureCallParametersProvider.IN_CALL_ARGUMENT.accepts(position)) {
        new SameSignatureCallParametersProvider().addSignatureItems(parameters, result);
      }
    }
  }

  public static boolean isInJavaContext(PsiElement position) {
    return PsiUtilCore.findLanguageFromElement(position).isKindOf(JavaLanguage.INSTANCE);
  }

  public static void addAllClasses(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    if (!isClassNamePossible(parameters) || !mayStartClassName(result)) {
      return;
    }

    if (parameters.getInvocationCount() >= 2) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, parameters.getInvocationCount() <= 2, result.getPrefixMatcher(), element ->
      {
        if (!session.alreadyProcessed(element)) {
          result.addElement(JavaCompletionUtil.highlightIfNeeded(null, element, element.getObject(), parameters.getPosition()));
        }
      });
    } else {
      advertiseSecondCompletion(parameters.getPosition().getProject(), result);
    }
  }

  public static void advertiseSecondCompletion(Project project, CompletionResultSet result) {
    if (FeatureUsageTracker.getInstance().isToBeAdvertisedInLookup(CodeCompletionFeatures.SECOND_BASIC_COMPLETION, project)) {
      result.addLookupAdvertisement("Press " + getActionShortcut(IdeActions.ACTION_CODE_COMPLETION) + " to see non-imported classes");
    }
  }

  private static MultiMap<CompletionResultSet, LookupElement> addReferenceVariants(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    MultiMap<CompletionResultSet, LookupElement> items = MultiMap.create();
    PsiElement position = parameters.getPosition();
    boolean first = parameters.getInvocationCount() <= 1;
    boolean isSwitchLabel = SWITCH_LABEL.accepts(position);
    boolean isAfterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
    boolean pkgContext = JavaCompletionUtil.inSomePackage(position);
    PsiType[] expectedTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
    LegacyCompletionContributor.processReferences(parameters, result, (reference, result1) ->
    {
      if (reference instanceof PsiJavaReference) {
        ElementFilter filter = getReferenceFilter(position);
        if (filter != null) {
          if (INSIDE_CONSTRUCTOR.accepts(position) && (parameters.getInvocationCount() <= 1 || CheckInitialized.isInsideConstructorCall(position))) {
            filter = new AndFilter(filter, new CheckInitialized(position));
          }
          PsiFile originalFile = parameters.getOriginalFile();
          JavaCompletionProcessor.Options options = JavaCompletionProcessor.Options.DEFAULT_OPTIONS.withCheckAccess(first).withFilterStaticAfterInstance(first)
              .withShowInstanceInStaticContext(!first);
          for (LookupElement element : JavaCompletionUtil.processJavaReference(position, (PsiJavaReference) reference, new ElementExtractorFilter(filter), options, result1.getPrefixMatcher
              (), parameters)) {
            if (session.alreadyProcessed(element)) {
              continue;
            }

            if (isSwitchLabel) {
              items.putValue(result1, new IndentingDecorator(TailTypeDecorator.withTail(element, TailType.createSimpleTailType(':'))));
            } else {
              LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
              if (originalFile instanceof PsiJavaCodeReferenceCodeFragment && !((PsiJavaCodeReferenceCodeFragment) originalFile).isClassesAccepted() && item != null) {
                item.setTailType(TailType.NONE);
              }
              if (item instanceof JavaMethodCallElement) {
                JavaMethodCallElement call = (JavaMethodCallElement) item;
                PsiMethod method = call.getObject();
                if (method.getTypeParameters().length > 0) {
                  PsiType returned = TypeConversionUtil.erasure(method.getReturnType());
                  PsiType matchingExpectation = returned == null ? null : ContainerUtil.find(expectedTypes, type -> type.isAssignableFrom(returned));
                  if (matchingExpectation != null) {
                    call.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, matchingExpectation), position);
                  }
                }
              }

              items.putValue(result1, element);
            }
          }
        }
        return;
      }
      if (reference instanceof PsiLabelReference) {
        items.putValues(result1, LabelReferenceCompletion.processLabelReference((PsiLabelReference) reference));
        return;
      }

      Object[] variants = reference.getVariants();
      //noinspection ConstantConditions
      if (variants == null) {
        LOG.error("Reference=" + reference);
      }
      for (Object completion : variants) {
        if (completion == null) {
          LOG.error("Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(variants));
        }
        if (completion instanceof LookupElement && !session.alreadyProcessed((LookupElement) completion)) {
          items.putValue(result1, (LookupElement) completion);
        } else if (completion instanceof PsiClass) {
          Condition<PsiClass> condition = psiClass -> !session.alreadyProcessed(psiClass) && JavaCompletionUtil.isSourceLevelAccessible(position, psiClass, pkgContext);
          items.putValues(result1, JavaClassNameCompletionContributor.createClassLookupItems((PsiClass) completion, isAfterNew, JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
              condition));

        } else {
          //noinspection deprecation
          items.putValue(result1, LookupItemUtil.objectToLookupItem(completion));
        }
      }
    });
    return items;
  }

  static boolean isClassNamePossible(CompletionParameters parameters) {
    boolean isSecondCompletion = parameters.getInvocationCount() >= 2;

    PsiElement position = parameters.getPosition();
    if (JavaKeywordCompletion.isInstanceofPlace(position) || JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      return false;
    }

    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) {
      return isSecondCompletion;
    }
    if (((PsiJavaCodeReferenceElement) parent).getQualifier() != null) {
      return isSecondCompletion;
    }

    if (parent instanceof PsiJavaCodeReferenceElementImpl && ((PsiJavaCodeReferenceElementImpl) parent).getKindEnum(parent.getContainingFile()) == PsiJavaCodeReferenceElementImpl.Kind.PACKAGE_NAME_KIND) {
      return false;
    }

    PsiElement grand = parent.getParent();
    if (grand instanceof PsiSwitchLabelStatement) {
      return false;
    }

    if (psiElement().inside(PsiImportStatement.class).accepts(parent)) {
      return isSecondCompletion;
    }

    if (grand instanceof PsiAnonymousClass) {
      grand = grand.getParent();
    }
    if (grand instanceof PsiNewExpression && ((PsiNewExpression) grand).getQualifier() != null) {
      return false;
    }

    if (JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position)) {
      return false;
    }

    return true;
  }

  public static boolean mayStartClassName(CompletionResultSet result) {
    return StringUtil.isNotEmpty(result.getPrefixMatcher().getPrefix());
  }

  private static void completeAnnotationAttributeName(CompletionResultSet result, PsiElement insertedElement, CompletionParameters parameters) {
    PsiNameValuePair pair = PsiTreeUtil.getParentOfType(insertedElement, PsiNameValuePair.class);
    PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList) assertNotNull(pair).getParent();
    PsiAnnotation anno = (PsiAnnotation) parameterList.getParent();
    boolean showClasses = psiElement().afterLeaf("(").accepts(insertedElement);
    PsiClass annoClass = null;
    PsiJavaCodeReferenceElement referenceElement = anno.getNameReferenceElement();
    if (referenceElement != null) {
      PsiElement element = referenceElement.resolve();
      if (element instanceof PsiClass) {
        annoClass = (PsiClass) element;
        if (annoClass.findMethodsByName("value", false).length == 0) {
          showClasses = false;
        }
      }
    }

    if (showClasses && insertedElement.getParent() instanceof PsiReferenceExpression) {
      Set<LookupElement> set = JavaCompletionUtil.processJavaReference(insertedElement, (PsiJavaReference) insertedElement.getParent(), new ElementExtractorFilter(createAnnotationFilter
          (insertedElement)), JavaCompletionProcessor.Options.DEFAULT_OPTIONS, result.getPrefixMatcher(), parameters);
      for (LookupElement element : set) {
        result.addElement(element);
      }
      addAllClasses(parameters, result, new JavaCompletionSession(result));
    }

    if (annoClass != null) {
      PsiNameValuePair[] existingPairs = parameterList.getAttributes();

      methods:
      for (PsiMethod method : annoClass.getMethods()) {
        if (!(method instanceof PsiAnnotationMethod)) {
          continue;
        }

        String attrName = method.getName();
        for (PsiNameValuePair existingAttr : existingPairs) {
          if (PsiTreeUtil.isAncestor(existingAttr, insertedElement, false)) {
            break;
          }
          if (Comparing.equal(existingAttr.getName(), attrName) || PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attrName) && existingAttr.getName() == null) {
            continue methods;
          }
        }
        LookupElementBuilder element = LookupElementBuilder.createWithIcon(method).withInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            Editor editor = context.getEditor();
            EqTailType.INSTANCE.processTail(editor, editor.getCaretModel().getOffset());
            context.setAddCompletionChar(false);

            context.commitDocument();
            PsiAnnotationParameterList paramList = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiAnnotationParameterList.class, false);
            if (paramList != null && paramList.getAttributes().length > 0 && paramList.getAttributes()[0].getName() == null) {
              int valueOffset = paramList.getAttributes()[0].getTextRange().getStartOffset();
              context.getDocument().insertString(valueOffset, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
              EqTailType.INSTANCE.processTail(editor, valueOffset + PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.length());
            }
          }
        });

        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod) method).getDefaultValue();
        if (defaultValue != null) {
          element = element.withTailText(" default " + defaultValue.getText(), true);
        }

        result.addElement(element);
      }
    }
  }

  @Override
  public String advertise(@Nonnull CompletionParameters parameters) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) {
      return null;
    }

    if (parameters.getCompletionType() == CompletionType.BASIC && parameters.getInvocationCount() > 0) {
      PsiElement position = parameters.getPosition();
      if (psiElement().withParent(psiReferenceExpression().withFirstChild(psiReferenceExpression().referencing(psiClass()))).accepts(position)) {
        if (CompletionUtilCore.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
          String shortcut = getActionShortcut(IdeActions.ACTION_CODE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            return "Pressing " + shortcut + " twice without a class qualifier would show all accessible static methods";
          }
        }
      }
    }

    if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition())) {
      if (CompletionUtilCore.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL)) {
        String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
        if (StringUtil.isNotEmpty(shortcut)) {
          return CompletionBundle.message("completion.smart.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 1) {
      PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
      if (psiTypes.length > 0) {
        if (CompletionUtilCore.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR)) {
          String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            for (PsiType psiType : psiTypes) {
              PsiType type = PsiUtil.extractIterableTypeParameter(psiType, false);
              if (type != null) {
                return CompletionBundle.message("completion.smart.aslist.hint", shortcut, type.getPresentableText());
              }
            }
          }
        }
        if (CompletionUtilCore.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST)) {
          String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            for (PsiType psiType : psiTypes) {
              if (psiType instanceof PsiArrayType) {
                PsiType componentType = ((PsiArrayType) psiType).getComponentType();
                if (!(componentType instanceof PsiPrimitiveType)) {
                  return CompletionBundle.message("completion.smart.toar.hint", shortcut, componentType.getPresentableText());
                }
              }
            }
          }
        }

        if (CompletionUtilCore.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN)) {
          String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            return CompletionBundle.message("completion.smart.chain.hint", shortcut);
          }
        }
      }
    }
    return null;
  }

  @Override
  public String handleEmptyLookup(@Nonnull CompletionParameters parameters, Editor editor) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) {
      return null;
    }

    String ad = advertise(parameters);
    String suffix = ad == null ? "" : "; " + StringUtil.decapitalize(ad);
    if (parameters.getCompletionType() == CompletionType.SMART) {
      PsiExpression expression = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
      if (expression instanceof PsiLiteralExpression) {
        return LangBundle.message("completion.no.suggestions") + suffix;
      }

      if (expression instanceof PsiInstanceOfExpression) {
        PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression) expression;
        if (PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), parameters.getPosition(), false)) {
          return LangBundle.message("completion.no.suggestions") + suffix;
        }
      }

      Set<PsiType> expectedTypes = JavaCompletionUtil.getExpectedTypes(parameters);
      if (expectedTypes != null) {
        PsiType type = expectedTypes.size() == 1 ? expectedTypes.iterator().next() : null;
        if (type != null) {
          PsiType deepComponentType = type.getDeepComponentType();
          String expectedType = type.getPresentableText();
          if (expectedType.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
            return null;
          }

          if (deepComponentType instanceof PsiClassType) {
            if (((PsiClassType) deepComponentType).resolve() != null) {
              return CompletionBundle.message("completion.no.suggestions.of.type", expectedType) + suffix;
            }
            return CompletionBundle.message("completion.unknown.type", expectedType) + suffix;
          }
          if (!PsiType.NULL.equals(type)) {
            return CompletionBundle.message("completion.no.suggestions.of.type", expectedType) + suffix;
          }
        }
      }
    }
    return LangBundle.message("completion.no.suggestions") + suffix;
  }

  @Override
  public boolean invokeAutoPopup(@Nonnull PsiElement position, char typeChar) {
    return typeChar == ':' && JavaTokenType.COLON == position.getNode().getElementType();
  }

  private static boolean shouldSuggestSmartCompletion(PsiElement element) {
    if (shouldSuggestClassNameCompletion(element)) {
      return false;
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression) parent).getQualifier() != null) {
      return false;
    }
    if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiReferenceExpression) {
      return true;
    }

    return ExpectedTypesGetter.getExpectedTypes(element, false).length > 0;
  }

  private static boolean shouldSuggestClassNameCompletion(PsiElement element) {
    if (element == null) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (parent == null) {
      return false;
    }
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement || parent.getParent() instanceof PsiReferenceList;
  }

  @Override
  public void beforeCompletion(@Nonnull CompletionInitializationContext context) {
    PsiFile file = context.getFile();

    if (file instanceof PsiJavaFile) {
      if (context.getInvocationCount() > 0) {
        autoImport(file, context.getStartOffset() - 1, context.getEditor());

        PsiElement leaf = file.findElementAt(context.getStartOffset() - 1);
        if (leaf != null) {
          leaf = PsiTreeUtil.prevVisibleLeaf(leaf);
        }

        PsiVariable variable = PsiTreeUtil.getParentOfType(leaf, PsiVariable.class);
        if (variable != null) {
          PsiTypeElement typeElement = variable.getTypeElement();
          if (typeElement != null) {
            PsiType type = typeElement.getType();
            if (type instanceof PsiClassType && ((PsiClassType) type).resolve() == null) {
              autoImportReference(file, context.getEditor(), typeElement.getInnermostComponentReferenceElement());
            }
          }
        }
      }

      if (context.getCompletionType() == CompletionType.BASIC) {
        if (semicolonNeeded(file, context.getStartOffset())) {
          context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER.trim() + ";");
          return;
        }

        PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null && !(ref instanceof PsiReferenceExpression)) {
          if (JavaSmartCompletionContributor.AFTER_NEW.accepts(ref)) {
            PsiReferenceParameterList paramList = ref.getParameterList();
            if (paramList != null && paramList.getTextLength() > 0) {
              context.getOffsetMap().addOffset(ConstructorInsertHandler.PARAM_LIST_START, paramList.getTextRange().getStartOffset());
              context.getOffsetMap().addOffset(ConstructorInsertHandler.PARAM_LIST_END, paramList.getTextRange().getEndOffset());
            }
          }

          return;
        }

        PsiElement element = file.findElementAt(context.getStartOffset());

        if (psiElement().inside(PsiAnnotation.class).accepts(element)) {
          return;
        }

        context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
      }
    }
  }

  public static boolean semicolonNeeded(PsiFile file, int startOffset) {
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiJavaCodeReferenceElement.class, false);
    if (ref != null && !(ref instanceof PsiReferenceExpression)) {
      if (ref.getParent() instanceof PsiTypeElement) {
        return true;
      }
    }
    PsiElement at = file.findElementAt(startOffset);
    if (psiElement(PsiIdentifier.class).withParent(psiParameter()).accepts(at)) {
      return true;
    }

    if (PsiUtilCore.getElementType(at) == JavaTokenType.IDENTIFIER) {
      at = PsiTreeUtil.nextLeaf(at);
    }

    at = skipWhitespacesAndComments(at);

    if (PsiUtilCore.getElementType(at) == JavaTokenType.LPARENTH &&
      PsiTreeUtil.getParentOfType(ref, PsiExpression.class, PsiClass.class) == null &&
      PsiTreeUtil.getParentOfType(at, PsiImplicitClass.class) == null) { // TODO check before it that there is record
      // looks like a method declaration, e.g. StringBui<caret>methodName() inside a class
      return true;
    }

    if (PsiUtilCore.getElementType(at) == JavaTokenType.COLON &&
      PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiConditionalExpression.class, false) == null) {
      return true;
    }

    at = skipWhitespacesAndComments(at);

    if (PsiUtilCore.getElementType(at) != JavaTokenType.IDENTIFIER) {
      return false;
    }

    at = PsiTreeUtil.nextLeaf(at);
    at = skipWhitespacesAndComments(at);

    // <caret> foo = something, we don't want the reference to be treated as a type
    return at != null && at.getNode().getElementType() == JavaTokenType.EQ;
  }

  @Nullable
  private static PsiElement skipWhitespacesAndComments(@Nullable PsiElement at) {
    PsiElement nextLeaf = at;
    while (nextLeaf != null && (nextLeaf instanceof PsiWhiteSpace ||
      nextLeaf instanceof PsiComment ||
      nextLeaf instanceof PsiErrorElement ||
      nextLeaf.getTextLength() == 0)) {
      nextLeaf = PsiTreeUtil.nextLeaf(nextLeaf, true);
    }
    return nextLeaf;
  }

  private static void autoImport(@Nonnull PsiFile file, int offset, @Nonnull Editor editor) {
    CharSequence text = editor.getDocument().getCharsSequence();
    while (offset > 0 && Character.isJavaIdentifierPart(text.charAt(offset))) {
      offset--;
    }
    if (offset <= 0) {
      return;
    }

    while (offset > 0 && Character.isWhitespace(text.charAt(offset))) {
      offset--;
    }
    if (offset <= 0 || text.charAt(offset) != '.') {
      return;
    }

    offset--;

    while (offset > 0 && Character.isWhitespace(text.charAt(offset))) {
      offset--;
    }
    if (offset <= 0) {
      return;
    }

    autoImportReference(file, editor, extractReference(PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiExpression.class, false)));
  }

  private static void autoImportReference(@Nonnull PsiFile file, @Nonnull Editor editor, @Nullable PsiJavaCodeReferenceElement element) {
    if (element == null) {
      return;
    }

    while (true) {
      PsiJavaCodeReferenceElement qualifier = extractReference(element.getQualifier());
      if (qualifier == null) {
        break;
      }

      element = qualifier;
    }
    if (!(element.getParent() instanceof PsiMethodCallExpression) && element.multiResolve(true).length == 0) {
      new ImportClassFix(element).doFix(editor, false, false);
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    }
  }

  @Nullable
  private static PsiJavaCodeReferenceElement extractReference(@Nullable PsiElement expression) {
    if (expression instanceof PsiJavaCodeReferenceElement) {
      return (PsiJavaCodeReferenceElement) expression;
    }
    if (expression instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression) expression).getMethodExpression();
    }
    return null;
  }

  private static void addExpectedTypeMembers(CompletionParameters parameters, Consumer<LookupElement> result) {
    if (parameters.getInvocationCount() <= 1) { // on second completion, StaticMemberProcessor will suggest those
      for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new JavaMembersGetter(info.getDefaultType(), parameters).addMembers(false, result);
      }
    }
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  static class IndentingDecorator extends LookupElementDecorator<LookupElement> {
    public IndentingDecorator(LookupElement delegate) {
      super(delegate);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      super.handleInsert(context);
      Project project = context.getProject();
      Document document = context.getDocument();
      int lineStartOffset = DocumentUtil.getLineStartOffset(context.getStartOffset(), document);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      CodeStyleManager.getInstance(project).adjustLineIndent(context.getFile(), lineStartOffset);
    }
  }
}
