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
package com.intellij.java.analysis.codeInsight.intention;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyMemberType;
import consulo.annotation.DeprecationInfo;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author cdr
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class QuickFixFactory {
    public interface ModifierFixBuilder {
        default ModifierFixBuilder add(@PsiModifier.ModifierConstant String modifier) {
            return toggle(modifier, true);
        }

        default ModifierFixBuilder remove(@PsiModifier.ModifierConstant String modifier) {
            return toggle(modifier, false);
        }

        ModifierFixBuilder toggle(@PsiModifier.ModifierConstant String modifier, boolean shouldHave);

        ModifierFixBuilder showContainingClass();

        @RequiredReadAction
        public abstract LocalQuickFixAndIntentionActionOnPsiElement create();
    }

    public static QuickFixFactory getInstance() {
        return ServiceManager.getService(QuickFixFactory.class);
    }

    public abstract ModifierFixBuilder createModifierFixBuilder(@Nonnull PsiModifierList modifierList);

    public abstract ModifierFixBuilder createModifierFixBuilder(@Nonnull PsiModifierListOwner owner);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(
        @Nonnull PsiMethod method,
        @Nonnull PsiType toReturn,
        boolean fixWholeHierarchy
    );

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@Nonnull PsiMethod method, @Nonnull PsiClass toClass);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(
        @Nonnull String methodText,
        @Nonnull PsiClass toClass,
        @Nonnull String... exceptions
    );

    /**
     * @param psiElement psiClass or enum constant without class initializer
     */
    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@Nonnull PsiElement psiElement);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(PsiAssignmentExpression expr);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@Nonnull PsiClass psiElement);

    @Nonnull
    public abstract LocalQuickFixOnPsiElement createMethodThrowsFix(
        @Nonnull PsiMethod method,
        @Nonnull PsiClassType exceptionClass,
        boolean shouldThrow,
        boolean showContainingClass
    );

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@Nonnull PsiClass aClass);

    @Nullable
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(
        @Nonnull PsiClass aClass,
        @PsiModifier.ModifierConstant @Nonnull String modifier
    );

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(
        @Nonnull PsiMethod method,
        int index,
        @Nonnull PsiType newType,
        boolean fixWholeHierarchy
    );

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@Nonnull PsiClass aClass);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(
        @Nonnull PsiClass aClass,
        final boolean makeInterface
    );

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(
        @Nonnull PsiClass aClass,
        @Nonnull PsiClassType typeToExtendFrom,
        boolean toAdd
    );

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@Nonnull PsiParameter parameter);

    @Nonnull
    public abstract IntentionAction createRemoveUnusedVariableFix(@Nonnull PsiVariable variable);

    @Nullable
    public abstract IntentionAction createCreateClassOrPackageFix(
        @Nonnull PsiElement context,
        @Nonnull String qualifiedName,
        final boolean createClass,
        final String superClass
    );

    @Nullable
    public abstract IntentionAction createCreateClassOrInterfaceFix(
        @Nonnull PsiElement context,
        @Nonnull String qualifiedName,
        final boolean createClass,
        final String superClass
    );

    @Nonnull
    public abstract IntentionAction createCreateFieldOrPropertyFix(
        @Nonnull PsiClass aClass,
        @Nonnull String name,
        @Nonnull PsiType type,
        @Nonnull PropertyMemberType targetMember,
        @Nonnull PsiAnnotation... annotations
    );

    @Nonnull
    public abstract IntentionAction createSetupJDKFix();

    @Nonnull
    public abstract IntentionAction createAddExceptionToCatchFix();

    @Nonnull
    public abstract IntentionAction createAddExceptionToThrowsFix(@Nonnull PsiElement element);

    @Nonnull
    public abstract IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@Nonnull PsiElement element);

    @Nonnull
    public abstract IntentionAction createSurroundWithTryCatchFix(@Nonnull PsiElement element);

    @Nonnull
    public abstract IntentionAction createGeneralizeCatchFix(@Nonnull PsiElement element, @Nonnull PsiClassType type);

    @Nonnull
    public abstract IntentionAction createChangeToAppendFix(
        @Nonnull IElementType sign,
        @Nonnull PsiType type,
        @Nonnull PsiAssignmentExpression assignment
    );

    @Nonnull
    public abstract IntentionAction createAddTypeCastFix(@Nonnull PsiType type, @Nonnull PsiExpression expression);

    @Nonnull
    public abstract IntentionAction createWrapExpressionFix(@Nonnull PsiType type, @Nonnull PsiExpression expression);

    @Nonnull
    public abstract IntentionAction createReuseVariableDeclarationFix(@Nonnull PsiLocalVariable variable);

    @Nonnull
    public abstract IntentionAction createConvertToStringLiteralAction();

    @Nonnull
    public abstract IntentionAction createDeleteCatchFix(@Nonnull PsiParameter parameter);

    @Nonnull
    public abstract IntentionAction createDeleteMultiCatchFix(@Nonnull PsiTypeElement element);

    @Nonnull
    public abstract IntentionAction createConvertSwitchToIfIntention(@Nonnull PsiSwitchStatement statement);

    @Nonnull
    public abstract IntentionAction createNegationBroadScopeFix(@Nonnull PsiPrefixExpression expr);

    @Nonnull
    public abstract IntentionAction createCreateFieldFromUsageFix(@Nonnull PsiReferenceExpression place);

    @Nonnull
    public abstract IntentionAction createReplaceWithListAccessFix(@Nonnull PsiArrayAccessExpression expression);

    @Nonnull
    public abstract IntentionAction createAddNewArrayExpressionFix(@Nonnull PsiArrayInitializerExpression expression);

    @Nonnull
    public abstract IntentionAction createMoveCatchUpFix(@Nonnull PsiCatchSection section, @Nonnull PsiCatchSection section1);

    @Nonnull
    public abstract IntentionAction createRenameWrongRefFix(@Nonnull PsiReferenceExpression ref);

    @Nonnull
    public abstract IntentionAction createRemoveQualifierFix(
        @Nonnull PsiExpression qualifier,
        @Nonnull PsiReferenceExpression expression,
        @Nonnull PsiClass resolved
    );

    @Nonnull
    public abstract IntentionAction createRemoveParameterListFix(@Nonnull PsiMethod parent);

    @Nonnull
    public abstract IntentionAction createShowModulePropertiesFix(@Nonnull PsiElement element);

    @Nonnull
    public abstract IntentionAction createShowModulePropertiesFix(@Nonnull Module module);

    @Nonnull
    public abstract IntentionAction createIncreaseLanguageLevelFix(@Nonnull LanguageLevel level);

    @Nonnull
    public abstract IntentionAction createChangeParameterClassFix(@Nonnull PsiClass aClass, @Nonnull PsiClassType type);

    @Nonnull
    public abstract IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(
        @Nonnull PsiElement element,
        @Nonnull PsiMethod getter,
        boolean isSetter
    );

    @Nonnull
    public abstract IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression);

    @Nonnull
    public abstract IntentionAction createImplementAbstractClassMethodsFix(@Nonnull PsiElement elementToHighlight);

    @Nonnull
    public abstract IntentionAction createMoveClassToSeparateFileFix(@Nonnull PsiClass aClass);

    @Nonnull
    public abstract IntentionAction createRenameFileFix(@Nonnull String newName);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@Nonnull PsiNamedElement element);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(
        @Nonnull PsiNamedElement element,
        @Nonnull String newName
    );

    @Nonnull
    public abstract IntentionAction createChangeExtendsToImplementsFix(@Nonnull PsiClass aClass, @Nonnull PsiClassType classToExtendFrom);

    @Nonnull
    public abstract IntentionAction createCreateConstructorMatchingSuperFix(@Nonnull PsiClass aClass);

    @Nonnull
    public abstract IntentionAction createRemoveNewQualifierFix(@Nonnull PsiNewExpression expression, @Nullable PsiClass aClass);

    @Nonnull
    public abstract IntentionAction createSuperMethodReturnFix(@Nonnull PsiMethod superMethod, @Nonnull PsiType superMethodType);

    @Nonnull
    public abstract IntentionAction createInsertNewFix(@Nonnull PsiMethodCallExpression call, @Nonnull PsiClass aClass);

    @Nonnull
    public abstract IntentionAction createAddMethodBodyFix(@Nonnull PsiMethod method);

    @Nonnull
    public abstract IntentionAction createDeleteMethodBodyFix(@Nonnull PsiMethod method);

    @Nonnull
    public abstract IntentionAction createInsertSuperFix(@Nonnull PsiMethod constructor);

    @Nonnull
    public abstract IntentionAction createInsertThisFix(@Nonnull PsiMethod constructor);

    @Nonnull
    public abstract IntentionAction createChangeMethodSignatureFromUsageFix(
        @Nonnull PsiMethod targetMethod,
        @Nonnull PsiExpression[] expressions,
        @Nonnull PsiSubstitutor substitutor,
        @Nonnull PsiElement context,
        boolean changeAllUsages,
        int minUsagesNumberToShowDialog
    );

    @Nonnull
    public abstract IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(
        @Nonnull PsiMethod targetMethod,
        @Nonnull PsiExpression[] expressions,
        @Nonnull PsiSubstitutor substitutor,
        @Nonnull PsiElement context,
        boolean changeAllUsages,
        int minUsagesNumberToShowDialog
    );

    @Nonnull
    public abstract IntentionAction createCreateMethodFromUsageFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createCreateMethodFromUsageFix(PsiMethodReferenceExpression methodReferenceExpression);

    @Nonnull
    public abstract IntentionAction createCreateAbstractMethodFromUsageFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createCreatePropertyFromUsageFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createCreateConstructorFromSuperFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createCreateConstructorFromThisFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createCreateGetterSetterPropertyFromUsageFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createStaticImportMethodFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createReplaceAddAllArrayToCollectionFix(@Nonnull PsiMethodCallExpression call);

    @Nonnull
    public abstract IntentionAction createCreateConstructorFromCallFix(@Nonnull PsiConstructorCall call);

    @Nonnull
    public abstract List<IntentionAction> getVariableTypeFromCallFixes(
        @Nonnull PsiMethodCallExpression call,
        @Nonnull PsiExpressionList list
    );

    @Nonnull
    public abstract IntentionAction createAddReturnFix(@Nonnull PsiMethod method);

    @Nonnull
    public abstract IntentionAction createAddVariableInitializerFix(@Nonnull PsiVariable variable);

    @Nonnull
    public abstract IntentionAction createDeferFinalAssignmentFix(
        @Nonnull PsiVariable variable,
        @Nonnull PsiReferenceExpression expression
    );

    @Nonnull
    public abstract IntentionAction createVariableAccessFromInnerClassFix(@Nonnull PsiVariable variable, @Nonnull PsiElement scope);

    @Nonnull
    public abstract IntentionAction createCreateConstructorParameterFromFieldFix(@Nonnull PsiField field);

    @Nonnull
    public abstract IntentionAction createInitializeFinalFieldInConstructorFix(@Nonnull PsiField field);

    @Nonnull
    public abstract IntentionAction createRemoveTypeArgumentsFix(@Nonnull PsiElement variable);

    @Nonnull
    public abstract IntentionAction createChangeClassSignatureFromUsageFix(
        @Nonnull PsiClass owner,
        @Nonnull PsiReferenceParameterList parameterList
    );

    @Nonnull
    public abstract IntentionAction createReplacePrimitiveWithBoxedTypeAction(
        @Nonnull PsiTypeElement element,
        @Nonnull String typeName,
        @Nonnull String boxedTypeName
    );

    @Nonnull
    public abstract IntentionAction createMakeVarargParameterLastFix(@Nonnull PsiParameter parameter);

    @Nonnull
    public abstract IntentionAction createMoveBoundClassToFrontFix(@Nonnull PsiClass aClass, @Nonnull PsiClassType type);

    public abstract void registerPullAsAbstractUpFixes(@Nonnull PsiMethod method, @Nonnull QuickFixActionRegistrar registrar);

    @Nonnull
    public abstract IntentionAction createCreateAnnotationMethodFromUsageFix(@Nonnull PsiNameValuePair pair);

    @Nonnull
    public abstract IntentionAction createOptimizeImportsFix(boolean onTheFly);

    public abstract void registerFixesForUnusedParameter(@Nonnull PsiParameter parameter, @Nonnull Object highlightInfo);

    @Nonnull
    public abstract IntentionAction createAddToDependencyInjectionAnnotationsFix(
        @Nonnull Project project,
        @Nonnull String qualifiedName,
        @Nonnull String element
    );

    @Nonnull
    public abstract IntentionAction createAddToImplicitlyWrittenFieldsFix(Project project, @Nonnull String qualifiedName);

    @Nonnull
    public abstract IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @Nonnull PsiField field);

    @Nonnull
    public abstract IntentionAction createRenameToIgnoredFix(@Nonnull PsiNamedElement namedElement);

    @Nonnull
    public abstract IntentionAction createEnableOptimizeImportsOnTheFlyFix();

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@Nonnull PsiElement element);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@Nonnull PsiElement element, @Nonnull LocalizeValue text);

    @Nonnull
    public abstract IntentionAction createDeleteSideEffectAwareFix(@Nonnull PsiExpressionStatement statement);

    @Nonnull
    public abstract IntentionAction createSafeDeleteFix(@Nonnull PsiElement element);

    @Nullable
    public abstract List<LocalQuickFix> registerOrderEntryFixes(@Nonnull PsiReference reference);

    @Nonnull
    public abstract IntentionAction createAddMissingRequiredAnnotationParametersFix(
        @Nonnull PsiAnnotation annotation,
        @Nonnull PsiMethod[] annotationMethods,
        @Nonnull Collection<String> missedElements
    );

    @Nonnull
    public abstract IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(
        @Nonnull PsiAnnotationMemberValue value,
        @Nonnull PsiType expectedType
    );

    @Nonnull
    public abstract IntentionAction addMethodQualifierFix(@Nonnull PsiMethodCallExpression methodCall);

    @Nonnull
    public abstract IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, @Nonnull PsiExpression expression);

    @Nonnull
    public abstract IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @Nonnull PsiExpression expression);

    @Nullable
    public abstract IntentionAction createNotIterableForEachLoopFix(@Nonnull PsiExpression expression);

    @Nonnull
    public abstract List<IntentionAction> createAddAnnotationAttributeNameFixes(@Nonnull PsiNameValuePair pair);

    @Nonnull
    public abstract IntentionAction createCollectionToArrayFix(
        @Nonnull PsiExpression collectionExpression,
        @Nonnull PsiArrayType arrayType
    );

    @Nonnull
    public abstract IntentionAction createInsertMethodCallFix(@Nonnull PsiMethodCallExpression call, PsiMethod method);

    @Nonnull
    public abstract LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(
        PsiReferenceExpression methodRef,
        JavaResolveResult result
    );

    @Nonnull
    public abstract IntentionAction createWrapStringWithFileFix(@Nullable PsiType type, @Nonnull PsiExpression expression);

    @Nonnull
    public abstract IntentionAction createAddMissingEnumBranchesFix(@Nonnull PsiSwitchBlock switchBlock, @Nonnull Set<String> missingCases);

    @Nonnull
    public abstract IntentionAction createAddSwitchDefaultFix(@Nonnull PsiSwitchBlock switchBlock, @Nonnull LocalizeValue message);

    @Nonnull
    public abstract IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement rule);

    @Deprecated
    @DeprecationInfo("Use #fixModifiers()...#build()")
    @Nonnull
    @RequiredReadAction
    public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(
        @Nonnull PsiModifierList modifierList,
        @PsiModifier.ModifierConstant @Nonnull String modifier,
        boolean shouldHave,
        boolean showContainingClass
    ) {
        ModifierFixBuilder builder = createModifierFixBuilder(modifierList).toggle(modifier, shouldHave);
        if (showContainingClass) {
            builder.showContainingClass();
        }
        return builder.create();
    }

    @Deprecated
    @DeprecationInfo("Use #fixModifiers()...#build()")
    @Nonnull
    @RequiredReadAction
    public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(
        @Nonnull PsiModifierListOwner owner,
        @PsiModifier.ModifierConstant @Nonnull String modifier,
        boolean shouldHave,
        boolean showContainingClass
    ) {
        ModifierFixBuilder builder = createModifierFixBuilder(owner).toggle(modifier, shouldHave);
        if (showContainingClass) {
            builder.showContainingClass();
        }
        return builder.create();
    }
}