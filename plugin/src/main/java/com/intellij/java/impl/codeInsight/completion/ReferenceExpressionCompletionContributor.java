/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.java.impl.psi.filters.ElementExtractorFilter;
import com.intellij.java.impl.psi.filters.types.AssignableFromFilter;
import com.intellij.java.language.impl.psi.filters.element.ModifierFilter;
import com.intellij.java.language.patterns.PsiMethodPattern;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.AllIcons;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.filter.AndFilter;
import consulo.language.psi.filter.ClassFilter;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.filter.TrueFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiMethod;

/**
 * @author peter
 */
public class ReferenceExpressionCompletionContributor {
  private static final Logger LOG = Logger.getInstance(ReferenceExpressionCompletionContributor.class);
  private static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod()
      .withName(
          StandardPatterns.string()
              .oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass", "clone", "toString")
      )
      .definedInClass(CommonClassNames.JAVA_LANG_OBJECT);
  private static final PrefixMatcher TRUE_MATCHER = new PrefixMatcher("") {
    @Override
    public boolean prefixMatches(@Nonnull String name) {
      return true;
    }

    @Nonnull
    @Override
    public PrefixMatcher cloneWithPrefix(@Nonnull String prefix) {
      return this;
    }
  };
  public static final ElementPattern<PsiElement> IN_SWITCH_LABEL = psiElement().withSuperParent(2, psiElement(PsiSwitchLabelStatement.class).withSuperParent(2, PsiSwitchStatement.class));

  @Nonnull
  private static ElementFilter getReferenceFilter(PsiElement element, boolean allowRecursion) {
    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return TrueFilter.INSTANCE;
    }

