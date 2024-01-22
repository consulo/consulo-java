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
package com.intellij.java.impl.codeInsight.intention.impl.config;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.WrapLongWithMathToIntExactFix;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.*;
import com.intellij.java.analysis.impl.codeInsight.quickfix.SetupJDKFix;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.java.analysis.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.*;
import com.intellij.java.impl.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.java.impl.codeInsight.daemon.quickFix.CreateFieldOrPropertyFix;
import com.intellij.java.impl.codeInsight.intention.impl.ReplaceAssignmentWithComparisonFix;
import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.impl.ig.fixes.CreateDefaultBranchFix;
import com.intellij.java.impl.ig.fixes.CreateMissingSwitchBranchesFix;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ClassKind;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyMemberType;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.daemon.impl.quickfix.RenameElementFix;
import consulo.ide.impl.idea.diagnostic.LogMessageEx;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.editor.AutoImportHelper;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.impl.intention.RenameFileFix;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.*;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.*;
import consulo.language.util.AttachmentFactoryUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.undoRedo.ProjectUndoManager;
import consulo.undoRedo.UndoManager;
import consulo.undoRedo.util.UndoUtil;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.Nls;

import java.util.*;

/**
 * @author cdr
 */
@Singleton
@ServiceImpl
public class QuickFixFactoryImpl extends QuickFixFactory {
  private static final Logger LOG = Logger.getInstance(QuickFixFactoryImpl.class);

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@jakarta.annotation.Nonnull PsiModifierList modifierList,
                                                                           @jakarta.annotation.Nonnull String modifier,
                                                                           boolean shouldHave,
                                                                           boolean showContainingClass) {
    return new ModifierFix(modifierList, modifier, shouldHave, showContainingClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@jakarta.annotation.Nonnull PsiModifierListOwner owner,
                                                                           @jakarta.annotation.Nonnull final String modifier,
                                                                           final boolean shouldHave,
                                                                           final boolean showContainingClass) {
    return new ModifierFix(owner, modifier, shouldHave, showContainingClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@jakarta.annotation.Nonnull PsiMethod method,
                                                                           @jakarta.annotation.Nonnull PsiType toReturn,
                                                                           boolean fixWholeHierarchy) {
    return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@jakarta.annotation.Nonnull PsiMethod method, @jakarta.annotation.Nonnull PsiClass toClass) {
    return new AddMethodFix(method, toClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@jakarta.annotation.Nonnull String methodText,
                                                                        @jakarta.annotation.Nonnull PsiClass toClass,
                                                                        @Nonnull String... exceptions) {
    return new AddMethodFix(methodText, toClass, exceptions);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@jakarta.annotation.Nonnull PsiClass aClass) {
    return new ImplementMethodsFix(aClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@jakarta.annotation.Nonnull PsiElement psiElement) {
    return new ImplementMethodsFix(psiElement);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(PsiAssignmentExpression expr) {
    return new ReplaceAssignmentWithComparisonFix(expr);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixOnPsiElement createMethodThrowsFix(@jakarta.annotation.Nonnull PsiMethod method,
                                                         @jakarta.annotation.Nonnull PsiClassType exceptionClass,
                                                         boolean shouldThrow,
                                                         boolean showContainingClass) {
    return new MethodThrowsFix(method, exceptionClass, shouldThrow, showContainingClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@Nonnull PsiClass aClass) {
    return new AddDefaultConstructorFix(aClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@jakarta.annotation.Nonnull PsiClass aClass, @jakarta.annotation.Nonnull String modifier) {
    return aClass.getName() != null ? new AddDefaultConstructorFix(aClass, modifier) : null;
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@jakarta.annotation.Nonnull PsiMethod method,
                                                                                  int index,
                                                                                  @jakarta.annotation.Nonnull PsiType newType,
                                                                                  boolean fixWholeHierarchy) {
    return new MethodParameterFix(method, newType, index, fixWholeHierarchy);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@jakarta.annotation.Nonnull PsiClass aClass) {
    return new MakeClassInterfaceFix(aClass, true);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@jakarta.annotation.Nonnull PsiClass aClass, final boolean makeInterface) {
    return new MakeClassInterfaceFix(aClass, makeInterface);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@jakarta.annotation.Nonnull PsiClass aClass,
                                                                          @jakarta.annotation.Nonnull PsiClassType typeToExtendFrom,
                                                                          boolean toAdd) {
    return new ExtendsListFix(aClass, typeToExtendFrom, toAdd);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@jakarta.annotation.Nonnull PsiParameter parameter) {
    return new RemoveUnusedParameterFix(parameter);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createRemoveUnusedVariableFix(@jakarta.annotation.Nonnull PsiVariable variable) {
    return new RemoveUnusedVariableFix(variable);
  }

  @Override
  @jakarta.annotation.Nullable
  public IntentionAction createCreateClassOrPackageFix(@jakarta.annotation.Nonnull final PsiElement context,
                                                       @jakarta.annotation.Nonnull final String qualifiedName,
                                                       final boolean createClass,
                                                       final String superClass) {
    return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : null, superClass);
  }

  @Override
  @jakarta.annotation.Nullable
  public IntentionAction createCreateClassOrInterfaceFix(@jakarta.annotation.Nonnull final PsiElement context,
                                                         @jakarta.annotation.Nonnull final String qualifiedName,
                                                         final boolean createClass,
                                                         final String superClass) {
    return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : ClassKind.INTERFACE, superClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateFieldOrPropertyFix(@Nonnull final PsiClass aClass,
                                                        @jakarta.annotation.Nonnull final String name,
                                                        @jakarta.annotation.Nonnull final PsiType type,
                                                        @jakarta.annotation.Nonnull final PropertyMemberType targetMember,
                                                        @jakarta.annotation.Nonnull final PsiAnnotation... annotations) {
    return new CreateFieldOrPropertyFix(aClass, name, type, targetMember, annotations);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createSetupJDKFix() {
    return SetupJDKFix.getInstance();
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddExceptionToCatchFix() {
    return new AddExceptionToCatchFix();
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddExceptionToThrowsFix(@jakarta.annotation.Nonnull PsiElement element) {
    return new AddExceptionToThrowsFix(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@jakarta.annotation.Nonnull PsiElement element) {
    return new AddExceptionFromFieldInitializerToConstructorThrowsFix(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createSurroundWithTryCatchFix(@jakarta.annotation.Nonnull PsiElement element) {
    return new SurroundWithTryCatchFix(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createGeneralizeCatchFix(@jakarta.annotation.Nonnull PsiElement element, @jakarta.annotation.Nonnull PsiClassType type) {
    return new GeneralizeCatchFix(element, type);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createChangeToAppendFix(@jakarta.annotation.Nonnull IElementType sign,
                                                 @jakarta.annotation.Nonnull PsiType type,
                                                 @jakarta.annotation.Nonnull PsiAssignmentExpression assignment) {
    return new ChangeToAppendFix(sign, type, assignment);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddTypeCastFix(@jakarta.annotation.Nonnull PsiType type, @jakarta.annotation.Nonnull PsiExpression expression) {
    return new AddTypeCastFix(type, expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createWrapExpressionFix(@jakarta.annotation.Nonnull PsiType type, @jakarta.annotation.Nonnull PsiExpression expression) {
    return new WrapExpressionFix(type, expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createReuseVariableDeclarationFix(@jakarta.annotation.Nonnull PsiLocalVariable variable) {
    return new ReuseVariableDeclarationFix(variable);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createConvertToStringLiteralAction() {
    return new ConvertToStringLiteralAction();
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createDeleteCatchFix(@jakarta.annotation.Nonnull PsiParameter parameter) {
    return new DeleteCatchFix(parameter);
  }

  @Nonnull
  @Override
  public IntentionAction createDeleteMultiCatchFix(@jakarta.annotation.Nonnull PsiTypeElement element) {
    return new DeleteMultiCatchFix(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createConvertSwitchToIfIntention(@jakarta.annotation.Nonnull PsiSwitchStatement statement) {
    return new ConvertSwitchToIfIntention(statement);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createNegationBroadScopeFix(@jakarta.annotation.Nonnull PsiPrefixExpression expr) {
    return new NegationBroadScopeFix(expr);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateFieldFromUsageFix(@jakarta.annotation.Nonnull PsiReferenceExpression place) {
    return new CreateFieldFromUsageFix(place);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createReplaceWithListAccessFix(@jakarta.annotation.Nonnull PsiArrayAccessExpression expression) {
    return new ReplaceWithListAccessFix(expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddNewArrayExpressionFix(@jakarta.annotation.Nonnull PsiArrayInitializerExpression expression) {
    return new AddNewArrayExpressionFix(expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createMoveCatchUpFix(@jakarta.annotation.Nonnull PsiCatchSection section, @jakarta.annotation.Nonnull PsiCatchSection section1) {
    return new MoveCatchUpFix(section, section1);
  }

  @Nonnull
  @Override
  public IntentionAction createRenameWrongRefFix(@jakarta.annotation.Nonnull PsiReferenceExpression ref) {
    return new RenameWrongRefFix(ref);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createRemoveQualifierFix(@jakarta.annotation.Nonnull PsiExpression qualifier,
                                                  @jakarta.annotation.Nonnull PsiReferenceExpression expression,
                                                  @jakarta.annotation.Nonnull PsiClass resolved) {
    return new RemoveQualifierFix(qualifier, expression, resolved);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createRemoveParameterListFix(@jakarta.annotation.Nonnull PsiMethod parent) {
    return new RemoveParameterListFix(parent);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createShowModulePropertiesFix(@jakarta.annotation.Nonnull PsiElement element) {
    return new ShowModulePropertiesFix(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createShowModulePropertiesFix(@jakarta.annotation.Nonnull Module module) {
    return new ShowModulePropertiesFix(module);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createIncreaseLanguageLevelFix(@jakarta.annotation.Nonnull LanguageLevel level) {
    return new IncreaseLanguageLevelFix(level);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createChangeParameterClassFix(@Nonnull PsiClass aClass, @jakarta.annotation.Nonnull PsiClassType type) {
    return new ChangeParameterClassFix(aClass, type);
  }

  @Nonnull
  @Override
  public IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@jakarta.annotation.Nonnull PsiElement element,
                                                                           @jakarta.annotation.Nonnull PsiMethod getter,
                                                                           boolean isSetter) {
    return new ReplaceInaccessibleFieldWithGetterSetterFix(element, getter, isSetter);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createSurroundWithArrayFix(@jakarta.annotation.Nullable PsiCall methodCall, @jakarta.annotation.Nullable PsiExpression expression) {
    return new SurroundWithArrayFix(methodCall, expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createImplementAbstractClassMethodsFix(@jakarta.annotation.Nonnull PsiElement elementToHighlight) {
    return new ImplementAbstractClassMethodsFix(elementToHighlight);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createMoveClassToSeparateFileFix(@jakarta.annotation.Nonnull PsiClass aClass) {
    return new MoveClassToSeparateFileFix(aClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createRenameFileFix(@jakarta.annotation.Nonnull String newName) {
    return new RenameFileFix(newName);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@jakarta.annotation.Nonnull PsiNamedElement element) {
    return new RenameElementFix(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@jakarta.annotation.Nonnull PsiNamedElement element, @jakarta.annotation.Nonnull String newName) {
    return new RenameElementFix(element, newName);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createChangeExtendsToImplementsFix(@jakarta.annotation.Nonnull PsiClass aClass, @jakarta.annotation.Nonnull PsiClassType classToExtendFrom) {
    return new ChangeExtendsToImplementsFix(aClass, classToExtendFrom);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateConstructorMatchingSuperFix(@jakarta.annotation.Nonnull PsiClass aClass) {
    return new CreateConstructorMatchingSuperFix(aClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createRemoveNewQualifierFix(@jakarta.annotation.Nonnull PsiNewExpression expression, PsiClass aClass) {
    return new RemoveNewQualifierFix(expression, aClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createSuperMethodReturnFix(@Nonnull PsiMethod superMethod, @jakarta.annotation.Nonnull PsiType superMethodType) {
    return new SuperMethodReturnFix(superMethod, superMethodType);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createInsertNewFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call, @jakarta.annotation.Nonnull PsiClass aClass) {
    return new InsertNewFix(call, aClass);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddMethodBodyFix(@jakarta.annotation.Nonnull PsiMethod method) {
    return new AddMethodBodyFix(method);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createDeleteMethodBodyFix(@jakarta.annotation.Nonnull PsiMethod method) {
    return new DeleteMethodBodyFix(method);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createInsertSuperFix(@jakarta.annotation.Nonnull PsiMethod constructor) {
    return new InsertSuperFix(constructor);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createInsertThisFix(@jakarta.annotation.Nonnull PsiMethod constructor) {
    return new InsertThisFix(constructor);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createChangeMethodSignatureFromUsageFix(@Nonnull PsiMethod targetMethod,
                                                                 @jakarta.annotation.Nonnull PsiExpression[] expressions,
                                                                 @jakarta.annotation.Nonnull PsiSubstitutor substitutor,
                                                                 @jakarta.annotation.Nonnull PsiElement context,
                                                                 boolean changeAllUsages,
                                                                 int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageFix(targetMethod,
                                                 expressions,
                                                 substitutor,
                                                 context,
                                                 changeAllUsages,
                                                 minUsagesNumberToShowDialog);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@jakarta.annotation.Nonnull PsiMethod targetMethod,
                                                                             @jakarta.annotation.Nonnull PsiExpression[] expressions,
                                                                             @jakarta.annotation.Nonnull PsiSubstitutor substitutor,
                                                                             @jakarta.annotation.Nonnull PsiElement context,
                                                                             boolean changeAllUsages,
                                                                             int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageReverseOrderFix(targetMethod,
                                                             expressions,
                                                             substitutor,
                                                             context,
                                                             changeAllUsages,
                                                             minUsagesNumberToShowDialog);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateMethodFromUsageFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call) {
    return new CreateMethodFromUsageFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateMethodFromUsageFix(PsiMethodReferenceExpression methodReferenceExpression) {
    return new CreateMethodFromMethodReferenceFix(methodReferenceExpression);
  }

  @Nonnull
  @Override
  public IntentionAction createCreateAbstractMethodFromUsageFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call) {
    return new CreateAbstractMethodFromUsageFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreatePropertyFromUsageFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call) {
    return new CreatePropertyFromUsageFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateConstructorFromSuperFix(@Nonnull PsiMethodCallExpression call) {
    return new CreateConstructorFromSuperFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateConstructorFromThisFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call) {
    return new CreateConstructorFromThisFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateGetterSetterPropertyFromUsageFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call) {
    return new CreateGetterSetterPropertyFromUsageFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createStaticImportMethodFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call) {
    return new StaticImportMethodFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createReplaceAddAllArrayToCollectionFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call) {
    return new ReplaceAddAllArrayToCollectionFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateConstructorFromCallFix(@jakarta.annotation.Nonnull PsiConstructorCall call) {
    return new CreateConstructorFromCallFix(call);
  }

  @jakarta.annotation.Nonnull
  @Override
  public List<IntentionAction> getVariableTypeFromCallFixes(@jakarta.annotation.Nonnull PsiMethodCallExpression call, @jakarta.annotation.Nonnull PsiExpressionList list) {
    return VariableTypeFromCallFix.getQuickFixActions(call, list);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddReturnFix(@jakarta.annotation.Nonnull PsiMethod method) {
    return new AddReturnFix(method);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddVariableInitializerFix(@jakarta.annotation.Nonnull PsiVariable variable) {
    return new AddVariableInitializerFix(variable);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createDeferFinalAssignmentFix(@jakarta.annotation.Nonnull PsiVariable variable, @jakarta.annotation.Nonnull PsiReferenceExpression expression) {
    return new DeferFinalAssignmentFix(variable, expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createVariableAccessFromInnerClassFix(@jakarta.annotation.Nonnull PsiVariable variable, @jakarta.annotation.Nonnull PsiElement scope) {
    return new VariableAccessFromInnerClassFix(variable, scope);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateConstructorParameterFromFieldFix(@jakarta.annotation.Nonnull PsiField field) {
    return new CreateConstructorParameterFromFieldFix(field);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createInitializeFinalFieldInConstructorFix(@jakarta.annotation.Nonnull PsiField field) {
    return new InitializeFinalFieldInConstructorFix(field);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createRemoveTypeArgumentsFix(@jakarta.annotation.Nonnull PsiElement variable) {
    return new RemoveTypeArgumentsFix(variable);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createChangeClassSignatureFromUsageFix(@jakarta.annotation.Nonnull PsiClass owner, @jakarta.annotation.Nonnull PsiReferenceParameterList parameterList) {
    return new ChangeClassSignatureFromUsageFix(owner, parameterList);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createReplacePrimitiveWithBoxedTypeAction(@jakarta.annotation.Nonnull PsiTypeElement element,
                                                                   @jakarta.annotation.Nonnull String typeName,
                                                                   @jakarta.annotation.Nonnull String boxedTypeName) {
    return new ReplacePrimitiveWithBoxedTypeAction(element, typeName, boxedTypeName);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createMakeVarargParameterLastFix(@jakarta.annotation.Nonnull PsiParameter parameter) {
    return new MakeVarargParameterLastFix(parameter);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createMoveBoundClassToFrontFix(@jakarta.annotation.Nonnull PsiClass aClass, @jakarta.annotation.Nonnull PsiClassType type) {
    return new MoveBoundClassToFrontFix(aClass, type);
  }

  @Override
  public void registerPullAsAbstractUpFixes(@jakarta.annotation.Nonnull PsiMethod method, @jakarta.annotation.Nonnull QuickFixActionRegistrar registrar) {
    PullAsAbstractUpFix.registerQuickFix(method, registrar);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateAnnotationMethodFromUsageFix(@jakarta.annotation.Nonnull PsiNameValuePair pair) {
    return new CreateAnnotationMethodFromUsageFix(pair);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createOptimizeImportsFix(final boolean onTheFly) {
    final OptimizeImportsFix fix = new OptimizeImportsFix();

    return new SyntheticIntentionAction() {
      @jakarta.annotation.Nonnull
      @Override
      public String getText() {
        return fix.getText();
      }

      @Override
      public boolean isAvailable(@jakarta.annotation.Nonnull Project project, Editor editor, PsiFile file) {
        return (!onTheFly || timeToOptimizeImports(file)) && fix.isAvailable(project, editor, file);
      }

      @Override
      public void invoke(@jakarta.annotation.Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        invokeOnTheFlyImportOptimizer(() -> fix.invoke(project, editor, file), file);
      }

      @Override
      public boolean startInWriteAction() {
        return fix.startInWriteAction();
      }
    };
  }

  @Override
  public void registerFixesForUnusedParameter(@jakarta.annotation.Nonnull PsiParameter parameter, @jakarta.annotation.Nonnull Object highlightInfo) {
    Project myProject = parameter.getProject();
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    UnusedDeclarationInspectionBase unusedParametersInspection =
      (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedSymbolLocalInspectionBase.SHORT_NAME, parameter);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || unusedParametersInspection != null);
    List<IntentionAction> options = new ArrayList<>();
    HighlightDisplayKey myUnusedSymbolKey = HighlightDisplayKey.find(UnusedSymbolLocalInspectionBase.SHORT_NAME);
    options.addAll(IntentionManager.getInstance().getStandardIntentionOptions(myUnusedSymbolKey, parameter));
    if (unusedParametersInspection != null) {
      SuppressQuickFix[] batchSuppressActions = unusedParametersInspection.getBatchSuppressActions(parameter);
      Collections.addAll(options, SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions));
    }
    //need suppress from Unused Parameters but settings from Unused Symbol
    QuickFixAction.registerQuickFixAction((HighlightInfo)highlightInfo,
                                          new SafeDeleteFix(parameter),
                                          options,
                                          HighlightDisplayKey.getDisplayNameByKey(myUnusedSymbolKey));
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddToDependencyInjectionAnnotationsFix(@jakarta.annotation.Nonnull Project project,
                                                                      @jakarta.annotation.Nonnull String qualifiedName,
                                                                      @jakarta.annotation.Nonnull String element) {
    final EntryPointsManagerBase entryPointsManager = EntryPointsManagerBase.getInstance(project);
    return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(JavaQuickFixBundle.message(
      "fix.unused.symbol.injection.text",
      element,
      qualifiedName),
                                                                                   JavaQuickFixBundle
                                                                                     .message
                                                                                       ("fix.unused.symbol.injection.family"),
                                                                                   entryPointsManager.ADDITIONAL_ANNOTATIONS,
                                                                                   qualifiedName);
  }

  @Nonnull
  @Override
  public IntentionAction createAddToImplicitlyWrittenFieldsFix(Project project, @jakarta.annotation.Nonnull final String qualifiedName) {
    EntryPointsManagerBase entryPointsManagerBase = EntryPointsManagerBase.getInstance(project);
    return entryPointsManagerBase.new AddImplicitlyWriteAnnotation(qualifiedName);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @jakarta.annotation.Nonnull PsiField field) {
    return new CreateGetterOrSetterFix(createGetter, createSetter, field);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createRenameToIgnoredFix(@jakarta.annotation.Nonnull PsiNamedElement namedElement) {
    return new RenameToIgnoredFix(namedElement);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createEnableOptimizeImportsOnTheFlyFix() {
    return new EnableOptimizeImportsOnTheFlyFix();
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@jakarta.annotation.Nonnull PsiElement element) {
    return new DeleteElementFix(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@jakarta.annotation.Nonnull PsiElement element, @Nls @jakarta.annotation.Nonnull String text) {
    return new DeleteElementFix(element, text);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createSafeDeleteFix(@Nonnull PsiElement element) {
    return new SafeDeleteFix(element);
  }

  @Nullable
  @Override
  public List<LocalQuickFix> registerOrderEntryFixes(@jakarta.annotation.Nonnull PsiReference reference) {
    return OrderEntryFix.registerFixes(reference);
  }

  private static void invokeOnTheFlyImportOptimizer(@jakarta.annotation.Nonnull final Runnable runnable, @jakarta.annotation.Nonnull final PsiFile file) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) {
      return;
    }
    final long stamp = document.getModificationStamp();
    DumbService.getInstance(file.getProject()).smartInvokeLater(() ->
                                                                {
                                                                  if (project.isDisposed() || document.getModificationStamp() != stamp) {
                                                                    return;
                                                                  }
                                                                  //no need to optimize imports on the fly during undo/redo
                                                                  final UndoManager undoManager = ProjectUndoManager.getInstance(project);
                                                                  if (undoManager.isUndoInProgress() || undoManager.isRedoInProgress()) {
                                                                    return;
                                                                  }
                                                                  PsiDocumentManager.getInstance(project).commitAllDocuments();
                                                                  String beforeText = file.getText();
                                                                  final long oldStamp = document.getModificationStamp();
                                                                  UndoUtil.writeInRunUndoTransparentAction(runnable);
                                                                  if (oldStamp != document.getModificationStamp()) {
                                                                    String afterText = file.getText();
                                                                    if (Comparing.strEqual(beforeText, afterText)) {
                                                                      LOG.error(LogMessageEx.createEvent(
                                                                        "Import optimizer  hasn't optimized any imports",
                                                                        file.getViewProvider().getVirtualFile().getPath(),
                                                                        AttachmentFactoryUtil.createAttachment(file
                                                                                                                 .getViewProvider()
                                                                                                                 .getVirtualFile())));
                                                                    }
                                                                  }
                                                                });
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddMissingRequiredAnnotationParametersFix(@jakarta.annotation.Nonnull final PsiAnnotation annotation,
                                                                         @jakarta.annotation.Nonnull final PsiMethod[] annotationMethods,
                                                                         @jakarta.annotation.Nonnull final Collection<String> missedElements) {
    return new AddMissingRequiredAnnotationParametersFix(annotation, annotationMethods, missedElements);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@jakarta.annotation.Nonnull PsiAnnotationMemberValue value,
                                                                             @jakarta.annotation.Nonnull PsiType expectedType) {
    return new SurroundWithQuotesAnnotationParameterValueFix(value, expectedType);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction addMethodQualifierFix(@Nonnull PsiMethodCallExpression methodCall) {
    return new AddMethodQualifierFix(methodCall);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createWrapWithAdapterFix(@jakarta.annotation.Nullable PsiType type, @jakarta.annotation.Nonnull PsiExpression expression) {
    return new WrapLongWithMathToIntExactFix(type, expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createWrapWithOptionalFix(@jakarta.annotation.Nullable PsiType type, @jakarta.annotation.Nonnull PsiExpression expression) {
    return WrapObjectWithOptionalOfNullableFix.createFix(type, expression);
  }

  @jakarta.annotation.Nullable
  @Override
  public IntentionAction createNotIterableForEachLoopFix(@jakarta.annotation.Nonnull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiForeachStatement) {
      final PsiType type = expression.getType();
      if (InheritanceUtil.isInheritor(type, JavaClassNames.JAVA_UTIL_ITERATOR)) {
        return new ReplaceIteratorForEachLoopWithIteratorForLoopFix((PsiForeachStatement)parent);
      }
    }
    return null;
  }

  @jakarta.annotation.Nonnull
  @Override
  public List<IntentionAction> createAddAnnotationAttributeNameFixes(@jakarta.annotation.Nonnull PsiNameValuePair pair) {
    return AddAnnotationAttributeNameFix.createFixes(pair);
  }

  private static boolean timeToOptimizeImports(@jakarta.annotation.Nonnull PsiFile file) {
    if (!CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) {
      return false;
    }

    Project project = file.getProject();
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    // dont optimize out imports in JSP since it can be included in other JSP
    if (!codeAnalyzer.isHighlightingAvailable(file) || !(file instanceof PsiJavaFile) || file instanceof ServerPageFile) {
      return false;
    }

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) {
      return false;
    }
    boolean errors = containsErrorsPreventingOptimize(file);

    return !errors && AutoImportHelper.getInstance(project).canChangeFileSilently(file);
  }

  private static boolean containsErrorsPreventingOptimize(@jakarta.annotation.Nonnull PsiFile file) {
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return true;
    }
    // ignore unresolved imports errors
    PsiImportList importList = ((PsiJavaFile)file).getImportList();
    final TextRange importsRange = importList == null ? TextRange.EMPTY_RANGE : importList.getTextRange();
    boolean hasErrorsExceptUnresolvedImports =
      !DaemonCodeAnalyzer.processHighlights(document, file.getProject(), HighlightSeverity.ERROR, 0, document.getTextLength(), error ->
      {
        int infoStart = error.getActualStartOffset();
        int infoEnd = error.getActualEndOffset();

        return importsRange.containsRange(infoStart, infoEnd) && error.getType().equals(HighlightInfoType.WRONG_REF);
      });

    return hasErrorsExceptUnresolvedImports;
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createCollectionToArrayFix(@jakarta.annotation.Nonnull PsiExpression collectionExpression, @jakarta.annotation.Nonnull PsiArrayType arrayType) {
    return new ConvertCollectionToArrayFix(collectionExpression, arrayType);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createInsertMethodCallFix(@jakarta.annotation.Nonnull PsiMethodCallExpression call, PsiMethod method) {
    return new InsertMethodCallFix(call, method);
  }

  @jakarta.annotation.Nonnull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(PsiReferenceExpression methodRef,
                                                                                      JavaResolveResult result) {
    return new AccessStaticViaInstanceFix(methodRef, result, true);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createWrapStringWithFileFix(@jakarta.annotation.Nullable PsiType type, @jakarta.annotation.Nonnull PsiExpression expression) {
    return new WrapStringWithFileFix(type, expression);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddMissingEnumBranchesFix(@jakarta.annotation.Nonnull PsiSwitchBlock switchBlock, @jakarta.annotation.Nonnull Set<String> missingCases) {
    return new CreateMissingSwitchBranchesFix(switchBlock, missingCases);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createAddSwitchDefaultFix(@jakarta.annotation.Nonnull PsiSwitchBlock switchBlock, @jakarta.annotation.Nullable String message) {
    return new CreateDefaultBranchFix(switchBlock, message);
  }

  @jakarta.annotation.Nonnull
  @Override
  public IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement rule) {
    return new WrapSwitchRuleStatementsIntoBlockFix(rule);
  }

  @Nonnull
  @Override
  public IntentionAction createDeleteSideEffectAwareFix(@jakarta.annotation.Nonnull PsiExpressionStatement statement) {
    return new DeleteSideEffectsAwareFix(statement, statement.getExpression());
  }
}
