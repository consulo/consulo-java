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

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.JavaLanguageLevelPusher;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.*;
import com.intellij.java.analysis.impl.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.java.analysis.impl.psi.util.EnclosingLoopMatcherExpression;
import com.intellij.java.analysis.impl.psi.util.EnclosingLoopOrSwitchMatcherExpression;
import com.intellij.java.analysis.impl.psi.util.JavaMatchers;
import com.intellij.java.analysis.impl.psi.util.PsiMatchers;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.impl.codeInsight.daemon.impl.analysis.HighlightUtilBase;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.component.extension.Extensions;
import consulo.document.util.TextRange;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.highlight.HighlightUsagesDescriptionLocation;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import consulo.language.editor.inspection.PriorityActionWrapper;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.intention.UnresolvedReferenceQuickFixProvider;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.findUsage.FindUsagesProvider;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ContainerProvider;
import consulo.language.psi.util.PsiMatcherImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.FilePropertyPusher;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.util.lang.xml.XmlStringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 * @since Jul 30, 2002
 */
public class HighlightUtil extends HighlightUtilBase {
    private static final Logger LOG = Logger.getInstance(HighlightUtil.class);

    private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers = new HashMap<>(7);
    private static final Map<String, Set<String>> ourMethodIncompatibleModifiers = new HashMap<>(11);
    private static final Map<String, Set<String>> ourFieldIncompatibleModifiers = new HashMap<>(8);
    private static final Map<String, Set<String>> ourClassIncompatibleModifiers = new HashMap<>(8);
    private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers = new HashMap<>(1);
    private static final Map<String, Set<String>> ourModuleIncompatibleModifiers = new HashMap<>(1);
    private static final Map<String, Set<String>> ourRequiresIncompatibleModifiers = new HashMap<>(2);

    private static final Set<String> ourConstructorNotAllowedModifiers =
        Set.of(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.NATIVE, PsiModifier.FINAL, PsiModifier
            .STRICTFP, PsiModifier.SYNCHRONIZED);

    private static final String SERIAL_PERSISTENT_FIELDS_FIELD_NAME = "serialPersistentFields";

