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
import consulo.module.Module;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;

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
  public static QuickFixFactory getInstance() {
    return ServiceManager.getService(QuickFixFactory.class);
  }

  @Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@Nonnull PsiModifierList modifierList,
                                                                                    @PsiModifier.ModifierConstant @jakarta.annotation.Nonnull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@jakarta.annotation.Nonnull PsiModifierListOwner owner,
                                                                                    @PsiModifier.ModifierConstant @Nonnull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@Nonnull PsiMethod method, @jakarta.annotation.Nonnull PsiType toReturn, boolean fixWholeHierarchy);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@jakarta.annotation.Nonnull PsiMethod method, @jakarta.annotation.Nonnull PsiClass toClass);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@Nonnull String methodText, @jakarta.annotation.Nonnull PsiClass toClass, @jakarta.annotation.Nonnull String... exceptions);

  /**
   * @param psiElement psiClass or enum constant without class initializer
   */
  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@jakarta.annotation.Nonnull PsiElement psiElement);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(PsiAssignmentExpression expr);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@jakarta.annotation.Nonnull PsiClass psiElement);

  @Nonnull
  public abstract LocalQuickFixOnPsiElement createMethodThrowsFix(@jakarta.annotation.Nonnull PsiMethod method, @jakarta.annotation.Nonnull PsiClassType exceptionClass, boolean shouldThrow, boolean showContainingClass);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@Nonnull PsiClass aClass);

  @Nullable
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@jakarta.annotation.Nonnull PsiClass aClass, @PsiModifier.ModifierConstant @Nonnull String modifier);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@jakarta.annotation.Nonnull PsiMethod method, int index, @Nonnull PsiType newType, boolean fixWholeHierarchy);

  @Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@jakarta.annotation.Nonnull PsiClass aClass);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@jakarta.annotation.Nonnull PsiClass aClass, final boolean makeInterface);

  @Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@jakarta.annotation.Nonnull PsiClass aClass, @jakarta.annotation.Nonnull PsiClassType typeToExtendFrom, boolean toAdd);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@jakarta.annotation.Nonnull PsiParameter parameter);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createRemoveUnusedVariableFix(@jakarta.annotation.Nonnull PsiVariable variable);

  @jakarta.annotation.Nullable
  public abstract IntentionAction createCreateClassOrPackageFix(@Nonnull PsiElement context, @jakarta.annotation.Nonnull String qualifiedName, final boolean createClass, final String superClass);

  @Nullable
  public abstract IntentionAction createCreateClassOrInterfaceFix(@Nonnull PsiElement context, @jakarta.annotation.Nonnull String qualifiedName, final boolean createClass, final String superClass);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateFieldOrPropertyFix(@jakarta.annotation.Nonnull PsiClass aClass,
                                                                 @Nonnull String name,
                                                                 @jakarta.annotation.Nonnull PsiType type,
                                                                 @jakarta.annotation.Nonnull PropertyMemberType targetMember,
                                                                 @jakarta.annotation.Nonnull PsiAnnotation... annotations);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createSetupJDKFix();

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createAddExceptionToCatchFix();

  @Nonnull
  public abstract IntentionAction createAddExceptionToThrowsFix(@jakarta.annotation.Nonnull PsiElement element);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@jakarta.annotation.Nonnull PsiElement element);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createSurroundWithTryCatchFix(@jakarta.annotation.Nonnull PsiElement element);

  @Nonnull
  public abstract IntentionAction createGeneralizeCatchFix(@Nonnull PsiElement element, @Nonnull PsiClassType type);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createChangeToAppendFix(@jakarta.annotation.Nonnull IElementType sign, @jakarta.annotation.Nonnull PsiType type, @jakarta.annotation.Nonnull PsiAssignmentExpression assignment);

  @Nonnull
  public abstract IntentionAction createAddTypeCastFix(@Nonnull PsiType type, @Nonnull PsiExpression expression);

  @Nonnull
  public abstract IntentionAction createWrapExpressionFix(@Nonnull PsiType type, @jakarta.annotation.Nonnull PsiExpression expression);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createReuseVariableDeclarationFix(@jakarta.annotation.Nonnull PsiLocalVariable variable);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createConvertToStringLiteralAction();

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createDeleteCatchFix(@Nonnull PsiParameter parameter);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createDeleteMultiCatchFix(@Nonnull PsiTypeElement element);

  @Nonnull
  public abstract IntentionAction createConvertSwitchToIfIntention(@jakarta.annotation.Nonnull PsiSwitchStatement statement);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createNegationBroadScopeFix(@Nonnull PsiPrefixExpression expr);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateFieldFromUsageFix(@jakarta.annotation.Nonnull PsiReferenceExpression place);

  @Nonnull
  public abstract IntentionAction createReplaceWithListAccessFix(@jakarta.annotation.Nonnull PsiArrayAccessExpression expression);

  @Nonnull
  public abstract IntentionAction createAddNewArrayExpressionFix(@jakarta.annotation.Nonnull PsiArrayInitializerExpression expression);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createMoveCatchUpFix(@Nonnull PsiCatchSection section, @jakarta.annotation.Nonnull PsiCatchSection section1);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createRenameWrongRefFix(@jakarta.annotation.Nonnull PsiReferenceExpression ref);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createRemoveQualifierFix(@jakarta.annotation.Nonnull PsiExpression qualifier, @Nonnull PsiReferenceExpression expression, @jakarta.annotation.Nonnull PsiClass resolved);

  @Nonnull
  public abstract IntentionAction createRemoveParameterListFix(@jakarta.annotation.Nonnull PsiMethod parent);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createShowModulePropertiesFix(@jakarta.annotation.Nonnull PsiElement element);

  @Nonnull
  public abstract IntentionAction createShowModulePropertiesFix(@Nonnull Module module);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createIncreaseLanguageLevelFix(@Nonnull LanguageLevel level);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createChangeParameterClassFix(@jakarta.annotation.Nonnull PsiClass aClass, @jakarta.annotation.Nonnull PsiClassType type);

  @Nonnull
  public abstract IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@jakarta.annotation.Nonnull PsiElement element, @jakarta.annotation.Nonnull PsiMethod getter, boolean isSetter);

  @Nonnull
  public abstract IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createImplementAbstractClassMethodsFix(@jakarta.annotation.Nonnull PsiElement elementToHighlight);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createMoveClassToSeparateFileFix(@jakarta.annotation.Nonnull PsiClass aClass);

  @Nonnull
  public abstract IntentionAction createRenameFileFix(@jakarta.annotation.Nonnull String newName);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@Nonnull PsiNamedElement element);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@Nonnull PsiNamedElement element, @Nonnull String newName);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createChangeExtendsToImplementsFix(@Nonnull PsiClass aClass, @jakarta.annotation.Nonnull PsiClassType classToExtendFrom);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateConstructorMatchingSuperFix(@Nonnull PsiClass aClass);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createRemoveNewQualifierFix(@jakarta.annotation.Nonnull PsiNewExpression expression, @Nullable PsiClass aClass);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createSuperMethodReturnFix(@jakarta.annotation.Nonnull PsiMethod superMethod, @jakarta.annotation.Nonnull PsiType superMethodType);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createInsertNewFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call, @jakarta.annotation.Nonnull PsiClass aClass);

  @Nonnull
  public abstract IntentionAction createAddMethodBodyFix(@Nonnull PsiMethod method);

  @Nonnull
  public abstract IntentionAction createDeleteMethodBodyFix(@jakarta.annotation.Nonnull PsiMethod method);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createInsertSuperFix(@jakarta.annotation.Nonnull PsiMethod constructor);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createInsertThisFix(@jakarta.annotation.Nonnull PsiMethod constructor);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createChangeMethodSignatureFromUsageFix(@jakarta.annotation.Nonnull PsiMethod targetMethod,
                                                                          @Nonnull PsiExpression[] expressions,
                                                                          @jakarta.annotation.Nonnull PsiSubstitutor substitutor,
                                                                          @jakarta.annotation.Nonnull PsiElement context,
                                                                          boolean changeAllUsages,
                                                                          int minUsagesNumberToShowDialog);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@jakarta.annotation.Nonnull PsiMethod targetMethod,
                                                                                      @jakarta.annotation.Nonnull PsiExpression[] expressions,
                                                                                      @jakarta.annotation.Nonnull PsiSubstitutor substitutor,
                                                                                      @jakarta.annotation.Nonnull PsiElement context,
                                                                                      boolean changeAllUsages,
                                                                                      int minUsagesNumberToShowDialog);

  @Nonnull
  public abstract IntentionAction createCreateMethodFromUsageFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateMethodFromUsageFix(PsiMethodReferenceExpression methodReferenceExpression);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateAbstractMethodFromUsageFix(@Nonnull PsiMethodCallExpression call);

  @Nonnull
  public abstract IntentionAction createCreatePropertyFromUsageFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call);

  @Nonnull
  public abstract IntentionAction createCreateConstructorFromSuperFix(@Nonnull PsiMethodCallExpression call);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateConstructorFromThisFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateGetterSetterPropertyFromUsageFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call);

  @Nonnull
  public abstract IntentionAction createStaticImportMethodFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call);

  @Nonnull
  public abstract IntentionAction createReplaceAddAllArrayToCollectionFix(@Nonnull PsiMethodCallExpression call);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateConstructorFromCallFix(@jakarta.annotation.Nonnull PsiConstructorCall call);

  @jakarta.annotation.Nonnull
  public abstract List<IntentionAction> getVariableTypeFromCallFixes(@jakarta.annotation.Nonnull PsiMethodCallExpression call, @Nonnull PsiExpressionList list);

  @Nonnull
  public abstract IntentionAction createAddReturnFix(@jakarta.annotation.Nonnull PsiMethod method);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createAddVariableInitializerFix(@Nonnull PsiVariable variable);

  @Nonnull
  public abstract IntentionAction createDeferFinalAssignmentFix(@jakarta.annotation.Nonnull PsiVariable variable, @Nonnull PsiReferenceExpression expression);

  @Nonnull
  public abstract IntentionAction createVariableAccessFromInnerClassFix(@jakarta.annotation.Nonnull PsiVariable variable, @Nonnull PsiElement scope);

  @Nonnull
  public abstract IntentionAction createCreateConstructorParameterFromFieldFix(@Nonnull PsiField field);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createInitializeFinalFieldInConstructorFix(@jakarta.annotation.Nonnull PsiField field);

  @Nonnull
  public abstract IntentionAction createRemoveTypeArgumentsFix(@Nonnull PsiElement variable);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createChangeClassSignatureFromUsageFix(@jakarta.annotation.Nonnull PsiClass owner, @jakarta.annotation.Nonnull PsiReferenceParameterList parameterList);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createReplacePrimitiveWithBoxedTypeAction(@jakarta.annotation.Nonnull PsiTypeElement element, @jakarta.annotation.Nonnull String typeName, @Nonnull String boxedTypeName);

  @Nonnull
  public abstract IntentionAction createMakeVarargParameterLastFix(@Nonnull PsiParameter parameter);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createMoveBoundClassToFrontFix(@Nonnull PsiClass aClass, @jakarta.annotation.Nonnull PsiClassType type);

  public abstract void registerPullAsAbstractUpFixes(@Nonnull PsiMethod method, @Nonnull QuickFixActionRegistrar registrar);

  @Nonnull
  public abstract IntentionAction createCreateAnnotationMethodFromUsageFix(@Nonnull PsiNameValuePair pair);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createOptimizeImportsFix(boolean onTheFly);

  public abstract void registerFixesForUnusedParameter(@jakarta.annotation.Nonnull PsiParameter parameter, @Nonnull Object highlightInfo);

  @Nonnull
  public abstract IntentionAction createAddToDependencyInjectionAnnotationsFix(@jakarta.annotation.Nonnull Project project, @Nonnull String qualifiedName, @Nonnull String element);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createAddToImplicitlyWrittenFieldsFix(Project project, @Nonnull String qualifiedName);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @jakarta.annotation.Nonnull PsiField field);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createRenameToIgnoredFix(@jakarta.annotation.Nonnull PsiNamedElement namedElement);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createEnableOptimizeImportsOnTheFlyFix();

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@jakarta.annotation.Nonnull PsiElement element);

  @jakarta.annotation.Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@jakarta.annotation.Nonnull PsiElement element, @jakarta.annotation.Nonnull @Nls String text);

  @Nonnull
  public abstract IntentionAction createDeleteSideEffectAwareFix(@jakarta.annotation.Nonnull PsiExpressionStatement statement);

  @Nonnull
  public abstract IntentionAction createSafeDeleteFix(@jakarta.annotation.Nonnull PsiElement element);

  @jakarta.annotation.Nullable
  public abstract List<LocalQuickFix> registerOrderEntryFixes(@jakarta.annotation.Nonnull PsiReference reference);

  @Nonnull
  public abstract IntentionAction createAddMissingRequiredAnnotationParametersFix(@jakarta.annotation.Nonnull PsiAnnotation annotation,
                                                                                  @jakarta.annotation.Nonnull PsiMethod[] annotationMethods,
                                                                                  @Nonnull Collection<String> missedElements);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@jakarta.annotation.Nonnull PsiAnnotationMemberValue value, @jakarta.annotation.Nonnull PsiType expectedType);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction addMethodQualifierFix(@Nonnull PsiMethodCallExpression methodCall);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createWrapWithAdapterFix(@jakarta.annotation.Nullable PsiType type, @jakarta.annotation.Nonnull PsiExpression expression);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @Nonnull PsiExpression expression);

  @jakarta.annotation.Nullable
  public abstract IntentionAction createNotIterableForEachLoopFix(@jakarta.annotation.Nonnull PsiExpression expression);

  @jakarta.annotation.Nonnull
  public abstract List<IntentionAction> createAddAnnotationAttributeNameFixes(@jakarta.annotation.Nonnull PsiNameValuePair pair);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createCollectionToArrayFix(@jakarta.annotation.Nonnull PsiExpression collectionExpression, @jakarta.annotation.Nonnull PsiArrayType arrayType);

  @Nonnull
  public abstract IntentionAction createInsertMethodCallFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call, PsiMethod method);

  @Nonnull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(PsiReferenceExpression methodRef, JavaResolveResult result);

  @Nonnull
  public abstract IntentionAction createWrapStringWithFileFix(@Nullable PsiType type, @jakarta.annotation.Nonnull PsiExpression expression);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createAddMissingEnumBranchesFix(@Nonnull PsiSwitchBlock switchBlock, @Nonnull Set<String> missingCases);

  @Nonnull
  public abstract IntentionAction createAddSwitchDefaultFix(@Nonnull PsiSwitchBlock switchBlock, @Nullable String message);

  @jakarta.annotation.Nonnull
  public abstract IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement rule);

}