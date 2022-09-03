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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.psi.search.ContainerProvider;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import com.intellij.java.language.impl.codeInsight.daemon.impl.analysis.HighlightUtilBase;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.internal.QuickFixActionRegistrarImpl;
import consulo.language.editor.highlight.HighlightUsagesDescriptionLocation;
import consulo.language.editor.intention.IntentionAction;
import consulo.ide.impl.idea.codeInsight.intention.impl.PriorityActionWrapper;
import consulo.language.editor.intention.UnresolvedReferenceQuickFixProvider;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.JavaLanguageLevelPusher;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.*;
import com.intellij.java.analysis.impl.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.java.analysis.impl.psi.util.EnclosingLoopMatcherExpression;
import com.intellij.java.analysis.impl.psi.util.EnclosingLoopOrSwitchMatcherExpression;
import com.intellij.java.analysis.impl.psi.util.JavaMatchers;
import com.intellij.java.analysis.impl.psi.util.PsiMatchers;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.*;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.java.language.JavaLanguage;
import consulo.component.extension.Extensions;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.FilePropertyPusher;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.impl.psi.scope.processor.VariablesNotProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.OuterLanguageElement;
import consulo.language.ast.IElementType;
import com.intellij.psi.util.*;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.*;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.xml.XmlStringUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.module.util.JavaClassNames;
import consulo.logging.Logger;
import consulo.psi.PsiPackage;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 * @since Jul 30, 2002
 */
public class HighlightUtil extends HighlightUtilBase {
  private static final Logger LOG = Logger.getInstance(HighlightUtil.class);

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers = new HashMap<>(7);
  private static final Map<String, Set<String>> ourMethodIncompatibleModifiers = new HashMap<>(11);
  private static final Map<String, Set<String>> ourFieldIncompatibleModifiers = new HashMap<>(8);
  private static final Map<String, Set<String>> ourClassIncompatibleModifiers = new HashMap<>(8);
  private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers = new HashMap<>(1);
  private static final Map<String, Set<String>> ourModuleIncompatibleModifiers = new HashMap<>(1);
  private static final Map<String, Set<String>> ourRequiresIncompatibleModifiers = new HashMap<>(2);

  private static final Set<String> ourConstructorNotAllowedModifiers = ContainerUtil.newHashSet(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.NATIVE, PsiModifier.FINAL, PsiModifier
      .STRICTFP, PsiModifier.SYNCHRONIZED);

  private static final String SERIAL_PERSISTENT_FIELDS_FIELD_NAME = "serialPersistentFields";