    static {
        ourClassIncompatibleModifiers.put(PsiModifier.ABSTRACT, Set.of(PsiModifier.FINAL));
        ourClassIncompatibleModifiers.put(PsiModifier.FINAL, Set.of(PsiModifier.ABSTRACT, PsiModifier.SEALED, PsiModifier.NON_SEALED));
        ourClassIncompatibleModifiers.put(
            PsiModifier.PACKAGE_LOCAL,
            Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED)
        );
        ourClassIncompatibleModifiers.put(
            PsiModifier.PRIVATE,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED)
        );
        ourClassIncompatibleModifiers.put(
            PsiModifier.PUBLIC,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED)
        );
        ourClassIncompatibleModifiers.put(
            PsiModifier.PROTECTED,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE)
        );
        ourClassIncompatibleModifiers.put(PsiModifier.STRICTFP, Set.of());
        ourClassIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
        ourClassIncompatibleModifiers.put(PsiModifier.SEALED, Set.of(PsiModifier.FINAL, PsiModifier.NON_SEALED));
        ourClassIncompatibleModifiers.put(PsiModifier.NON_SEALED, Set.of(PsiModifier.FINAL, PsiModifier.SEALED));

        ourInterfaceIncompatibleModifiers.put(PsiModifier.ABSTRACT, Set.of());
        ourInterfaceIncompatibleModifiers
            .put(PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
        ourInterfaceIncompatibleModifiers
            .put(PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
        ourInterfaceIncompatibleModifiers
            .put(PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
        ourInterfaceIncompatibleModifiers
            .put(PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
        ourInterfaceIncompatibleModifiers.put(PsiModifier.STRICTFP, Set.of());
        ourInterfaceIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
        ourInterfaceIncompatibleModifiers.put(PsiModifier.SEALED, Set.of(PsiModifier.NON_SEALED));
        ourInterfaceIncompatibleModifiers.put(PsiModifier.NON_SEALED, Set.of(PsiModifier.SEALED));

        ourMethodIncompatibleModifiers.put(
            PsiModifier.ABSTRACT,
            Set.of(
                PsiModifier.NATIVE,
                PsiModifier.STATIC,
                PsiModifier.FINAL,
                PsiModifier.PRIVATE,
                PsiModifier.STRICTFP,
                PsiModifier.SYNCHRONIZED,
                PsiModifier.DEFAULT
            )
        );
        ourMethodIncompatibleModifiers.put(PsiModifier.NATIVE, Set.of(PsiModifier.ABSTRACT, PsiModifier.STRICTFP));
        ourMethodIncompatibleModifiers.put(
            PsiModifier.PACKAGE_LOCAL,
            Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED)
        );
        ourMethodIncompatibleModifiers.put(
            PsiModifier.PRIVATE,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED)
        );
        ourMethodIncompatibleModifiers.put(
            PsiModifier.PUBLIC,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED)
        );
        ourMethodIncompatibleModifiers.put(
            PsiModifier.PROTECTED,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE)
        );
        ourMethodIncompatibleModifiers.put(PsiModifier.STATIC, Set.of(PsiModifier.ABSTRACT, PsiModifier.DEFAULT));
        ourMethodIncompatibleModifiers
            .put(PsiModifier.DEFAULT, Set.of(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.PRIVATE));
        ourMethodIncompatibleModifiers.put(PsiModifier.SYNCHRONIZED, Set.of(PsiModifier.ABSTRACT));
        ourMethodIncompatibleModifiers.put(PsiModifier.STRICTFP, Set.of(PsiModifier.ABSTRACT));
        ourMethodIncompatibleModifiers.put(PsiModifier.FINAL, Set.of(PsiModifier.ABSTRACT));

        ourFieldIncompatibleModifiers.put(PsiModifier.FINAL, Set.of(PsiModifier.VOLATILE));
        ourFieldIncompatibleModifiers.put(
            PsiModifier.PACKAGE_LOCAL,
            Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED)
        );
        ourFieldIncompatibleModifiers.put(
            PsiModifier.PRIVATE,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED)
        );
        ourFieldIncompatibleModifiers.put(
            PsiModifier.PUBLIC,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED)
        );
        ourFieldIncompatibleModifiers.put(
            PsiModifier.PROTECTED,
            Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE)
        );
        ourFieldIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
        ourFieldIncompatibleModifiers.put(PsiModifier.TRANSIENT, Set.of());
        ourFieldIncompatibleModifiers.put(PsiModifier.VOLATILE, Set.of(PsiModifier.FINAL));

        ourClassInitializerIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());

        ourModuleIncompatibleModifiers.put(PsiModifier.OPEN, Set.of());

        ourRequiresIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
        ourRequiresIncompatibleModifiers.put(PsiModifier.TRANSITIVE, Set.of());
    }

    private HighlightUtil() {
    }

    @Nullable
    @RequiredReadAction
    private static String getIncompatibleModifier(
        String modifier,
        @Nullable PsiModifierList modifierList,
        @Nonnull Map<String, Set<String>> incompatibleModifiersHash
    ) {
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
        boolean level8OrHigher = PsiUtil.isLanguageLevel8OrHigher(modifierList);
        boolean level9OrHigher = PsiUtil.isLanguageLevel9OrHigher(modifierList);
        for (@PsiModifier.ModifierConstant String incompatible : incompatibles) {
            if (level8OrHigher) {
                if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.ABSTRACT)) {
                    continue;
                }
            }
            if (modifierList.getParent() instanceof PsiMethod method) {
                if (level9OrHigher && modifier.equals(PsiModifier.PRIVATE) && incompatible.equals(PsiModifier.PUBLIC)) {
                    continue;
                }

                if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.FINAL)) {
                    PsiClass containingClass = method.getContainingClass();
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
    @RequiredReadAction
    public static void registerAccessQuickFixAction(
        @Nonnull PsiMember refElement,
        @Nonnull PsiJavaCodeReferenceElement place,
        @Nullable HighlightInfo errorResult,
        PsiElement fileResolveScope
    ) {
        if (errorResult == null) {
            return;
        }
        PsiClass accessObjectClass = null;
        PsiElement qualifier = place.getQualifier();
        if (qualifier instanceof PsiExpression qExpr) {
            accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qExpr).getElement();
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
            IntentionAction fix = QuickFixFactory.getInstance()
                .createModifierListFix(packageLocalClassInTheMiddle, PsiModifier.PUBLIC, true, true);
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
            if (refElement.isProtected()) {
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
                    IntentionAction fix = QuickFixFactory.getInstance().createModifierListFix(refElement, modifier, true, true);
                    TextRange fixRange = new TextRange(errorResult.getStartOffset(), errorResult.getEndOffset());
                    PsiElement ref = place.getReferenceNameElement();
                    if (ref != null) {
                        fixRange = fixRange.union(ref.getTextRange());
                    }
                    QuickFixAction.registerQuickFixAction(errorResult, fixRange, fix);
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Nullable
    @RequiredReadAction
    private static PsiClass getPackageLocalClassInTheMiddle(@Nonnull PsiElement place) {
        if (place instanceof PsiReferenceExpression refExpr) {
            // check for package-private classes in the middle
            while (true) {
                if (refExpr.resolve() instanceof PsiField field) {
                    PsiClass aClass = field.getContainingClass();
                    if (aClass != null && aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
                        && !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, place)) {
                        return aClass;
                    }
                }
                PsiExpression qualifier = refExpr.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression qualifierRefExpr)) {
                    break;
                }
                refExpr = qualifierRefExpr;
            }
        }
        return null;
    }


    @Nullable
    @RequiredReadAction
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
        if (TypeConversionUtil.isPrimitiveAndNotNull(operandType)
            || TypeConversionUtil.isPrimitiveAndNotNull(checkType)
            || !TypeConversionUtil.areTypesConvertible(operandType, checkType)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.inconvertibleTypeCast(
                    JavaHighlightUtil.formatType(operandType),
                    JavaHighlightUtil.formatType(checkType)
                ))
                .create();
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
    @RequiredReadAction
    public static HighlightInfo checkIntersectionInTypeCast(
        @Nonnull PsiTypeCastExpression expression,
        @Nonnull LanguageLevel languageLevel,
        @Nonnull PsiFile file
    ) {
        PsiTypeElement castTypeElement = expression.getCastType();
        if (castTypeElement != null && isIntersection(castTypeElement, castTypeElement.getType())) {
            HighlightInfo info = checkFeature(expression, JavaFeature.INTERSECTION_CASTS, languageLevel, file);
            if (info != null) {
                return info;
            }

            PsiTypeElement[] conjuncts = PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class);
            if (conjuncts != null) {
                Set<PsiType> erasures = new HashSet<>(conjuncts.length);
                erasures.add(TypeConversionUtil.erasure(conjuncts[0].getType()));
                List<PsiTypeElement> conjList = new ArrayList<>(Arrays.asList(conjuncts));
                for (int i = 1; i < conjuncts.length; i++) {
                    PsiTypeElement conjunct = conjuncts[i];
                    PsiType conjType = conjunct.getType();
                    if (conjType instanceof PsiClassType classType) {
                        PsiClass aClass = classType.resolve();
                        if (aClass != null && !aClass.isInterface()) {
                            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .range(conjunct)
                                .descriptionAndTooltip(JavaErrorLocalize.interfaceExpected())
                                .registerFix(
                                    new FlipIntersectionSidesFix(aClass.getName(), conjList, conjunct, castTypeElement),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                                .create();
                        }
                    }
                    else {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(conjunct)
                            .descriptionAndTooltip(LocalizeValue.localizeTODO("Unexpected type: class is expected"))
                            .create();
                    }
                    if (!erasures.add(TypeConversionUtil.erasure(conjType))) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(conjunct)
                            .descriptionAndTooltip(LocalizeValue.localizeTODO("Repeated interface"))
                            .registerFix(new DeleteRepeatedInterfaceFix(conjunct, conjList), null, null, null, null)
                            .create();
                    }
                }

                List<PsiType> typeList = ContainerUtil.map(conjList, PsiTypeElement::getType);
                SimpleReference<String> differentArgumentsMessage = SimpleReference.create();
                PsiClass sameGenericParameterization = InferenceSession.findParameterizationOfTheSameGenericClass(
                    typeList,
                    pair -> {
                        if (!TypesDistinctProver.provablyDistinct(pair.first, pair.second)) {
                            return true;
                        }
                        differentArgumentsMessage.set(pair.first.getPresentableText() + " and " + pair.second.getPresentableText());
                        return false;
                    }
                );
                if (sameGenericParameterization != null) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(LocalizeValue.localizeTODO(
                            formatClass(sameGenericParameterization) + " cannot be inherited with different arguments: " +
                                differentArgumentsMessage.get()
                        ))
                        .create();
                }
            }
        }

        return null;
    }

    @RequiredReadAction
    private static boolean isIntersection(PsiTypeElement castTypeElement, PsiType castType) {
        //noinspection SimplifiableIfStatement
        if (castType instanceof PsiIntersectionType) {
            return true;
        }
        return castType instanceof PsiClassType && PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class) != null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkInconvertibleTypeCast(@Nonnull PsiTypeCastExpression expression) {
        PsiTypeElement castTypeElement = expression.getCastType();
        if (castTypeElement == null) {
            return null;
        }
        PsiType castType = castTypeElement.getType();

        PsiExpression operand = expression.getOperand();
        if (operand == null) {
            return null;
        }
        PsiType operandType = operand.getType();

        if (operandType != null
            && !TypeConversionUtil.areTypesConvertible(operandType, castType, PsiUtil.getLanguageLevel(expression))
            && !RedundantCastUtil.isInPolymorphicCall(expression)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.inconvertibleTypeCast(
                    JavaHighlightUtil.formatType(operandType),
                    JavaHighlightUtil.formatType(castType)
                ))
                .create();
        }


        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkVariableExpected(@Nonnull PsiExpression expression) {
        PsiExpression lValue;
        if (expression instanceof PsiAssignmentExpression assignment) {
            lValue = assignment.getLExpression();
        }
        else if (PsiUtil.isIncrementDecrementOperation(expression)) {
            lValue = expression instanceof PsiPostfixExpression postfixExpr
                ? postfixExpr.getOperand()
                : ((PsiPrefixExpression)expression).getOperand();
        }
        else {
            lValue = null;
        }
        HighlightInfo errorResult = null;
        if (lValue != null && !TypeConversionUtil.isLValue(lValue)) {
            errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(lValue)
                .descriptionAndTooltip(JavaErrorLocalize.variableExpected())
                .create();
        }

        return errorResult;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkAssignmentOperatorApplicable(@Nonnull PsiAssignmentExpression assignment) {
        PsiJavaToken operationSign = assignment.getOperationSign();
        IElementType eqOpSign = operationSign.getTokenType();
        IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
        if (opSign == null) {
            return null;
        }
        PsiType lType = assignment.getLExpression().getType();
        PsiExpression rExpression = assignment.getRExpression();
        if (rExpression == null) {
            return null;
        }
        PsiType rType = rExpression.getType();
        if (!TypeConversionUtil.isBinaryOperatorApplicable(opSign, lType, rType, true)) {
            String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
            LocalizeValue message = JavaErrorLocalize.binaryOperatorNotApplicable(
                operatorText,
                JavaHighlightUtil.formatType(lType),
                JavaHighlightUtil.formatType(rType)
            );

            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(assignment)
                .descriptionAndTooltip(message)
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
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

        IElementType sign = assignment.getOperationTokenType();
        HighlightInfo highlightInfo;
        if (JavaTokenType.EQ.equals(sign)) {
            highlightInfo = checkAssignability(lType, rType, rExpr, assignment);
        }
        else {
            // 15.26.2. Compound Assignment Operators
            IElementType opSign = TypeConversionUtil.convertEQtoOperation(sign);
            PsiType type = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opSign, true);
            if (type == null || lType == null || TypeConversionUtil.areTypesConvertible(type, lType)) {
                return null;
            }
            highlightInfo = createIncompatibleTypeHighlightInfo(lType, type, assignment.getTextRange(), 0);
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createChangeToAppendFix(sign, lType, assignment)
            );
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

    @RequiredReadAction
    private static void registerChangeVariableTypeFixes(
        @Nonnull PsiExpression expression,
        @Nonnull PsiType type,
        @Nullable PsiExpression lExpr,
        @Nullable HighlightInfo highlightInfo
    ) {
        if (highlightInfo == null || !(expression instanceof PsiReferenceExpression refExpr)
            || !(refExpr.resolve() instanceof PsiVariable variable)) {
            return;
        }

        registerChangeVariableTypeFixes(variable, type, lExpr, highlightInfo);

        if (lExpr instanceof PsiMethodCallExpression methodCall
            && methodCall.getParent() instanceof PsiAssignmentExpression assignment
            && assignment.getParent() instanceof PsiStatement) {
            PsiMethod method = methodCall.resolveMethod();
            if (method != null && PsiType.VOID.equals(method.getReturnType())) {
                highlightInfo.registerFix(
                    new ReplaceAssignmentFromVoidWithStatementIntentionAction(assignment, lExpr),
                    null,
                    null,
                    null,
                    null
                );
            }
        }
    }

    private static boolean isCastIntentionApplicable(@Nonnull PsiExpression expression, @Nullable PsiType toType) {
        while (expression instanceof PsiTypeCastExpression || expression instanceof PsiParenthesizedExpression) {
            if (expression instanceof PsiTypeCastExpression typeCast) {
                expression = typeCast.getOperand();
            }
            if (expression instanceof PsiParenthesizedExpression parenthesized) {
                expression = parenthesized.getExpression();
            }
        }
        if (expression == null) {
            return false;
        }
        PsiType rType = expression.getType();
        return rType != null && toType != null && TypeConversionUtil.areTypesConvertible(rType, toType);
    }


    @Nullable
    @RequiredReadAction
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
    @RequiredReadAction
    public static HighlightInfo checkAssignability(
        @Nullable PsiType lType,
        @Nullable PsiType rType,
        @Nullable PsiExpression expression,
        @Nonnull PsiElement elementToHighlight
    ) {
        TextRange textRange = elementToHighlight.getTextRange();
        return checkAssignability(lType, rType, expression, textRange, 0);
    }

    @Nullable
    private static HighlightInfo checkAssignability(
        @Nullable PsiType lType,
        @Nullable PsiType rType,
        @Nullable PsiExpression expression,
        @Nonnull TextRange textRange,
        int navigationShift
    ) {
        if (lType == rType) {
            return null;
        }
        if (expression == null) {
            if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) {
                return null;
            }
        }
        else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression)) {
            return null;
        }
        if (rType == null) {
            rType = expression.getType();
        }
        HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(lType, rType, textRange, navigationShift);
        if (rType != null && expression != null && isCastIntentionApplicable(expression, lType)) {
            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createAddTypeCastFix(lType, expression));
        }
        if (expression != null) {
            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createWrapWithAdapterFix(lType, expression));
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createWrapWithOptionalFix(lType, expression)
            );
            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createWrapExpressionFix(lType, expression));
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createWrapStringWithFileFix(lType, expression)
            );
            AddTypeArgumentsConditionalFix.register(highlightInfo, expression, lType);
            registerCollectionToArrayFixAction(highlightInfo, rType, lType, expression);
        }
        ChangeNewOperatorTypeFix.register(highlightInfo, expression, lType);
        return highlightInfo;
    }

    @Nullable
    @RequiredReadAction
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
            if (parent instanceof PsiLambdaExpression parentLambda) {
                lambda = parentLambda;
                break;
            }
            if (parent instanceof PsiMethod parentMethod) {
                method = parentMethod;
                break;
            }
            parent = parent.getParent();
        }
        if (parent instanceof PsiCodeFragment) {
            return null;
        }
        HighlightInfo errorResult = null;
        if (method == null && lambda != null) {
            //todo check return statements type inside lambda
        }
        else if (method == null && !(parent instanceof ServerPageFile)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(JavaErrorLocalize.returnOutsideMethod())
                .create();
        }
        else {
            PsiType returnType = method != null ? method.getReturnType() : null/*JSP page returns void*/;
            boolean isMethodVoid = returnType == null || PsiType.VOID.equals(returnType);
            PsiExpression returnValue = statement.getReturnValue();
            if (returnValue != null) {
                PsiType valueType = RefactoringChangeUtil.getTypeByExpression(returnValue);
                if (isMethodVoid) {
                    errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(statement)
                        .descriptionAndTooltip(JavaErrorLocalize.returnFromVoidMethod())
                        .create();
                    if (valueType != null) {
                        QuickFixAction.registerQuickFixAction(
                            errorResult,
                            QuickFixFactory.getInstance().createMethodReturnFix(method, valueType, true)
                        );
                    }
                }
                else {
                    TextRange textRange = statement.getTextRange();
                    errorResult = checkAssignability(returnType, valueType, returnValue, textRange, returnValue.getStartOffsetInParent());
                    if (errorResult != null && valueType != null) {
                        if (!PsiType.VOID.equals(valueType)) {
                            QuickFixAction.registerQuickFixAction(
                                errorResult,
                                QuickFixFactory.getInstance().createMethodReturnFix(method, valueType, true)
                            );
                        }
                        registerChangeParameterClassFix(returnType, valueType, errorResult);
                        if (returnType instanceof PsiArrayType arrayType) {
                            PsiType erasedValueType = TypeConversionUtil.erasure(valueType);
                            if (erasedValueType != null
                                && TypeConversionUtil.isAssignable(arrayType.getComponentType(), erasedValueType)) {
                                QuickFixAction.registerQuickFixAction(errorResult, QuickFixFactory.getInstance()
                                    .createSurroundWithArrayFix(null, returnValue));
                            }
                        }
                        registerCollectionToArrayFixAction(errorResult, valueType, returnType, returnValue);
                    }
                }
            }
            else if (!isMethodVoid) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(statement)
                    .descriptionAndTooltip(JavaErrorLocalize.missingReturnValue())
                    .navigationShift(PsiKeyword.RETURN.length())
                    .registerFix(QuickFixFactory.getInstance().createMethodReturnFix(method, PsiType.VOID, true), null, null, null, null)
                    .create();
            }
        }
        return errorResult;
    }

    private static void registerCollectionToArrayFixAction(
        @Nullable HighlightInfo info,
        @Nullable PsiType fromType,
        @Nullable PsiType toType,
        @Nonnull PsiExpression expression
    ) {
        if (toType instanceof PsiArrayType arrayType) {
            PsiType arrayComponentType = arrayType.getComponentType();
            if (!(arrayComponentType instanceof PsiPrimitiveType)
                && !(PsiUtil.resolveClassInType(arrayComponentType) instanceof PsiTypeParameter)
                && InheritanceUtil.isInheritor(fromType, JavaClassNames.JAVA_UTIL_COLLECTION)) {
                PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(fromType, expression.getResolveScope());
                if (collectionItemType != null && arrayComponentType.isAssignableFrom(collectionItemType)) {
                    QuickFixAction.registerQuickFixAction(
                        info,
                        QuickFixFactory.getInstance().createCollectionToArrayFix(expression, arrayType)
                    );
                }
            }
        }
    }

    @Nonnull
    public static LocalizeValue getUnhandledExceptionsDescriptor(@Nonnull Collection<PsiClassType> unhandled) {
        return getUnhandledExceptionsDescriptor(unhandled, null);
    }

    @Nonnull
    private static LocalizeValue getUnhandledExceptionsDescriptor(@Nonnull Collection<PsiClassType> unhandled, @Nullable String source) {
        String exceptions = formatTypes(unhandled);
        return source != null
            ? JavaErrorLocalize.unhandledCloseExceptions(exceptions, unhandled.size(), source)
            : JavaErrorLocalize.unhandledExceptions(exceptions, unhandled.size());
    }

    @Nonnull
    private static String formatTypes(@Nonnull Collection<PsiClassType> unhandled) {
        return StringUtil.join(unhandled, JavaHighlightUtil::formatType, ", ");
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkVariableAlreadyDefined(@Nonnull PsiVariable variable) {
        PsiVariable oldVariable = JavaPsiVariableUtil.findPreviousVariableDeclaration(variable);
        if (oldVariable != null) {
            PsiIdentifier identifier = variable.getNameIdentifier();
            assert identifier != null : variable;
            HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(identifier)
                .descriptionAndTooltip(JavaErrorLocalize.variableAlreadyDefined(variable.getName()))
                .create();
            if (variable instanceof PsiLocalVariable localVar) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createReuseVariableDeclarationFix(localVar)
                );
            }
            return highlightInfo;
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkUnderscore(@Nonnull PsiIdentifier identifier, @Nonnull LanguageLevel languageLevel) {
        if ("_".equals(identifier.getText())) {
            if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(identifier)
                    .descriptionAndTooltip(JavaErrorLocalize.underscoreIdentifierError())
                    .create();
            }
            else if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                PsiElement parent = identifier.getParent();
                if (parent instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiLambdaExpression) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(identifier)
                        .descriptionAndTooltip(JavaErrorLocalize.underscoreLambdaIdentifier())
                        .create();
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
        return PsiFormatUtil.formatClass(
            aClass,
            PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0)
        );
    }

    @Nonnull
    private static String formatField(@Nonnull PsiField field) {
        return PsiFormatUtil.formatVariable(
            field,
            PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME,
            PsiSubstitutor.EMPTY
        );
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkUnhandledExceptions(@Nonnull PsiElement element, @Nullable TextRange textRange) {
        List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
        if (unhandledExceptions.isEmpty()) {
            return null;
        }

        HighlightInfoType highlightType = getUnhandledExceptionHighlightType(element);
        if (highlightType == null) {
            return null;
        }

        if (textRange == null) {
            textRange = element.getTextRange();
        }
        HighlightInfo errorResult = HighlightInfo.newHighlightInfo(highlightType)
            .range(textRange)
            .descriptionAndTooltip(getUnhandledExceptionsDescriptor(unhandledExceptions))
            .create();
        registerUnhandledExceptionFixes(element, errorResult, unhandledExceptions);
        return errorResult;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkUnhandledCloserExceptions(@Nonnull PsiResourceListElement resource) {
        List<PsiClassType> unhandled = ExceptionUtil.getUnhandledCloserExceptions(resource, null);
        if (unhandled.isEmpty()) {
            return null;
        }

        HighlightInfoType highlightType = getUnhandledExceptionHighlightType(resource);
        if (highlightType == null) {
            return null;
        }

        HighlightInfo highlight = HighlightInfo.newHighlightInfo(highlightType)
            .range(resource)
            .descriptionAndTooltip(getUnhandledExceptionsDescriptor(unhandled, "auto-closeable resource"))
            .create();
        registerUnhandledExceptionFixes(resource, highlight, unhandled);
        return highlight;
    }

    private static void registerUnhandledExceptionFixes(PsiElement element, HighlightInfo errorResult, List<PsiClassType> unhandled) {
        QuickFixAction.registerQuickFixAction(errorResult, QuickFixFactory.getInstance().createAddExceptionToCatchFix());
        QuickFixAction.registerQuickFixAction(errorResult, QuickFixFactory.getInstance().createAddExceptionToThrowsFix(element));
        QuickFixAction.registerQuickFixAction(
            errorResult,
            QuickFixFactory.getInstance().createAddExceptionFromFieldInitializerToConstructorThrowsFix(element)
        );
        QuickFixAction.registerQuickFixAction(errorResult, QuickFixFactory.getInstance().createSurroundWithTryCatchFix(element));
        if (unhandled.size() == 1) {
            QuickFixAction.registerQuickFixAction(
                errorResult,
                QuickFixFactory.getInstance().createGeneralizeCatchFix(element, unhandled.get(0))
            );
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
    @RequiredReadAction
    public static HighlightInfo checkBreakOutsideLoop(@Nonnull PsiBreakStatement statement) {
        if (statement.getLabelIdentifier() == null) {
            if (new PsiMatcherImpl(statement).ancestor(EnclosingLoopOrSwitchMatcherExpression.INSTANCE).getElement() == null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(statement)
                    .descriptionAndTooltip(JavaErrorLocalize.breakOutsideSwitchOrLoop())
                    .create();
            }
        }
        else {
            // todo labeled
        }
        return null;
    }


    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkContinueOutsideLoop(@Nonnull PsiContinueStatement statement) {
        if (statement.getLabelIdentifier() == null) {
            if (new PsiMatcherImpl(statement).ancestor(EnclosingLoopMatcherExpression.INSTANCE).getElement() == null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(statement)
                    .descriptionAndTooltip(JavaErrorLocalize.continueOutsideLoop())
                    .create();
            }
        }
        else {
            PsiStatement exitedStatement = statement.findContinuedStatement();
            if (exitedStatement == null) {
                return null;
            }
            if (!(exitedStatement instanceof PsiForStatement)
                && !(exitedStatement instanceof PsiWhileStatement)
                && !(exitedStatement instanceof PsiDoWhileStatement)
                && !(exitedStatement instanceof PsiForeachStatement)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(statement)
                    .descriptionAndTooltip(JavaErrorLocalize.notLoopLabel(statement.getLabelIdentifier().getText()))
                    .create();
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkIllegalModifierCombination(@Nonnull PsiKeyword keyword, @Nonnull PsiModifierList modifierList) {
        @PsiModifier.ModifierConstant String modifier = keyword.getText();
        String incompatible = getIncompatibleModifier(modifier, modifierList);
        if (incompatible != null) {
            HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(keyword)
                .descriptionAndTooltip(JavaErrorLocalize.incompatibleModifiers(modifier, incompatible))
                .create();
            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                .createModifierListFix(modifierList, modifier, false, false));
            return highlightInfo;
        }

        return null;
    }

    @Contract("null -> null")
    private static Map<String, Set<String>> getIncompatibleModifierMap(@Nullable PsiElement modifierListOwner) {
        if (modifierListOwner == null || PsiUtilCore.hasErrorElementChild(modifierListOwner)) {
            return null;
        }
        if (modifierListOwner instanceof PsiClass psiClass) {
            return psiClass.isInterface() ? ourInterfaceIncompatibleModifiers : ourClassIncompatibleModifiers;
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
    @RequiredReadAction
    public static String getIncompatibleModifier(String modifier, @Nonnull PsiModifierList modifierList) {
        Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList.getParent());
        return incompatibleModifierMap != null ? getIncompatibleModifier(modifier, modifierList, incompatibleModifierMap) : null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkNotAllowedModifier(@Nonnull PsiKeyword keyword, @Nonnull PsiModifierList modifierList) {
        PsiElement modifierOwner = modifierList.getParent();
        Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierOwner);
        if (incompatibleModifierMap == null) {
            return null;
        }

        @PsiModifier.ModifierConstant String modifier = keyword.getText();
        Set<String> incompatibles = incompatibleModifierMap.get(modifier);
        PsiElement modifierOwnerParent =
            modifierOwner instanceof PsiMember member ? member.getContainingClass() : modifierOwner.getParent();
        if (modifierOwnerParent == null) {
            modifierOwnerParent = modifierOwner.getParent();
        }
        boolean isAllowed = true;
        if (modifierOwner instanceof PsiClass aClass) {
            if (aClass.isInterface()) {
                if (PsiModifier.STATIC.equals(modifier)
                    || PsiModifier.PRIVATE.equals(modifier)
                    || PsiModifier.PROTECTED.equals(modifier)
                    || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
                    isAllowed = modifierOwnerParent instanceof PsiClass;
                }
            }
            else {
                if (PsiModifier.PUBLIC.equals(modifier)) {
                    isAllowed = modifierOwnerParent instanceof PsiJavaFile
                        || modifierOwnerParent instanceof PsiClass psiClass
                        && (psiClass instanceof PsiSyntheticClass || psiClass.getQualifiedName() != null);
                }
                else if (PsiModifier.STATIC.equals(modifier)
                    || PsiModifier.PRIVATE.equals(modifier)
                    || PsiModifier.PROTECTED.equals(modifier)
                    || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
                    isAllowed = modifierOwnerParent instanceof PsiClass psiClass && psiClass.getQualifiedName() != null
                        || FileTypeUtils.isInServerPageFile(modifierOwnerParent);
                }

                if (aClass.isEnum()) {
                    isAllowed &= !(PsiModifier.FINAL.equals(modifier) || PsiModifier.ABSTRACT.equals(modifier));
                }

                if (aClass.getContainingClass() instanceof PsiAnonymousClass) {
                    isAllowed &= !(PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier));
                }
            }
        }
        else if (modifierOwner instanceof PsiMethod method) {
            isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
            PsiClass containingClass = method.getContainingClass();
            if ((method.isPublic() || method.isProtected()) && method.isConstructor()
                && containingClass != null && containingClass.isEnum()) {
                isAllowed = false;
            }

            if (PsiModifier.PRIVATE.equals(modifier)) {
                isAllowed &= modifierOwnerParent instanceof PsiClass modifierClass && (!modifierClass.isInterface()
                    || PsiUtil.isLanguageLevel9OrHigher(modifierOwner) && !modifierClass.isAnnotationType());
            }
            else if (PsiModifier.STRICTFP.equals(modifier)) {
                isAllowed &= modifierOwnerParent instanceof PsiClass modifierClass
                    && (!modifierClass.isInterface() || PsiUtil.isLanguageLevel8OrHigher(modifierOwner));
            }
            else if (PsiModifier.PROTECTED.equals(modifier)
                || PsiModifier.TRANSIENT.equals(modifier)
                || PsiModifier.SYNCHRONIZED.equals(modifier)) {
                isAllowed &= modifierOwnerParent instanceof PsiClass modifierClass && !modifierClass.isInterface();
            }

            if (containingClass != null && containingClass.isInterface()) {
                isAllowed &= !PsiModifier.NATIVE.equals(modifier);
            }

            if (containingClass != null && containingClass.isAnnotationType()) {
                isAllowed &= !PsiModifier.STATIC.equals(modifier);
                isAllowed &= !PsiModifier.DEFAULT.equals(modifier);
            }
        }
        else if (modifierOwner instanceof PsiField) {
            if (PsiModifier.PRIVATE.equals(modifier)
                || PsiModifier.PROTECTED.equals(modifier)
                || PsiModifier.TRANSIENT.equals(modifier)
                || PsiModifier.STRICTFP.equals(modifier)
                || PsiModifier.SYNCHRONIZED.equals(modifier)) {
                isAllowed = modifierOwnerParent instanceof PsiClass modifierClass && !modifierClass.isInterface();
            }
        }
        else if (modifierOwner instanceof PsiClassInitializer) {
            isAllowed = PsiModifier.STATIC.equals(modifier);
        }
        else if (modifierOwner instanceof PsiLocalVariable || modifierOwner instanceof PsiParameter) {
            isAllowed = PsiModifier.FINAL.equals(modifier);
        }
        else if (modifierOwner instanceof PsiReceiverParameter) {
            isAllowed = false;
        }

        isAllowed &= incompatibles != null;
        if (!isAllowed) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(keyword)
                .descriptionAndTooltip(JavaErrorLocalize.modifierNotAllowed(modifier))
                .registerFix(QuickFixFactory.getInstance().createModifierListFix(modifierList, modifier, false, false), null, null, null, null)
                .create();
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkLiteralExpressionParsingError(
        @Nonnull PsiLiteralExpression expression,
        LanguageLevel level,
        PsiFile file
    ) {
        PsiElement literal = expression.getFirstChild();
        assert literal instanceof PsiJavaToken : literal;
        IElementType type = ((PsiJavaToken)literal).getTokenType();
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
                    HighlightInfo info = checkFeature(expression, JavaFeature.HEX_FP_LITERALS, level, file);
                    if (info != null) {
                        return info;
                    }
                }
            }
            if (isInt) {
                if (text.startsWith(PsiLiteralUtil.BIN_PREFIX)) {
                    HighlightInfo info = checkFeature(expression, JavaFeature.BIN_LITERALS, level, file);
                    if (info != null) {
                        return info;
                    }
                }
            }
            if (isInt || isFP) {
                if (text.contains("_")) {
                    HighlightInfo info = checkFeature(expression, JavaFeature.UNDERSCORES, level, file);
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

        if (type == JavaTokenType.INTEGER_LITERAL) {
            String cleanText = StringUtil.replace(text, "_", "");
            //literal 2147483648 may appear only as the operand of the unary negation operator -.
            if (!(cleanText.equals(PsiLiteralUtil._2_IN_31)
                && expression.getParent() instanceof PsiPrefixExpression prefixExpr
                && prefixExpr.getOperationTokenType() == JavaTokenType.MINUS)) {
                if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.hexadecimalNumbersMustContainAtLeastOneHexadecimalDigit())
                        .create();
                }
                if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.binaryNumbersMustContainAtLeastOneHexadecimalDigit())
                        .create();
                }
                if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_31)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.integerNumberTooLarge())
                        .create();
                }
            }
        }
        else if (type == JavaTokenType.LONG_LITERAL) {
            String cleanText = StringUtil.replace(StringUtil.trimEnd(text, 'l'), "_", "");
            //literal 9223372036854775808L may appear only as the operand of the unary negation operator -.
            if (!(cleanText.equals(PsiLiteralUtil._2_IN_63)
                && expression.getParent() instanceof PsiPrefixExpression prefixExpr
                && prefixExpr.getOperationTokenType() == JavaTokenType.MINUS)) {
                if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.hexadecimalNumbersMustContainAtLeastOneHexadecimalDigit())
                        .create();
                }
                if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.binaryNumbersMustContainAtLeastOneHexadecimalDigit())
                        .create();
                }
                if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_63)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.longNumberTooLarge())
                        .create();
                }
            }
        }
        else if (isFP) {
            if (value == null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaErrorLocalize.malformedFloatingPointLiteral().get())
                    .create();
            }
        }
        else if (type == JavaTokenType.CHARACTER_LITERAL) {
            if (value == null) {
                if (!StringUtil.startsWithChar(text, '\'')) {
                    return null;
                }
                if (!StringUtil.endsWithChar(text, '\'') || text.length() == 1) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.unclosedCharLiteral())
                        .create();
                }
                text = text.substring(1, text.length() - 1);

                CharSequence chars = CodeInsightUtilCore.parseStringCharacters(text, null);
                if (chars == null) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.illegalEscapeCharacterInCharacterLiteral())
                        .create();
                }
                int length = chars.length();
                if (length > 1) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.tooManyCharactersInCharacterLiteral())
                        .registerFix(QuickFixFactory.getInstance().createConvertToStringLiteralAction(), null, null, null, null)
                        .create();
                }
                else if (length == 0) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(JavaErrorLocalize.emptyCharacterLiteral())
                        .create();
                }
            }
        }
        else if (type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.TEXT_BLOCK_LITERAL) {
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
                            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .range(expression)
                                .descriptionAndTooltip(JavaErrorLocalize.illegalLineEndInStringLiteral())
                                .create();
                        }
                        text = text.substring(1, text.length() - 1);
                    }
                    else {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(expression)
                            .descriptionAndTooltip(JavaErrorLocalize.illegalLineEndInStringLiteral())
                            .create();
                    }

                    if (CodeInsightUtilCore.parseStringCharacters(text, null) == null) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(expression)
                            .descriptionAndTooltip(JavaErrorLocalize.illegalEscapeCharacterInStringLiteral())
                            .create();
                    }
                }
            }
            else if (value == null) {
                if (!text.endsWith("\"\"\"")) {
                    int p = expression.getTextRange().getEndOffset();
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(p, p)
                        .endOfLine()
                        .descriptionAndTooltip(JavaErrorLocalize.textBlockUnclosed())
                        .create();
                }
                else {
                    StringBuilder chars = new StringBuilder(text.length());
                    int[] offsets = new int[text.length() + 1];
                    boolean success = CodeInsightUtilCore.parseStringCharacters(text, chars, offsets);
                    if (!success) {
                        TextRange textRange = chars.length() < text.length() - 1
                            ? new TextRange(offsets[chars.length()], offsets[chars.length() + 1])
                            : expression.getTextRange();
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(expression, textRange)
                            .descriptionAndTooltip(JavaErrorLocalize.illegalEscapeCharacterInStringLiteral())
                            .create();
                    }
                    else {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(expression)
                            .descriptionAndTooltip(JavaErrorLocalize.textBlockNewLine())
                            .create();
                    }
                }
            }
            else {
                if (file != null && containsUnescaped(text, "\\\n")) {
                    HighlightInfo info = checkFeature(expression, JavaFeature.TEXT_BLOCK_ESCAPES, level, file);
                    if (info != null) {
                        return info;
                    }
                }
            }
            if (file != null && containsUnescaped(text, "\\s")) {
                HighlightInfo info = checkFeature(expression, JavaFeature.TEXT_BLOCK_ESCAPES, level, file);
                if (info != null) {
                    return info;
                }
            }
        }

        if (value instanceof Float number) {
            if (number.isInfinite()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaErrorLocalize.floatingPointNumberTooLarge())
                    .create();
            }
            if (number == 0 && !TypeConversionUtil.isFPZero(text)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaErrorLocalize.floatingPointNumberTooSmall())
                    .create();
            }
        }
        else if (value instanceof Double number) {
            if (number.isInfinite()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaErrorLocalize.floatingPointNumberTooLarge())
                    .create();
            }
            if (number == 0 && !TypeConversionUtil.isFPZero(text)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaErrorLocalize.floatingPointNumberTooSmall())
                    .create();
            }
        }

        return null;
    }

    private static final Pattern FP_LITERAL_PARTS = Pattern.compile(
        "(?:" +
            "(?:0x([_\\p{XDigit}]*)\\.?([_\\p{XDigit}]*)p[+-]?([_\\d]*))" +
            "|" +
            "(?:([_\\d]*)\\.?([_\\d]*)e?[+-]?([_\\d]*))" +
            ")" +
            "[fd]?"
    );

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

    @RequiredReadAction
    private static HighlightInfo checkUnderscores(@Nonnull PsiElement expression, @Nonnull String text, boolean isInt) {
        String[] parts = ArrayUtil.EMPTY_STRING_ARRAY;

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
        }
        else {
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
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaErrorLocalize.illegalUnderscore())
                    .create();
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkMustBeBoolean(@Nonnull PsiExpression expr, PsiType type) {
        PsiElement parent = expr.getParent();
        if (parent instanceof PsiIfStatement || parent instanceof PsiWhileStatement
            || parent instanceof PsiForStatement forStmt && expr.equals(forStmt.getCondition())
            || parent instanceof PsiDoWhileStatement doWhileStmt && expr.equals(doWhileStmt.getCondition())) {
            if (expr.getNextSibling() instanceof PsiErrorElement) {
                return null;
            }

            if (!TypeConversionUtil.isBooleanType(type)) {
                HighlightInfo info = createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expr.getTextRange(), 0);
                if (expr instanceof PsiMethodCallExpression methodCall) {
                    PsiMethod method = methodCall.resolveMethod();
                    if (method != null && PsiType.VOID.equals(method.getReturnType())) {
                        QuickFixAction.registerQuickFixAction(
                            info,
                            QuickFixFactory.getInstance().createMethodReturnFix(method, PsiType.BOOLEAN, true)
                        );
                    }
                }
                else if (expr instanceof PsiAssignmentExpression assignment && assignment.getOperationTokenType() == JavaTokenType.EQ) {
                    QuickFixAction.registerQuickFixAction(
                        info,
                        QuickFixFactory.getInstance().createAssignmentToComparisonFix(assignment)
                    );
                }
                return info;
            }
        }
        return null;
    }

    @Nonnull
    public static Set<PsiClassType> collectUnhandledExceptions(@Nonnull PsiTryStatement statement) {
        Set<PsiClassType> thrownTypes = new HashSet<>();

        PsiCodeBlock tryBlock = statement.getTryBlock();
        if (tryBlock != null) {
            thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock));
        }

        PsiResourceList resources = statement.getResourceList();
        if (resources != null) {
            thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(resources, resources));
        }

        return thrownTypes;
    }

    @Nonnull
    @RequiredReadAction
    public static List<HighlightInfo> checkExceptionThrownInTry(@Nonnull PsiParameter parameter, @Nonnull Set<PsiClassType> thrownTypes) {
        if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) {
            return Collections.emptyList();
        }

        PsiType caughtType = parameter.getType();
        if (caughtType instanceof PsiClassType classType) {
            HighlightInfo info = checkSimpleCatchParameter(parameter, thrownTypes, classType);
            return info == null ? Collections.emptyList() : Collections.singletonList(info);
        }
        if (caughtType instanceof PsiDisjunctionType) {
            return checkMultiCatchParameter(parameter, thrownTypes);
        }

        return Collections.emptyList();
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkSimpleCatchParameter(
        @Nonnull PsiParameter parameter,
        @Nonnull Collection<PsiClassType> thrownTypes,
        @Nonnull PsiClassType caughtType
    ) {
        if (ExceptionUtil.isUncheckedExceptionOrSuperclass(caughtType)) {
            return null;
        }

        for (PsiClassType exceptionType : thrownTypes) {
            if (exceptionType.isAssignableFrom(caughtType) || caughtType.isAssignableFrom(exceptionType)) {
                return null;
            }
        }

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(parameter)
            .descriptionAndTooltip(JavaErrorLocalize.exceptionNeverThrownTry(JavaHighlightUtil.formatType(caughtType)))
            .registerFix(QuickFixFactory.getInstance().createDeleteCatchFix(parameter), null, null, null, null)
            .create();
    }

    @Nonnull
    @RequiredReadAction
    private static List<HighlightInfo> checkMultiCatchParameter(
        @Nonnull PsiParameter parameter,
        @Nonnull Collection<PsiClassType> thrownTypes
    ) {
        List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
        List<HighlightInfo> highlights = new ArrayList<>(typeElements.size());

        for (PsiTypeElement typeElement : typeElements) {
            PsiType catchType = typeElement.getType();
            if (catchType instanceof PsiClassType classType && ExceptionUtil.isUncheckedExceptionOrSuperclass(classType)) {
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
                HighlightInfo highlight = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(typeElement)
                    .descriptionAndTooltip(JavaErrorLocalize.exceptionNeverThrownTry(JavaHighlightUtil.formatType(catchType)))
                    .registerFix(QuickFixFactory.getInstance().createDeleteMultiCatchFix(typeElement), null, null, null, null)
                    .create();
                highlights.add(highlight);
            }
        }

        return highlights;
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<HighlightInfo> checkWithImprovedCatchAnalysis(
        @Nonnull PsiParameter parameter,
        @Nonnull Collection<PsiClassType> thrownInTryStatement,
        @Nonnull PsiFile containingFile
    ) {
        PsiElement scope = parameter.getDeclarationScope();
        if (!(scope instanceof PsiCatchSection catchSection)) {
            return Collections.emptyList();
        }

        PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
        int idx = ArrayUtil.find(allCatchSections, catchSection);
        if (idx <= 0) {
            return Collections.emptyList();
        }

        Collection<PsiClassType> thrownTypes = new HashSet<>(thrownInTryStatement);
        PsiManager manager = containingFile.getManager();
        GlobalSearchScope parameterResolveScope = parameter.getResolveScope();
        thrownTypes.add(PsiType.getJavaLangError(manager, parameterResolveScope));
        thrownTypes.add(PsiType.getJavaLangRuntimeException(manager, parameterResolveScope));
        Collection<HighlightInfo> result = new ArrayList<>();

        List<PsiTypeElement> parameterTypeElements = PsiUtil.getParameterTypeElements(parameter);
        boolean isMultiCatch = parameterTypeElements.size() > 1;
        for (PsiTypeElement catchTypeElement : parameterTypeElements) {
            PsiType catchType = catchTypeElement.getType();
            if (ExceptionUtil.isGeneralExceptionType(catchType)) {
                continue;
            }

            // collect exceptions which are caught by this type
            Collection<PsiClassType> caught = ContainerUtil.findAll(thrownTypes, catchType::isAssignableFrom);
            if (caught.isEmpty()) {
                continue;
            }
            Collection<PsiClassType> caughtCopy = new HashSet<>(caught);

            // exclude all which are caught by previous catch sections
            for (int i = 0; i < idx; i++) {
                PsiParameter prevCatchParameter = allCatchSections[i].getParameter();
                if (prevCatchParameter == null) {
                    continue;
                }
                for (PsiTypeElement prevCatchTypeElement : PsiUtil.getParameterTypeElements(prevCatchParameter)) {
                    PsiType prevCatchType = prevCatchTypeElement.getType();
                    caught.removeIf(prevCatchType::isAssignableFrom);
                    if (caught.isEmpty()) {
                        break;
                    }
                }
            }

            // check & warn
            if (caught.isEmpty()) {
                HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                    .range(catchSection)
                    .descriptionAndTooltip(JavaErrorLocalize.exceptionAlreadyCaughtWarn(formatTypes(caughtCopy), caughtCopy.size()))
                    .create();
                if (isMultiCatch) {
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        QuickFixFactory.getInstance().createDeleteMultiCatchFix(catchTypeElement)
                    );
                }
                else {
                    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createDeleteCatchFix(parameter));
                }
                result.add(highlightInfo);
            }
        }

        return result;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkNotAStatement(@Nonnull PsiStatement statement) {
        if (!PsiUtil.isStatement(statement) && !PsiUtilCore.hasErrorElementChild(statement)) {
            boolean isDeclarationNotAllowed = false;
            if (statement instanceof PsiDeclarationStatement) {
                PsiElement parent = statement.getParent();
                isDeclarationNotAllowed = parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement;
            }

            String description = isDeclarationNotAllowed
                ? JavaErrorBundle.message("declaration.not.allowed")
                : JavaErrorBundle.message("not.a.statement");
            HighlightInfo error = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(description)
                .create();
            if (statement instanceof PsiExpressionStatement expressionStmt) {
                QuickFixAction.registerQuickFixAction(
                    error,
                    QuickFixFactory.getInstance().createDeleteSideEffectAwareFix(expressionStmt)
                );
            }
            return error;
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkSwitchBlockStatements(
        @Nonnull PsiSwitchBlock switchBlock,
        @Nonnull LanguageLevel languageLevel,
        @Nonnull PsiFile file
    ) {
        PsiCodeBlock body = switchBlock.getBody();
        if (body != null) {
            PsiElement first = PsiTreeUtil.skipWhitespacesAndCommentsForward(body.getLBrace());
            if (first != null && !(first instanceof PsiSwitchLabelStatementBase) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(first)
                    .descriptionAndTooltip(JavaErrorLocalize.statementMustBePrependedWithCaseLabel())
                    .create();
            }

            PsiElement element = first;
            PsiStatement alien = null;
            boolean classicLabels = false;
            boolean enhancedLabels = false;
            boolean levelChecked = false;
            while (element != null && !PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)) {
                if (element instanceof PsiSwitchLabeledRuleStatement) {
                    if (!levelChecked) {
                        HighlightInfo info = checkFeature(element, JavaFeature.ENHANCED_SWITCH, languageLevel, file);
                        if (info != null) {
                            return info;
                        }
                        levelChecked = true;
                    }
                    if (classicLabels) {
                        alien = (PsiStatement)element;
                        break;
                    }
                    enhancedLabels = true;
                }
                else if (element instanceof PsiStatement statement) {
                    if (enhancedLabels) {
                        alien = statement;
                        break;
                    }
                    classicLabels = true;
                }

                if (!levelChecked && element instanceof PsiSwitchLabelStatementBase switchLabelStatementBase) {
                    PsiExpressionList values = switchLabelStatementBase.getCaseValues();
                    if (values != null && values.getExpressionCount() > 1) {
                        HighlightInfo info = checkFeature(values, JavaFeature.ENHANCED_SWITCH, languageLevel, file);
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
                    PsiSwitchLabeledRuleStatement previousRule =
                        PsiTreeUtil.getPrevSiblingOfType(alien, PsiSwitchLabeledRuleStatement.class);
                    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(alien)
                        .descriptionAndTooltip(JavaErrorLocalize.statementMustBePrependedWithCaseLabel())
                        .create();
                    if (previousRule != null) {
                        QuickFixAction.registerQuickFixAction(
                            info,
                            QuickFixFactory.getInstance().createWrapSwitchRuleStatementsIntoBlockFix(previousRule)
                        );
                    }
                    return info;
                }
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(alien)
                    .descriptionAndTooltip(JavaErrorBundle.message("different.case.kinds.in.switch"))
                    .create();
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
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
            LocalizeValue expected = is7
                ? JavaErrorLocalize.validSwitch1_7SelectorTypes()
                : JavaErrorLocalize.validSwitchSelectorTypes();
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.incompatibleTypes(expected, JavaHighlightUtil.formatType(type)))
                .create();
            if (switchBlock instanceof PsiSwitchStatement switchStmt) {
                QuickFixAction.registerQuickFixAction(
                    info,
                    QuickFixFactory.getInstance().createConvertSwitchToIfIntention(switchStmt)
                );
            }
            if (PsiType.LONG.equals(type) || PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type)) {
                QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createAddTypeCastFix(PsiType.INT, expression));
                QuickFixAction.registerQuickFixAction(
                    info,
                    QuickFixFactory.getInstance().createWrapWithAdapterFix(PsiType.INT, expression)
                );
            }
            if (requiredLevel != null) {
                QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(requiredLevel));
            }
            return info;
        }

        PsiClass member = PsiUtil.resolveClassInClassTypeOnly(type);
        if (member != null && !PsiUtil.isAccessible(member.getProject(), member, expression, null)) {
            String className = PsiFormatUtil.formatClass(member, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.inaccessibleType(className).get())
                .create();
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
    @RequiredReadAction
    public static HighlightInfo checkPolyadicOperatorApplicable(@Nonnull PsiPolyadicExpression expression) {
        PsiExpression[] operands = expression.getOperands();

        PsiType lType = operands[0].getType();
        IElementType operationSign = expression.getOperationTokenType();
        for (int i = 1; i < operands.length; i++) {
            PsiExpression operand = operands[i];
            PsiType rType = operand.getType();
            if (!TypeConversionUtil.isBinaryOperatorApplicable(operationSign, lType, rType, false)) {
                PsiJavaToken token = expression.getTokenBeforeOperand(operand);
                LocalizeValue message = JavaErrorLocalize.binaryOperatorNotApplicable(
                    token.getText(),
                    JavaHighlightUtil.formatType(lType),
                    JavaHighlightUtil.formatType(rType)
                );
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(message)
                    .create();
            }
            lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, operationSign, true);
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkUnaryOperatorApplicable(@Nullable PsiJavaToken token, @Nullable PsiExpression expression) {
        if (token != null && expression != null && !TypeConversionUtil.isUnaryOperatorApplicable(token, expression)) {
            PsiType type = expression.getType();
            if (type == null) {
                return null;
            }

            PsiElement parentExpr = token.getParent();
            HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(parentExpr)
                .descriptionAndTooltip(JavaErrorLocalize.unaryOperatorNotApplicable(token.getText(), JavaHighlightUtil.formatType(type)))
                .create();
            if (parentExpr instanceof PsiPrefixExpression prefixExpr && token.getTokenType() == JavaTokenType.EXCL) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createNegationBroadScopeFix(prefixExpr)
                );
            }
            return highlightInfo;
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkThisOrSuperExpressionInIllegalContext(
        @Nonnull PsiExpression expr,
        @Nullable PsiJavaCodeReferenceElement qualifier,
        @Nonnull LanguageLevel languageLevel
    ) {
        if (expr instanceof PsiSuperExpression && !(expr.getParent() instanceof PsiReferenceExpression)) {
            // like in 'Object o = super;'
            int o = expr.getTextRange().getEndOffset();
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(o, o + 1)
                .descriptionAndTooltip(JavaErrorLocalize.dotExpectedAfterSuperOrThis())
                .create();
        }

        PsiClass aClass = null;
        if (qualifier != null) {
            PsiElement resolved = qualifier.advancedResolve(true).getElement();
            if (resolved instanceof PsiClass resolvedClass) {
                aClass = resolvedClass;
            }
            else if (resolved != null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(qualifier)
                    .descriptionAndTooltip(JavaErrorLocalize.classExpected())
                    .create();
            }
        }
        else {
            aClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
            if (aClass instanceof PsiAnonymousClass anonymousClass && PsiTreeUtil.isAncestor(anonymousClass.getArgumentList(), expr, false)) {
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
                PsiElement resolved = ((PsiReferenceExpression)expr.getParent()).resolve();
                //15.11.2
                //The form T.super.Identifier refers to the field named Identifier of the lexically enclosing instance corresponding to T,
                //but with that instance viewed as an instance of the superclass of T.
                if (resolved instanceof PsiField) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expr)
                        .descriptionAndTooltip(JavaErrorLocalize.isNotAnEnclosingClass(formatClass(aClass)))
                        .create();
                }
            }
        }

        if (qualifier != null && aClass.isInterface() && expr instanceof PsiSuperExpression
            && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            //15.12.1 for method invocation expressions; 15.13 for method references
            //If TypeName denotes an interface, I, then let T be the type declaration immediately enclosing the method reference expression.
            //It is a compile-time error if I is not a direct superinterface of T,
            //or if there exists some other direct superclass or direct superinterface of T, J, such that J is a subtype of I.
            PsiClass classT = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
            if (classT != null) {
                PsiElement resolved = expr.getParent() instanceof PsiReferenceExpression refExpr ? refExpr.resolve() : null;

                PsiClass containingClass =
                    ObjectUtil.notNull(resolved instanceof PsiMethod method ? method.getContainingClass() : null, aClass);
                for (PsiClass superClass : classT.getSupers()) {
                    if (superClass.isInheritor(containingClass, true)) {
                        String cause = null;
                        if (superClass.isInheritor(aClass, true) && superClass.isInterface()) {
                            cause = "redundant interface " + format(containingClass) + " is extended by ";
                        }
                        else if (resolved instanceof PsiMethod method
                            && MethodSignatureUtil.findMethodBySuperMethod(superClass, method, true) != method) {
                            cause = "method " + method.getName() + " is overridden in ";
                        }

                        if (cause != null) {
                            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .range(qualifier)
                                .descriptionAndTooltip(
                                    JavaErrorLocalize.badQualifierInSuperMethodReference(cause + formatClass(superClass))
                                )
                                .create();
                        }
                    }
                }

                if (!classT.isInheritor(aClass, false)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(qualifier)
                        .descriptionAndTooltip(JavaErrorLocalize.noEnclosingInstanceInScope(format(aClass)))
                        .create();
                }
            }
        }

        if (expr instanceof PsiThisExpression) {
            PsiMethod psiMethod = PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
            if (psiMethod == null || psiMethod.getContainingClass() != aClass && !isInsideDefaultMethod(psiMethod, aClass)) {
                if (aClass.isInterface()) {
                    return thisNotFoundInInterfaceInfo(expr);
                }

                if (aClass instanceof PsiAnonymousClass anonymousClass
                    && PsiTreeUtil.isAncestor(anonymousClass.getArgumentList(), expr, true)) {
                    PsiClass parentClass = PsiTreeUtil.getParentOfType(anonymousClass, PsiClass.class, true);
                    if (parentClass != null && parentClass.isInterface()) {
                        return thisNotFoundInInterfaceInfo(expr);
                    }
                }
            }
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkUnqualifiedSuperInDefaultMethod(
        @Nonnull LanguageLevel languageLevel,
        @Nonnull PsiReferenceExpression expr,
        PsiExpression qualifier
    ) {
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && qualifier instanceof PsiSuperExpression superExpr) {
            PsiMethod method = PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
            if (method != null && method.hasModifierProperty(PsiModifier.DEFAULT) && superExpr.getQualifier() == null) {
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expr)
                    .descriptionAndTooltip(JavaErrorLocalize.unqualifiedSuperDisallowed())
                    .create();
                QualifySuperArgumentFix.registerQuickFixAction((PsiSuperExpression)qualifier, info);
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

    @RequiredReadAction
    private static HighlightInfo thisNotFoundInInterfaceInfo(@Nonnull PsiExpression expr) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(expr)
            .descriptionAndTooltip(LocalizeValue.localizeTODO("Cannot find symbol variable this"))
            .create();
    }

    private static boolean resolvesToImmediateSuperInterface(
        @Nonnull PsiExpression expr,
        @Nullable PsiJavaCodeReferenceElement qualifier,
        @Nonnull PsiClass aClass,
        @Nonnull LanguageLevel languageLevel
    ) {
        if (!(expr instanceof PsiSuperExpression) || qualifier == null || !languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return false;
        }
        PsiType superType = expr.getType();
        if (!(superType instanceof PsiClassType classType)) {
            return false;
        }
        PsiClass superClass = classType.resolve();
        return superClass != null && aClass.equals(superClass)
            && PsiUtil.getEnclosingStaticElement(expr, PsiTreeUtil.getParentOfType(expr, PsiClass.class)) == null;
    }

    @Nonnull
    public static LocalizeValue buildProblemWithStaticDescription(@Nonnull PsiElement refElement) {
        String type = FindUsagesProvider.forLanguage(JavaLanguage.INSTANCE).getType(refElement);
        String name = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
        return JavaErrorLocalize.nonStaticSymbolReferencedFromStaticContext(type, name);
    }

    @RequiredReadAction
    public static void registerStaticProblemQuickFixAction(
        @Nonnull PsiElement refElement,
        HighlightInfo errorResult,
        @Nonnull PsiJavaCodeReferenceElement place
    ) {
        if (refElement instanceof PsiModifierListOwner modifierListOwner) {
            QuickFixAction.registerQuickFixAction(
                errorResult,
                QuickFixFactory.getInstance().createModifierListFix(
                    modifierListOwner,
                    PsiModifier.STATIC,
                    true,
                    false
                )
            );
        }
        // make context non static
        PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
        if (staticParent != null && isInstanceReference(place)) {
            QuickFixAction.registerQuickFixAction(
                errorResult,
                QuickFixFactory.getInstance()
                    .createModifierListFix(staticParent, PsiModifier.STATIC, false, false)
            );
        }
        if (place instanceof PsiReferenceExpression placeRefExpr && refElement instanceof PsiField) {
            QuickFixAction.registerQuickFixAction(
                errorResult,
                QuickFixFactory.getInstance().createCreateFieldFromUsageFix(placeRefExpr)
            );
        }
    }

    @RequiredReadAction
    private static boolean isInstanceReference(@Nonnull PsiJavaCodeReferenceElement place) {
        PsiElement qualifier = place.getQualifier();
        if (qualifier == null) {
            return true;
        }
        if (!(qualifier instanceof PsiJavaCodeReferenceElement codeReferenceElement)) {
            return false;
        }
        PsiElement q = codeReferenceElement.resolve();
        if (q instanceof PsiClass) {
            return false;
        }
        if (q != null) {
            return true;
        }
        String qname = ((PsiJavaCodeReferenceElement)qualifier).getQualifiedName();
        return qname == null || !Character.isLowerCase(qname.charAt(0));
    }

    @Nonnull
    @RequiredReadAction
    public static LocalizeValue buildProblemWithAccessDescription(@Nonnull PsiElement reference, @Nonnull JavaResolveResult result) {
        return buildProblemWithAccessDescription(reference, result, ObjectUtil.notNull(result.getElement()));
    }

    @Nonnull
    @RequiredReadAction
    private static LocalizeValue buildProblemWithAccessDescription(
        @Nonnull PsiElement reference,
        @Nonnull JavaResolveResult result,
        @Nonnull PsiElement resolved
    ) {
        assert resolved instanceof PsiModifierListOwner : resolved;
        PsiModifierListOwner refElement = (PsiModifierListOwner)resolved;
        String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());

        if (refElement.hasModifierProperty(PsiModifier.PRIVATE)) {
            String containerName = getContainerName(refElement, result.getSubstitutor());
            return JavaErrorLocalize.privateSymbol(symbolName, containerName);
        }
        else if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
            String containerName = getContainerName(refElement, result.getSubstitutor());
            return JavaErrorLocalize.protectedSymbol(symbolName, containerName);
        }
        else {
            PsiClass packageLocalClass = getPackageLocalClassInTheMiddle(reference);
            if (packageLocalClass != null) {
                refElement = packageLocalClass;
                symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
            }
            if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
                String containerName = getContainerName(refElement, result.getSubstitutor());
                return JavaErrorLocalize.packageLocalSymbol(symbolName, containerName);
            }
            else {
                String containerName = getContainerName(refElement, result.getSubstitutor());
                return JavaErrorLocalize.visibilityAccessProblem(symbolName, containerName);
            }
        }
    }

    private static PsiElement getContainer(PsiModifierListOwner refElement) {
        for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
            PsiElement container = provider.getContainer(refElement);
            if (container != null) {
                return container;
            }
        }
        return refElement.getParent();
    }

    private static String getContainerName(PsiModifierListOwner refElement, PsiSubstitutor substitutor) {
        PsiElement container = getContainer(refElement);
        return container == null ? "?" : HighlightMessageUtil.getSymbolName(container, substitutor);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkValidArrayAccessExpression(@Nonnull PsiArrayAccessExpression arrayAccessExpression) {
        PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
        PsiType arrayExpressionType = arrayExpression.getType();

        if (arrayExpressionType != null && !(arrayExpressionType instanceof PsiArrayType)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(arrayExpression)
                .descriptionAndTooltip(JavaErrorLocalize.arrayTypeExpected(JavaHighlightUtil.formatType(arrayExpressionType)))
                .registerFix(QuickFixFactory.getInstance().createReplaceWithListAccessFix(arrayAccessExpression), null, null, null, null)
                .create();
        }

        PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
        return indexExpression != null
            ? checkAssignability(PsiType.INT, indexExpression.getType(), indexExpression, indexExpression)
            : null;
    }


    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkCatchParameterIsThrowable(@Nonnull PsiParameter parameter) {
        if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
            PsiType type = parameter.getType();
            return checkMustBeThrowable(type, parameter, true);
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
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
    @RequiredReadAction
    public static HighlightInfo checkResourceVariableIsFinal(@Nonnull PsiResourceExpression resource) {
        PsiExpression expression = resource.getExpression();

        if (expression instanceof PsiThisExpression) {
            return null;
        }

        if (expression instanceof PsiReferenceExpression refExpr) {
            PsiElement target = refExpr.resolve();
            if (target == null) {
                return null;
            }

            if (target instanceof PsiVariable variable) {
                PsiModifierList modifierList = variable.getModifierList();
                if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) {
                    return null;
                }

                if (!(variable instanceof PsiField)
                    && HighlightControlFlowUtil.isEffectivelyFinal(variable, resource, (PsiJavaCodeReferenceElement)expression)) {
                    return null;
                }
            }

            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorBundle.message("resource.variable.must.be.final"))
                .create();
        }

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(expression)
            .descriptionAndTooltip(JavaErrorBundle.message("declaration.or.variable.expected"))
            .create();
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<HighlightInfo> checkArrayInitializer(PsiExpression initializer, PsiType type) {
        if (!(initializer instanceof PsiArrayInitializerExpression arrayInitializer)) {
            return Collections.emptyList();
        }
        if (!(type instanceof PsiArrayType arrayType)) {
            return Collections.emptyList();
        }

        PsiType componentType = arrayType.getComponentType();

        boolean arrayTypeFixChecked = false;
        VariableArrayTypeFix fix = null;

        Collection<HighlightInfo> result = new ArrayList<>();
        PsiExpression[] initializers = arrayInitializer.getInitializers();
        for (PsiExpression expression : initializers) {
            HighlightInfo info = checkArrayInitializerCompatibleTypes(expression, componentType);
            if (info != null) {
                result.add(info);

                if (!arrayTypeFixChecked) {
                    PsiType checkResult = JavaHighlightUtil.sameType(initializers);
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
    @RequiredReadAction
    private static HighlightInfo checkArrayInitializerCompatibleTypes(@Nonnull PsiExpression initializer, PsiType componentType) {
        PsiType initializerType = initializer.getType();
        if (initializerType == null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(initializer)
                .descriptionAndTooltip(JavaErrorBundle.message("illegal.initializer", JavaHighlightUtil.formatType(componentType)))
                .create();
        }
        PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
        return checkAssignability(componentType, initializerType, expression, initializer);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkExpressionRequired(
        @Nonnull PsiReferenceExpression expression,
        @Nonnull JavaResolveResult resultForIncompleteCode
    ) {
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

        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(expression)
            .descriptionAndTooltip(JavaErrorLocalize.expressionExpected())
            .create();
        if (info != null) {
            UnresolvedReferenceQuickFixProvider.registerReferenceFixes(expression, QuickFixActionRegistrar.create(info));
        }
        return info;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkArrayInitializerApplicable(@Nonnull PsiArrayInitializerExpression expression) {
        /*
         * JLS 10.6 Array Initializers
         * An array initializer may be specified in a declaration, or as part of an array creation expression
         */
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiVariable variable) {
            if (variable.getType() instanceof PsiArrayType) {
                return null;
            }
        }
        else if (parent instanceof PsiNewExpression || parent instanceof PsiArrayInitializerExpression) {
            return null;
        }

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(expression)
            .descriptionAndTooltip(JavaErrorLocalize.arrayInitializerNotAllowed())
            .registerFix(QuickFixFactory.getInstance().createAddNewArrayExpressionFix(expression), null, null, null, null)
            .create();
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkCaseStatement(@Nonnull PsiSwitchLabelStatementBase statement) {
        PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
        if (switchBlock == null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(JavaErrorLocalize.caseStatementOutsideSwitch())
                .create();
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
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
            if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) {
                continue;
            }
            boolean defaultCase = labelStatement.isDefaultCase();

            if (defaultCase) {
                values.putValue(defaultValue, ObjectUtil.notNull(labelStatement.getFirstChild(), labelStatement));
                hasDefaultCase = true;
            }
            else {
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
                        if (expr instanceof PsiReferenceExpression refExpr
                            && refExpr.resolve() instanceof PsiEnumConstant enumConst) {
                            value = enumConst.getName();
                            if (refExpr.getQualifier() != null) {
                                results.add(
                                    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                        .range(refExpr)
                                        .descriptionAndTooltip(JavaErrorLocalize.qualifiedEnumConstantInSwitch())
                                        .create()
                                );
                                continue;
                            }
                        }
                        if (value == null) {
                            value = ConstantExpressionUtil.computeCastTo(expr, selectorType);
                        }
                        if (value == null) {
                            results.add(
                                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                    .range(expr)
                                    .descriptionAndTooltip(JavaErrorLocalize.constantExpressionRequired())
                                    .create()
                            );
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
                LocalizeValue description = value == defaultValue
                    ? JavaErrorLocalize.duplicateDefaultSwitchLabel()
                    : JavaErrorLocalize.duplicateSwitchLabel(value);
                for (PsiElement element : entry.getValue()) {
                    results.add(
                        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(element)
                            .descriptionAndTooltip(description)
                            .create()
                    );
                }
            }
        }

        if (results.isEmpty() && switchBlock instanceof PsiSwitchExpression) {
            Set<String> missingConstants = new HashSet<>();
            boolean exhaustive = hasDefaultCase;
            if (!exhaustive) {
                if (!values.isEmpty() && selectorType instanceof PsiClassType classType) {
                    PsiClass type = classType.resolve();
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
                String message = values.isEmpty()
                    ? JavaErrorBundle.message("switch.expr.empty")
                    : JavaErrorBundle.message("switch.expr.incomplete");
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(ObjectUtil.notNull(selectorExpression, switchBlock))
                    .descriptionAndTooltip(message)
                    .create();
                if (!missingConstants.isEmpty()) {
                    QuickFixAction.registerQuickFixAction(
                        info,
                        QuickFixFactory.getInstance().createAddMissingEnumBranchesFix(switchBlock, missingConstants)
                    );
                }
                QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createAddSwitchDefaultFix(switchBlock, null));
                results.add(info);
            }
        }

        return results;
    }


    /**
     * see JLS 8.3.2.3
     */
    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkIllegalForwardReferenceToField(
        @Nonnull PsiReferenceExpression expression,
        @Nonnull PsiField referencedField
    ) {
        Boolean isIllegalForwardReference = isIllegalForwardReferenceToField(expression, referencedField, false);
        if (isIllegalForwardReference == null) {
            return null;
        }
        String description = isIllegalForwardReference
            ? JavaErrorLocalize.illegalForwardReference().get()
            : JavaErrorBundle.message("illegal.self.reference");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(expression)
            .descriptionAndTooltip(description)
            .create();
    }

    @RequiredReadAction
    public static Boolean isIllegalForwardReferenceToField(
        @Nonnull PsiReferenceExpression expression,
        @Nonnull PsiField referencedField,
        boolean acceptQualified
    ) {
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
        boolean isStaticClassInitializer = classInitializer != null && classInitializer.isStatic();
        boolean isStaticInitField = initField != null && initField.isStatic();
        boolean inStaticContext = isStaticInitField || isStaticClassInitializer;
        if (!inStaticContext && referencedField.isStatic()) {
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
            if (parent instanceof PsiField field) {
                if (element == field.getInitializer()) {
                    return field;
                }
                if (field instanceof PsiEnumConstant enumConst && element == enumConst.getArgumentList()) {
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
            if (element instanceof PsiClassInitializer classInitializer) {
                return classInitializer;
            }
            if (element instanceof PsiClass || element instanceof PsiMethod) {
                return null;
            }
            element = element.getParent();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
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
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(typeElement)
                    .descriptionAndTooltip(JavaErrorLocalize.unknownClass(canonicalText))
                    .create();

                PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
                if (referenceElement != null && info != null) {
                    UnresolvedReferenceQuickFixProvider.registerReferenceFixes(referenceElement, QuickFixActionRegistrar.create(info));
                }
                return info;
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
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

            if (typeOwner instanceof PsiMethod method) {
                if (method.getReturnTypeElement() == parent && PsiType.VOID.equals(method.getReturnType())) {
                    return null;
                }
            }
            else if (typeOwner instanceof PsiClassObjectAccessExpression classObjectAccessExpression) {
                if (TypeConversionUtil.isVoidType(classObjectAccessExpression.getOperand().getType())) {
                    return null;
                }
            }
            else if (typeOwner instanceof JavaCodeFragment) {
                if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) {
                    return null;
                }
            }
        }

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(type)
            .descriptionAndTooltip(JavaErrorLocalize.illegalTypeVoid())
            .create();
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkMemberReferencedBeforeConstructorCalled(
        @Nonnull PsiElement expression,
        PsiElement resolved,
        @Nonnull PsiFile containingFile
    ) {
        PsiClass referencedClass;
        String resolvedName;
        PsiType type;
        if (expression instanceof PsiJavaCodeReferenceElement javaCodeRef) {
            // redirected ctr
            if (PsiKeyword.THIS.equals(javaCodeRef.getReferenceName()) && resolved instanceof PsiMethod method && method.isConstructor()) {
                return null;
            }
            PsiElement qualifier = javaCodeRef.getQualifier();
            type = qualifier instanceof PsiExpression qExpr ? qExpr.getType() : null;
            referencedClass = PsiUtil.resolveClassInType(type);

            boolean isSuperCall = RefactoringChangeUtil.isSuperMethodCall(javaCodeRef.getParent());
            if (resolved == null && isSuperCall) {
                if (qualifier instanceof PsiReferenceExpression qRefExpr) {
                    resolved = qRefExpr.resolve();
                    expression = qualifier;
                    type = qRefExpr.getType();
                    referencedClass = PsiUtil.resolveClassInType(type);
                }
                else if (qualifier == null) {
                    resolved = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class);
                    if (resolved != null) {
                        referencedClass = ((PsiMethod)resolved).getContainingClass();
                    }
                }
                else if (qualifier instanceof PsiThisExpression thisExpr) {
                    referencedClass = PsiUtil.resolveClassInType(thisExpr.getType());
                }
            }
            if (resolved instanceof PsiField referencedField) {
                if (referencedField.isStatic()) {
                    return null;
                }
                resolvedName = PsiFormatUtil.formatVariable(
                    referencedField,
                    PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME,
                    PsiSubstitutor.EMPTY
                );
                referencedClass = referencedField.getContainingClass();
            }
            else if (resolved instanceof PsiMethod method) {
                if (method.isStatic()) {
                    return null;
                }
                PsiElement nameElement = expression instanceof PsiThisExpression
                    ? expression
                    : ((PsiJavaCodeReferenceElement)expression).getReferenceNameElement();
                String name = nameElement == null ? null : nameElement.getText();
                if (isSuperCall) {
                    if (referencedClass == null) {
                        return null;
                    }
                    if (qualifier == null) {
                        PsiClass superClass = referencedClass.getSuperClass();
                        if (superClass != null && PsiUtil.isInnerClass(superClass)
                            && InheritanceUtil.isInheritorOrSelf(referencedClass, superClass.getContainingClass(), true)) {
                            // by default super() is considered this. - qualified
                            resolvedName = PsiKeyword.THIS;
                        }
                        else {
                            return null;
                        }
                    }
                    else {
                        resolvedName = qualifier.getText();
                    }
                }
                else if (PsiKeyword.THIS.equals(name)) {
                    resolvedName = PsiKeyword.THIS;
                }
                else {
                    resolvedName = PsiFormatUtil.formatMethod(
                        method,
                        PsiSubstitutor.EMPTY,
                        PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME,
                        0
                    );
                    if (referencedClass == null) {
                        referencedClass = method.getContainingClass();
                    }
                }
            }
            else if (resolved instanceof PsiClass aClass) {
                if (aClass.isStatic()) {
                    return null;
                }
                referencedClass = aClass.getContainingClass();
                if (referencedClass == null) {
                    return null;
                }
                resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME);
            }
            else {
                return null;
            }
        }
        else if (expression instanceof PsiThisExpression thisExpr) {
            type = thisExpr.getType();
            referencedClass = PsiUtil.resolveClassInType(type);
            if (thisExpr.getQualifier() != null) {
                resolvedName =
                    referencedClass == null ? null : PsiFormatUtil.formatClass(referencedClass, PsiFormatUtilBase.SHOW_NAME) + ".this";
            }
            else {
                resolvedName = "this";
            }
        }
        else {
            return null;
        }

        if (referencedClass == null) {
            return null;
        }
        return checkReferenceToOurInstanceInsideThisOrSuper(expression, referencedClass, resolvedName, containingFile);
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkReferenceToOurInstanceInsideThisOrSuper(
        @Nonnull PsiElement expression,
        @Nonnull PsiClass referencedClass,
        String resolvedName,
        @Nonnull PsiFile containingFile
    ) {
        if (PsiTreeUtil.getParentOfType(expression, PsiReferenceParameterList.class) != null) {
            return null;
        }
        PsiElement element = expression.getParent();
        while (element != null) {
            // check if expression inside super()/this() call
            if (RefactoringChangeUtil.isSuperOrThisMethodCall(element)) {
                PsiElement parentClass = new PsiMatcherImpl(element)
                    .parent(PsiMatchers.hasClass(PsiExpressionStatement.class))
                    .parent(PsiMatchers.hasClass(PsiCodeBlock.class))
                    .parent(PsiMatchers.hasClass(PsiMethod.class))
                    .dot(JavaMatchers.isConstructor(true))
                    .parent(PsiMatchers.hasClass(PsiClass.class))
                    .getElement();
                if (parentClass == null) {
                    return null;
                }

                // only this class/superclasses instance methods are not allowed to call
                PsiClass aClass = (PsiClass)parentClass;
                if (PsiUtil.isInnerClass(aClass) && referencedClass == aClass.getContainingClass()) {
                    return null;
                }
                // field or method should be declared in this class or super
                if (!InheritanceUtil.isInheritorOrSelf(aClass, referencedClass, true)) {
                    return null;
                }
                // and point to our instance
                if (expression instanceof PsiReferenceExpression refExpr
                    && !thisOrSuperReference(refExpr.getQualifierExpression(), aClass)) {
                    return null;
                }

                if (expression instanceof PsiJavaCodeReferenceElement
                    && !aClass.equals(PsiTreeUtil.getParentOfType(expression, PsiClass.class))
                    && PsiTreeUtil.getParentOfType(expression, PsiTypeElement.class) != null) {
                    return null;
                }

                if (expression instanceof PsiJavaCodeReferenceElement
                    && PsiTreeUtil.getParentOfType(expression, PsiClassObjectAccessExpression.class) != null) {
                    return null;
                }

                HighlightInfo highlightInfo = createMemberReferencedError(resolvedName, expression.getTextRange());
                if (expression instanceof PsiReferenceExpression refExpr && PsiUtil.isInnerClass(aClass)) {
                    String referenceName = refExpr.getReferenceName();
                    PsiClass containingClass = aClass.getContainingClass();
                    LOG.assertTrue(containingClass != null);
                    PsiField fieldInContainingClass = containingClass.findFieldByName(referenceName, true);
                    if (fieldInContainingClass != null && refExpr.getQualifierExpression() == null) {
                        QuickFixAction.registerQuickFixAction(highlightInfo, new QualifyWithThisFix(containingClass, refExpr));
                    }
                }

                return highlightInfo;
            }

            if (element instanceof PsiReferenceExpression refExpr) {
                PsiElement resolve;
                if (element instanceof PsiReferenceExpressionImpl refExprImpl) {
                    JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(
                        refExprImpl,
                        PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE,
                        true,
                        false,
                        containingFile
                    );
                    resolve = results.length == 1 ? results[0].getElement() : null;
                }
                else {
                    resolve = refExpr.resolve();
                }

                if (resolve instanceof PsiField field && field.isStatic()) {
                    return null;
                }
            }

            element = element.getParent();
            if (element instanceof PsiClass psiClass && InheritanceUtil.isInheritorOrSelf(psiClass, referencedClass, true)) {
                return null;
            }
        }
        return null;
    }

    private static HighlightInfo createMemberReferencedError(String resolvedName, @Nonnull TextRange textRange) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(textRange)
            .descriptionAndTooltip(JavaErrorLocalize.memberReferencedBeforeConstructorCalled(resolvedName))
            .create();
    }

    @Nullable
    @RequiredReadAction
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
                return createMemberReferencedError(
                    aClass.getName() + ".this",
                    HighlightNamesUtil.getMethodDeclarationTextRange(constructor)
                );
            }
        }
        return null;
    }

    private static boolean isSuperCalledInConstructor(@Nonnull PsiMethod constructor) {
        PsiCodeBlock body = constructor.getBody();
        if (body == null) {
            return false;
        }
        PsiStatement[] statements = body.getStatements();
        if (statements.length == 0) {
            return false;
        }
        PsiStatement statement = statements[0];
        PsiElement element = new PsiMatcherImpl(statement)
            .dot(PsiMatchers.hasClass(PsiExpressionStatement.class))
            .firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class))
            .firstChild(PsiMatchers.hasClass(PsiReferenceExpression.class))
            .firstChild(PsiMatchers.hasClass(PsiKeyword.class))
            .dot(PsiMatchers.hasText(PsiKeyword.SUPER))
            .getElement();
        return element != null;
    }

    @RequiredReadAction
    private static boolean thisOrSuperReference(@Nullable PsiExpression qualifierExpression, PsiClass aClass) {
        if (qualifierExpression == null) {
            return true;
        }
        PsiJavaCodeReferenceElement qualifier;
        if (qualifierExpression instanceof PsiThisExpression thisExpr) {
            qualifier = thisExpr.getQualifier();
        }
        else if (qualifierExpression instanceof PsiSuperExpression superExpr) {
            qualifier = superExpr.getQualifier();
        }
        else {
            return false;
        }
        //noinspection SimplifiableIfStatement
        if (qualifier == null) {
            return true;
        }
        return qualifier.resolve() instanceof PsiClass psiClass && InheritanceUtil.isInheritorOrSelf(aClass, psiClass, true);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkLabelWithoutStatement(@Nonnull PsiLabeledStatement statement) {
        if (statement.getStatement() == null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(JavaErrorLocalize.labelWithoutStatement())
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkLabelAlreadyInUse(@Nonnull PsiLabeledStatement statement) {
        PsiIdentifier identifier = statement.getLabelIdentifier();
        String text = identifier.getText();
        PsiElement element = statement;
        while (element != null) {
            if (element instanceof PsiMethod || element instanceof PsiClass) {
                break;
            }
            if (element instanceof PsiLabeledStatement labeledStmt && element != statement
                && Objects.equals(labeledStmt.getLabelIdentifier().getText(), text)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(identifier)
                    .descriptionAndTooltip(JavaErrorLocalize.duplicateLabel(text))
                    .create();
            }
            element = element.getParent();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkUnclosedComment(@Nonnull PsiComment comment) {
        if (!(comment instanceof PsiDocComment) && comment.getTokenType() != JavaTokenType.C_STYLE_COMMENT) {
            return null;
        }
        if (!comment.getText().endsWith("*/")) {
            int start = comment.getTextRange().getEndOffset() - 1;
            int end = start + 1;
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(start, end)
                .descriptionAndTooltip(JavaErrorLocalize.unclosedComment())
                .create();
        }
        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<HighlightInfo> checkCatchTypeIsDisjoint(@Nonnull PsiParameter parameter) {
        if (!(parameter.getType() instanceof PsiDisjunctionType)) {
            return Collections.emptyList();
        }

        Collection<HighlightInfo> result = new ArrayList<>();
        List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
        for (int i = 0, size = typeElements.size(); i < size; i++) {
            PsiClass class1 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(i).getType());
            if (class1 == null) {
                continue;
            }
            for (int j = i + 1; j < size; j++) {
                PsiClass class2 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(j).getType());
                if (class2 == null) {
                    continue;
                }
                boolean sub = InheritanceUtil.isInheritorOrSelf(class1, class2, true);
                boolean sup = InheritanceUtil.isInheritorOrSelf(class2, class1, true);
                if (sub || sup) {
                    String name1 = PsiFormatUtil.formatClass(class1, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
                    String name2 = PsiFormatUtil.formatClass(class2, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
                    PsiTypeElement element = typeElements.get(sub ? i : j);
                    HighlightInfo highlight = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(element)
                        .descriptionAndTooltip(JavaErrorLocalize.exceptionMustBeDisjoint(sub ? name1 : name2, sub ? name2 : name1).get())
                        .registerFix(QuickFixFactory.getInstance().createDeleteMultiCatchFix(element), null, null, null, null)
                        .create();
                    result.add(highlight);
                    break;
                }
            }
        }

        return result;
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<HighlightInfo> checkExceptionAlreadyCaught(@Nonnull PsiParameter parameter) {
        PsiElement scope = parameter.getDeclarationScope();
        if (!(scope instanceof PsiCatchSection catchSection)) {
            return Collections.emptyList();
        }

        PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
        int startFrom = ArrayUtil.find(allCatchSections, catchSection) - 1;
        if (startFrom < 0) {
            return Collections.emptyList();
        }

        List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
        boolean isInMultiCatch = typeElements.size() > 1;
        Collection<HighlightInfo> result = new ArrayList<>();

        for (PsiTypeElement typeElement : typeElements) {
            PsiClass catchClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
            if (catchClass == null) {
                continue;
            }

            for (int i = startFrom; i >= 0; i--) {
                PsiCatchSection upperCatchSection = allCatchSections[i];
                PsiType upperCatchType = upperCatchSection.getCatchType();

                boolean highlight = upperCatchType instanceof PsiDisjunctionType disjunctionType
                    ? checkMultipleTypes(catchClass, disjunctionType.getDisjunctions())
                    : checkSingleType(catchClass, upperCatchType);
                if (highlight) {
                    String className =
                        PsiFormatUtil.formatClass(catchClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
                    HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(typeElement)
                        .descriptionAndTooltip(JavaErrorLocalize.exceptionAlreadyCaught(className))
                        .registerFix(
                            QuickFixFactory.getInstance().createMoveCatchUpFix(catchSection, upperCatchSection),
                            null,
                            null,
                            null,
                            null
                        )
                        .create();
                    result.add(highlightInfo);

                    if (isInMultiCatch) {
                        QuickFixAction.registerQuickFixAction(
                            highlightInfo,
                            QuickFixFactory.getInstance().createDeleteMultiCatchFix(typeElement)
                        );
                    }
                    else {
                        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createDeleteCatchFix(parameter));
                    }
                }
            }
        }

        return result;
    }

    private static boolean checkMultipleTypes(PsiClass catchClass, @Nonnull List<PsiType> upperCatchTypes) {
        for (int i = upperCatchTypes.size() - 1; i >= 0; i--) {
            if (checkSingleType(catchClass, upperCatchTypes.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkSingleType(PsiClass catchClass, PsiType upperCatchType) {
        PsiClass upperCatchClass = PsiUtil.resolveClassInType(upperCatchType);
        return upperCatchClass != null && InheritanceUtil.isInheritorOrSelf(catchClass, upperCatchClass, true);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkTernaryOperatorConditionIsBoolean(@Nonnull PsiExpression expression, PsiType type) {
        if (expression.getParent() instanceof PsiConditionalExpression condExpr && condExpr.getCondition() == expression
            && !TypeConversionUtil.isBooleanType(type)) {
            return createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expression.getTextRange(), 0);
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkStatementPrependedWithCaseInsideSwitch(@Nonnull PsiSwitchStatement statement) {
        PsiCodeBlock body = statement.getBody();
        if (body != null) {
            PsiElement first = PsiTreeUtil.skipSiblingsForward(body.getLBrace(), PsiWhiteSpace.class, PsiComment.class);
            if (first != null && !(first instanceof PsiSwitchLabelStatement) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(first)
                    .descriptionAndTooltip(JavaErrorLocalize.statementMustBePrependedWithCaseLabel())
                    .create();
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkAssertOperatorTypes(@Nonnull PsiExpression expression, @Nullable PsiType type) {
        if (type == null) {
            return null;
        }
        if (!(expression.getParent() instanceof PsiAssertStatement assertStatement)) {
            return null;
        }
        if (expression == assertStatement.getAssertCondition() && !TypeConversionUtil.isBooleanType(type)) {
            // addTypeCast quickfix is not applicable here since no type can be cast to boolean
            HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expression.getTextRange(), 0);
            if (expression instanceof PsiAssignmentExpression assignment && assignment.getOperationTokenType() == JavaTokenType.EQ) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createAssignmentToComparisonFix(assignment)
                );
            }
            return highlightInfo;
        }
        if (expression == assertStatement.getAssertDescription() && TypeConversionUtil.isVoidType(type)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.voidTypeIsNotAllowed())
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkSynchronizedExpressionType(
        @Nonnull PsiExpression expression,
        @Nullable PsiType type,
        @Nonnull PsiFile containingFile
    ) {
        if (type == null) {
            return null;
        }
        if (expression.getParent() instanceof PsiSynchronizedStatement syncStmt
            && expression == syncStmt.getLockExpression() && (type instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(type))) {
            PsiClassType objectType = PsiType.getJavaLangObject(containingFile.getManager(), expression.getResolveScope());
            return createIncompatibleTypeHighlightInfo(objectType, type, expression.getTextRange(), 0);
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkConditionalExpressionBranchTypesMatch(@Nonnull PsiExpression expression, PsiType type) {
        PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiConditionalExpression conditionalExpr)) {
            return null;
        }
        // check else branches only
        if (conditionalExpr.getElseExpression() != expression) {
            return null;
        }
        PsiExpression thenExpression = conditionalExpr.getThenExpression();
        assert thenExpression != null;
        PsiType thenType = thenExpression.getType();
        if (thenType == null || type == null) {
            return null;
        }
        if (conditionalExpr.getType() == null) {
            if (PsiUtil.isLanguageLevel8OrHigher(conditionalExpr) && PsiPolyExpressionUtil.isPolyExpression(conditionalExpr)) {
                return null;
            }
            // cannot derive type of conditional expression
            // elseType will never be cast-able to thenType, so no quick fix here
            return createIncompatibleTypeHighlightInfo(thenType, type, expression.getTextRange(), 0);
        }
        return null;
    }

    @SuppressWarnings("StringContatenationInLoop")
    public static HighlightInfo createIncompatibleTypeHighlightInfo(
        PsiType lType,
        PsiType rType,
        @Nonnull TextRange textRange,
        int navigationShift
    ) {
        PsiType lType1 = lType;
        PsiType rType1 = rType;
        PsiTypeParameter[] lTypeParams = PsiTypeParameter.EMPTY_ARRAY;
        PsiSubstitutor lTypeSubstitutor = PsiSubstitutor.EMPTY;
        if (lType1 instanceof PsiClassType lClassType) {
            PsiClassType.ClassResolveResult resolveResult = lClassType.resolveGenerics();
            lTypeSubstitutor = resolveResult.getSubstitutor();
            PsiClass psiClass = resolveResult.getElement();
            if (psiClass instanceof PsiAnonymousClass anonymousClass) {
                lType1 = anonymousClass.getBaseClassType();
                resolveResult = ((PsiClassType)lType1).resolveGenerics();
                lTypeSubstitutor = resolveResult.getSubstitutor();
                psiClass = resolveResult.getElement();
            }
            lTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
        }
        PsiTypeParameter[] rTypeParams = PsiTypeParameter.EMPTY_ARRAY;
        PsiSubstitutor rTypeSubstitutor = PsiSubstitutor.EMPTY;
        if (rType1 instanceof PsiClassType rClassType) {
            PsiClassType.ClassResolveResult resolveResult = rClassType.resolveGenerics();
            rTypeSubstitutor = resolveResult.getSubstitutor();
            PsiClass psiClass = resolveResult.getElement();
            if (psiClass instanceof PsiAnonymousClass anonymousClass) {
                rType1 = anonymousClass.getBaseClassType();
                resolveResult = ((PsiClassType)rType1).resolveGenerics();
                rTypeSubstitutor = resolveResult.getSubstitutor();
                psiClass = resolveResult.getElement();
            }
            rTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
        }

        int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
        @Language("HTML") String requiredRow = "";
        @Language("HTML") String foundRow = "";
        for (int i = 0; i < typeParamColumns; i++) {
            PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
            PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
            PsiType lSubstitutedType = lTypeParameter == null ? null : lTypeSubstitutor.substitute(lTypeParameter);
            PsiType rSubstitutedType = rTypeParameter == null ? null : rTypeSubstitutor.substitute(rTypeParameter);
            boolean matches = Comparing.equal(lSubstitutedType, rSubstitutedType);
            String openBrace = i == 0 ? "&lt;" : "";
            String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
            requiredRow += "<td>" + (lTypeParams.length == 0 ? "" : openBrace) +
                redIfNotMatch(lSubstitutedType, matches) +
                (i < lTypeParams.length ? closeBrace : "") + "</td>";
            foundRow += "<td>" + (rTypeParams.length == 0 ? "" : openBrace) +
                redIfNotMatch(rSubstitutedType, matches) +
                (i < rTypeParams.length ? closeBrace : "") + "</td>";
        }
        PsiType lRawType = lType1 instanceof PsiClassType lClassType1 ? lClassType1.rawType() : lType1;
        PsiType rRawType = rType1 instanceof PsiClassType rClassType1 ? rClassType1.rawType() : rType1;
        boolean assignable = lRawType == null || rRawType == null || TypeConversionUtil.isAssignable(lRawType, rRawType);

        LocalizeValue toolTip = JavaErrorLocalize.incompatibleTypesHtmlTooltip(
            redIfNotMatch(lRawType, assignable),
            requiredRow,
            redIfNotMatch(rRawType, assignable),
            foundRow
        );

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(textRange)
            .description(JavaErrorLocalize.incompatibleTypes(JavaHighlightUtil.formatType(lType1), JavaHighlightUtil.formatType(rType1))
                .get())
            .escapedToolTip(toolTip.get())
            .navigationShift(navigationShift)
            .create();
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkSingleImportClassConflict(
        @Nonnull PsiImportStatement statement,
        @Nonnull Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> importedClasses,
        @Nonnull PsiFile containingFile
    ) {
        if (statement.isOnDemand()) {
            return null;
        }
        PsiElement element = statement.resolve();
        if (element instanceof PsiClass psiClass) {
            String name = psiClass.getName();
            Pair<PsiImportStaticReferenceElement, PsiClass> imported = importedClasses.get(name);
            PsiClass importedClass = imported == null ? null : imported.getSecond();
            if (importedClass != null && !containingFile.getManager().areElementsEquivalent(importedClass, element)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(statement)
                    .descriptionAndTooltip(JavaErrorLocalize.singleImportClassConflict(formatClass(importedClass)).get())
                    .create();
            }
            importedClasses.put(name, Pair.create(null, (PsiClass)element));
        }
        return null;
    }

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
    @RequiredReadAction
    public static HighlightInfo checkMustBeThrowable(@Nullable PsiType type, @Nonnull PsiElement context, boolean addCastIntention) {
        if (type == null) {
            return null;
        }
        PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
        PsiClassType throwable = factory.createTypeByFQClassName("java.lang.Throwable", context.getResolveScope());
        if (!TypeConversionUtil.isAssignable(throwable, type)) {
            HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(throwable, type, context.getTextRange(), 0);
            if (addCastIntention && TypeConversionUtil.areTypesConvertible(type, throwable)
                && context instanceof PsiExpression contextErpr) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createAddTypeCastFix(throwable, contextErpr)
                );
            }

            PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
            if (aClass != null) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createExtendsListFix(aClass, throwable, true)
                );
            }
            return highlightInfo;
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkMustBeThrowable(@Nullable PsiClass aClass, @Nonnull PsiElement context) {
        if (aClass == null) {
            return null;
        }
        PsiClassType type = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
        return checkMustBeThrowable(type, context, false);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkLabelDefined(@Nullable PsiIdentifier labelIdentifier, @Nullable PsiStatement exitedStatement) {
        if (labelIdentifier == null) {
            return null;
        }
        String label = labelIdentifier.getText();
        if (label == null) {
            return null;
        }
        if (exitedStatement == null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(labelIdentifier)
                .descriptionAndTooltip(JavaErrorLocalize.unresolvedLabel(label))
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkReference(
        @Nonnull PsiJavaCodeReferenceElement ref,
        @Nonnull JavaResolveResult result,
        @Nonnull PsiFile containingFile,
        @Nonnull LanguageLevel languageLevel
    ) {
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
        if (refParent instanceof PsiReferenceExpression && refParent.getParent() instanceof PsiMethodCallExpression methodCall) {
            PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
            PsiExpression qualifierExpression = referenceToMethod.getQualifierExpression();
            if (qualifierExpression == ref && resolved != null && !(resolved instanceof PsiClass) && !(resolved instanceof PsiVariable)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                    .range(qualifierExpression)
                    .descriptionAndTooltip(JavaErrorBundle.message("qualifier.must.be.expression"))
                    .create();
            }
        }
        else if (refParent instanceof PsiMethodCallExpression) {
            return null;  // methods checked elsewhere
        }

        if (resolved == null) {
            // do not highlight unknown packages (javac does not care), Javadoc, and module references (checked elsewhere)
            PsiElement outerParent = getOuterReferenceParent(ref);
            if (outerParent instanceof PsiPackageStatement
                || result.isPackagePrefixPackageReference()
                || PsiUtil.isInsideJavadocComment(ref)
                || outerParent instanceof PsiPackageAccessibilityStatement) {
                return null;
            }

            JavaResolveResult[] results = ref.multiResolve(true);
            LocalizeValue description;
            if (results.length > 1) {
                String t1 = format(ObjectUtil.notNull(results[0].getElement()));
                String t2 = format(ObjectUtil.notNull(results[1].getElement()));
                description = JavaErrorLocalize.ambiguousReference(refName.getText(), t1, t2);
            }
            else {
                description = JavaErrorLocalize.cannotResolveSymbol(refName.getText());
            }

            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                .range(refName)
                .descriptionAndTooltip(description)
                .createUnconditionally();
            UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, QuickFixActionRegistrar.create(info));
            return info;
        }

        if (!result.isValidResult() && !PsiUtil.isInsideJavadocComment(ref)) {
            if (!result.isAccessible()) {
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                    .range(refName)
                    .descriptionAndTooltip(buildProblemWithAccessDescription(ref, result, resolved))
                    .create();
                if (result.isStaticsScopeCorrect()) {
                    registerAccessQuickFixAction((PsiMember)resolved, ref, info, result.getCurrentFileResolveScope());
                    if (ref instanceof PsiReferenceExpression refExpr) {
                        QuickFixAction.registerQuickFixAction(
                            info,
                            QuickFixFactory.getInstance().createRenameWrongRefFix(refExpr)
                        );
                    }
                }
                if (info != null) {
                    UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, QuickFixActionRegistrar.create(info));
                }
                return info;
            }

            if (!result.isStaticsScopeCorrect()) {
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                    .range(refName)
                    .descriptionAndTooltip(buildProblemWithStaticDescription(resolved))
                    .create();
                registerStaticProblemQuickFixAction(resolved, info, ref);
                if (ref instanceof PsiReferenceExpression refExpr) {
                    QuickFixAction.registerQuickFixAction(
                        info,
                        QuickFixFactory.getInstance().createRenameWrongRefFix(refExpr)
                    );
                }
                return info;
            }
        }

        if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
            return HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable)resolved, ref, languageLevel);
        }
        if (resolved instanceof PsiClass resolvedClass && resolvedClass.getContainingClass() == null
            && PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null
            && PsiUtil.isFromDefaultPackage(resolvedClass)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                .range(refName)
                .descriptionAndTooltip(JavaErrorLocalize.cannotResolveSymbol(refName.getText()))
                .create();
        }

        return null;
    }

    @Nonnull
    private static String format(@Nonnull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            return formatClass(psiClass);
        }
        if (element instanceof PsiMethod method) {
            return JavaHighlightUtil.formatMethod(method);
        }
        if (element instanceof PsiField field) {
            return formatField(field);
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
    @RequiredReadAction
    public static HighlightInfo checkPackageAndClassConflict(@Nonnull PsiJavaCodeReferenceElement ref, @Nonnull PsiFile containingFile) {
        if (ref.isQualified() && getOuterReferenceParent(ref) instanceof PsiPackageStatement) {
            VirtualFile file = containingFile.getVirtualFile();
            if (file != null) {
                Module module = ProjectFileIndex.SERVICE.getInstance(ref.getProject()).getModuleForFile(file);
                if (module != null) {
                    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
                    PsiClass aClass = JavaPsiFacade.getInstance(ref.getProject()).findClass(ref.getCanonicalText(), scope);
                    if (aClass != null) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(ref)
                            .descriptionAndTooltip(JavaErrorLocalize.packageClashesWithClass(ref.getText()))
                            .create();
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkElementInReferenceList(
        @Nonnull PsiJavaCodeReferenceElement ref,
        @Nonnull PsiReferenceList referenceList,
        @Nonnull JavaResolveResult resolveResult
    ) {
        PsiElement resolved = resolveResult.getElement();
        HighlightInfo highlightInfo = null;
        PsiElement refGrandParent = referenceList.getParent();
        if (resolved instanceof PsiClass aClass) {
            if (refGrandParent instanceof PsiClass parentClass) {
                if (refGrandParent instanceof PsiTypeParameter typeParameter) {
                    highlightInfo =
                        GenericsHighlightUtil.checkElementInTypeParameterExtendsList(referenceList, typeParameter, resolveResult, ref);
                }
                else if (referenceList.equals(parentClass.getImplementsList()) ||
                    referenceList.equals(parentClass.getExtendsList())) {
                    highlightInfo = HighlightClassUtil.checkExtendsClassAndImplementsInterface(referenceList, resolveResult, ref);
                    if (highlightInfo == null) {
                        highlightInfo = HighlightClassUtil.checkCannotInheritFromFinal(aClass, ref);
                    }
                    if (highlightInfo == null) {
                        // TODO highlightInfo = HighlightClassUtil.checkExtendsProhibitedClass(aClass, parentClass, ref);
                    }
                    if (highlightInfo == null) {
                        highlightInfo = GenericsHighlightUtil.checkCannotInheritFromTypeParameter(aClass, ref);
                    }
                    if (highlightInfo == null) {
                        // TODO highlightInfo = HighlightClassUtil.checkExtendsSealedClass(parentClass, aClass, ref);
                    }
                }
            }
            else if (refGrandParent instanceof PsiMethod method && method.getThrowsList() == referenceList) {
                highlightInfo = checkMustBeThrowable(aClass, ref);
            }
        }
        else if (refGrandParent instanceof PsiMethod method && referenceList == method.getThrowsList()) {
            highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(ref)
                .descriptionAndTooltip(JavaErrorLocalize.classNameExpected())
                .create();
        }
        return highlightInfo;
    }

    public static boolean isSerializationImplicitlyUsedField(@Nonnull PsiField field) {
        String name = field.getName();
        if (!SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !SERIAL_PERSISTENT_FIELDS_FIELD_NAME.equals(name)) {
            return false;
        }
        if (!field.isStatic()) {
            return false;
        }
        PsiClass aClass = field.getContainingClass();
        return aClass == null || JavaHighlightUtil.isSerializable(aClass);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkClassReferenceAfterQualifier(@Nonnull PsiReferenceExpression expression, PsiElement resolved) {
        if (!(resolved instanceof PsiClass resolvedClass)) {
            return null;
        }
        PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier == null) {
            return null;
        }
        if (qualifier instanceof PsiReferenceExpression qRefExpr) {
            PsiElement qualifierResolved = qRefExpr.resolve();
            if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) {
                return null;
            }
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(qualifier)
            .descriptionAndTooltip(JavaErrorBundle.message("expected.class.or.package"))
            .registerFix(
                QuickFixFactory.getInstance().createRemoveQualifierFix(qualifier, expression, resolvedClass),
                null,
                null,
                null,
                null
            )
            .create();
    }

    public static void registerChangeVariableTypeFixes(
        @Nonnull PsiVariable parameter,
        PsiType itemType,
        @Nullable PsiExpression expr,
        @Nonnull HighlightInfo highlightInfo
    ) {
        for (IntentionAction action : getChangeVariableTypeFixes(parameter, itemType)) {
            QuickFixAction.registerQuickFixAction(highlightInfo, action);
        }
        if (expr instanceof PsiMethodCallExpression methodCall) {
            PsiMethod method = methodCall.resolveMethod();
            if (method != null) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    PriorityActionWrapper.lowPriority(
                        method,
                        QuickFixFactory.getInstance().createMethodReturnFix(
                            method,
                            parameter.getType(),
                            true
                        )
                    )
                );
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
    @RequiredReadAction
    public static HighlightInfo checkAnnotationMethodParameters(@Nonnull PsiParameterList list) {
        PsiElement parent = list.getParent();
        if (PsiUtil.isAnnotationMethod(parent) && list.getParametersCount() > 0) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(list)
                .descriptionAndTooltip(JavaErrorLocalize.annotationInterfaceMembersMayNotHaveParameters())
                .registerFix(QuickFixFactory.getInstance().createRemoveParameterListFix((PsiMethod)parent), null, null, null, null)
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkForStatement(@Nonnull PsiForStatement statement) {
        PsiStatement init = statement.getInitialization();
        if (init == null || init instanceof PsiEmptyStatement
            || init instanceof PsiDeclarationStatement declaration && ArrayUtil.getFirstElement(declaration.getDeclaredElements())
            instanceof PsiLocalVariable || init instanceof PsiExpressionStatement || init instanceof PsiExpressionListStatement) {
            return null;
        }

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(init)
            .descriptionAndTooltip(JavaErrorLocalize.invalidStatement())
            .create();
    }

    private static void registerChangeParameterClassFix(PsiType lType, PsiType rType, HighlightInfo info) {
        QuickFixAction.registerQuickFixAction(info, getChangeParameterClassFix(lType, rType));
    }

    @Nullable
    private static IntentionAction getChangeParameterClassFix(PsiType lType, PsiType rType) {
        PsiClass lClass = PsiUtil.resolveClassInClassTypeOnly(lType);
        PsiClass rClass = PsiUtil.resolveClassInClassTypeOnly(rType);

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

        return QuickFixFactory.getInstance().createChangeParameterClassFix(rClass, (PsiClassType)lType);
    }

    private static void registerReplaceInaccessibleFieldWithGetterSetterFix(
        PsiMember refElement,
        PsiJavaCodeReferenceElement place,
        PsiClass accessObjectClass,
        HighlightInfo error
    ) {
        if (refElement instanceof PsiField field && place instanceof PsiReferenceExpression placeRefExpr) {
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                if (PsiUtil.isOnAssignmentLeftHand(placeRefExpr)) {
                    PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
                    PsiMethod setter = containingClass.findMethodBySignature(setterPrototype, true);
                    if (setter != null && PsiUtil.isAccessible(setter, placeRefExpr, accessObjectClass)) {
                        PsiElement element = PsiTreeUtil.skipParentsOfType(placeRefExpr, PsiParenthesizedExpression.class);
                        if (element instanceof PsiAssignmentExpression assignment && assignment.getOperationTokenType() == JavaTokenType.EQ) {
                            QuickFixAction.registerQuickFixAction(
                                error,
                                QuickFixFactory.getInstance().createReplaceInaccessibleFieldWithGetterSetterFix(
                                    placeRefExpr,
                                    setter,
                                    true
                                )
                            );
                        }
                    }
                }
                else if (PsiUtil.isAccessedForReading(placeRefExpr)) {
                    PsiMethod getterPrototype = PropertyUtil.generateGetterPrototype(field);
                    PsiMethod getter = containingClass.findMethodBySignature(getterPrototype, true);
                    if (getter != null && PsiUtil.isAccessible(getter, placeRefExpr, accessObjectClass)) {
                        QuickFixAction.registerQuickFixAction(
                            error,
                            QuickFixFactory.getInstance().createReplaceInaccessibleFieldWithGetterSetterFix(placeRefExpr, getter, false)
                        );
                    }
                }
            }
        }
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkFeature(
        @Nonnull PsiElement element,
        @Nonnull JavaFeature feature,
        @Nonnull LanguageLevel level,
        @Nonnull PsiFile file
    ) {
        if (file.getManager().isInProject(file) && !level.isAtLeast(feature.getMinimumLevel())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(element)
                .descriptionAndTooltip(getUnsupportedFeatureMessage(element, feature, level, file))
                .registerFix(
                    QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(feature.getMinimumLevel()),
                    null,
                    null,
                    null,
                    null
                )
                .registerFix(QuickFixFactory.getInstance().createShowModulePropertiesFix(element), null, null, null, null)
                .create();
        }

        return null;
    }

    @RequiredReadAction
    private static LocalizeValue getUnsupportedFeatureMessage(PsiElement element, JavaFeature feature, LanguageLevel level, PsiFile file) {
        String name = feature.getFeatureName();
        LocalizeValue message = JavaErrorLocalize.insufficientLanguageLevel(name, level.getCompilerComplianceDefaultOption());

        Module module = element.getModule();
        if (module != null) {
            LanguageLevel moduleLanguageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
            if (moduleLanguageLevel.isAtLeast(feature.getMinimumLevel())) {
                for (FilePropertyPusher pusher : FilePropertyPusher.EP_NAME.getExtensionList()) {
                    if (pusher instanceof JavaLanguageLevelPusher languageLevelPusher) {
                        LocalizeValue newMessage = languageLevelPusher.getInconsistencyLanguageLevelMessage(message, element, level, file);
                        if (newMessage != LocalizeValue.empty()) {
                            return newMessage;
                        }
                    }
                }
            }
        }

        return message;
    }
}