    if (psiElement().inside(StandardPatterns.or(psiElement(PsiAnnotationParameterList.class), psiElement(PsiSwitchLabelStatement.class))).accepts(element)) {
      return new ElementExtractorFilter(new AndFilter(new ClassFilter(PsiField.class), new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL)));
    }

    final PsiForeachStatement foreach = PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
    if (foreach != null && !PsiTreeUtil.isAncestor(foreach.getBody(), element, false)) {
      return new ElementExtractorFilter(new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return element != foreach.getIterationParameter();
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      });
    }

    if (!allowRecursion) {
      ElementFilter filter = RecursionWeigher.recursionFilter(element);
      if (filter != null) {
        return new ElementExtractorFilter(filter);
      }
    }

    return TrueFilter.INSTANCE;
  }

  @Nullable
  public static Runnable fillCompletionVariants(final JavaSmartCompletionParameters parameters, final Consumer<LookupElement> result) {
    final PsiElement element = parameters.getPosition();
    if (JavaSmartCompletionContributor.INSIDE_TYPECAST_EXPRESSION.accepts(element)) {
      return null;
    }
    if (JavaKeywordCompletion.isAfterPrimitiveOrArrayType(element)) {
      return null;
    }

    int offset = parameters.getParameters().getOffset();
    final PsiJavaCodeReferenceElement reference = PsiTreeUtil.findElementOfClassAtOffset(element.getContainingFile(), offset, PsiJavaCodeReferenceElement.class, false);
    if (reference != null) {
      ElementFilter filter = getReferenceFilter(element, false);
      for (LookupElement item : completeFinalReference(element, reference, filter, parameters)) {
        result.accept(item);
      }

      boolean secondTime = parameters.getParameters().getInvocationCount() >= 2;

      final Set<LookupElement> base = JavaSmartCompletionContributor.completeReference(element, reference, filter, false, true, parameters.getParameters(), PrefixMatcher.ALWAYS_TRUE);
      for (LookupElement item : new LinkedHashSet<LookupElement>(base)) {
        ExpressionLookupItem access = getSingleArrayElementAccess(element, item);
        if (access != null) {
          base.add(access);
          PsiType type = access.getType();
          if (type != null && parameters.getExpectedType().isAssignableFrom(type)) {
            result.accept(access);
          }
        }
      }

      if (secondTime) {
        return new Runnable() {
          @Override
          public void run() {
            for (LookupElement item : base) {
              addSecondCompletionVariants(element, reference, item, parameters, result);
            }
            if (!psiElement().afterLeaf(".").accepts(element)) {
              BasicExpressionCompletionContributor.processDataflowExpressionTypes(parameters, null, TRUE_MATCHER, it ->
              {
                addSecondCompletionVariants(element, reference, it, parameters, result);
              });
            }
          }
        };
      }
    }
    return null;
  }

  private static Set<LookupElement> completeFinalReference(final PsiElement element, PsiJavaCodeReferenceElement reference, ElementFilter filter, final JavaSmartCompletionParameters parameters) {
    final Set<PsiField> used = parameters.getParameters().getInvocationCount() < 2 ? findConstantsUsedInSwitch(element) : Collections.<PsiField>emptySet();

    Set<LookupElement> elements = JavaSmartCompletionContributor.completeReference(element, reference, new AndFilter(filter, new ElementFilter() {
      @Override
      public boolean isAcceptable(Object o, PsiElement context) {
        if (o instanceof CandidateInfo) {
          CandidateInfo info = (CandidateInfo) o;
          PsiElement member = info.getElement();

          PsiType expectedType = parameters.getExpectedType();
          if (expectedType.equals(PsiType.VOID)) {
            return member instanceof PsiMethod;
          }

          //noinspection SuspiciousMethodCalls
          if (member instanceof PsiEnumConstant && used.contains(CompletionUtilCore.getOriginalOrSelf(member))) {
            return false;
          }

          return AssignableFromFilter.isAcceptable(member, element, expectedType, info.getSubstitutor());
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }), false, true, parameters.getParameters(), PrefixMatcher.ALWAYS_TRUE);
    for (LookupElement lookupElement : elements) {
      if (lookupElement.getObject() instanceof PsiMethod) {
        JavaMethodCallElement item = lookupElement.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
        if (item != null) {
          PsiMethod method = (PsiMethod) lookupElement.getObject();
          if (SmartCompletionDecorator.hasUnboundTypeParams(method, parameters.getExpectedType())) {
            item.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, parameters.getExpectedType()), element);
          }
        }
      }
    }

    return elements;
  }

  @Nonnull
  public static Set<PsiField> findConstantsUsedInSwitch(@Nullable PsiElement position) {
    if (IN_SWITCH_LABEL.accepts(position)) {
      Set<PsiField> used = new LinkedHashSet<>();
      PsiSwitchStatement sw = PsiTreeUtil.getParentOfType(position, PsiSwitchStatement.class);
      assert sw != null;
      PsiCodeBlock body = sw.getBody();
      assert body != null;
      for (PsiStatement statement : body.getStatements()) {
        if (statement instanceof PsiSwitchLabelStatement) {
          PsiExpression value = ((PsiSwitchLabelStatement) statement).getCaseValue();
          if (value instanceof PsiReferenceExpression) {
            PsiElement target = ((PsiReferenceExpression) value).resolve();
            if (target instanceof PsiField) {
              used.add(CompletionUtilCore.getOriginalOrSelf((PsiField) target));
            }
          }
        }
      }
      return used;
    }
    return Collections.emptySet();
  }

  @Nullable
  private static ExpressionLookupItem getSingleArrayElementAccess(PsiElement element, LookupElement item) {
    if (item.getObject() instanceof PsiLocalVariable) {
      PsiLocalVariable variable = (PsiLocalVariable) item.getObject();
      PsiType type = variable.getType();
      PsiExpression expression = variable.getInitializer();
      if (type instanceof PsiArrayType && expression instanceof PsiNewExpression) {
        PsiNewExpression newExpression = (PsiNewExpression) expression;
        PsiExpression[] dimensions = newExpression.getArrayDimensions();
        if (dimensions.length == 1 && "1".equals(dimensions[0].getText()) && newExpression.getArrayInitializer() == null) {
          String text = variable.getName() + "[0]";
          return new ExpressionLookupItem(createExpression(text, element), IconDescriptorUpdaters.getIcon(variable, 0), text, text);
        }
      }
    }
    return null;
  }

  private static PsiExpression createExpression(String text, PsiElement element) {
    return JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createExpressionFromText(text, element);
  }

  private static void addSecondCompletionVariants(PsiElement element, PsiReference reference, LookupElement baseItem, JavaSmartCompletionParameters parameters, Consumer<LookupElement> result) {
    Object object = baseItem.getObject();

    try {
      PsiType itemType = JavaCompletionUtil.getLookupElementType(baseItem);
      if (itemType instanceof PsiWildcardType) {
        itemType = ((PsiWildcardType) itemType).getExtendsBound();
      }
      if (itemType == null) {
        return;
      }
      assert itemType.isValid() : baseItem + "; " + baseItem.getClass();

      PsiElement element1 = reference.getElement();
      PsiElement qualifier = element1 instanceof PsiJavaCodeReferenceElement ? ((PsiJavaCodeReferenceElement) element1).getQualifier() : null;
      PsiType expectedType = parameters.getExpectedType();
      if (!OBJECT_METHOD_PATTERN.accepts(object) || allowGetClass(object, parameters)) {
        if (parameters.getParameters().getInvocationCount() >= 3 || !itemType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          if (!(object instanceof PsiMethod && ((PsiMethod) object).getParameterList().getParametersCount() > 0)) {
            addChainedCallVariants(element, baseItem, result, itemType, expectedType, parameters);
          }
        }
      }

      String prefix = getItemText(object);
      if (prefix == null) {
        return;
      }

      addConversionsToArray(element, prefix, itemType, result, qualifier, expectedType);

      addToArrayConversions(element, object, prefix, itemType, result, qualifier, expectedType);

      addArrayMemberAccessors(element, prefix, itemType, qualifier, result, (PsiModifierListOwner) object, expectedType);
    } catch (IncorrectOperationException ignored) {
    }
  }

  private static void addArrayMemberAccessors(final PsiElement element,
                                              final String prefix,
                                              PsiType itemType,
                                              PsiElement qualifier,
                                              Consumer<LookupElement> result,
                                              PsiModifierListOwner object,
                                              PsiType expectedType) throws IncorrectOperationException {
    if (itemType instanceof PsiArrayType && expectedType.isAssignableFrom(((PsiArrayType) itemType).getComponentType())) {
      final PsiExpression conversion = createExpression(getQualifierText(qualifier) + prefix + "[0]", element);
      result.accept(new ExpressionLookupItem(conversion, IconDescriptorUpdaters.getIcon(object, 0), prefix + "[...]", prefix) {
        @Override
        public void handleInsert(InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ARRAY_MEMBER);

          int tailOffset = context.getTailOffset();
          String callSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_BRACKETS);
          context.getDocument().insertString(tailOffset, "[" + callSpace + callSpace + "]");
          context.getEditor().getCaretModel().moveToOffset(tailOffset + 1 + callSpace.length());
        }
      });
    }
  }

  private static boolean allowGetClass(Object object, JavaSmartCompletionParameters parameters) {
    if (!"getClass".equals(((PsiMethod) object).getName())) {
      return false;
    }

    PsiType type = parameters.getDefaultType();
    @NonNls String canonicalText = type.getCanonicalText();
    if ("java.lang.ClassLoader".equals(canonicalText)) {
      return true;
    }
    if (canonicalText.startsWith("java.lang.reflect.")) {
      return true;
    }
    return false;
  }

  private static void addConversionsToArray(final PsiElement element,
                                            final String prefix,
                                            PsiType itemType,
                                            Consumer<LookupElement> result,
                                            @Nullable PsiElement qualifier,
                                            PsiType expectedType) throws IncorrectOperationException {
    final String methodName = getArraysConversionMethod(itemType, expectedType);
    if (methodName == null) {
      return;
    }

    final String qualifierText = getQualifierText(qualifier);
    final PsiExpression conversion = createExpression("java.util.Arrays." + methodName + "(" + qualifierText + prefix + ")", element);
    final String presentable = "Arrays." + methodName + "(" + qualifierText + prefix + ")";
    String[] lookupStrings = {
        StringUtil.isEmpty(qualifierText) ? presentable : prefix,
        prefix,
        presentable,
        methodName + "(" + prefix + ")"
    };
    result.accept(new ExpressionLookupItem(conversion, AllIcons.Nodes.Method, presentable, lookupStrings) {
      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST);

        int startOffset = context.getStartOffset() - qualifierText.length();
        Project project = element.getProject();
        String callSpace = getSpace(CodeStyleSettingsManager.getSettings(project).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
        String newText = "java.util.Arrays." + methodName + "(" + callSpace + qualifierText + prefix + callSpace + ")";
        context.getDocument().replaceString(startOffset, context.getTailOffset(), newText);

        context.commitDocument();
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(context.getFile(), startOffset, startOffset + CommonClassNames.JAVA_UTIL_ARRAYS.length());
      }
    });
  }

  @Nullable
  private static String getArraysConversionMethod(PsiType itemType, PsiType expectedType) {
    String methodName = "asList";
    PsiType componentType = PsiUtil.extractIterableTypeParameter(expectedType, true);
    if (componentType == null) {
      methodName = "stream";
      componentType = getStreamComponentType(expectedType);
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(componentType);
      if (unboxedType != null) {
        componentType = unboxedType;
      }
    }

    if (componentType == null ||
        !(itemType instanceof PsiArrayType) ||
        !componentType.isAssignableFrom(((PsiArrayType) itemType).getComponentType())) {
      return null;

    }
    return methodName;
  }

  private static PsiType getStreamComponentType(PsiType expectedType) {
    return PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, 0, true);
  }

  private static void addToArrayConversions(PsiElement element,
                                            Object object,
                                            String prefix,
                                            PsiType itemType,
                                            Consumer<LookupElement> result,
                                            @Nullable PsiElement qualifier,
                                            PsiType expectedType) {
    String callSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
    PsiType componentType = PsiUtil.extractIterableTypeParameter(itemType, true);
    if (componentType == null || !(expectedType instanceof PsiArrayType)) {
      return;
    }

    PsiArrayType type = (PsiArrayType) expectedType;
    if (!type.getComponentType().isAssignableFrom(componentType) || componentType instanceof PsiClassType && ((PsiClassType) componentType).hasParameters()) {
      return;
    }

    String bracketSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_BRACKETS);
    if (object instanceof PsiVariable && !JavaCompletionUtil.mayHaveSideEffects(qualifier)) {
      PsiVariable variable = (PsiVariable) object;
      addToArrayConversion(element, prefix, "new " + componentType.getCanonicalText() +
          "[" + bracketSpace + getQualifierText(qualifier) + variable.getName() + ".size(" + callSpace + ")" + bracketSpace + "]", "new " + getQualifierText(qualifier) + componentType
          .getPresentableText() + "[" + variable.getName() + ".size()]", result, qualifier);
    } else {
      boolean hasEmptyArrayField = false;
      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null) {
        for (PsiField field : psiClass.getAllFields()) {
          if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL) &&
              JavaPsiFacade.getInstance(field.getProject()).getResolveHelper().isAccessible(field, element, null) &&
              type.isAssignableFrom(field.getType()) && isEmptyArrayInitializer(field.getInitializer())) {
            boolean needQualify;
            try {
              needQualify = !field.isEquivalentTo(((PsiReferenceExpression) createExpression(field.getName(), element)).resolve());
            } catch (IncorrectOperationException e) {
              continue;
            }

            addToArrayConversion(element, prefix, (needQualify ? field.getContainingClass().getQualifiedName() + "." : "") + field.getName(),
                (needQualify ? field.getContainingClass().getName() + "." : "") + field.getName(), result, qualifier);
            hasEmptyArrayField = true;
          }
        }
      }
      if (!hasEmptyArrayField) {
        addToArrayConversion(element, prefix, "new " + componentType.getCanonicalText() + "[" + bracketSpace + "0" + bracketSpace + "]", "new " + componentType.getPresentableText() + "[0]",
            result, qualifier);
      }
    }
  }

  private static String getQualifierText(@Nullable PsiElement qualifier) {
    return qualifier == null ? "" : qualifier.getText() + ".";
  }

  private static void addChainedCallVariants(PsiElement place,
                                             LookupElement qualifierItem,
                                             Consumer<LookupElement> result,
                                             PsiType qualifierType,
                                             PsiType expectedType,
                                             JavaSmartCompletionParameters parameters) throws IncorrectOperationException {
    PsiReferenceExpression mockRef = createMockReference(place, qualifierType, qualifierItem);
    if (mockRef == null) {
      return;
    }

    ElementFilter filter = getReferenceFilter(place, true);
    for (final LookupElement item : completeFinalReference(place, mockRef, filter, parameters)) {
      if (shouldChain(place, qualifierType, expectedType, item)) {
        result.accept(new JavaChainLookupElement(qualifierItem, item) {
          @Override
          public void handleInsert(InsertionContext context) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN);
            super.handleInsert(context);
          }
        });
      }
    }
  }

  @Nullable
  public static PsiReferenceExpression createMockReference(PsiElement place, @Nonnull PsiType qualifierType, LookupElement qualifierItem) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());
    if (qualifierItem.getObject() instanceof PsiClass) {
      String qname = ((PsiClass) qualifierItem.getObject()).getQualifiedName();
      if (qname == null) {
        return null;
      }

      String text = qname + ".xxx";
      try {
        PsiExpression expr = factory.createExpressionFromText(text, place);
        if (expr instanceof PsiReferenceExpression) {
          return (PsiReferenceExpression) expr;
        }
        return null; // ignore ill-formed qualified names like "org.spark-project.jetty" that can't be used from Java code anyway
      } catch (IncorrectOperationException e) {
        LOG.info(e);
        return null;
      }
    }

    return (PsiReferenceExpression) factory.createExpressionFromText("xxx.xxx", JavaCompletionUtil.createContextWithXxxVariable(place, qualifierType));
  }

  private static boolean shouldChain(PsiElement element, PsiType qualifierType, PsiType expectedType, LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiModifierListOwner && ((PsiModifierListOwner) object).hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }

    if (object instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) object;
      if (psiMethod().withName("toArray").withParameterCount(1).definedInClass(CommonClassNames.JAVA_UTIL_COLLECTION).accepts(method)) {
        return false;
      }
      PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (isUselessObjectMethod(method, parentMethod, qualifierType)) {
        return false;
      }

      PsiType type = method.getReturnType();
      if (type instanceof PsiClassType) {
        PsiClassType classType = (PsiClassType) type;
        PsiClass psiClass = classType.resolve();
        if (psiClass instanceof PsiTypeParameter && method.getTypeParameterList() == psiClass.getParent()) {
          PsiTypeParameter typeParameter = (PsiTypeParameter) psiClass;
          if (typeParameter.getExtendsListTypes().length == 0) {
            return false;
          }
          if (!expectedType.isAssignableFrom(TypeConversionUtil.typeParameterErasure(typeParameter))) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean isUselessObjectMethod(PsiMethod method, PsiMethod parentMethod, PsiType qualifierType) {
    if (!OBJECT_METHOD_PATTERN.accepts(method)) {
      return false;
    }

    if (OBJECT_METHOD_PATTERN.accepts(parentMethod) && method.getName().equals(parentMethod.getName())) {
      return false;
    }

    if ("toString".equals(method.getName())) {
      if (qualifierType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUFFER)
          || InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
        return false;
      }
    }

    return true;
  }

  private static void addToArrayConversion(PsiElement element,
                                           final String prefix,
                                           @NonNls String expressionString,
                                           @NonNls String presentableString,
                                           Consumer<LookupElement> result,
                                           PsiElement qualifier) {
    boolean callSpace = CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES;
    final PsiExpression conversion;
    try {
      conversion = createExpression(getQualifierText(qualifier) + prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")", element);
    } catch (IncorrectOperationException e) {
      return;
    }

    String[] lookupStrings = {
        prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")",
        presentableString
    };
    result.accept(new ExpressionLookupItem(conversion, AllIcons.Nodes.Method, prefix + ".toArray(" + presentableString + ")", lookupStrings) {
      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR);

        context.commitDocument();
        JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(context.getFile(), context.getStartOffset(), context.getTailOffset());
      }
    });
  }

  private static boolean isEmptyArrayInitializer(@Nullable PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiNewExpression expression = (PsiNewExpression) element;
      PsiExpression[] dimensions = expression.getArrayDimensions();
      for (PsiExpression dimension : dimensions) {
        if (!(dimension instanceof PsiLiteralExpression) || !"0".equals(dimension.getText())) {
          return false;
        }
      }
      PsiArrayInitializerExpression initializer = expression.getArrayInitializer();
      if (initializer != null && initializer.getInitializers().length > 0) {
        return false;
      }

      return true;
    }
    return false;
  }

  @Nullable
  private static String getItemText(Object o) {
    if (o instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) o;
      PsiType type = method.getReturnType();
      if (PsiType.VOID.equals(type) || PsiType.NULL.equals(type)) {
        return null;
      }
      if (method.getParameterList().getParametersCount() > 0) {
        return null;
      }
      return method.getName() + "(" + getSpace(CodeStyleSettingsManager.getSettings(method.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES) + ")";
    } else if (o instanceof PsiVariable) {
      return ((PsiVariable) o).getName();
    }
    return null;
  }

  private static String getSpace(boolean needSpace) {
    return needSpace ? " " : "";
  }

}