  static {
    ourClassIncompatibleModifiers.put(PsiModifier.ABSTRACT, ContainerUtil.newHashSet(PsiModifier.FINAL));
    ourClassIncompatibleModifiers.put(PsiModifier.FINAL, ContainerUtil.newHashSet(PsiModifier.ABSTRACT));
    ourClassIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.newHashSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourClassIncompatibleModifiers.put(PsiModifier.PRIVATE, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourClassIncompatibleModifiers.put(PsiModifier.PUBLIC, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourClassIncompatibleModifiers.put(PsiModifier.PROTECTED, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourClassIncompatibleModifiers.put(PsiModifier.STRICTFP, Collections.emptySet());
    ourClassIncompatibleModifiers.put(PsiModifier.STATIC, Collections.emptySet());

    ourInterfaceIncompatibleModifiers.put(PsiModifier.ABSTRACT, Collections.emptySet());
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.newHashSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PRIVATE, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PUBLIC, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PROTECTED, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STRICTFP, Collections.emptySet());
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STATIC, Collections.emptySet());

    ourMethodIncompatibleModifiers.put(PsiModifier.ABSTRACT, ContainerUtil.newHashSet(PsiModifier.NATIVE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STRICTFP,
        PsiModifier.SYNCHRONIZED, PsiModifier.DEFAULT));
    ourMethodIncompatibleModifiers.put(PsiModifier.NATIVE, ContainerUtil.newHashSet(PsiModifier.ABSTRACT, PsiModifier.STRICTFP));
    ourMethodIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.newHashSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourMethodIncompatibleModifiers.put(PsiModifier.PRIVATE, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourMethodIncompatibleModifiers.put(PsiModifier.PUBLIC, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourMethodIncompatibleModifiers.put(PsiModifier.PROTECTED, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourMethodIncompatibleModifiers.put(PsiModifier.STATIC, ContainerUtil.newHashSet(PsiModifier.ABSTRACT, PsiModifier.DEFAULT, PsiModifier.FINAL));
    ourMethodIncompatibleModifiers.put(PsiModifier.DEFAULT, ContainerUtil.newHashSet(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.PRIVATE));
    ourMethodIncompatibleModifiers.put(PsiModifier.SYNCHRONIZED, ContainerUtil.newHashSet(PsiModifier.ABSTRACT));
    ourMethodIncompatibleModifiers.put(PsiModifier.STRICTFP, ContainerUtil.newHashSet(PsiModifier.ABSTRACT));
    ourMethodIncompatibleModifiers.put(PsiModifier.FINAL, ContainerUtil.newHashSet(PsiModifier.ABSTRACT));

    ourFieldIncompatibleModifiers.put(PsiModifier.FINAL, ContainerUtil.newHashSet(PsiModifier.VOLATILE));
    ourFieldIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.newHashSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourFieldIncompatibleModifiers.put(PsiModifier.PRIVATE, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourFieldIncompatibleModifiers.put(PsiModifier.PUBLIC, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourFieldIncompatibleModifiers.put(PsiModifier.PROTECTED, ContainerUtil.newHashSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourFieldIncompatibleModifiers.put(PsiModifier.STATIC, Collections.emptySet());
    ourFieldIncompatibleModifiers.put(PsiModifier.TRANSIENT, Collections.emptySet());
    ourFieldIncompatibleModifiers.put(PsiModifier.VOLATILE, ContainerUtil.newHashSet(PsiModifier.FINAL));

    ourClassInitializerIncompatibleModifiers.put(PsiModifier.STATIC, Collections.emptySet());

    ourModuleIncompatibleModifiers.put(PsiModifier.OPEN, Collections.emptySet());

    ourRequiresIncompatibleModifiers.put(PsiModifier.STATIC, Collections.emptySet());
    ourRequiresIncompatibleModifiers.put(PsiModifier.TRANSITIVE, Collections.emptySet());
  }

  private HighlightUtil() {
  }

  @Nullable
  private static String getIncompatibleModifier(String modifier, @Nullable PsiModifierList modifierList, @Nonnull Map<String, Set<String>> incompatibleModifiersHash) {
    if (modifierList == null) {
      return null;
    }

    // modifier is always incompatible with itself
    PsiElement[] modifiers = modifierList.getChildren();
    int modifierCount = 0;
    for (PsiElement otherModifier : modifiers) {
      if (Comparing.equal(modifier, otherModifier.getText(), true)) {
        modifierCount++;
      }
    }
    if (modifierCount > 1) {
      return modifier;
    }

    Set<String> incompatibles = incompatibleModifiersHash.get(modifier);
    if (incompatibles == null) {
      return null;
    }
    final PsiElement parent = modifierList.getParent();
    final boolean level8OrHigher = PsiUtil.isLanguageLevel8OrHigher(modifierList);
    final boolean level9OrHigher = PsiUtil.isLanguageLevel9OrHigher(modifierList);
    for (@PsiModifier.ModifierConstant String incompatible : incompatibles) {
      if (level8OrHigher) {
        if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.ABSTRACT)) {
          continue;
        }
      }
      if (parent instanceof PsiMethod) {
        if (level9OrHigher && modifier.equals(PsiModifier.PRIVATE) && incompatible.equals(PsiModifier.PUBLIC)) {
          continue;
        }

        if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.FINAL)) {
          final PsiClass containingClass = ((PsiMethod) parent).getContainingClass();
          if (containingClass == null || !containingClass.isInterface()) {
            continue;
          }
        }
      }
      if (modifierList.hasModifierProperty(incompatible)) {
        return incompatible;
      }
      if (PsiModifier.ABSTRACT.equals(incompatible) && modifierList.hasExplicitModifier(incompatible)) {
        return incompatible;
      }
    }

    return null;
  }

  /**
   * make element protected/package-private/public suggestion
   * for private method in the interface it should add default modifier as well
   */
  public static void registerAccessQuickFixAction(@Nonnull PsiMember refElement, @Nonnull PsiJavaCodeReferenceElement place, @Nullable HighlightInfo errorResult, final PsiElement fileResolveScope) {
    if (errorResult == null) {
      return;
    }
    PsiClass accessObjectClass = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      accessObjectClass = (PsiClass) PsiUtil.getAccessObjectClass((PsiExpression) qualifier).getElement();
    }
    registerReplaceInaccessibleFieldWithGetterSetterFix(refElement, place, accessObjectClass, errorResult);

    if (refElement instanceof PsiCompiledElement) {
      return;
    }
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) {
      return;
    }

    PsiClass packageLocalClassInTheMiddle = getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(packageLocalClassInTheMiddle, PsiModifier.PUBLIC, true, true);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
      return;
    }

    try {
      Project project = refElement.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiModifierList modifierListCopy = facade.getElementFactory().createFieldFromText("int a;", null).getModifierList();
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      String minModifier = PsiModifier.PACKAGE_LOCAL;
      if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        minModifier = PsiModifier.PROTECTED;
      }
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        minModifier = PsiModifier.PUBLIC;
      }
      PsiClass containingClass = refElement.getContainingClass();
      if (containingClass != null && containingClass.isInterface()) {
        minModifier = PsiModifier.PUBLIC;
      }
      String[] modifiers = {
          PsiModifier.PACKAGE_LOCAL,
          PsiModifier.PROTECTED,
          PsiModifier.PUBLIC,
      };
      for (int i = ArrayUtil.indexOf(modifiers, minModifier); i < modifiers.length; i++) {
        @PsiModifier.ModifierConstant String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, place, accessObjectClass, fileResolveScope)) {
          IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(refElement, modifier, true, true);
          TextRange fixRange = new TextRange(errorResult.startOffset, errorResult.endOffset);
          PsiElement ref = place.getReferenceNameElement();
          if (ref != null) {
            fixRange = fixRange.union(ref.getTextRange());
          }
          QuickFixAction.registerQuickFixAction(errorResult, fixRange, fix);
        }
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static PsiClass getPackageLocalClassInTheMiddle(@Nonnull PsiElement place) {
    if (place instanceof PsiReferenceExpression) {
      // check for package-private classes in the middle
      PsiReferenceExpression expression = (PsiReferenceExpression) place;
      while (true) {
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField) resolved;
          PsiClass aClass = field.getContainingClass();
          if (aClass != null && aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, place)) {

            return aClass;
          }
        }
        PsiExpression qualifier = expression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          break;
        }
        expression = (PsiReferenceExpression) qualifier;
      }
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkInstanceOfApplicable(@Nonnull PsiInstanceOfExpression expression) {
    PsiExpression operand = expression.getOperand();
    PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement == null) {
      return null;
    }
    PsiType checkType = typeElement.getType();
    PsiType operandType = operand.getType();
    if (operandType == null) {
      return null;
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(operandType) || TypeConversionUtil.isPrimitiveAndNotNull(checkType) || !TypeConversionUtil.areTypesConvertible(operandType, checkType)) {
      String message = JavaErrorBundle.message("inconvertible.type.cast", JavaHighlightUtil.formatType(operandType), JavaHighlightUtil.formatType(checkType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
    }
    return null;
  }


  /**
   * 15.16 Cast Expressions
   * ( ReferenceType {AdditionalBound} ) expression, where AdditionalBound: & InterfaceType then all must be true
   * - ReferenceType must denote a class or interface type.
   * - The erasures of all the listed types must be pairwise different.
   * - No two listed types may be subtypes of different parameterization of the same generic interface.
   */
  @Nullable
  public static HighlightInfo checkIntersectionInTypeCast(@Nonnull PsiTypeCastExpression expression, @Nonnull LanguageLevel languageLevel, @Nonnull PsiFile file) {
    PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement != null && isIntersection(castTypeElement, castTypeElement.getType())) {
      HighlightInfo info = checkFeature(expression, Feature.INTERSECTION_CASTS, languageLevel, file);
      if (info != null) {
        return info;
      }

      final PsiTypeElement[] conjuncts = PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class);
      if (conjuncts != null) {
        final Set<PsiType> erasures = new HashSet<>(conjuncts.length);
        erasures.add(TypeConversionUtil.erasure(conjuncts[0].getType()));
        final List<PsiTypeElement> conjList = new ArrayList<>(Arrays.asList(conjuncts));
        for (int i = 1; i < conjuncts.length; i++) {
          final PsiTypeElement conjunct = conjuncts[i];
          final PsiType conjType = conjunct.getType();
          if (conjType instanceof PsiClassType) {
            final PsiClass aClass = ((PsiClassType) conjType).resolve();
            if (aClass != null && !aClass.isInterface()) {
              final HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(conjunct).descriptionAndTooltip(JavaErrorBundle.message("interface" +
                  ".expected")).create();
              QuickFixAction.registerQuickFixAction(errorResult, new FlipIntersectionSidesFix(aClass.getName(), conjList, conjunct, castTypeElement), null);
              return errorResult;
            }
          } else {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(conjunct).descriptionAndTooltip("Unexpected type: class is expected").create();
          }
          if (!erasures.add(TypeConversionUtil.erasure(conjType))) {
            final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(conjunct).descriptionAndTooltip("Repeated interface").create();
            QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteRepeatedInterfaceFix(conjunct, conjList), null);
            return highlightInfo;
          }
        }

        final List<PsiType> typeList = ContainerUtil.map(conjList, PsiTypeElement::getType);
        final Ref<String> differentArgumentsMessage = new Ref<>();
        final PsiClass sameGenericParameterization = InferenceSession.findParameterizationOfTheSameGenericClass(typeList, pair ->
        {
          if (!TypesDistinctProver.provablyDistinct(pair.first, pair.second)) {
            return true;
          }
          differentArgumentsMessage.set(pair.first.getPresentableText() + " and " + pair.second.getPresentableText());
          return false;
        });
        if (sameGenericParameterization != null) {
          final String message = formatClass(sameGenericParameterization) + " cannot be inherited with different arguments: " + differentArgumentsMessage.get();
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  private static boolean isIntersection(PsiTypeElement castTypeElement, PsiType castType) {
    if (castType instanceof PsiIntersectionType) {
      return true;
    }
    return castType instanceof PsiClassType && PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class) != null;
  }

  @Nullable
  public static HighlightInfo checkInconvertibleTypeCast(@Nonnull PsiTypeCastExpression expression) {
    final PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement == null) {
      return null;
    }
    PsiType castType = castTypeElement.getType();

    PsiExpression operand = expression.getOperand();
    if (operand == null) {
      return null;
    }
    PsiType operandType = operand.getType();

    if (operandType != null && !TypeConversionUtil.areTypesConvertible(operandType, castType, PsiUtil.getLanguageLevel(expression)) && !RedundantCastUtil.isInPolymorphicCall(expression)) {
      String message = JavaErrorBundle.message("inconvertible.type.cast", JavaHighlightUtil.formatType(operandType), JavaHighlightUtil.formatType(castType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
    }


    return null;
  }

  @Nullable
  public static HighlightInfo checkVariableExpected(@Nonnull PsiExpression expression) {
    PsiExpression lValue;
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) expression;
      lValue = assignment.getLExpression();
    } else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      lValue = expression instanceof PsiPostfixExpression ? ((PsiPostfixExpression) expression).getOperand() : ((PsiPrefixExpression) expression).getOperand();
    } else {
      lValue = null;
    }
    HighlightInfo errorResult = null;
    if (lValue != null && !TypeConversionUtil.isLValue(lValue)) {
      String description = JavaErrorBundle.message("variable.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(lValue).descriptionAndTooltip(description).create();
    }

    return errorResult;
  }


  @Nullable
  public static HighlightInfo checkAssignmentOperatorApplicable(@Nonnull PsiAssignmentExpression assignment) {
    PsiJavaToken operationSign = assignment.getOperationSign();
    IElementType eqOpSign = operationSign.getTokenType();
    IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
    if (opSign == null) {
      return null;
    }
    final PsiType lType = assignment.getLExpression().getType();
    final PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) {
      return null;
    }
    final PsiType rType = rExpression.getType();
    HighlightInfo errorResult = null;
    if (!TypeConversionUtil.isBinaryOperatorApplicable(opSign, lType, rType, true)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = JavaErrorBundle.message("binary.operator.not.applicable", operatorText, JavaHighlightUtil.formatType(lType), JavaHighlightUtil.formatType(rType));

      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(assignment).descriptionAndTooltip(message).create();
    }
    return errorResult;
  }


  @Nullable
  public static HighlightInfo checkAssignmentCompatibleTypes(@Nonnull PsiAssignmentExpression assignment) {
    PsiExpression lExpr = assignment.getLExpression();
    PsiExpression rExpr = assignment.getRExpression();
    if (rExpr == null) {
      return null;
    }
    PsiType lType = lExpr.getType();
    PsiType rType = rExpr.getType();
    if (rType == null) {
      return null;
    }

    final IElementType sign = assignment.getOperationTokenType();
    HighlightInfo highlightInfo;
    if (JavaTokenType.EQ.equals(sign)) {
      highlightInfo = checkAssignability(lType, rType, rExpr, assignment);
    } else {
      // 15.26.2. Compound Assignment Operators
      final IElementType opSign = TypeConversionUtil.convertEQtoOperation(sign);
      final PsiType type = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opSign, true);
      if (type == null || lType == null || TypeConversionUtil.areTypesConvertible(type, lType)) {
        return null;
      }
      highlightInfo = createIncompatibleTypeHighlightInfo(lType, type, assignment.getTextRange(), 0);
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createChangeToAppendFix(sign, lType, assignment));
    }
    if (highlightInfo == null) {
      return null;
    }
    registerChangeVariableTypeFixes(lExpr, rType, rExpr, highlightInfo);
    if (lType != null) {
      registerChangeVariableTypeFixes(rExpr, lType, lExpr, highlightInfo);
    }
    return highlightInfo;
  }

  private static void registerChangeVariableTypeFixes(@Nonnull PsiExpression expression, @Nonnull PsiType type, @Nullable final PsiExpression lExpr, @Nullable HighlightInfo highlightInfo) {
    if (highlightInfo == null || !(expression instanceof PsiReferenceExpression)) {
      return;
    }

    final PsiElement element = ((PsiReferenceExpression) expression).resolve();
    if (!(element instanceof PsiVariable)) {
      return;
    }

    registerChangeVariableTypeFixes((PsiVariable) element, type, lExpr, highlightInfo);

    if (lExpr instanceof PsiMethodCallExpression && lExpr.getParent() instanceof PsiAssignmentExpression) {
      final PsiElement parent = lExpr.getParent();
      if (parent.getParent() instanceof PsiStatement) {
        final PsiMethod method = ((PsiMethodCallExpression) lExpr).resolveMethod();
        if (method != null && PsiType.VOID.equals(method.getReturnType())) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new ReplaceAssignmentFromVoidWithStatementIntentionAction(parent, lExpr));
        }
      }
    }
  }

  private static boolean isCastIntentionApplicable(@Nonnull PsiExpression expression, @Nullable PsiType toType) {
    while (expression instanceof PsiTypeCastExpression || expression instanceof PsiParenthesizedExpression) {
      if (expression instanceof PsiTypeCastExpression) {
        expression = ((PsiTypeCastExpression) expression).getOperand();
      }
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression) expression).getExpression();
      }
    }
    if (expression == null) {
      return false;
    }
    PsiType rType = expression.getType();
    return rType != null && toType != null && TypeConversionUtil.areTypesConvertible(rType, toType);
  }


  @Nullable
  public static HighlightInfo checkVariableInitializerType(@Nonnull PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    // array initializer checked in checkArrayInitializerApplicable
    if (initializer == null || initializer instanceof PsiArrayInitializerExpression) {
      return null;
    }
    PsiType lType = variable.getType();
    PsiType rType = initializer.getType();
    PsiTypeElement typeElement = variable.getTypeElement();
    int start = typeElement != null ? typeElement.getTextRange().getStartOffset() : variable.getTextRange().getStartOffset();
    int end = variable.getTextRange().getEndOffset();
    HighlightInfo highlightInfo = checkAssignability(lType, rType, initializer, new TextRange(start, end), 0);
    if (highlightInfo != null) {
      registerChangeVariableTypeFixes(variable, rType, variable.getInitializer(), highlightInfo);
      registerChangeVariableTypeFixes(initializer, lType, null, highlightInfo);
    }
    return highlightInfo;
  }

  @Nullable
  public static HighlightInfo checkAssignability(@Nullable PsiType lType, @Nullable PsiType rType, @Nullable PsiExpression expression, @Nonnull PsiElement elementToHighlight) {
    TextRange textRange = elementToHighlight.getTextRange();
    return checkAssignability(lType, rType, expression, textRange, 0);
  }

  @Nullable
  private static HighlightInfo checkAssignability(@Nullable PsiType lType, @Nullable PsiType rType, @Nullable PsiExpression expression, @Nonnull TextRange textRange, int navigationShift) {
    if (lType == rType) {
      return null;
    }
    if (expression == null) {
      if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) {
        return null;
      }
    } else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression)) {
      return null;
    }
    if (rType == null) {
      rType = expression.getType();
    }
    HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(lType, rType, textRange, navigationShift);
    if (rType != null && expression != null && isCastIntentionApplicable(expression, lType)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddTypeCastFix(lType, expression));
    }
    if (expression != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createWrapWithAdapterFix(lType, expression));
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createWrapWithOptionalFix(lType, expression));
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createWrapExpressionFix(lType, expression));
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createWrapStringWithFileFix(lType, expression));
      AddTypeArgumentsConditionalFix.register(highlightInfo, expression, lType);
      registerCollectionToArrayFixAction(highlightInfo, rType, lType, expression);
    }
    ChangeNewOperatorTypeFix.register(highlightInfo, expression, lType);
    return highlightInfo;
  }


  @Nullable
  public static HighlightInfo checkReturnStatementType(@Nonnull PsiReturnStatement statement) {
    PsiMethod method = null;
    PsiLambdaExpression lambda = null;
    PsiElement parent = statement.getParent();
    while (true) {
      if (parent instanceof PsiFile) {
        break;
      }
      if (parent instanceof PsiClassInitializer) {
        break;
      }
      if (parent instanceof PsiLambdaExpression) {
        lambda = (PsiLambdaExpression) parent;
        break;
      }
      if (parent instanceof PsiMethod) {
        method = (PsiMethod) parent;
        break;
      }
      parent = parent.getParent();
    }
    if (parent instanceof PsiCodeFragment) {
      return null;
    }
    String description;
    HighlightInfo errorResult = null;
    if (method == null && lambda != null) {
      //todo check return statements type inside lambda
    } else if (method == null && !(parent instanceof ServerPageFile)) {
      description = JavaErrorBundle.message("return.outside.method");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
    } else {
      PsiType returnType = method != null ? method.getReturnType() : null/*JSP page returns void*/;
      boolean isMethodVoid = returnType == null || PsiType.VOID.equals(returnType);
      final PsiExpression returnValue = statement.getReturnValue();
      if (returnValue != null) {
        PsiType valueType = RefactoringChangeUtil.getTypeByExpression(returnValue);
        if (isMethodVoid) {
          description = JavaErrorBundle.message("return.from.void.method");
          errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
          if (valueType != null) {
            QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMethodReturnFix(method, valueType, true));
          }
        } else {
          TextRange textRange = statement.getTextRange();
          errorResult = checkAssignability(returnType, valueType, returnValue, textRange, returnValue.getStartOffsetInParent());
          if (errorResult != null && valueType != null) {
            if (!PsiType.VOID.equals(valueType)) {
              QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMethodReturnFix(method, valueType, true));
            }
            registerChangeParameterClassFix(returnType, valueType, errorResult);
            if (returnType instanceof PsiArrayType) {
              final PsiType erasedValueType = TypeConversionUtil.erasure(valueType);
              if (erasedValueType != null && TypeConversionUtil.isAssignable(((PsiArrayType) returnType).getComponentType(), erasedValueType)) {
                QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createSurroundWithArrayFix(null, returnValue));
              }
            }
            registerCollectionToArrayFixAction(errorResult, valueType, returnType, returnValue);
          }
        }
      } else {
        if (!isMethodVoid) {
          description = JavaErrorBundle.message("missing.return.value");
          errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).navigationShift(PsiKeyword.RETURN.length()).create();
          QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.VOID, true));
        }
      }
    }
    return errorResult;
  }

  private static void registerCollectionToArrayFixAction(@Nullable HighlightInfo info, @Nullable PsiType fromType, @Nullable PsiType toType, @Nonnull PsiExpression expression) {
    if (toType instanceof PsiArrayType) {
      PsiType arrayComponentType = ((PsiArrayType) toType).getComponentType();
      if (!(arrayComponentType instanceof PsiPrimitiveType) && !(PsiUtil.resolveClassInType(arrayComponentType) instanceof PsiTypeParameter) && InheritanceUtil.isInheritor(fromType,
          JavaClassNames.JAVA_UTIL_COLLECTION)) {
        PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(fromType, expression.getResolveScope());
        if (collectionItemType != null && arrayComponentType.isAssignableFrom(collectionItemType)) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createCollectionToArrayFix(expression, (PsiArrayType) toType));
        }
      }
    }
  }

  @Nonnull
  public static String getUnhandledExceptionsDescriptor(@Nonnull final Collection<PsiClassType> unhandled) {
    return getUnhandledExceptionsDescriptor(unhandled, null);
  }

  @Nonnull
  private static String getUnhandledExceptionsDescriptor(@Nonnull final Collection<PsiClassType> unhandled, @Nullable final String source) {
    final String exceptions = formatTypes(unhandled);
    return source != null ? JavaErrorBundle.message("unhandled.close.exceptions", exceptions, unhandled.size(), source) : JavaErrorBundle.message("unhandled.exceptions", exceptions,
        unhandled.size());
  }

  @Nonnull
  private static String formatTypes(@Nonnull Collection<PsiClassType> unhandled) {
    return StringUtil.join(unhandled, JavaHighlightUtil::formatType, ", ");
  }

  @Nullable
  public static HighlightInfo checkVariableAlreadyDefined(@Nonnull PsiVariable variable) {
    if (variable instanceof ExternallyDefinedPsiElement) {
      return null;
    }
    boolean isIncorrect = false;
    PsiElement declarationScope = null;
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter && ((declarationScope = ((PsiParameter) variable).getDeclarationScope()) instanceof PsiCatchSection ||
        declarationScope instanceof PsiForeachStatement || declarationScope instanceof PsiLambdaExpression)) {
      @SuppressWarnings("unchecked") PsiElement scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class, PsiResourceList.class);
      VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
        @Override
        protected boolean check(final PsiVariable var, final ResolveState state) {
          return (var instanceof PsiLocalVariable || var instanceof PsiParameter) && super.check(var, state);
        }
      };
      PsiIdentifier identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      if (scope instanceof PsiResourceList && proc.size() == 0) {
        scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class);
        PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      }
      if (proc.size() > 0) {
        isIncorrect = true;
      } else if (declarationScope instanceof PsiLambdaExpression) {
        isIncorrect = checkSameNames(variable);
      }
    } else if (variable instanceof PsiField) {
      PsiField field = (PsiField) variable;
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return null;
      }
      PsiField fieldByName = aClass.findFieldByName(variable.getName(), false);
      if (fieldByName != null && fieldByName != field) {
        isIncorrect = true;
      }
    } else {
      isIncorrect = checkSameNames(variable);
    }

    if (isIncorrect) {
      String description = JavaErrorBundle.message("variable.already.defined", variable.getName());
      PsiIdentifier identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(description).create();
      if (variable instanceof PsiLocalVariable) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createReuseVariableDeclarationFix((PsiLocalVariable) variable));
      }
      return highlightInfo;
    }
    return null;
  }

  private static boolean checkSameNames(@Nonnull PsiVariable variable) {
    PsiElement scope = variable.getParent();
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiVariable) {
        if (child.equals(variable)) {
          continue;
        }
        if (Objects.equals(variable.getName(), ((PsiVariable) child).getName())) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static HighlightInfo checkUnderscore(@Nonnull PsiIdentifier identifier, @Nonnull LanguageLevel languageLevel) {
    if ("_".equals(identifier.getText())) {
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
        String text = JavaErrorBundle.message("underscore.identifier.error");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text).create();
      } else if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiParameter && ((PsiParameter) parent).getDeclarationScope() instanceof PsiLambdaExpression) {
          String text = JavaErrorBundle.message("underscore.lambda.identifier");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text).create();
        }
      }
    }

    return null;
  }

  @Nonnull
  public static String formatClass(@Nonnull PsiClass aClass) {
    return formatClass(aClass, true);
  }

  @Nonnull
  public static String formatClass(@Nonnull PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0));
  }

  @Nonnull
  private static String formatField(@Nonnull PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
  }

  @Nullable
  public static HighlightInfo checkUnhandledExceptions(@Nonnull final PsiElement element, @Nullable TextRange textRange) {
    final List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    if (unhandledExceptions.isEmpty()) {
      return null;
    }

    final HighlightInfoType highlightType = getUnhandledExceptionHighlightType(element);
    if (highlightType == null) {
      return null;
    }

    if (textRange == null) {
      textRange = element.getTextRange();
    }
    final String description = getUnhandledExceptionsDescriptor(unhandledExceptions);
    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(highlightType).range(textRange).descriptionAndTooltip(description).create();
    registerUnhandledExceptionFixes(element, errorResult, unhandledExceptions);
    return errorResult;
  }

  @Nullable
  public static HighlightInfo checkUnhandledCloserExceptions(@Nonnull PsiResourceListElement resource) {
    List<PsiClassType> unhandled = ExceptionUtil.getUnhandledCloserExceptions(resource, null);
    if (unhandled.isEmpty()) {
      return null;
    }

    HighlightInfoType highlightType = getUnhandledExceptionHighlightType(resource);
    if (highlightType == null) {
      return null;
    }

    String description = getUnhandledExceptionsDescriptor(unhandled, "auto-closeable resource");
    HighlightInfo highlight = HighlightInfo.newHighlightInfo(highlightType).range(resource).descriptionAndTooltip(description).create();
    registerUnhandledExceptionFixes(resource, highlight, unhandled);
    return highlight;
  }

  private static void registerUnhandledExceptionFixes(PsiElement element, HighlightInfo errorResult, List<PsiClassType> unhandled) {
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToCatchFix());
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionToThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddExceptionFromFieldInitializerToConstructorThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createSurroundWithTryCatchFix(element));
    if (unhandled.size() == 1) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createGeneralizeCatchFix(element, unhandled.get(0)));
    }
  }

  @Nullable
  private static HighlightInfoType getUnhandledExceptionHighlightType(PsiElement element) {
    // JSP top level errors are handled by UnhandledExceptionInJSP inspection
    if (FileTypeUtils.isInServerPageFile(element)) {
      PsiMethod targetMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiLambdaExpression.class);
      if (targetMethod instanceof SyntheticElement) {
        return null;
      }
    }

    return HighlightInfoType.UNHANDLED_EXCEPTION;
  }

  @Nullable
  public static HighlightInfo checkBreakOutsideLoop(@Nonnull PsiBreakStatement statement) {
    if (statement.getLabelIdentifier() == null) {
      if (new PsiMatcherImpl(statement).ancestor(EnclosingLoopOrSwitchMatcherExpression.INSTANCE).getElement() == null) {
        String description = JavaErrorBundle.message("break.outside.switch.or.loop");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
      }
    } else {
      // todo labeled
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkContinueOutsideLoop(@Nonnull PsiContinueStatement statement) {
    if (statement.getLabelIdentifier() == null) {
      if (new PsiMatcherImpl(statement).ancestor(EnclosingLoopMatcherExpression.INSTANCE).getElement() == null) {
        String description = JavaErrorBundle.message("continue.outside.loop");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
      }
    } else {
      PsiStatement exitedStatement = statement.findContinuedStatement();
      if (exitedStatement == null) {
        return null;
      }
      if (!(exitedStatement instanceof PsiForStatement) && !(exitedStatement instanceof PsiWhileStatement) && !(exitedStatement instanceof PsiDoWhileStatement) && !(exitedStatement instanceof
          PsiForeachStatement)) {
        String description = JavaErrorBundle.message("not.loop.label", statement.getLabelIdentifier().getText());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkIllegalModifierCombination(@Nonnull PsiKeyword keyword, @Nonnull PsiModifierList modifierList) {
    @PsiModifier.ModifierConstant String modifier = keyword.getText();
    String incompatible = getIncompatibleModifier(modifier, modifierList);
    if (incompatible != null) {
      String message = JavaErrorBundle.message("incompatible.modifiers", modifier, incompatible);
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(modifierList, modifier, false, false));
      return highlightInfo;
    }

    return null;
  }

  @Contract("null -> null")
  private static Map<String, Set<String>> getIncompatibleModifierMap(@Nullable PsiElement modifierListOwner) {
    if (modifierListOwner == null || PsiUtilCore.hasErrorElementChild(modifierListOwner)) {
      return null;
    }
    if (modifierListOwner instanceof PsiClass) {
      return ((PsiClass) modifierListOwner).isInterface() ? ourInterfaceIncompatibleModifiers : ourClassIncompatibleModifiers;
    }
    if (modifierListOwner instanceof PsiMethod) {
      return ourMethodIncompatibleModifiers;
    }
    if (modifierListOwner instanceof PsiVariable) {
      return ourFieldIncompatibleModifiers;
    }
    if (modifierListOwner instanceof PsiClassInitializer) {
      return ourClassInitializerIncompatibleModifiers;
    }
    if (modifierListOwner instanceof PsiJavaModule) {
      return ourModuleIncompatibleModifiers;
    }
    if (modifierListOwner instanceof PsiRequiresStatement) {
      return ourRequiresIncompatibleModifiers;
    }
    return null;
  }

  @Nullable
  public static String getIncompatibleModifier(String modifier, @Nonnull PsiModifierList modifierList) {
    Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList.getParent());
    return incompatibleModifierMap != null ? getIncompatibleModifier(modifier, modifierList, incompatibleModifierMap) : null;
  }

  @Nullable
  public static HighlightInfo checkNotAllowedModifier(@Nonnull PsiKeyword keyword, @Nonnull PsiModifierList modifierList) {
    PsiElement modifierOwner = modifierList.getParent();
    Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierOwner);
    if (incompatibleModifierMap == null) {
      return null;
    }

    @PsiModifier.ModifierConstant String modifier = keyword.getText();
    Set<String> incompatibles = incompatibleModifierMap.get(modifier);
    PsiElement modifierOwnerParent = modifierOwner instanceof PsiMember ? ((PsiMember) modifierOwner).getContainingClass() : modifierOwner.getParent();
    if (modifierOwnerParent == null) {
      modifierOwnerParent = modifierOwner.getParent();
    }
    boolean isAllowed = true;
    if (modifierOwner instanceof PsiClass) {
      PsiClass aClass = (PsiClass) modifierOwner;
      if (aClass.isInterface()) {
        if (PsiModifier.STATIC.equals(modifier) || PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass;
        }
      } else {
        if (PsiModifier.PUBLIC.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiJavaFile || modifierOwnerParent instanceof PsiClass && (modifierOwnerParent instanceof PsiSyntheticClass || ((PsiClass)
              modifierOwnerParent).getQualifiedName() != null);
        } else if (PsiModifier.STATIC.equals(modifier) || PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass && ((PsiClass) modifierOwnerParent).getQualifiedName() != null || FileTypeUtils.isInServerPageFile(modifierOwnerParent);
        }

        if (aClass.isEnum()) {
          isAllowed &= !(PsiModifier.FINAL.equals(modifier) || PsiModifier.ABSTRACT.equals(modifier));
        }

        if (aClass.getContainingClass() instanceof PsiAnonymousClass) {
          isAllowed &= !(PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier));
        }
      }
    } else if (modifierOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) modifierOwner;
      isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
      PsiClass containingClass = method.getContainingClass();
      if ((method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) && method.isConstructor() && containingClass != null && containingClass.isEnum()) {
        isAllowed = false;
      }

      if (PsiModifier.PRIVATE.equals(modifier)) {
        isAllowed &= modifierOwnerParent instanceof PsiClass && (!((PsiClass) modifierOwnerParent).isInterface() || PsiUtil.isLanguageLevel9OrHigher(modifierOwner) && !((PsiClass)
            modifierOwnerParent).isAnnotationType());
      } else if (PsiModifier.STRICTFP.equals(modifier)) {
        isAllowed &= modifierOwnerParent instanceof PsiClass && (!((PsiClass) modifierOwnerParent).isInterface() || PsiUtil.isLanguageLevel8OrHigher(modifierOwner));
      } else if (PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) || PsiModifier.SYNCHRONIZED.equals(modifier)) {
        isAllowed &= modifierOwnerParent instanceof PsiClass && !((PsiClass) modifierOwnerParent).isInterface();
      }

      if (containingClass != null && containingClass.isInterface()) {
        isAllowed &= !PsiModifier.NATIVE.equals(modifier);
      }

      if (containingClass != null && containingClass.isAnnotationType()) {
        isAllowed &= !PsiModifier.STATIC.equals(modifier);
        isAllowed &= !PsiModifier.DEFAULT.equals(modifier);
      }
    } else if (modifierOwner instanceof PsiField) {
      if (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) || PsiModifier.STRICTFP.equals(modifier) || PsiModifier
          .SYNCHRONIZED.equals(modifier)) {
        isAllowed = modifierOwnerParent instanceof PsiClass && !((PsiClass) modifierOwnerParent).isInterface();
      }
    } else if (modifierOwner instanceof PsiClassInitializer) {
      isAllowed = PsiModifier.STATIC.equals(modifier);
    } else if (modifierOwner instanceof PsiLocalVariable || modifierOwner instanceof PsiParameter) {
      isAllowed = PsiModifier.FINAL.equals(modifier);
    } else if (modifierOwner instanceof PsiReceiverParameter) {
      isAllowed = false;
    }

    isAllowed &= incompatibles != null;
    if (!isAllowed) {
      String message = JavaErrorBundle.message("modifier.not.allowed", modifier);
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(modifierList, modifier, false, false));
      return highlightInfo;
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkLiteralExpressionParsingError(@Nonnull PsiLiteralExpression expression, LanguageLevel level, PsiFile file) {
    PsiElement literal = expression.getFirstChild();
    assert literal instanceof PsiJavaToken : literal;
    IElementType type = ((PsiJavaToken) literal).getTokenType();
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD || type == JavaTokenType.NULL_KEYWORD) {
      return null;
    }

    boolean isInt = ElementType.INTEGER_LITERALS.contains(type);
    boolean isFP = ElementType.REAL_LITERALS.contains(type);
    String text = isInt || isFP ? StringUtil.toLowerCase(literal.getText()) : literal.getText();
    Object value = expression.getValue();

    if (file != null) {
      if (isFP) {
        if (text.startsWith(PsiLiteralUtil.HEX_PREFIX)) {
          HighlightInfo info = checkFeature(expression, Feature.HEX_FP_LITERALS, level, file);
          if (info != null) {
            return info;
          }
        }
      }
      if (isInt) {
        if (text.startsWith(PsiLiteralUtil.BIN_PREFIX)) {
          HighlightInfo info = checkFeature(expression, Feature.BIN_LITERALS, level, file);
          if (info != null) {
            return info;
          }
        }
      }
      if (isInt || isFP) {
        if (text.contains("_")) {
          HighlightInfo info = checkFeature(expression, Feature.UNDERSCORES, level, file);
          if (info != null) {
            return info;
          }
          info = checkUnderscores(expression, text, isInt);
          if (info != null) {
            return info;
          }
        }
      }
    }

    PsiElement parent = expression.getParent();
    if (type == JavaTokenType.INTEGER_LITERAL) {
      String cleanText = StringUtil.replace(text, "_", "");
      //literal 2147483648 may appear only as the operand of the unary negation operator -.
      if (!(cleanText.equals(PsiLiteralUtil._2_IN_31) &&
          parent instanceof PsiPrefixExpression &&
          ((PsiPrefixExpression) parent).getOperationTokenType() == JavaTokenType.MINUS)) {
        if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
          String message = JavaErrorBundle.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
        if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
          String message = JavaErrorBundle.message("binary.numbers.must.contain.at.least.one.hexadecimal.digit");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
        if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_31)) {
          String message = JavaErrorBundle.message("integer.number.too.large");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
      }
    } else if (type == JavaTokenType.LONG_LITERAL) {
      String cleanText = StringUtil.replace(StringUtil.trimEnd(text, 'l'), "_", "");
      //literal 9223372036854775808L may appear only as the operand of the unary negation operator -.
      if (!(cleanText.equals(PsiLiteralUtil._2_IN_63) &&
          parent instanceof PsiPrefixExpression &&
          ((PsiPrefixExpression) parent).getOperationTokenType() == JavaTokenType.MINUS)) {
        if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
          String message = JavaErrorBundle.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
        if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
          String message = JavaErrorBundle.message("binary.numbers.must.contain.at.least.one.hexadecimal.digit");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
        if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_63)) {
          String message = JavaErrorBundle.message("long.number.too.large");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
      }
    } else if (isFP) {
      if (value == null) {
        String message = JavaErrorBundle.message("malformed.floating.point.literal");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      }
    } else if (type == JavaTokenType.CHARACTER_LITERAL) {
      if (value == null) {
        if (!StringUtil.startsWithChar(text, '\'')) {
          return null;
        }
        if (!StringUtil.endsWithChar(text, '\'') || text.length() == 1) {
          String message = JavaErrorBundle.message("unclosed.char.literal");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
        text = text.substring(1, text.length() - 1);

        CharSequence chars = CodeInsightUtilCore.parseStringCharacters(text, null);
        if (chars == null) {
          String message = JavaErrorBundle.message("illegal.escape.character.in.character.literal");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
        int length = chars.length();
        if (length > 1) {
          String message = JavaErrorBundle.message("too.many.characters.in.character.literal");
          HighlightInfo info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createConvertToStringLiteralAction());
          return info;
        } else if (length == 0) {
          String message = JavaErrorBundle.message("empty.character.literal");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
        }
      }
    } else if (type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      if (type == JavaTokenType.STRING_LITERAL) {
        if (value == null) {
          for (PsiElement element = expression.getFirstChild(); element != null; element = element.getNextSibling()) {
            if (element instanceof OuterLanguageElement) {
              return null;
            }
          }

          if (!StringUtil.startsWithChar(text, '\"')) {
            return null;
          }
          if (StringUtil.endsWithChar(text, '\"')) {
            if (text.length() == 1) {
              String message = JavaErrorBundle.message("illegal.line.end.in.string.literal");
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
            }
            text = text.substring(1, text.length() - 1);
          } else {
            String message = JavaErrorBundle.message("illegal.line.end.in.string.literal");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
          }

          if (CodeInsightUtilCore.parseStringCharacters(text, null) == null) {
            String message = JavaErrorBundle.message("illegal.escape.character.in.string.literal");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
          }
        }
      } else {
        if (value == null) {
          if (!text.endsWith("\"\"\"")) {
            String message = JavaErrorBundle.message("text.block.unclosed");
            int p = expression.getTextRange().getEndOffset();
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(p, p).endOfLine().descriptionAndTooltip(message).create();
          } else {
            StringBuilder chars = new StringBuilder(text.length());
            int[] offsets = new int[text.length() + 1];
            boolean success = CodeInsightUtilCore.parseStringCharacters(text, chars, offsets);
            if (!success) {
              String message = JavaErrorBundle.message("illegal.escape.character.in.string.literal");
              TextRange textRange = chars.length() < text.length() - 1 ? new TextRange(offsets[chars.length()], offsets[chars.length() + 1])
                  : expression.getTextRange();
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                  .range(expression, textRange)
                  .descriptionAndTooltip(message).create();
            } else {
              String message = JavaErrorBundle.message("text.block.new.line");
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
            }
          }
        } else {
          if (file != null && containsUnescaped(text, "\\\n")) {
            HighlightInfo info = checkFeature(expression, Feature.TEXT_BLOCK_ESCAPES, level, file);
            if (info != null) {
              return info;
            }
          }
        }
      }
      if (file != null && containsUnescaped(text, "\\s")) {
        HighlightInfo info = checkFeature(expression, Feature.TEXT_BLOCK_ESCAPES, level, file);
        if (info != null) {
          return info;
        }
      }
    }

    if (value instanceof Float) {
      Float number = (Float) value;
      if (number.isInfinite()) {
        String message = JavaErrorBundle.message("floating.point.number.too.large");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      }
      if (number.floatValue() == 0 && !TypeConversionUtil.isFPZero(text)) {
        String message = JavaErrorBundle.message("floating.point.number.too.small");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      }
    } else if (value instanceof Double) {
      Double number = (Double) value;
      if (number.isInfinite()) {
        String message = JavaErrorBundle.message("floating.point.number.too.large");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      }
      if (number.doubleValue() == 0 && !TypeConversionUtil.isFPZero(text)) {
        String message = JavaErrorBundle.message("floating.point.number.too.small");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      }
    }

    return null;
  }

  private static final Pattern FP_LITERAL_PARTS = Pattern.compile("(?:" + "(?:0x([_\\p{XDigit}]*)\\.?([_\\p{XDigit}]*)p[+-]?([_\\d]*))" + "|" + "(?:([_\\d]*)\\.?([_\\d]*)e?[+-]?([_\\d]*))" + ")" +
      "[fd]?");

  private static boolean containsUnescaped(@Nonnull String text, @Nonnull String subText) {
    int start = 0;
    while ((start = StringUtil.indexOf(text, subText, start)) != -1) {
      int nSlashes = 0;
      for (int pos = start - 1; pos >= 0; pos--) {
        if (text.charAt(pos) != '\\') {
          break;
        }
        nSlashes++;
      }
      if (nSlashes % 2 == 0) {
        return true;
      }
      start += subText.length();
    }
    return false;
  }

  private static HighlightInfo checkUnderscores(@Nonnull PsiElement expression, @Nonnull String text, boolean isInt) {
    String[] parts = ArrayUtilRt.EMPTY_STRING_ARRAY;

    if (isInt) {
      int start = 0;
      if (text.startsWith(PsiLiteralUtil.HEX_PREFIX) || text.startsWith(PsiLiteralUtil.BIN_PREFIX)) {
        start += 2;
      }
      int end = text.length();
      if (StringUtil.endsWithChar(text, 'l')) {
        --end;
      }
      parts = new String[]{text.substring(start, end)};
    } else {
      Matcher matcher = FP_LITERAL_PARTS.matcher(text);
      if (matcher.matches()) {
        parts = new String[matcher.groupCount()];
        for (int i = 0; i < matcher.groupCount(); i++) {
          parts[i] = matcher.group(i + 1);
        }
      }
    }

    for (String part : parts) {
      if (part != null && (StringUtil.startsWithChar(part, '_') || StringUtil.endsWithChar(part, '_'))) {
        String message = JavaErrorBundle.message("illegal.underscore");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkMustBeBoolean(@Nonnull PsiExpression expr, PsiType type) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiIfStatement || parent instanceof PsiWhileStatement || parent instanceof PsiForStatement && expr.equals(((PsiForStatement) parent).getCondition()) || parent instanceof
        PsiDoWhileStatement && expr.equals(((PsiDoWhileStatement) parent).getCondition())) {
      if (expr.getNextSibling() instanceof PsiErrorElement) {
        return null;
      }

      if (!TypeConversionUtil.isBooleanType(type)) {
        final HighlightInfo info = createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expr.getTextRange(), 0);
        if (expr instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expr;
          final PsiMethod method = methodCall.resolveMethod();
          if (method != null && PsiType.VOID.equals(method.getReturnType())) {
            QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.BOOLEAN, true));
          }
        } else if (expr instanceof PsiAssignmentExpression && ((PsiAssignmentExpression) expr).getOperationTokenType() == JavaTokenType.EQ) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAssignmentToComparisonFix((PsiAssignmentExpression) expr));
        }
        return info;
      }
    }
    return null;
  }


  @Nonnull
  public static Set<PsiClassType> collectUnhandledExceptions(@Nonnull final PsiTryStatement statement) {
    final Set<PsiClassType> thrownTypes = ContainerUtil.newHashSet();

    final PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock));
    }

    final PsiResourceList resources = statement.getResourceList();
    if (resources != null) {
      thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(resources, resources));
    }

    return thrownTypes;
  }

  @Nonnull
  public static List<HighlightInfo> checkExceptionThrownInTry(@Nonnull final PsiParameter parameter, @Nonnull final Set<PsiClassType> thrownTypes) {
    final PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiCatchSection)) {
      return Collections.emptyList();
    }

    final PsiType caughtType = parameter.getType();
    if (caughtType instanceof PsiClassType) {
      HighlightInfo info = checkSimpleCatchParameter(parameter, thrownTypes, (PsiClassType) caughtType);
      return info == null ? Collections.emptyList() : Collections.singletonList(info);
    }
    if (caughtType instanceof PsiDisjunctionType) {
      return checkMultiCatchParameter(parameter, thrownTypes);
    }

    return Collections.emptyList();
  }

  @Nullable
  private static HighlightInfo checkSimpleCatchParameter(@Nonnull final PsiParameter parameter, @Nonnull final Collection<PsiClassType> thrownTypes, @Nonnull final PsiClassType caughtType) {
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(caughtType)) {
      return null;
    }

    for (PsiClassType exceptionType : thrownTypes) {
      if (exceptionType.isAssignableFrom(caughtType) || caughtType.isAssignableFrom(exceptionType)) {
        return null;
      }
    }

    final String description = JavaErrorBundle.message("exception.never.thrown.try", JavaHighlightUtil.formatType(caughtType));
    final HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createDeleteCatchFix(parameter));
    return errorResult;
  }

  @Nonnull
  private static List<HighlightInfo> checkMultiCatchParameter(@Nonnull final PsiParameter parameter, @Nonnull final Collection<PsiClassType> thrownTypes) {
    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    final List<HighlightInfo> highlights = new ArrayList<>(typeElements.size());

    for (final PsiTypeElement typeElement : typeElements) {
      final PsiType catchType = typeElement.getType();
      if (catchType instanceof PsiClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType) catchType)) {
        continue;
      }

      boolean used = false;
      for (PsiClassType exceptionType : thrownTypes) {
        if (exceptionType.isAssignableFrom(catchType) || catchType.isAssignableFrom(exceptionType)) {
          used = true;
          break;
        }
      }
      if (!used) {
        final String description = JavaErrorBundle.message("exception.never.thrown.try", JavaHighlightUtil.formatType(catchType));
        final HighlightInfo highlight = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(highlight, QUICK_FIX_FACTORY.createDeleteMultiCatchFix(typeElement));
        highlights.add(highlight);
      }
    }

    return highlights;
  }


  @Nonnull
  public static Collection<HighlightInfo> checkWithImprovedCatchAnalysis(@Nonnull PsiParameter parameter, @Nonnull Collection<PsiClassType> thrownInTryStatement, @Nonnull PsiFile containingFile) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiCatchSection)) {
      return Collections.emptyList();
    }

    final PsiCatchSection catchSection = (PsiCatchSection) scope;
    final PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
    final int idx = ArrayUtilRt.find(allCatchSections, catchSection);
    if (idx <= 0) {
      return Collections.emptyList();
    }

    final Collection<PsiClassType> thrownTypes = ContainerUtil.newHashSet(thrownInTryStatement);
    final PsiManager manager = containingFile.getManager();
    final GlobalSearchScope parameterResolveScope = parameter.getResolveScope();
    thrownTypes.add(PsiType.getJavaLangError(manager, parameterResolveScope));
    thrownTypes.add(PsiType.getJavaLangRuntimeException(manager, parameterResolveScope));
    final Collection<HighlightInfo> result = ContainerUtil.newArrayList();

    final List<PsiTypeElement> parameterTypeElements = PsiUtil.getParameterTypeElements(parameter);
    final boolean isMultiCatch = parameterTypeElements.size() > 1;
    for (PsiTypeElement catchTypeElement : parameterTypeElements) {
      final PsiType catchType = catchTypeElement.getType();
      if (ExceptionUtil.isGeneralExceptionType(catchType)) {
        continue;
      }

      // collect exceptions which are caught by this type
      Collection<PsiClassType> caught = ContainerUtil.findAll(thrownTypes, catchType::isAssignableFrom);
      if (caught.isEmpty()) {
        continue;
      }
      final Collection<PsiClassType> caughtCopy = ContainerUtil.newHashSet(caught);

      // exclude all which are caught by previous catch sections
      for (int i = 0; i < idx; i++) {
        final PsiParameter prevCatchParameter = allCatchSections[i].getParameter();
        if (prevCatchParameter == null) {
          continue;
        }
        for (PsiTypeElement prevCatchTypeElement : PsiUtil.getParameterTypeElements(prevCatchParameter)) {
          final PsiType prevCatchType = prevCatchTypeElement.getType();
          caught.removeIf(prevCatchType::isAssignableFrom);
          if (caught.isEmpty()) {
            break;
          }
        }
      }

      // check & warn
      if (caught.isEmpty()) {
        final String message = JavaErrorBundle.message("exception.already.caught.warn", formatTypes(caughtCopy), caughtCopy.size());
        final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(catchSection).descriptionAndTooltip(message).create();
        if (isMultiCatch) {
          QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createDeleteMultiCatchFix(catchTypeElement));
        } else {
          QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createDeleteCatchFix(parameter));
        }
        result.add(highlightInfo);
      }
    }

    return result;
  }


  @Nullable
  public static HighlightInfo checkNotAStatement(@Nonnull PsiStatement statement) {
    if (!PsiUtil.isStatement(statement) && !PsiUtilCore.hasErrorElementChild(statement)) {
      boolean isDeclarationNotAllowed = false;
      if (statement instanceof PsiDeclarationStatement) {
        final PsiElement parent = statement.getParent();
        isDeclarationNotAllowed = parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement;
      }

      String description = JavaErrorBundle.message(isDeclarationNotAllowed ? "declaration.not.allowed" : "not.a.statement");
      HighlightInfo error = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
      if (statement instanceof PsiExpressionStatement) {
        QuickFixAction.registerQuickFixAction(error, QuickFixFactory.getInstance().createDeleteSideEffectAwareFix((PsiExpressionStatement) statement));
      }
      return error;
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkSwitchBlockStatements(@Nonnull PsiSwitchBlock switchBlock,
                                                         @Nonnull LanguageLevel languageLevel,
                                                         @Nonnull PsiFile file) {
    PsiCodeBlock body = switchBlock.getBody();
    if (body != null) {
      PsiElement first = PsiTreeUtil.skipWhitespacesAndCommentsForward(body.getLBrace());
      if (first != null && !(first instanceof PsiSwitchLabelStatementBase) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
        String description = JavaErrorBundle.message("statement.must.be.prepended.with.case.label");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(first).descriptionAndTooltip(description).create();
      }

      PsiElement element = first;
      PsiStatement alien = null;
      boolean classicLabels = false;
      boolean enhancedLabels = false;
      boolean levelChecked = false;
      while (element != null && !PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)) {
        if (element instanceof PsiSwitchLabeledRuleStatement) {
          if (!levelChecked) {
            HighlightInfo info = checkFeature(element, Feature.ENHANCED_SWITCH, languageLevel, file);
            if (info != null) {
              return info;
            }
            levelChecked = true;
          }
          if (classicLabels) {
            alien = (PsiStatement) element;
            break;
          }
          enhancedLabels = true;
        } else if (element instanceof PsiStatement) {
          if (enhancedLabels) {
            alien = (PsiStatement) element;
            break;
          }
          classicLabels = true;
        }

        if (!levelChecked && element instanceof PsiSwitchLabelStatementBase) {
          PsiExpressionList values = ((PsiSwitchLabelStatementBase) element).getCaseValues();
          if (values != null && values.getExpressionCount() > 1) {
            HighlightInfo info = checkFeature(values, Feature.ENHANCED_SWITCH, languageLevel, file);
            if (info != null) {
              return info;
            }
            levelChecked = true;
          }
        }

        element = PsiTreeUtil.skipWhitespacesAndCommentsForward(element);
      }
      if (alien != null) {
        if (enhancedLabels && !(alien instanceof PsiSwitchLabelStatementBase)) {
          PsiSwitchLabeledRuleStatement previousRule = PsiTreeUtil.getPrevSiblingOfType(alien, PsiSwitchLabeledRuleStatement.class);
          String description = JavaErrorBundle.message("statement.must.be.prepended.with.case.label");
          HighlightInfo info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(alien).descriptionAndTooltip(description).create();
          if (previousRule != null) {
            QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapSwitchRuleStatementsIntoBlockFix(previousRule));
          }
          return info;
        }
        String description = JavaErrorBundle.message("different.case.kinds.in.switch");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(alien).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkSwitchSelectorType(@Nonnull PsiSwitchBlock switchBlock, @Nonnull LanguageLevel level) {
    PsiExpression expression = switchBlock.getExpression();
    if (expression == null) {
      return null;
    }
    PsiType type = expression.getType();
    if (type == null) {
      return null;
    }

    SelectorKind kind = getSwitchSelectorKind(type);
    if (kind == SelectorKind.INT) {
      return null;
    }

    LanguageLevel requiredLevel = null;
    if (kind == SelectorKind.ENUM) {
      requiredLevel = LanguageLevel.JDK_1_5;
    }
    if (kind == SelectorKind.STRING) {
      requiredLevel = LanguageLevel.JDK_1_7;
    }

    if (kind == null || requiredLevel != null && !level.isAtLeast(requiredLevel)) {
      boolean is7 = level.isAtLeast(LanguageLevel.JDK_1_7);
      String expected = JavaErrorBundle.message(is7 ? "valid.switch.17.selector.types" : "valid.switch.selector.types");
      String message = JavaErrorBundle.message("incompatible.types", expected, JavaHighlightUtil.formatType(type));
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      if (switchBlock instanceof PsiSwitchStatement) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createConvertSwitchToIfIntention((PsiSwitchStatement) switchBlock));
      }
      if (PsiType.LONG.equals(type) || PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type)) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddTypeCastFix(PsiType.INT, expression));
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapWithAdapterFix(PsiType.INT, expression));
      }
      if (requiredLevel != null) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createIncreaseLanguageLevelFix(requiredLevel));
      }
      return info;
    }

    PsiClass member = PsiUtil.resolveClassInClassTypeOnly(type);
    if (member != null && !PsiUtil.isAccessible(member.getProject(), member, expression, null)) {
      String className = PsiFormatUtil.formatClass(member, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
      String message = JavaErrorBundle.message("inaccessible.type", className);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
    }

    return null;
  }

  private enum SelectorKind {
    INT,
    ENUM,
    STRING
  }

  private static SelectorKind getSwitchSelectorKind(@Nonnull PsiType type) {
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.INT_RANK) {
      return SelectorKind.INT;
    }

    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (psiClass != null) {
      if (psiClass.isEnum()) {
        return SelectorKind.ENUM;
      }
      if (Comparing.strEqual(psiClass.getQualifiedName(), JavaClassNames.JAVA_LANG_STRING)) {
        return SelectorKind.STRING;
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkPolyadicOperatorApplicable(@Nonnull PsiPolyadicExpression expression) {
    PsiExpression[] operands = expression.getOperands();

    PsiType lType = operands[0].getType();
    IElementType operationSign = expression.getOperationTokenType();
    for (int i = 1; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      PsiType rType = operand.getType();
      if (!TypeConversionUtil.isBinaryOperatorApplicable(operationSign, lType, rType, false)) {
        PsiJavaToken token = expression.getTokenBeforeOperand(operand);
        String message = JavaErrorBundle.message("binary.operator.not.applicable", token.getText(), JavaHighlightUtil.formatType(lType), JavaHighlightUtil.formatType(rType));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message).create();
      }
      lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, operationSign, true);
    }

    return null;
  }


  @Nullable
  public static HighlightInfo checkUnaryOperatorApplicable(@Nullable PsiJavaToken token, @Nullable PsiExpression expression) {
    if (token != null && expression != null && !TypeConversionUtil.isUnaryOperatorApplicable(token, expression)) {
      PsiType type = expression.getType();
      if (type == null) {
        return null;
      }
      String message = JavaErrorBundle.message("unary.operator.not.applicable", token.getText(), JavaHighlightUtil.formatType(type));

      PsiElement parentExpr = token.getParent();
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parentExpr).descriptionAndTooltip(message).create();
      if (parentExpr instanceof PsiPrefixExpression && token.getTokenType() == JavaTokenType.EXCL) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createNegationBroadScopeFix((PsiPrefixExpression) parentExpr));
      }
      return highlightInfo;
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkThisOrSuperExpressionInIllegalContext(@Nonnull PsiExpression expr, @Nullable PsiJavaCodeReferenceElement qualifier, @Nonnull LanguageLevel languageLevel) {
    if (expr instanceof PsiSuperExpression) {
      final PsiElement parent = expr.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        // like in 'Object o = super;'
        final int o = expr.getTextRange().getEndOffset();
        String description = JavaErrorBundle.message("dot.expected.after.super.or.this");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(o, o + 1).descriptionAndTooltip(description).create();
      }
    }

    PsiClass aClass;
    if (qualifier != null) {
      PsiElement resolved = qualifier.advancedResolve(true).getElement();
      if (resolved != null && !(resolved instanceof PsiClass)) {
        String description = JavaErrorBundle.message("class.expected");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description).create();
      }
      aClass = (PsiClass) resolved;
    } else {
      aClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
      if (aClass instanceof PsiAnonymousClass && PsiTreeUtil.isAncestor(((PsiAnonymousClass) aClass).getArgumentList(), expr, false)) {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
      }
    }
    if (aClass == null) {
      return null;
    }

    if (!InheritanceUtil.hasEnclosingInstanceInScope(aClass, expr, false, false)) {
      if (!resolvesToImmediateSuperInterface(expr, qualifier, aClass, languageLevel)) {
        return HighlightClassUtil.reportIllegalEnclosingUsage(expr, null, aClass, expr);
      }

      if (expr instanceof PsiSuperExpression) {
        final PsiElement resolved = ((PsiReferenceExpression) expr.getParent()).resolve();
        //15.11.2
        //The form T.super.Identifier refers to the field named Identifier of the lexically enclosing instance corresponding to T,
        //but with that instance viewed as an instance of the superclass of T.
        if (resolved instanceof PsiField) {
          String description = JavaErrorBundle.message("is.not.an.enclosing.class", formatClass(aClass));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description).create();
        }
      }
    }

    if (qualifier != null && aClass.isInterface() && expr instanceof PsiSuperExpression && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      //15.12.1 for method invocation expressions; 15.13 for method references
      //If TypeName denotes an interface, I, then let T be the type declaration immediately enclosing the method reference expression.
      //It is a compile-time error if I is not a direct superinterface of T,
      //or if there exists some other direct superclass or direct superinterface of T, J, such that J is a subtype of I.
      final PsiClass classT = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
      if (classT != null) {
        final PsiElement parent = expr.getParent();
        final PsiElement resolved = parent instanceof PsiReferenceExpression ? ((PsiReferenceExpression) parent).resolve() : null;

        PsiClass containingClass = ObjectUtil.notNull(resolved instanceof PsiMethod ? ((PsiMethod) resolved).getContainingClass() : null, aClass);
        for (PsiClass superClass : classT.getSupers()) {
          if (superClass.isInheritor(containingClass, true)) {
            String cause = null;
            if (superClass.isInheritor(aClass, true) && superClass.isInterface()) {
              cause = "redundant interface " + format(containingClass) + " is extended by ";
            } else if (resolved instanceof PsiMethod && MethodSignatureUtil.findMethodBySuperMethod(superClass, (PsiMethod) resolved, true) != resolved) {
              cause = "method " + ((PsiMethod) resolved).getName() + " is overridden in ";
            }

            if (cause != null) {
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(JavaErrorBundle.message("bad.qualifier.in.super.method.reference",
                  cause + formatClass(superClass))).create();
            }
          }
        }

        if (!classT.isInheritor(aClass, false)) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(JavaErrorBundle.message("no.enclosing.instance.in.scope", format(aClass)))
              .create();
        }
      }
    }

    if (expr instanceof PsiThisExpression) {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
      if (psiMethod == null || psiMethod.getContainingClass() != aClass && !isInsideDefaultMethod(psiMethod, aClass)) {
        if (aClass.isInterface()) {
          return thisNotFoundInInterfaceInfo(expr);
        }

        if (aClass instanceof PsiAnonymousClass && PsiTreeUtil.isAncestor(((PsiAnonymousClass) aClass).getArgumentList(), expr, true)) {
          final PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
          if (parentClass != null && parentClass.isInterface()) {
            return thisNotFoundInInterfaceInfo(expr);
          }
        }
      }
    }
    return null;
  }

  public static HighlightInfo checkUnqualifiedSuperInDefaultMethod(@Nonnull LanguageLevel languageLevel, @Nonnull PsiReferenceExpression expr, PsiExpression qualifier) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && qualifier instanceof PsiSuperExpression) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
      if (method != null && method.hasModifierProperty(PsiModifier.DEFAULT) && ((PsiSuperExpression) qualifier).getQualifier() == null) {
        String description = JavaErrorBundle.message("unqualified.super.disallowed");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description).create();
        QualifySuperArgumentFix.registerQuickFixAction((PsiSuperExpression) qualifier, info);
        return info;
      }
    }
    return null;
  }

  private static boolean isInsideDefaultMethod(PsiMethod method, PsiClass aClass) {
    while (method != null && method.getContainingClass() != aClass) {
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class, true);
    }
    return method != null && method.hasModifierProperty(PsiModifier.DEFAULT);
  }

  private static HighlightInfo thisNotFoundInInterfaceInfo(@Nonnull PsiExpression expr) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip("Cannot find symbol variable this").create();
  }

  private static boolean resolvesToImmediateSuperInterface(@Nonnull PsiExpression expr,
                                                           @Nullable PsiJavaCodeReferenceElement qualifier,
                                                           @Nonnull PsiClass aClass,
                                                           @Nonnull LanguageLevel languageLevel) {
    if (!(expr instanceof PsiSuperExpression) || qualifier == null || !languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      return false;
    }
    final PsiType superType = expr.getType();
    if (!(superType instanceof PsiClassType)) {
      return false;
    }
    final PsiClass superClass = ((PsiClassType) superType).resolve();
    return superClass != null && aClass.equals(superClass) && PsiUtil.getEnclosingStaticElement(expr, PsiTreeUtil.getParentOfType(expr, PsiClass.class)) == null;
  }

  @Nonnull
  public static String buildProblemWithStaticDescription(@Nonnull PsiElement refElement) {
    String type = LanguageFindUsages.INSTANCE.forLanguage(JavaLanguage.INSTANCE).getType(refElement);
    String name = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
    return JavaErrorBundle.message("non.static.symbol.referenced.from.static.context", type, name);
  }

  public static void registerStaticProblemQuickFixAction(@Nonnull PsiElement refElement, HighlightInfo errorResult, @Nonnull PsiJavaCodeReferenceElement place) {
    if (refElement instanceof PsiModifierListOwner) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix((PsiModifierListOwner) refElement, PsiModifier.STATIC, true, false));
    }
    // make context non static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null && isInstanceReference(place)) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false));
    }
    if (place instanceof PsiReferenceExpression && refElement instanceof PsiField) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createCreateFieldFromUsageFix((PsiReferenceExpression) place));
    }
  }

  private static boolean isInstanceReference(@Nonnull PsiJavaCodeReferenceElement place) {
    PsiElement qualifier = place.getQualifier();
    if (qualifier == null) {
      return true;
    }
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    PsiElement q = ((PsiReference) qualifier).resolve();
    if (q instanceof PsiClass) {
      return false;
    }
    if (q != null) {
      return true;
    }
    String qname = ((PsiJavaCodeReferenceElement) qualifier).getQualifiedName();
    return qname == null || !Character.isLowerCase(qname.charAt(0));
  }

  @Nonnull
  public static String buildProblemWithAccessDescription(@Nonnull final PsiElement reference, @Nonnull final JavaResolveResult result) {
    return buildProblemWithAccessDescription(reference, result, ObjectUtil.notNull(result.getElement()));
  }

  @Nonnull
  private static String buildProblemWithAccessDescription(@Nonnull final PsiElement reference, @Nonnull final JavaResolveResult result, @Nonnull final PsiElement resolved) {
    assert resolved instanceof PsiModifierListOwner : resolved;
    PsiModifierListOwner refElement = (PsiModifierListOwner) resolved;
    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());

    if (refElement.hasModifierProperty(PsiModifier.PRIVATE)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return JavaErrorBundle.message("private.symbol", symbolName, containerName);
    } else if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return JavaErrorBundle.message("protected.symbol", symbolName, containerName);
    } else {
      PsiClass packageLocalClass = getPackageLocalClassInTheMiddle(reference);
      if (packageLocalClass != null) {
        refElement = packageLocalClass;
        symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
      }
      if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
        String containerName = getContainerName(refElement, result.getSubstitutor());
        return JavaErrorBundle.message("package.local.symbol", symbolName, containerName);
      } else {
        String containerName = getContainerName(refElement, result.getSubstitutor());
        return JavaErrorBundle.message("visibility.access.problem", symbolName, containerName);
      }
    }
  }

  private static PsiElement getContainer(PsiModifierListOwner refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
      final PsiElement container = provider.getContainer(refElement);
      if (container != null) {
        return container;
      }
    }
    return refElement.getParent();
  }

  private static String getContainerName(PsiModifierListOwner refElement, final PsiSubstitutor substitutor) {
    final PsiElement container = getContainer(refElement);
    return container == null ? "?" : HighlightMessageUtil.getSymbolName(container, substitutor);
  }

  @Nullable
  public static HighlightInfo checkValidArrayAccessExpression(@Nonnull PsiArrayAccessExpression arrayAccessExpression) {
    final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    final PsiType arrayExpressionType = arrayExpression.getType();

    if (arrayExpressionType != null && !(arrayExpressionType instanceof PsiArrayType)) {
      final String description = JavaErrorBundle.message("array.type.expected", JavaHighlightUtil.formatType(arrayExpressionType));
      final HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(arrayExpression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createReplaceWithListAccessFix(arrayAccessExpression));
      return info;
    }

    final PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
    return indexExpression != null ? checkAssignability(PsiType.INT, indexExpression.getType(), indexExpression, indexExpression) : null;
  }


  @Nullable
  public static HighlightInfo checkCatchParameterIsThrowable(@Nonnull final PsiParameter parameter) {
    if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
      final PsiType type = parameter.getType();
      return checkMustBeThrowable(type, parameter, true);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkTryResourceIsAutoCloseable(@Nonnull PsiResourceListElement resource) {
    PsiType type = resource.getType();
    if (type == null) {
      return null;
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(resource.getProject()).getElementFactory();
    PsiClassType autoCloseable = factory.createTypeByFQClassName(JavaClassNames.JAVA_LANG_AUTO_CLOSEABLE, resource.getResolveScope());
    if (TypeConversionUtil.isAssignable(autoCloseable, type)) {
      return null;
    }

    return createIncompatibleTypeHighlightInfo(autoCloseable, type, resource.getTextRange(), 0);
  }

  @Nullable
  public static HighlightInfo checkResourceVariableIsFinal(@Nonnull PsiResourceExpression resource) {
    PsiExpression expression = resource.getExpression();

    if (expression instanceof PsiThisExpression) {
      return null;
    }

    if (expression instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression) expression).resolve();
      if (target == null) {
        return null;
      }

      if (target instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable) target;

        PsiModifierList modifierList = variable.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) {
          return null;
        }

        if (!(variable instanceof PsiField) && HighlightControlFlowUtil.isEffectivelyFinal(variable, resource, (PsiJavaCodeReferenceElement) expression)) {
          return null;
        }
      }

      String text = JavaErrorBundle.message("resource.variable.must.be.final");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text).create();
    }

    String text = JavaErrorBundle.message("declaration.or.variable.expected");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text).create();
  }

  @Nonnull
  public static Collection<HighlightInfo> checkArrayInitializer(final PsiExpression initializer, PsiType type) {
    if (!(initializer instanceof PsiArrayInitializerExpression)) {
      return Collections.emptyList();
    }
    if (!(type instanceof PsiArrayType)) {
      return Collections.emptyList();
    }

    final PsiType componentType = ((PsiArrayType) type).getComponentType();
    final PsiArrayInitializerExpression arrayInitializer = (PsiArrayInitializerExpression) initializer;

    boolean arrayTypeFixChecked = false;
    VariableArrayTypeFix fix = null;

    final Collection<HighlightInfo> result = ContainerUtil.newArrayList();
    final PsiExpression[] initializers = arrayInitializer.getInitializers();
    for (PsiExpression expression : initializers) {
      final HighlightInfo info = checkArrayInitializerCompatibleTypes(expression, componentType);
      if (info != null) {
        result.add(info);

        if (!arrayTypeFixChecked) {
          final PsiType checkResult = JavaHighlightUtil.sameType(initializers);
          fix = checkResult != null ? VariableArrayTypeFix.createFix(arrayInitializer, checkResult) : null;
          arrayTypeFixChecked = true;
        }
        if (fix != null) {
          QuickFixAction.registerQuickFixAction(info, new LocalQuickFixOnPsiElementAsIntentionAdapter(fix));
        }
      }
    }
    return result;
  }

  @Nullable
  private static HighlightInfo checkArrayInitializerCompatibleTypes(@Nonnull PsiExpression initializer, final PsiType componentType) {
    PsiType initializerType = initializer.getType();
    if (initializerType == null) {
      String description = JavaErrorBundle.message("illegal.initializer", JavaHighlightUtil.formatType(componentType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(initializer).descriptionAndTooltip(description).create();
    }
    PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
    return checkAssignability(componentType, initializerType, expression, initializer);
  }

  @Nullable
  public static HighlightInfo checkExpressionRequired(@Nonnull PsiReferenceExpression expression, @Nonnull JavaResolveResult resultForIncompleteCode) {
    if (expression.getNextSibling() instanceof PsiErrorElement) {
      return null;
    }

    PsiElement resolved = resultForIncompleteCode.getElement();
    if (resolved == null || resolved instanceof PsiVariable) {
      return null;
    }

    PsiElement parent = expression.getParent();
    // String.class or String() are both correct
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression) {
      return null;
    }

    String description = JavaErrorBundle.message("expression.expected");
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
    UnresolvedReferenceQuickFixProvider.registerReferenceFixes(expression, new QuickFixActionRegistrarImpl(info));
    return info;
  }

  @Nullable
  public static HighlightInfo checkArrayInitializerApplicable(@Nonnull PsiArrayInitializerExpression expression) {
  /*
  JLS 10.6 Array Initializers
    An array initializer may be specified in a declaration, or as part of an array creation expression
    */
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable) parent;
      if (variable.getType() instanceof PsiArrayType) {
        return null;
      }
    } else if (parent instanceof PsiNewExpression || parent instanceof PsiArrayInitializerExpression) {
      return null;
    }

    String description = JavaErrorBundle.message("array.initializer.not.allowed");
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddNewArrayExpressionFix(expression));
    return info;
  }


  @Nullable
  public static HighlightInfo checkCaseStatement(@Nonnull PsiSwitchLabelStatementBase statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) {
      String description = JavaErrorBundle.message("case.statement.outside.switch");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
    }

    return null;
  }

  @Nonnull
  public static Collection<HighlightInfo> checkSwitchLabelValues(@Nonnull PsiSwitchBlock switchBlock) {
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) {
      return Collections.emptyList();
    }

    PsiExpression selectorExpression = switchBlock.getExpression();
    PsiType selectorType = selectorExpression == null ? PsiType.INT : selectorExpression.getType();
    MultiMap<Object, PsiElement> values = new MultiMap<>();
    Object defaultValue = new Object();
    Collection<HighlightInfo> results = new ArrayList<>();
    boolean hasDefaultCase = false;

    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase)) {
        continue;
      }
      PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase) st;
      boolean defaultCase = labelStatement.isDefaultCase();

      if (defaultCase) {
        values.putValue(defaultValue, ObjectUtils.notNull(labelStatement.getFirstChild(), labelStatement));
        hasDefaultCase = true;
      } else {
        PsiExpressionList expressionList = labelStatement.getCaseValues();
        if (expressionList != null) {
          for (PsiExpression expr : expressionList.getExpressions()) {
            if (selectorExpression != null) {
              HighlightInfo result = checkAssignability(selectorType, expr.getType(), expr, expr);
              if (result != null) {
                results.add(result);
                continue;
              }
            }

            Object value = null;
            if (expr instanceof PsiReferenceExpression) {
              PsiElement element = ((PsiReferenceExpression) expr).resolve();
              if (element instanceof PsiEnumConstant) {
                value = ((PsiEnumConstant) element).getName();
                if (((PsiReferenceExpression) expr).getQualifier() != null) {
                  String message = JavaErrorBundle.message("qualified.enum.constant.in.switch");
                  results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(message).create());
                  continue;
                }
              }
            }
            if (value == null) {
              value = ConstantExpressionUtil.computeCastTo(expr, selectorType);
            }
            if (value == null) {
              String description = JavaErrorBundle.message("constant.expression.required");
              results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description).create());
              continue;
            }

            values.putValue(value, expr);
          }
        }
      }
    }

    for (Map.Entry<Object, Collection<PsiElement>> entry : values.entrySet()) {
      if (entry.getValue().size() > 1) {
        Object value = entry.getKey();
        String description = value == defaultValue ? JavaErrorBundle.message("duplicate.default.switch.label") : JavaErrorBundle.message("duplicate.switch.label", value);
        for (PsiElement element : entry.getValue()) {
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create());
        }
      }
    }

    if (results.isEmpty() && switchBlock instanceof PsiSwitchExpression) {
      Set<String> missingConstants = new HashSet<>();
      boolean exhaustive = hasDefaultCase;
      if (!exhaustive) {
        if (!values.isEmpty() && selectorType instanceof PsiClassType) {
          PsiClass type = ((PsiClassType) selectorType).resolve();
          if (type != null && type.isEnum()) {
            for (PsiField field : type.getFields()) {
              if (field instanceof PsiEnumConstant && !values.containsKey(field.getName())) {
                missingConstants.add(field.getName());
              }
            }
            exhaustive = missingConstants.isEmpty();
          }
        }
      }
      if (!exhaustive) {
        PsiElement range = ObjectUtils.notNull(selectorExpression, switchBlock);
        String message = JavaErrorBundle.message(values.isEmpty() ? "switch.expr.empty" : "switch.expr.incomplete");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
        if (!missingConstants.isEmpty()) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddMissingEnumBranchesFix(switchBlock, missingConstants));
        }
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddSwitchDefaultFix(switchBlock, null));
        results.add(info);
      }
    }

    return results;
  }


  /**
   * see JLS 8.3.2.3
   */
  @Nullable
  public static HighlightInfo checkIllegalForwardReferenceToField(@Nonnull PsiReferenceExpression expression, @Nonnull PsiField referencedField) {
    Boolean isIllegalForwardReference = isIllegalForwardReferenceToField(expression, referencedField, false);
    if (isIllegalForwardReference == null) {
      return null;
    }
    String description = JavaErrorBundle.message(isIllegalForwardReference ? "illegal.forward.reference" : "illegal.self.reference");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
  }

  public static Boolean isIllegalForwardReferenceToField(@Nonnull PsiReferenceExpression expression, @Nonnull PsiField referencedField, boolean acceptQualified) {
    PsiClass containingClass = referencedField.getContainingClass();
    if (containingClass == null) {
      return null;
    }
    if (expression.getContainingFile() != referencedField.getContainingFile()) {
      return null;
    }
    if (expression.getTextRange().getStartOffset() >= referencedField.getTextRange().getEndOffset()) {
      return null;
    }
    // only simple reference can be illegal
    if (!acceptQualified && expression.getQualifierExpression() != null) {
      return null;
    }
    PsiField initField = findEnclosingFieldInitializer(expression);
    PsiClassInitializer classInitializer = findParentClassInitializer(expression);
    if (initField == null && classInitializer == null) {
      return null;
    }
    // instance initializers may access static fields
    boolean isStaticClassInitializer = classInitializer != null && classInitializer.hasModifierProperty(PsiModifier.STATIC);
    boolean isStaticInitField = initField != null && initField.hasModifierProperty(PsiModifier.STATIC);
    boolean inStaticContext = isStaticInitField || isStaticClassInitializer;
    if (!inStaticContext && referencedField.hasModifierProperty(PsiModifier.STATIC)) {
      return null;
    }
    if (PsiUtil.isOnAssignmentLeftHand(expression) && !PsiUtil.isAccessedForReading(expression)) {
      return null;
    }
    if (!containingClass.getManager().areElementsEquivalent(containingClass, PsiTreeUtil.getParentOfType(expression, PsiClass.class))) {
      return null;
    }
    return initField != referencedField;
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  @Nullable
  public static PsiField findEnclosingFieldInitializer(@Nullable PsiElement element) {
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField) {
        PsiField field = (PsiField) parent;
        if (element == field.getInitializer()) {
          return field;
        }
        if (field instanceof PsiEnumConstant && element == ((PsiEnumConstant) field).getArgumentList()) {
          return field;
        }
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) {
        return null;
      }
      element = parent;
    }
    return null;
  }

  @Nullable
  private static PsiClassInitializer findParentClassInitializer(@Nullable PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClassInitializer) {
        return (PsiClassInitializer) element;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) {
        return null;
      }
      element = element.getParent();
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkIllegalType(@Nullable PsiTypeElement typeElement) {
    if (typeElement == null || typeElement.getParent() instanceof PsiTypeElement) {
      return null;
    }

    if (PsiUtil.isInsideJavadocComment(typeElement)) {
      return null;
    }

    PsiType type = typeElement.getType();
    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiClassType) {
      PsiClass aClass = PsiUtil.resolveClassInType(componentType);
      if (aClass == null) {
        String canonicalText = type.getCanonicalText();
        String description = JavaErrorBundle.message("unknown.class", canonicalText);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();

        PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
        if (referenceElement != null && info != null) {
          UnresolvedReferenceQuickFixProvider.registerReferenceFixes(referenceElement, new QuickFixActionRegistrarImpl(info));
        }
        return info;
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkIllegalVoidType(@Nonnull PsiKeyword type) {
    if (!PsiKeyword.VOID.equals(type.getText())) {
      return null;
    }

    PsiElement parent = type.getParent();
    if (parent instanceof PsiTypeElement) {
      PsiElement typeOwner = parent.getParent();
      if (typeOwner != null) {
        // do not highlight incomplete declarations
        if (PsiUtilCore.hasErrorElementChild(typeOwner)) {
          return null;
        }
      }

      if (typeOwner instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) typeOwner;
        if (method.getReturnTypeElement() == parent && PsiType.VOID.equals(method.getReturnType())) {
          return null;
        }
      } else if (typeOwner instanceof PsiClassObjectAccessExpression) {
        if (TypeConversionUtil.isVoidType(((PsiClassObjectAccessExpression) typeOwner).getOperand().getType())) {
          return null;
        }
      } else if (typeOwner instanceof JavaCodeFragment) {
        if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) {
          return null;
        }
      }
    }

    String description = JavaErrorBundle.message("illegal.type.void");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(type).descriptionAndTooltip(description).create();
  }

  @Nullable
  public static HighlightInfo checkMemberReferencedBeforeConstructorCalled(@Nonnull PsiElement expression, PsiElement resolved, @Nonnull PsiFile containingFile) {
    PsiClass referencedClass;
    @NonNls String resolvedName;
    PsiType type;
    if (expression instanceof PsiJavaCodeReferenceElement) {
      // redirected ctr
      if (PsiKeyword.THIS.equals(((PsiJavaCodeReferenceElement) expression).getReferenceName()) && resolved instanceof PsiMethod && ((PsiMethod) resolved).isConstructor()) {
        return null;
      }
      PsiElement qualifier = ((PsiJavaCodeReferenceElement) expression).getQualifier();
      type = qualifier instanceof PsiExpression ? ((PsiExpression) qualifier).getType() : null;
      referencedClass = PsiUtil.resolveClassInType(type);

      boolean isSuperCall = RefactoringChangeUtil.isSuperMethodCall(expression.getParent());
      if (resolved == null && isSuperCall) {
        if (qualifier instanceof PsiReferenceExpression) {
          resolved = ((PsiReferenceExpression) qualifier).resolve();
          expression = qualifier;
          type = ((PsiReferenceExpression) qualifier).getType();
          referencedClass = PsiUtil.resolveClassInType(type);
        } else if (qualifier == null) {
          resolved = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class);
          if (resolved != null) {
            referencedClass = ((PsiMethod) resolved).getContainingClass();
          }
        } else if (qualifier instanceof PsiThisExpression) {
          referencedClass = PsiUtil.resolveClassInType(((PsiThisExpression) qualifier).getType());
        }
      }
      if (resolved instanceof PsiField) {
        PsiField referencedField = (PsiField) resolved;
        if (referencedField.hasModifierProperty(PsiModifier.STATIC)) {
          return null;
        }
        resolvedName = PsiFormatUtil.formatVariable(referencedField, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        referencedClass = referencedField.getContainingClass();
      } else if (resolved instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) resolved;
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          return null;
        }
        PsiElement nameElement = expression instanceof PsiThisExpression ? expression : ((PsiJavaCodeReferenceElement) expression).getReferenceNameElement();
        String name = nameElement == null ? null : nameElement.getText();
        if (isSuperCall) {
          if (referencedClass == null) {
            return null;
          }
          if (qualifier == null) {
            PsiClass superClass = referencedClass.getSuperClass();
            if (superClass != null && PsiUtil.isInnerClass(superClass) && InheritanceUtil.isInheritorOrSelf(referencedClass, superClass.getContainingClass(), true)) {
              // by default super() is considered this. - qualified
              resolvedName = PsiKeyword.THIS;
            } else {
              return null;
            }
          } else {
            resolvedName = qualifier.getText();
          }
        } else if (PsiKeyword.THIS.equals(name)) {
          resolvedName = PsiKeyword.THIS;
        } else {
          resolvedName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, 0);
          if (referencedClass == null) {
            referencedClass = method.getContainingClass();
          }
        }
      } else if (resolved instanceof PsiClass) {
        PsiClass aClass = (PsiClass) resolved;
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
          return null;
        }
        referencedClass = aClass.getContainingClass();
        if (referencedClass == null) {
          return null;
        }
        resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME);
      } else {
        return null;
      }
    } else if (expression instanceof PsiThisExpression) {
      PsiThisExpression thisExpression = (PsiThisExpression) expression;
      type = thisExpression.getType();
      referencedClass = PsiUtil.resolveClassInType(type);
      if (thisExpression.getQualifier() != null) {
        resolvedName = referencedClass == null ? null : PsiFormatUtil.formatClass(referencedClass, PsiFormatUtilBase.SHOW_NAME) + ".this";
      } else {
        resolvedName = "this";
      }
    } else {
      return null;
    }
    if (referencedClass == null) {
      return null;
    }
    return checkReferenceToOurInstanceInsideThisOrSuper(expression, referencedClass, resolvedName, containingFile);
  }

  @Nullable
  private static HighlightInfo checkReferenceToOurInstanceInsideThisOrSuper(@Nonnull final PsiElement expression,
                                                                            @Nonnull PsiClass referencedClass,
                                                                            final String resolvedName,
                                                                            @Nonnull PsiFile containingFile) {
    if (PsiTreeUtil.getParentOfType(expression, PsiReferenceParameterList.class) != null) {
      return null;
    }
    PsiElement element = expression.getParent();
    while (element != null) {
      // check if expression inside super()/this() call
      if (RefactoringChangeUtil.isSuperOrThisMethodCall(element)) {
        PsiElement parentClass = new PsiMatcherImpl(element).parent(PsiMatchers.hasClass(PsiExpressionStatement.class)).parent(PsiMatchers.hasClass(PsiCodeBlock.class)).parent(PsiMatchers
            .hasClass(PsiMethod.class)).dot(JavaMatchers.isConstructor(true)).parent(PsiMatchers.hasClass(PsiClass.class)).getElement();
        if (parentClass == null) {
          return null;
        }

        // only this class/superclasses instance methods are not allowed to call
        PsiClass aClass = (PsiClass) parentClass;
        if (PsiUtil.isInnerClass(aClass) && referencedClass == aClass.getContainingClass()) {
          return null;
        }
        // field or method should be declared in this class or super
        if (!InheritanceUtil.isInheritorOrSelf(aClass, referencedClass, true)) {
          return null;
        }
        // and point to our instance
        if (expression instanceof PsiReferenceExpression && !thisOrSuperReference(((PsiReferenceExpression) expression).getQualifierExpression(), aClass)) {
          return null;
        }

        if (expression instanceof PsiJavaCodeReferenceElement && !aClass.equals(PsiTreeUtil.getParentOfType(expression, PsiClass.class)) && PsiTreeUtil.getParentOfType(expression,
            PsiTypeElement.class) != null) {
          return null;
        }

        if (expression instanceof PsiJavaCodeReferenceElement && PsiTreeUtil.getParentOfType(expression, PsiClassObjectAccessExpression.class) != null) {
          return null;
        }

        final HighlightInfo highlightInfo = createMemberReferencedError(resolvedName, expression.getTextRange());
        if (expression instanceof PsiReferenceExpression && PsiUtil.isInnerClass(aClass)) {
          final String referenceName = ((PsiReferenceExpression) expression).getReferenceName();
          final PsiClass containingClass = aClass.getContainingClass();
          LOG.assertTrue(containingClass != null);
          final PsiField fieldInContainingClass = containingClass.findFieldByName(referenceName, true);
          if (fieldInContainingClass != null && ((PsiReferenceExpression) expression).getQualifierExpression() == null) {
            QuickFixAction.registerQuickFixAction(highlightInfo, new QualifyWithThisFix(containingClass, expression));
          }
        }

        return highlightInfo;
      }

      if (element instanceof PsiReferenceExpression) {
        final PsiElement resolve;
        if (element instanceof PsiReferenceExpressionImpl) {
          PsiReferenceExpressionImpl referenceExpression = (PsiReferenceExpressionImpl) element;
          JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(referenceExpression, PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE, true, false, containingFile);
          resolve = results.length == 1 ? results[0].getElement() : null;
        } else {
          resolve = ((PsiReferenceExpression) element).resolve();
        }
        if (resolve instanceof PsiField && ((PsiField) resolve).hasModifierProperty(PsiModifier.STATIC)) {
          return null;
        }
      }

      element = element.getParent();
      if (element instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass) element, referencedClass, true)) {
        return null;
      }
    }
    return null;
  }

  private static HighlightInfo createMemberReferencedError(@NonNls final String resolvedName, @Nonnull TextRange textRange) {
    String description = JavaErrorBundle.message("member.referenced.before.constructor.called", resolvedName);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
  }

  @Nullable
  public static HighlightInfo checkImplicitThisReferenceBeforeSuper(@Nonnull PsiClass aClass, @Nonnull JavaSdkVersion javaSdkVersion) {
    if (javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7)) {
      return null;
    }
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiTypeParameter) {
      return null;
    }
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null || !PsiUtil.isInnerClass(superClass)) {
      return null;
    }
    PsiClass outerClass = superClass.getContainingClass();
    if (!InheritanceUtil.isInheritorOrSelf(aClass, outerClass, true)) {
      return null;
    }
    // 'this' can be used as an (implicit) super() qualifier
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return createMemberReferencedError(aClass.getName() + ".this", range);
    }
    for (PsiMethod constructor : constructors) {
      if (!isSuperCalledInConstructor(constructor)) {
        return createMemberReferencedError(aClass.getName() + ".this", HighlightNamesUtil.getMethodDeclarationTextRange(constructor));
      }
    }
    return null;
  }

  private static boolean isSuperCalledInConstructor(@Nonnull final PsiMethod constructor) {
    final PsiCodeBlock body = constructor.getBody();
    if (body == null) {
      return false;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return false;
    }
    final PsiStatement statement = statements[0];
    final PsiElement element = new PsiMatcherImpl(statement).dot(PsiMatchers.hasClass(PsiExpressionStatement.class)).firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class)).firstChild
        (PsiMatchers.hasClass(PsiReferenceExpression.class)).firstChild(PsiMatchers.hasClass(PsiKeyword.class)).dot(PsiMatchers.hasText(PsiKeyword.SUPER)).getElement();
    return element != null;
  }

  private static boolean thisOrSuperReference(@Nullable PsiExpression qualifierExpression, PsiClass aClass) {
    if (qualifierExpression == null) {
      return true;
    }
    PsiJavaCodeReferenceElement qualifier;
    if (qualifierExpression instanceof PsiThisExpression) {
      qualifier = ((PsiThisExpression) qualifierExpression).getQualifier();
    } else if (qualifierExpression instanceof PsiSuperExpression) {
      qualifier = ((PsiSuperExpression) qualifierExpression).getQualifier();
    } else {
      return false;
    }
    if (qualifier == null) {
      return true;
    }
    PsiElement resolved = qualifier.resolve();
    return resolved instanceof PsiClass && InheritanceUtil.isInheritorOrSelf(aClass, (PsiClass) resolved, true);
  }


  @Nullable
  public static HighlightInfo checkLabelWithoutStatement(@Nonnull PsiLabeledStatement statement) {
    if (statement.getStatement() == null) {
      String description = JavaErrorBundle.message("label.without.statement");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkLabelAlreadyInUse(@Nonnull PsiLabeledStatement statement) {
    PsiIdentifier identifier = statement.getLabelIdentifier();
    String text = identifier.getText();
    PsiElement element = statement;
    while (element != null) {
      if (element instanceof PsiMethod || element instanceof PsiClass) {
        break;
      }
      if (element instanceof PsiLabeledStatement && element != statement && Comparing.equal(((PsiLabeledStatement) element).getLabelIdentifier().getText(), text)) {
        String description = JavaErrorBundle.message("duplicate.label", text);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(description).create();
      }
      element = element.getParent();
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkUnclosedComment(@Nonnull PsiComment comment) {
    if (!(comment instanceof PsiDocComment) && comment.getTokenType() != JavaTokenType.C_STYLE_COMMENT) {
      return null;
    }
    if (!comment.getText().endsWith("*/")) {
      int start = comment.getTextRange().getEndOffset() - 1;
      int end = start + 1;
      String description = JavaErrorBundle.message("unclosed.comment");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description).create();
    }
    return null;
  }


  @Nonnull
  public static Collection<HighlightInfo> checkCatchTypeIsDisjoint(@Nonnull final PsiParameter parameter) {
    if (!(parameter.getType() instanceof PsiDisjunctionType)) {
      return Collections.emptyList();
    }

    final Collection<HighlightInfo> result = ContainerUtil.newArrayList();
    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (int i = 0, size = typeElements.size(); i < size; i++) {
      final PsiClass class1 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(i).getType());
      if (class1 == null) {
        continue;
      }
      for (int j = i + 1; j < size; j++) {
        final PsiClass class2 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(j).getType());
        if (class2 == null) {
          continue;
        }
        final boolean sub = InheritanceUtil.isInheritorOrSelf(class1, class2, true);
        final boolean sup = InheritanceUtil.isInheritorOrSelf(class2, class1, true);
        if (sub || sup) {
          final String name1 = PsiFormatUtil.formatClass(class1, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          final String name2 = PsiFormatUtil.formatClass(class2, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          final String message = JavaErrorBundle.message("exception.must.be.disjoint", sub ? name1 : name2, sub ? name2 : name1);
          final PsiTypeElement element = typeElements.get(sub ? i : j);
          final HighlightInfo highlight = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(highlight, QUICK_FIX_FACTORY.createDeleteMultiCatchFix(element));
          result.add(highlight);
          break;
        }
      }
    }

    return result;
  }


  @Nonnull
  public static Collection<HighlightInfo> checkExceptionAlreadyCaught(@Nonnull final PsiParameter parameter) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiCatchSection)) {
      return Collections.emptyList();
    }

    final PsiCatchSection catchSection = (PsiCatchSection) scope;
    final PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
    final int startFrom = ArrayUtilRt.find(allCatchSections, catchSection) - 1;
    if (startFrom < 0) {
      return Collections.emptyList();
    }

    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    final boolean isInMultiCatch = typeElements.size() > 1;
    final Collection<HighlightInfo> result = ContainerUtil.newArrayList();

    for (PsiTypeElement typeElement : typeElements) {
      final PsiClass catchClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
      if (catchClass == null) {
        continue;
      }

      for (int i = startFrom; i >= 0; i--) {
        final PsiCatchSection upperCatchSection = allCatchSections[i];
        final PsiType upperCatchType = upperCatchSection.getCatchType();

        final boolean highlight = upperCatchType instanceof PsiDisjunctionType ? checkMultipleTypes(catchClass, ((PsiDisjunctionType) upperCatchType).getDisjunctions()) : checkSingleType
            (catchClass, upperCatchType);
        if (highlight) {
          final String className = PsiFormatUtil.formatClass(catchClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          final String description = JavaErrorBundle.message("exception.already.caught", className);
          final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
          result.add(highlightInfo);

          QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createMoveCatchUpFix(catchSection, upperCatchSection));
          if (isInMultiCatch) {
            QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createDeleteMultiCatchFix(typeElement));
          } else {
            QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createDeleteCatchFix(parameter));
          }
        }
      }
    }

    return result;
  }

  private static boolean checkMultipleTypes(final PsiClass catchClass, @Nonnull final List<PsiType> upperCatchTypes) {
    for (int i = upperCatchTypes.size() - 1; i >= 0; i--) {
      if (checkSingleType(catchClass, upperCatchTypes.get(i))) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkSingleType(final PsiClass catchClass, final PsiType upperCatchType) {
    final PsiClass upperCatchClass = PsiUtil.resolveClassInType(upperCatchType);
    return upperCatchClass != null && InheritanceUtil.isInheritorOrSelf(catchClass, upperCatchClass, true);
  }


  @Nullable
  public static HighlightInfo checkTernaryOperatorConditionIsBoolean(@Nonnull PsiExpression expression, PsiType type) {
    if (expression.getParent() instanceof PsiConditionalExpression && ((PsiConditionalExpression) expression.getParent()).getCondition() == expression && !TypeConversionUtil.isBooleanType(type)) {
      return createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expression.getTextRange(), 0);
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkStatementPrependedWithCaseInsideSwitch(@Nonnull PsiSwitchStatement statement) {
    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      PsiElement first = PsiTreeUtil.skipSiblingsForward(body.getLBrace(), PsiWhiteSpace.class, PsiComment.class);
      if (first != null && !(first instanceof PsiSwitchLabelStatement) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
        String description = JavaErrorBundle.message("statement.must.be.prepended.with.case.label");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(first).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }


  @Nullable
  public static HighlightInfo checkAssertOperatorTypes(@Nonnull PsiExpression expression, @Nullable PsiType type) {
    if (type == null) {
      return null;
    }
    if (!(expression.getParent() instanceof PsiAssertStatement)) {
      return null;
    }
    PsiAssertStatement assertStatement = (PsiAssertStatement) expression.getParent();
    if (expression == assertStatement.getAssertCondition() && !TypeConversionUtil.isBooleanType(type)) {
      // addTypeCast quickfix is not applicable here since no type can be cast to boolean
      HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expression.getTextRange(), 0);
      if (expression instanceof PsiAssignmentExpression && ((PsiAssignmentExpression) expression).getOperationTokenType() == JavaTokenType.EQ) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAssignmentToComparisonFix((PsiAssignmentExpression) expression));
      }
      return highlightInfo;
    }
    if (expression == assertStatement.getAssertDescription() && TypeConversionUtil.isVoidType(type)) {
      String description = JavaErrorBundle.message("void.type.is.not.allowed");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkSynchronizedExpressionType(@Nonnull PsiExpression expression, @Nullable PsiType type, @Nonnull PsiFile containingFile) {
    if (type == null) {
      return null;
    }
    if (expression.getParent() instanceof PsiSynchronizedStatement) {
      PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement) expression.getParent();
      if (expression == synchronizedStatement.getLockExpression() && (type instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(type))) {
        PsiClassType objectType = PsiType.getJavaLangObject(containingFile.getManager(), expression.getResolveScope());
        return createIncompatibleTypeHighlightInfo(objectType, type, expression.getTextRange(), 0);
      }
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkConditionalExpressionBranchTypesMatch(@Nonnull final PsiExpression expression, PsiType type) {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiConditionalExpression)) {
      return null;
    }
    PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) parent;
    // check else branches only
    if (conditionalExpression.getElseExpression() != expression) {
      return null;
    }
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    assert thenExpression != null;
    PsiType thenType = thenExpression.getType();
    if (thenType == null || type == null) {
      return null;
    }
    if (conditionalExpression.getType() == null) {
      if (PsiUtil.isLanguageLevel8OrHigher(conditionalExpression) && PsiPolyExpressionUtil.isPolyExpression(conditionalExpression)) {
        return null;
      }
      // cannot derive type of conditional expression
      // elseType will never be cast-able to thenType, so no quick fix here
      return createIncompatibleTypeHighlightInfo(thenType, type, expression.getTextRange(), 0);
    }
    return null;
  }

  @SuppressWarnings("StringContatenationInLoop")
  public static HighlightInfo createIncompatibleTypeHighlightInfo(final PsiType lType, final PsiType rType, @Nonnull final TextRange textRange, int navigationShift) {
    PsiType lType1 = lType;
    PsiType rType1 = rType;
    PsiTypeParameter[] lTypeParams = PsiTypeParameter.EMPTY_ARRAY;
    PsiSubstitutor lTypeSubstitutor = PsiSubstitutor.EMPTY;
    if (lType1 instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType) lType1).resolveGenerics();
      lTypeSubstitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      if (psiClass instanceof PsiAnonymousClass) {
        lType1 = ((PsiAnonymousClass) psiClass).getBaseClassType();
        resolveResult = ((PsiClassType) lType1).resolveGenerics();
        lTypeSubstitutor = resolveResult.getSubstitutor();
        psiClass = resolveResult.getElement();
      }
      lTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    PsiTypeParameter[] rTypeParams = PsiTypeParameter.EMPTY_ARRAY;
    PsiSubstitutor rTypeSubstitutor = PsiSubstitutor.EMPTY;
    if (rType1 instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType) rType1).resolveGenerics();
      rTypeSubstitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      if (psiClass instanceof PsiAnonymousClass) {
        rType1 = ((PsiAnonymousClass) psiClass).getBaseClassType();
        resolveResult = ((PsiClassType) rType1).resolveGenerics();
        rTypeSubstitutor = resolveResult.getSubstitutor();
        psiClass = resolveResult.getElement();
      }
      rTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }

    int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
    @Language("HTML") @NonNls String requiredRow = "";
    @Language("HTML") @NonNls String foundRow = "";
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstitutedType = lTypeParameter == null ? null : lTypeSubstitutor.substitute(lTypeParameter);
      PsiType rSubstitutedType = rTypeParameter == null ? null : rTypeSubstitutor.substitute(rTypeParameter);
      boolean matches = Comparing.equal(lSubstitutedType, rSubstitutedType);
      @NonNls String openBrace = i == 0 ? "&lt;" : "";
      @NonNls String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      requiredRow += "<td>" + (lTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(lSubstitutedType, matches) + (i < lTypeParams.length ? closeBrace : "") + "</td>";
      foundRow += "<td>" + (rTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(rSubstitutedType, matches) + (i < rTypeParams.length ? closeBrace : "") + "</td>";
    }
    PsiType lRawType = lType1 instanceof PsiClassType ? ((PsiClassType) lType1).rawType() : lType1;
    PsiType rRawType = rType1 instanceof PsiClassType ? ((PsiClassType) rType1).rawType() : rType1;
    boolean assignable = lRawType == null || rRawType == null || TypeConversionUtil.isAssignable(lRawType, rRawType);

    String toolTip = JavaErrorBundle.message("incompatible.types.html.tooltip", redIfNotMatch(lRawType, assignable), requiredRow, redIfNotMatch(rRawType, assignable), foundRow);

    String description = JavaErrorBundle.message("incompatible.types", JavaHighlightUtil.formatType(lType1), JavaHighlightUtil.formatType(rType1));

    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).description(description).escapedToolTip(toolTip).navigationShift(navigationShift).create();
  }

  @Nullable
  public static HighlightInfo checkSingleImportClassConflict(@Nonnull PsiImportStatement statement,
                                                             @Nonnull Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> importedClasses,
                                                             @Nonnull PsiFile containingFile) {
    if (statement.isOnDemand()) {
      return null;
    }
    PsiElement element = statement.resolve();
    if (element instanceof PsiClass) {
      String name = ((PsiClass) element).getName();
      Pair<PsiImportStaticReferenceElement, PsiClass> imported = importedClasses.get(name);
      PsiClass importedClass = imported == null ? null : imported.getSecond();
      if (importedClass != null && !containingFile.getManager().areElementsEquivalent(importedClass, element)) {
        String description = JavaErrorBundle.message("single.import.class.conflict", formatClass(importedClass));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description).create();
      }
      importedClasses.put(name, Pair.create(null, (PsiClass) element));
    }
    return null;
  }


  @NonNls
  private static String redIfNotMatch(PsiType type, boolean matches) {
    if (matches) {
      return getFQName(type, false);
    }
    String color = UIUtil.isUnderDarcula() ? "FF6B68" : "red";
    return "<font color='" + color + "'><b>" + getFQName(type, true) + "</b></font>";
  }

  private static String getFQName(@Nullable PsiType type, boolean longName) {
    if (type == null) {
      return "";
    }
    return XmlStringUtil.escapeString(longName ? type.getInternalCanonicalText() : type.getPresentableText());
  }


  @Nullable
  public static HighlightInfo checkMustBeThrowable(@Nullable PsiType type, @Nonnull PsiElement context, boolean addCastIntention) {
    if (type == null) {
      return null;
    }
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiClassType throwable = factory.createTypeByFQClassName("java.lang.Throwable", context.getResolveScope());
    if (!TypeConversionUtil.isAssignable(throwable, type)) {
      HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(throwable, type, context.getTextRange(), 0);
      if (addCastIntention && TypeConversionUtil.areTypesConvertible(type, throwable)) {
        if (context instanceof PsiExpression) {
          QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddTypeCastFix(throwable, (PsiExpression) context));
        }
      }

      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (aClass != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createExtendsListFix(aClass, throwable, true));
      }
      return highlightInfo;
    }
    return null;
  }


  @Nullable
  private static HighlightInfo checkMustBeThrowable(@Nullable PsiClass aClass, @Nonnull PsiElement context) {
    if (aClass == null) {
      return null;
    }
    PsiClassType type = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
    return checkMustBeThrowable(type, context, false);
  }


  @Nullable
  public static HighlightInfo checkLabelDefined(@Nullable PsiIdentifier labelIdentifier, @Nullable PsiStatement exitedStatement) {
    if (labelIdentifier == null) {
      return null;
    }
    String label = labelIdentifier.getText();
    if (label == null) {
      return null;
    }
    if (exitedStatement == null) {
      String message = JavaErrorBundle.message("unresolved.label", label);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(labelIdentifier).descriptionAndTooltip(message).create();
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkReference(@Nonnull PsiJavaCodeReferenceElement ref, @Nonnull JavaResolveResult result, @Nonnull PsiFile containingFile, @Nonnull LanguageLevel languageLevel) {
    PsiElement refName = ref.getReferenceNameElement();
    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) {
      return null;
    }
    PsiElement resolved = result.getElement();

    HighlightInfo highlightInfo = checkMemberReferencedBeforeConstructorCalled(ref, resolved, containingFile);
    if (highlightInfo != null) {
      return highlightInfo;
    }

    PsiElement refParent = ref.getParent();
    PsiElement granny;
    if (refParent instanceof PsiReferenceExpression && (granny = refParent.getParent()) instanceof PsiMethodCallExpression) {
      PsiReferenceExpression referenceToMethod = ((PsiMethodCallExpression) granny).getMethodExpression();
      PsiExpression qualifierExpression = referenceToMethod.getQualifierExpression();
      if (qualifierExpression == ref && resolved != null && !(resolved instanceof PsiClass) && !(resolved instanceof PsiVariable)) {
        String message = JavaErrorBundle.message("qualifier.must.be.expression");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(qualifierExpression).descriptionAndTooltip(message).create();
      }
    } else if (refParent instanceof PsiMethodCallExpression) {
      return null;  // methods checked elsewhere
    }

    if (resolved == null) {
      // do not highlight unknown packages (javac does not care), Javadoc, and module references (checked elsewhere)
      PsiElement outerParent = getOuterReferenceParent(ref);
      if (outerParent instanceof PsiPackageStatement || result.isPackagePrefixPackageReference() || PsiUtil.isInsideJavadocComment(ref) || outerParent instanceof
          PsiPackageAccessibilityStatement) {
        return null;
      }

      JavaResolveResult[] results = ref.multiResolve(true);
      String description;
      if (results.length > 1) {
        String t1 = format(ObjectUtil.notNull(results[0].getElement()));
        String t2 = format(ObjectUtil.notNull(results[1].getElement()));
        description = JavaErrorBundle.message("ambiguous.reference", refName.getText(), t1, t2);
      } else {
        description = JavaErrorBundle.message("cannot.resolve.symbol", refName.getText());
      }

      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description).create();
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, new QuickFixActionRegistrarImpl(info));
      return info;
    }

    if (!result.isValidResult() && !PsiUtil.isInsideJavadocComment(ref)) {
      if (!result.isAccessible()) {
        String message = buildProblemWithAccessDescription(ref, result, resolved);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(message).create();
        if (result.isStaticsScopeCorrect()) {
          registerAccessQuickFixAction((PsiMember) resolved, ref, info, result.getCurrentFileResolveScope());
          if (ref instanceof PsiReferenceExpression) {
            QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRenameWrongRefFix((PsiReferenceExpression) ref));
          }
        }
        UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, new QuickFixActionRegistrarImpl(info));
        return info;
      }

      if (!result.isStaticsScopeCorrect()) {
        String description = buildProblemWithStaticDescription(resolved);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description).create();
        registerStaticProblemQuickFixAction(resolved, info, ref);
        if (ref instanceof PsiReferenceExpression) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRenameWrongRefFix((PsiReferenceExpression) ref));
        }
        return info;
      }
    }

    if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
      return HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable) resolved, ref, languageLevel);
    }
    if (resolved instanceof PsiClass && ((PsiClass) resolved).getContainingClass() == null && PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null && PsiUtil
        .isFromDefaultPackage((PsiClass) resolved)) {
      String description = JavaErrorBundle.message("cannot.resolve.symbol", refName.getText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description).create();
    }

    return null;
  }

  @Nonnull
  private static String format(@Nonnull PsiElement element) {
    if (element instanceof PsiClass) {
      return formatClass((PsiClass) element);
    }
    if (element instanceof PsiMethod) {
      return JavaHighlightUtil.formatMethod((PsiMethod) element);
    }
    if (element instanceof PsiField) {
      return formatField((PsiField) element);
    }
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  private static PsiElement getOuterReferenceParent(PsiJavaCodeReferenceElement ref) {
    PsiElement element = ref;
    while (element instanceof PsiJavaCodeReferenceElement) {
      element = element.getParent();
    }
    return element;
  }

  @Nullable
  public static HighlightInfo checkPackageAndClassConflict(@Nonnull PsiJavaCodeReferenceElement ref, @Nonnull PsiFile containingFile) {
    if (ref.isQualified() && getOuterReferenceParent(ref) instanceof PsiPackageStatement) {
      VirtualFile file = containingFile.getVirtualFile();
      if (file != null) {
        Module module = ProjectFileIndex.SERVICE.getInstance(ref.getProject()).getModuleForFile(file);
        if (module != null) {
          GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
          PsiClass aClass = JavaPsiFacade.getInstance(ref.getProject()).findClass(ref.getCanonicalText(), scope);
          if (aClass != null) {
            String message = JavaErrorBundle.message("package.clashes.with.class", ref.getText());
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(message).create();
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkElementInReferenceList(@Nonnull PsiJavaCodeReferenceElement ref, @Nonnull PsiReferenceList referenceList, @Nonnull JavaResolveResult resolveResult) {
    PsiElement resolved = resolveResult.getElement();
    HighlightInfo highlightInfo = null;
    PsiElement refGrandParent = referenceList.getParent();
    if (resolved instanceof PsiClass) {
      PsiClass aClass = (PsiClass) resolved;
      if (refGrandParent instanceof PsiClass) {
        if (refGrandParent instanceof PsiTypeParameter) {
          highlightInfo = GenericsHighlightUtil.checkElementInTypeParameterExtendsList(referenceList, (PsiClass) refGrandParent, resolveResult, ref);
        } else {
          highlightInfo = HighlightClassUtil.checkExtendsClassAndImplementsInterface(referenceList, resolveResult, ref);
          if (highlightInfo == null) {
            highlightInfo = HighlightClassUtil.checkCannotInheritFromFinal(aClass, ref);
          }
          if (highlightInfo == null) {
            highlightInfo = GenericsHighlightUtil.checkCannotInheritFromEnum(aClass, ref);
          }
          if (highlightInfo == null) {
            highlightInfo = GenericsHighlightUtil.checkCannotInheritFromTypeParameter(aClass, ref);
          }
        }
      } else if (refGrandParent instanceof PsiMethod && ((PsiMethod) refGrandParent).getThrowsList() == referenceList) {
        highlightInfo = checkMustBeThrowable(aClass, ref);
      }
    } else if (refGrandParent instanceof PsiMethod && referenceList == ((PsiMethod) refGrandParent).getThrowsList()) {
      String description = JavaErrorBundle.message("class.name.expected");
      highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description).create();
    }
    return highlightInfo;
  }


  public static boolean isSerializationImplicitlyUsedField(@Nonnull PsiField field) {
    final String name = field.getName();
    if (!SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !SERIAL_PERSISTENT_FIELDS_FIELD_NAME.equals(name)) {
      return false;
    }
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    PsiClass aClass = field.getContainingClass();
    return aClass == null || JavaHighlightUtil.isSerializable(aClass);
  }

  @Nullable
  public static HighlightInfo checkClassReferenceAfterQualifier(@Nonnull final PsiReferenceExpression expression, final PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) {
      return null;
    }
    final PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) {
      return null;
    }
    if (qualifier instanceof PsiReferenceExpression) {
      PsiElement qualifierResolved = ((PsiReferenceExpression) qualifier).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) {
        return null;
      }
    }
    String description = JavaErrorBundle.message("expected.class.or.package");
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRemoveQualifierFix(qualifier, expression, (PsiClass) resolved));
    return info;
  }

  public static void registerChangeVariableTypeFixes(@Nonnull PsiVariable parameter, PsiType itemType, @Nullable PsiExpression expr, @Nonnull HighlightInfo highlightInfo) {
    for (IntentionAction action : getChangeVariableTypeFixes(parameter, itemType)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, action);
    }
    if (expr instanceof PsiMethodCallExpression) {
      final PsiMethod method = ((PsiMethodCallExpression) expr).resolveMethod();
      if (method != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, PriorityActionWrapper.lowPriority(method, QUICK_FIX_FACTORY.createMethodReturnFix(method, parameter.getType(), true)));
      }
    }
  }

  @Nonnull
  public static List<IntentionAction> getChangeVariableTypeFixes(@Nonnull PsiVariable parameter, PsiType itemType) {
    if (itemType instanceof PsiMethodReferenceType) {
      return Collections.emptyList();
    }
    List<IntentionAction> result = new ArrayList<>();
    if (itemType != null) {
      for (ChangeVariableTypeQuickFixProvider fixProvider : Extensions.getExtensions(ChangeVariableTypeQuickFixProvider.EP_NAME)) {
        Collections.addAll(result, fixProvider.getFixes(parameter, itemType));
      }
    }
    IntentionAction changeFix = getChangeParameterClassFix(parameter.getType(), itemType);
    if (changeFix != null) {
      result.add(changeFix);
    }
    return result;
  }

  @Nullable
  public static HighlightInfo checkAnnotationMethodParameters(@Nonnull PsiParameterList list) {
    final PsiElement parent = list.getParent();
    if (PsiUtil.isAnnotationMethod(parent) && list.getParametersCount() > 0) {
      final String message = JavaErrorBundle.message("annotation.interface.members.may.not.have.parameters");
      final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createRemoveParameterListFix((PsiMethod) parent));
      return highlightInfo;
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkForStatement(@Nonnull PsiForStatement statement) {
    PsiStatement init = statement.getInitialization();
    if (init == null || init instanceof PsiEmptyStatement || init instanceof PsiDeclarationStatement && ArrayUtil.getFirstElement(((PsiDeclarationStatement) init).getDeclaredElements())
        instanceof PsiLocalVariable || init instanceof PsiExpressionStatement || init instanceof PsiExpressionListStatement) {
      return null;
    }

    String message = JavaErrorBundle.message("invalid.statement");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(init).descriptionAndTooltip(message).create();
  }

  private static void registerChangeParameterClassFix(PsiType lType, PsiType rType, HighlightInfo info) {
    QuickFixAction.registerQuickFixAction(info, getChangeParameterClassFix(lType, rType));
  }

  @Nullable
  private static IntentionAction getChangeParameterClassFix(PsiType lType, PsiType rType) {
    final PsiClass lClass = PsiUtil.resolveClassInClassTypeOnly(lType);
    final PsiClass rClass = PsiUtil.resolveClassInClassTypeOnly(rType);

    if (rClass == null || lClass == null) {
      return null;
    }
    if (rClass instanceof PsiAnonymousClass) {
      return null;
    }
    if (rClass.isInheritor(lClass, true)) {
      return null;
    }
    if (lClass.isInheritor(rClass, true)) {
      return null;
    }
    if (lClass == rClass) {
      return null;
    }

    return QUICK_FIX_FACTORY.createChangeParameterClassFix(rClass, (PsiClassType) lType);
  }

  private static void registerReplaceInaccessibleFieldWithGetterSetterFix(PsiMember refElement, PsiJavaCodeReferenceElement place, PsiClass accessObjectClass, HighlightInfo error) {
    if (refElement instanceof PsiField && place instanceof PsiReferenceExpression) {
      final PsiField psiField = (PsiField) refElement;
      final PsiClass containingClass = psiField.getContainingClass();
      if (containingClass != null) {
        if (PsiUtil.isOnAssignmentLeftHand((PsiExpression) place)) {
          final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(psiField);
          final PsiMethod setter = containingClass.findMethodBySignature(setterPrototype, true);
          if (setter != null && PsiUtil.isAccessible(setter, place, accessObjectClass)) {
            final PsiElement element = PsiTreeUtil.skipParentsOfType(place, PsiParenthesizedExpression.class);
            if (element instanceof PsiAssignmentExpression && ((PsiAssignmentExpression) element).getOperationTokenType() == JavaTokenType.EQ) {
              QuickFixAction.registerQuickFixAction(error, QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(place, setter, true));
            }
          }
        } else if (PsiUtil.isAccessedForReading((PsiExpression) place)) {
          final PsiMethod getterPrototype = PropertyUtil.generateGetterPrototype(psiField);
          final PsiMethod getter = containingClass.findMethodBySignature(getterPrototype, true);
          if (getter != null && PsiUtil.isAccessible(getter, place, accessObjectClass)) {
            QuickFixAction.registerQuickFixAction(error, QUICK_FIX_FACTORY.createReplaceInaccessibleFieldWithGetterSetterFix(place, getter, false));
          }
        }
      }
    }
  }

  public enum Feature {
    GENERICS(LanguageLevel.JDK_1_5, "feature.generics"),
    ANNOTATIONS(LanguageLevel.JDK_1_5, "feature.annotations"),
    STATIC_IMPORTS(LanguageLevel.JDK_1_5, "feature.static.imports"),
    FOR_EACH(LanguageLevel.JDK_1_5, "feature.for.each"),
    VARARGS(LanguageLevel.JDK_1_5, "feature.varargs"),
    HEX_FP_LITERALS(LanguageLevel.JDK_1_5, "feature.hex.fp.literals"),
    DIAMOND_TYPES(LanguageLevel.JDK_1_7, "feature.diamond.types"),
    MULTI_CATCH(LanguageLevel.JDK_1_7, "feature.multi.catch"),
    TRY_WITH_RESOURCES(LanguageLevel.JDK_1_7, "feature.try.with.resources"),
    BIN_LITERALS(LanguageLevel.JDK_1_7, "feature.binary.literals"),
    UNDERSCORES(LanguageLevel.JDK_1_7, "feature.underscores.in.literals"),
    EXTENSION_METHODS(LanguageLevel.JDK_1_8, "feature.extension.methods"),
    METHOD_REFERENCES(LanguageLevel.JDK_1_8, "feature.method.references"),
    LAMBDA_EXPRESSIONS(LanguageLevel.JDK_1_8, "feature.lambda.expressions"),
    TYPE_ANNOTATIONS(LanguageLevel.JDK_1_8, "feature.type.annotations"),
    RECEIVERS(LanguageLevel.JDK_1_8, "feature.type.receivers"),
    INTERSECTION_CASTS(LanguageLevel.JDK_1_8, "feature.intersections.in.casts"),
    STATIC_INTERFACE_CALLS(LanguageLevel.JDK_1_8, "feature.static.interface.calls"),
    REFS_AS_RESOURCE(LanguageLevel.JDK_1_9, "feature.try.with.resources.refs"),
    MODULES(LanguageLevel.JDK_1_9, "feature.modules"),
    LVTI(LanguageLevel.JDK_10, "feature.lvti"),
    VAR_LAMBDA_PARAMETER(LanguageLevel.JDK_11, "feature.var.lambda.parameter"),
    ENHANCED_SWITCH(LanguageLevel.JDK_14, "feature.enhanced.switch"),
    SWITCH_EXPRESSION(LanguageLevel.JDK_14, "feature.switch.expressions"),
    RECORDS(LanguageLevel.JDK_16, "feature.records"),
    PATTERNS(LanguageLevel.JDK_16, "feature.patterns.instanceof"),
    TEXT_BLOCK_ESCAPES(LanguageLevel.JDK_15, "feature.text.block.escape.sequences"),
    TEXT_BLOCKS(LanguageLevel.JDK_15, "feature.text.blocks"),
    SEALED_CLASSES(LanguageLevel.JDK_17, "feature.sealed.classes"),
    LOCAL_INTERFACES(LanguageLevel.JDK_16, "feature.local.interfaces"),
    LOCAL_ENUMS(LanguageLevel.JDK_16, "feature.local.enums"),
    INNER_STATICS(LanguageLevel.JDK_16, "feature.inner.statics");

    private final LanguageLevel level;
    private final String key;

    Feature(LanguageLevel level, @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key) {
      this.level = level;
      this.key = key;
    }

    /**
     * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
     * @return true if this feature is available in the PsiFile the supplied element belongs to
     */
    public boolean isAvailable(PsiElement element) {
      return isSufficient(PsiUtil.getLanguageLevel(element));
    }

    private boolean isSufficient(LanguageLevel useSiteLevel) {
      return useSiteLevel.isAtLeast(level) && (!level.isPreview() || useSiteLevel.isPreview());
    }
  }

  @Nullable
  public static HighlightInfo checkFeature(@Nonnull PsiElement element, @Nonnull Feature feature, @Nonnull LanguageLevel level, @Nonnull PsiFile file) {
    if (file.getManager().isInProject(file) && !level.isAtLeast(feature.level)) {
      String message = getUnsupportedFeatureMessage(element, feature, level, file);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createIncreaseLanguageLevelFix(feature.level));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createShowModulePropertiesFix(element));
      return info;
    }

    return null;
  }

  @RequiredReadAction
  private static String getUnsupportedFeatureMessage(PsiElement element, Feature feature, LanguageLevel level, PsiFile file) {
    String name = JavaErrorBundle.message(feature.key);
    String message = JavaErrorBundle.message("insufficient.language.level", name, level.getCompilerComplianceDefaultOption());

    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      LanguageLevel moduleLanguageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
      if (moduleLanguageLevel.isAtLeast(feature.level)) {
        for (FilePropertyPusher pusher : FilePropertyPusher.EP_NAME.getExtensions()) {
          if (pusher instanceof JavaLanguageLevelPusher) {
            String newMessage = ((JavaLanguageLevelPusher) pusher).getInconsistencyLanguageLevelMessage(message, element, level, file);
            if (newMessage != null) {
              return newMessage;
            }
          }
        }
      }
    }

    return message;
  }
}