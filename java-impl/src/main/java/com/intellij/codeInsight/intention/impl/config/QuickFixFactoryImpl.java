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
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.daemon.quickFix.CreateFieldOrPropertyFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.ReplaceAssignmentWithComparisonFix;
import com.intellij.codeInspection.*;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.*;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.WrapLongWithMathToIntExactFix;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.java.analysis.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.fixes.CreateMissingSwitchBranchesFix;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.module.util.JavaClassNames;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * @author cdr
 */
@Singleton
public class QuickFixFactoryImpl extends QuickFixFactory
{
	private static final Logger LOG = Logger.getInstance(QuickFixFactoryImpl.class);

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@Nonnull PsiModifierList modifierList, @Nonnull String modifier, boolean shouldHave, boolean showContainingClass)
	{
		return new ModifierFix(modifierList, modifier, shouldHave, showContainingClass);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@Nonnull PsiModifierListOwner owner,
																			 @Nonnull final String modifier,
																			 final boolean shouldHave,
																			 final boolean showContainingClass)
	{
		return new ModifierFix(owner, modifier, shouldHave, showContainingClass);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@Nonnull PsiMethod method, @Nonnull PsiType toReturn, boolean fixWholeHierarchy)
	{
		return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@Nonnull PsiMethod method, @Nonnull PsiClass toClass)
	{
		return new AddMethodFix(method, toClass);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@Nonnull String methodText, @Nonnull PsiClass toClass, @Nonnull String... exceptions)
	{
		return new AddMethodFix(methodText, toClass, exceptions);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@Nonnull PsiClass aClass)
	{
		return new ImplementMethodsFix(aClass);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@Nonnull PsiElement psiElement)
	{
		return new ImplementMethodsFix(psiElement);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(PsiAssignmentExpression expr)
	{
		return new ReplaceAssignmentWithComparisonFix(expr);
	}

	@Nonnull
	@Override
	public LocalQuickFixOnPsiElement createMethodThrowsFix(@Nonnull PsiMethod method, @Nonnull PsiClassType exceptionClass, boolean shouldThrow, boolean showContainingClass)
	{
		return new MethodThrowsFix(method, exceptionClass, shouldThrow, showContainingClass);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@Nonnull PsiClass aClass)
	{
		return new AddDefaultConstructorFix(aClass);
	}

	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@Nonnull PsiClass aClass, @Nonnull String modifier)
	{
		return aClass.getName() != null ? new AddDefaultConstructorFix(aClass, modifier) : null;
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@Nonnull PsiMethod method, int index, @Nonnull PsiType newType, boolean fixWholeHierarchy)
	{
		return new MethodParameterFix(method, newType, index, fixWholeHierarchy);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@Nonnull PsiClass aClass)
	{
		return new MakeClassInterfaceFix(aClass, true);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@Nonnull PsiClass aClass, final boolean makeInterface)
	{
		return new MakeClassInterfaceFix(aClass, makeInterface);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@Nonnull PsiClass aClass, @Nonnull PsiClassType typeToExtendFrom, boolean toAdd)
	{
		return new ExtendsListFix(aClass, typeToExtendFrom, toAdd);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@Nonnull PsiParameter parameter)
	{
		return new RemoveUnusedParameterFix(parameter);
	}

	@Nonnull
	@Override
	public IntentionAction createRemoveUnusedVariableFix(@Nonnull PsiVariable variable)
	{
		return new RemoveUnusedVariableFix(variable);
	}

	@Override
	@Nullable
	public IntentionAction createCreateClassOrPackageFix(@Nonnull final PsiElement context, @Nonnull final String qualifiedName, final boolean createClass, final String superClass)
	{
		return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : null, superClass);
	}

	@Override
	@Nullable
	public IntentionAction createCreateClassOrInterfaceFix(@Nonnull final PsiElement context, @Nonnull final String qualifiedName, final boolean createClass, final String superClass)
	{
		return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : ClassKind.INTERFACE, superClass);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateFieldOrPropertyFix(@Nonnull final PsiClass aClass,
														  @Nonnull final String name,
														  @Nonnull final PsiType type,
														  @Nonnull final PropertyMemberType targetMember,
														  @Nonnull final PsiAnnotation... annotations)
	{
		return new CreateFieldOrPropertyFix(aClass, name, type, targetMember, annotations);
	}

	@Nonnull
	@Override
	public IntentionAction createSetupJDKFix()
	{
		return SetupJDKFix.getInstance();
	}

	@Nonnull
	@Override
	public IntentionAction createAddExceptionToCatchFix()
	{
		return new AddExceptionToCatchFix();
	}

	@Nonnull
	@Override
	public IntentionAction createAddExceptionToThrowsFix(@Nonnull PsiElement element)
	{
		return new AddExceptionToThrowsFix(element);
	}

	@Nonnull
	@Override
	public IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@Nonnull PsiElement element)
	{
		return new AddExceptionFromFieldInitializerToConstructorThrowsFix(element);
	}

	@Nonnull
	@Override
	public IntentionAction createSurroundWithTryCatchFix(@Nonnull PsiElement element)
	{
		return new SurroundWithTryCatchFix(element);
	}

	@Nonnull
	@Override
	public IntentionAction createGeneralizeCatchFix(@Nonnull PsiElement element, @Nonnull PsiClassType type)
	{
		return new GeneralizeCatchFix(element, type);
	}

	@Nonnull
	@Override
	public IntentionAction createChangeToAppendFix(@Nonnull IElementType sign, @Nonnull PsiType type, @Nonnull PsiAssignmentExpression assignment)
	{
		return new ChangeToAppendFix(sign, type, assignment);
	}

	@Nonnull
	@Override
	public IntentionAction createAddTypeCastFix(@Nonnull PsiType type, @Nonnull PsiExpression expression)
	{
		return new AddTypeCastFix(type, expression);
	}

	@Nonnull
	@Override
	public IntentionAction createWrapExpressionFix(@Nonnull PsiType type, @Nonnull PsiExpression expression)
	{
		return new WrapExpressionFix(type, expression);
	}

	@Nonnull
	@Override
	public IntentionAction createReuseVariableDeclarationFix(@Nonnull PsiLocalVariable variable)
	{
		return new ReuseVariableDeclarationFix(variable);
	}

	@Nonnull
	@Override
	public IntentionAction createConvertToStringLiteralAction()
	{
		return new ConvertToStringLiteralAction();
	}

	@Nonnull
	@Override
	public IntentionAction createDeleteCatchFix(@Nonnull PsiParameter parameter)
	{
		return new DeleteCatchFix(parameter);
	}

	@Nonnull
	@Override
	public IntentionAction createDeleteMultiCatchFix(@Nonnull PsiTypeElement element)
	{
		return new DeleteMultiCatchFix(element);
	}

	@Nonnull
	@Override
	public IntentionAction createConvertSwitchToIfIntention(@Nonnull PsiSwitchStatement statement)
	{
		return new ConvertSwitchToIfIntention(statement);
	}

	@Nonnull
	@Override
	public IntentionAction createNegationBroadScopeFix(@Nonnull PsiPrefixExpression expr)
	{
		return new NegationBroadScopeFix(expr);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateFieldFromUsageFix(@Nonnull PsiReferenceExpression place)
	{
		return new CreateFieldFromUsageFix(place);
	}

	@Nonnull
	@Override
	public IntentionAction createReplaceWithListAccessFix(@Nonnull PsiArrayAccessExpression expression)
	{
		return new ReplaceWithListAccessFix(expression);
	}

	@Nonnull
	@Override
	public IntentionAction createAddNewArrayExpressionFix(@Nonnull PsiArrayInitializerExpression expression)
	{
		return new AddNewArrayExpressionFix(expression);
	}

	@Nonnull
	@Override
	public IntentionAction createMoveCatchUpFix(@Nonnull PsiCatchSection section, @Nonnull PsiCatchSection section1)
	{
		return new MoveCatchUpFix(section, section1);
	}

	@Nonnull
	@Override
	public IntentionAction createRenameWrongRefFix(@Nonnull PsiReferenceExpression ref)
	{
		return new RenameWrongRefFix(ref);
	}

	@Nonnull
	@Override
	public IntentionAction createRemoveQualifierFix(@Nonnull PsiExpression qualifier, @Nonnull PsiReferenceExpression expression, @Nonnull PsiClass resolved)
	{
		return new RemoveQualifierFix(qualifier, expression, resolved);
	}

	@Nonnull
	@Override
	public IntentionAction createRemoveParameterListFix(@Nonnull PsiMethod parent)
	{
		return new RemoveParameterListFix(parent);
	}

	@Nonnull
	@Override
	public IntentionAction createShowModulePropertiesFix(@Nonnull PsiElement element)
	{
		return new ShowModulePropertiesFix(element);
	}

	@Nonnull
	@Override
	public IntentionAction createShowModulePropertiesFix(@Nonnull Module module)
	{
		return new ShowModulePropertiesFix(module);
	}

	@Nonnull
	@Override
	public IntentionAction createIncreaseLanguageLevelFix(@Nonnull LanguageLevel level)
	{
		return new IncreaseLanguageLevelFix(level);
	}

	@Nonnull
	@Override
	public IntentionAction createChangeParameterClassFix(@Nonnull PsiClass aClass, @Nonnull PsiClassType type)
	{
		return new ChangeParameterClassFix(aClass, type);
	}

	@Nonnull
	@Override
	public IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@Nonnull PsiElement element, @Nonnull PsiMethod getter, boolean isSetter)
	{
		return new ReplaceInaccessibleFieldWithGetterSetterFix(element, getter, isSetter);
	}

	@Nonnull
	@Override
	public IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression)
	{
		return new SurroundWithArrayFix(methodCall, expression);
	}

	@Nonnull
	@Override
	public IntentionAction createImplementAbstractClassMethodsFix(@Nonnull PsiElement elementToHighlight)
	{
		return new ImplementAbstractClassMethodsFix(elementToHighlight);
	}

	@Nonnull
	@Override
	public IntentionAction createMoveClassToSeparateFileFix(@Nonnull PsiClass aClass)
	{
		return new MoveClassToSeparateFileFix(aClass);
	}

	@Nonnull
	@Override
	public IntentionAction createRenameFileFix(@Nonnull String newName)
	{
		return new RenameFileFix(newName);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@Nonnull PsiNamedElement element)
	{
		return new RenameElementFix(element);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@Nonnull PsiNamedElement element, @Nonnull String newName)
	{
		return new RenameElementFix(element, newName);
	}

	@Nonnull
	@Override
	public IntentionAction createChangeExtendsToImplementsFix(@Nonnull PsiClass aClass, @Nonnull PsiClassType classToExtendFrom)
	{
		return new ChangeExtendsToImplementsFix(aClass, classToExtendFrom);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateConstructorMatchingSuperFix(@Nonnull PsiClass aClass)
	{
		return new CreateConstructorMatchingSuperFix(aClass);
	}

	@Nonnull
	@Override
	public IntentionAction createRemoveNewQualifierFix(@Nonnull PsiNewExpression expression, PsiClass aClass)
	{
		return new RemoveNewQualifierFix(expression, aClass);
	}

	@Nonnull
	@Override
	public IntentionAction createSuperMethodReturnFix(@Nonnull PsiMethod superMethod, @Nonnull PsiType superMethodType)
	{
		return new SuperMethodReturnFix(superMethod, superMethodType);
	}

	@Nonnull
	@Override
	public IntentionAction createInsertNewFix(@Nonnull PsiMethodCallExpression call, @Nonnull PsiClass aClass)
	{
		return new InsertNewFix(call, aClass);
	}

	@Nonnull
	@Override
	public IntentionAction createAddMethodBodyFix(@Nonnull PsiMethod method)
	{
		return new AddMethodBodyFix(method);
	}

	@Nonnull
	@Override
	public IntentionAction createDeleteMethodBodyFix(@Nonnull PsiMethod method)
	{
		return new DeleteMethodBodyFix(method);
	}

	@Nonnull
	@Override
	public IntentionAction createInsertSuperFix(@Nonnull PsiMethod constructor)
	{
		return new InsertSuperFix(constructor);
	}

	@Nonnull
	@Override
	public IntentionAction createInsertThisFix(@Nonnull PsiMethod constructor)
	{
		return new InsertThisFix(constructor);
	}

	@Nonnull
	@Override
	public IntentionAction createChangeMethodSignatureFromUsageFix(@Nonnull PsiMethod targetMethod,
																   @Nonnull PsiExpression[] expressions,
																   @Nonnull PsiSubstitutor substitutor,
																   @Nonnull PsiElement context,
																   boolean changeAllUsages,
																   int minUsagesNumberToShowDialog)
	{
		return new ChangeMethodSignatureFromUsageFix(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
	}

	@Nonnull
	@Override
	public IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@Nonnull PsiMethod targetMethod,
																			   @Nonnull PsiExpression[] expressions,
																			   @Nonnull PsiSubstitutor substitutor,
																			   @Nonnull PsiElement context,
																			   boolean changeAllUsages,
																			   int minUsagesNumberToShowDialog)
	{
		return new ChangeMethodSignatureFromUsageReverseOrderFix(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateMethodFromUsageFix(@Nonnull PsiMethodCallExpression call)
	{
		return new CreateMethodFromUsageFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateMethodFromUsageFix(PsiMethodReferenceExpression methodReferenceExpression)
	{
		return new CreateMethodFromMethodReferenceFix(methodReferenceExpression);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateAbstractMethodFromUsageFix(@Nonnull PsiMethodCallExpression call)
	{
		return new CreateAbstractMethodFromUsageFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createCreatePropertyFromUsageFix(@Nonnull PsiMethodCallExpression call)
	{
		return new CreatePropertyFromUsageFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateConstructorFromSuperFix(@Nonnull PsiMethodCallExpression call)
	{
		return new CreateConstructorFromSuperFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateConstructorFromThisFix(@Nonnull PsiMethodCallExpression call)
	{
		return new CreateConstructorFromThisFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateGetterSetterPropertyFromUsageFix(@Nonnull PsiMethodCallExpression call)
	{
		return new CreateGetterSetterPropertyFromUsageFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createStaticImportMethodFix(@Nonnull PsiMethodCallExpression call)
	{
		return new StaticImportMethodFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createReplaceAddAllArrayToCollectionFix(@Nonnull PsiMethodCallExpression call)
	{
		return new ReplaceAddAllArrayToCollectionFix(call);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateConstructorFromCallFix(@Nonnull PsiConstructorCall call)
	{
		return new CreateConstructorFromCallFix(call);
	}

	@Nonnull
	@Override
	public List<IntentionAction> getVariableTypeFromCallFixes(@Nonnull PsiMethodCallExpression call, @Nonnull PsiExpressionList list)
	{
		return VariableTypeFromCallFix.getQuickFixActions(call, list);
	}

	@Nonnull
	@Override
	public IntentionAction createAddReturnFix(@Nonnull PsiMethod method)
	{
		return new AddReturnFix(method);
	}

	@Nonnull
	@Override
	public IntentionAction createAddVariableInitializerFix(@Nonnull PsiVariable variable)
	{
		return new AddVariableInitializerFix(variable);
	}

	@Nonnull
	@Override
	public IntentionAction createDeferFinalAssignmentFix(@Nonnull PsiVariable variable, @Nonnull PsiReferenceExpression expression)
	{
		return new DeferFinalAssignmentFix(variable, expression);
	}

	@Nonnull
	@Override
	public IntentionAction createVariableAccessFromInnerClassFix(@Nonnull PsiVariable variable, @Nonnull PsiElement scope)
	{
		return new VariableAccessFromInnerClassFix(variable, scope);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateConstructorParameterFromFieldFix(@Nonnull PsiField field)
	{
		return new CreateConstructorParameterFromFieldFix(field);
	}

	@Nonnull
	@Override
	public IntentionAction createInitializeFinalFieldInConstructorFix(@Nonnull PsiField field)
	{
		return new InitializeFinalFieldInConstructorFix(field);
	}

	@Nonnull
	@Override
	public IntentionAction createRemoveTypeArgumentsFix(@Nonnull PsiElement variable)
	{
		return new RemoveTypeArgumentsFix(variable);
	}

	@Nonnull
	@Override
	public IntentionAction createChangeClassSignatureFromUsageFix(@Nonnull PsiClass owner, @Nonnull PsiReferenceParameterList parameterList)
	{
		return new ChangeClassSignatureFromUsageFix(owner, parameterList);
	}

	@Nonnull
	@Override
	public IntentionAction createReplacePrimitiveWithBoxedTypeAction(@Nonnull PsiTypeElement element, @Nonnull String typeName, @Nonnull String boxedTypeName)
	{
		return new ReplacePrimitiveWithBoxedTypeAction(element, typeName, boxedTypeName);
	}

	@Nonnull
	@Override
	public IntentionAction createMakeVarargParameterLastFix(@Nonnull PsiParameter parameter)
	{
		return new MakeVarargParameterLastFix(parameter);
	}

	@Nonnull
	@Override
	public IntentionAction createMoveBoundClassToFrontFix(@Nonnull PsiClass aClass, @Nonnull PsiClassType type)
	{
		return new MoveBoundClassToFrontFix(aClass, type);
	}

	@Override
	public void registerPullAsAbstractUpFixes(@Nonnull PsiMethod method, @Nonnull QuickFixActionRegistrar registrar)
	{
		PullAsAbstractUpFix.registerQuickFix(method, registrar);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateAnnotationMethodFromUsageFix(@Nonnull PsiNameValuePair pair)
	{
		return new CreateAnnotationMethodFromUsageFix(pair);
	}

	@Nonnull
	@Override
	public IntentionAction createOptimizeImportsFix(final boolean onTheFly)
	{
		final OptimizeImportsFix fix = new OptimizeImportsFix();

		return new IntentionAction()
		{
			@Nonnull
			@Override
			public String getText()
			{
				return fix.getText();
			}

			@Nonnull
			@Override
			public String getFamilyName()
			{
				return fix.getFamilyName();
			}

			@Override
			public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
			{
				return (!onTheFly || timeToOptimizeImports(file)) && fix.isAvailable(project, editor, file);
			}

			@Override
			public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException
			{
				invokeOnTheFlyImportOptimizer(() -> fix.invoke(project, editor, file), file);
			}

			@Override
			public boolean startInWriteAction()
			{
				return fix.startInWriteAction();
			}
		};
	}

	@Override
	public void registerFixesForUnusedParameter(@Nonnull PsiParameter parameter, @Nonnull Object highlightInfo)
	{
		Project myProject = parameter.getProject();
		InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
		UnusedDeclarationInspectionBase unusedParametersInspection = (UnusedDeclarationInspectionBase) profile.getUnwrappedTool(UnusedSymbolLocalInspectionBase.SHORT_NAME, parameter);
		LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || unusedParametersInspection != null);
		List<IntentionAction> options = new ArrayList<>();
		HighlightDisplayKey myUnusedSymbolKey = HighlightDisplayKey.find(UnusedSymbolLocalInspectionBase.SHORT_NAME);
		options.addAll(IntentionManager.getInstance().getStandardIntentionOptions(myUnusedSymbolKey, parameter));
		if(unusedParametersInspection != null)
		{
			SuppressQuickFix[] batchSuppressActions = unusedParametersInspection.getBatchSuppressActions(parameter);
			Collections.addAll(options, SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions));
		}
		//need suppress from Unused Parameters but settings from Unused Symbol
		QuickFixAction.registerQuickFixAction((HighlightInfo) highlightInfo, new SafeDeleteFix(parameter), options, HighlightDisplayKey.getDisplayNameByKey(myUnusedSymbolKey));
	}

	@Nonnull
	@Override
	public IntentionAction createAddToDependencyInjectionAnnotationsFix(@Nonnull Project project, @Nonnull String qualifiedName, @Nonnull String element)
	{
		final EntryPointsManagerBase entryPointsManager = EntryPointsManagerBase.getInstance(project);
		return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(JavaQuickFixBundle.message("fix.unused.symbol.injection.text", element, qualifiedName), JavaQuickFixBundle
				.message
				("fix" +
						".unused.symbol.injection.family"), entryPointsManager.ADDITIONAL_ANNOTATIONS, qualifiedName);
	}

	@Nonnull
	@Override
	public IntentionAction createAddToImplicitlyWrittenFieldsFix(Project project, @Nonnull final String qualifiedName)
	{
		EntryPointsManagerBase entryPointsManagerBase = EntryPointsManagerBase.getInstance(project);
		return entryPointsManagerBase.new AddImplicitlyWriteAnnotation(qualifiedName);
	}

	@Nonnull
	@Override
	public IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @Nonnull PsiField field)
	{
		return new CreateGetterOrSetterFix(createGetter, createSetter, field);
	}

	@Nonnull
	@Override
	public IntentionAction createRenameToIgnoredFix(@Nonnull PsiNamedElement namedElement)
	{
		return new RenameToIgnoredFix(namedElement);
	}

	@Nonnull
	@Override
	public IntentionAction createEnableOptimizeImportsOnTheFlyFix()
	{
		return new EnableOptimizeImportsOnTheFlyFix();
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@Nonnull PsiElement element)
	{
		return new DeleteElementFix(element);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@Nonnull PsiElement element, @Nls @Nonnull String text)
	{
		return new DeleteElementFix(element, text);
	}

	@Nonnull
	@Override
	public IntentionAction createSafeDeleteFix(@Nonnull PsiElement element)
	{
		return new SafeDeleteFix(element);
	}

	@Nullable
	@Override
	public List<LocalQuickFix> registerOrderEntryFixes(@Nonnull QuickFixActionRegistrar registrar, @Nonnull PsiReference reference)
	{
		return OrderEntryFix.registerFixes(registrar, reference);
	}

	private static void invokeOnTheFlyImportOptimizer(@Nonnull final Runnable runnable, @Nonnull final PsiFile file)
	{
		final Project project = file.getProject();
		final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
		if(document == null)
		{
			return;
		}
		final long stamp = document.getModificationStamp();
		DumbService.getInstance(file.getProject()).smartInvokeLater(() ->
		{
			if(project.isDisposed() || document.getModificationStamp() != stamp)
			{
				return;
			}
			//no need to optimize imports on the fly during undo/redo
			final UndoManager undoManager = UndoManager.getInstance(project);
			if(undoManager.isUndoInProgress() || undoManager.isRedoInProgress())
			{
				return;
			}
			PsiDocumentManager.getInstance(project).commitAllDocuments();
			String beforeText = file.getText();
			final long oldStamp = document.getModificationStamp();
			DocumentUtil.writeInRunUndoTransparentAction(runnable);
			if(oldStamp != document.getModificationStamp())
			{
				String afterText = file.getText();
				if(Comparing.strEqual(beforeText, afterText))
				{
					LOG.error(LogMessageEx.createEvent("Import optimizer  hasn't optimized any imports", file.getViewProvider().getVirtualFile().getPath(), AttachmentFactory.createAttachment(file
							.getViewProvider().getVirtualFile())));
				}
			}
		});
	}

	@Nonnull
	@Override
	public IntentionAction createAddMissingRequiredAnnotationParametersFix(@Nonnull final PsiAnnotation annotation,
																		   @Nonnull final PsiMethod[] annotationMethods,
																		   @Nonnull final Collection<String> missedElements)
	{
		return new AddMissingRequiredAnnotationParametersFix(annotation, annotationMethods, missedElements);
	}

	@Nonnull
	@Override
	public IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@Nonnull PsiAnnotationMemberValue value, @Nonnull PsiType expectedType)
	{
		return new SurroundWithQuotesAnnotationParameterValueFix(value, expectedType);
	}

	@Nonnull
	@Override
	public IntentionAction addMethodQualifierFix(@Nonnull PsiMethodCallExpression methodCall)
	{
		return new AddMethodQualifierFix(methodCall);
	}

	@Nonnull
	@Override
	public IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, @Nonnull PsiExpression expression)
	{
		return new WrapLongWithMathToIntExactFix(type, expression);
	}

	@Nonnull
	@Override
	public IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @Nonnull PsiExpression expression)
	{
		return WrapObjectWithOptionalOfNullableFix.createFix(type, expression);
	}

	@Nullable
	@Override
	public IntentionAction createNotIterableForEachLoopFix(@Nonnull PsiExpression expression)
	{
		final PsiElement parent = expression.getParent();
		if(parent instanceof PsiForeachStatement)
		{
			final PsiType type = expression.getType();
			if(InheritanceUtil.isInheritor(type, JavaClassNames.JAVA_UTIL_ITERATOR))
			{
				return new ReplaceIteratorForEachLoopWithIteratorForLoopFix((PsiForeachStatement) parent);
			}
		}
		return null;
	}

	@Nonnull
	@Override
	public List<IntentionAction> createAddAnnotationAttributeNameFixes(@Nonnull PsiNameValuePair pair)
	{
		return AddAnnotationAttributeNameFix.createFixes(pair);
	}

	private static boolean timeToOptimizeImports(@Nonnull PsiFile file)
	{
		if(!CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY)
		{
			return false;
		}

		DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(file.getProject());
		// dont optimize out imports in JSP since it can be included in other JSP
		if(!codeAnalyzer.isHighlightingAvailable(file) || !(file instanceof PsiJavaFile) || file instanceof ServerPageFile)
		{
			return false;
		}

		if(!codeAnalyzer.isErrorAnalyzingFinished(file))
		{
			return false;
		}
		boolean errors = containsErrorsPreventingOptimize(file);

		return !errors && DaemonListeners.canChangeFileSilently(file);
	}

	private static boolean containsErrorsPreventingOptimize(@Nonnull PsiFile file)
	{
		Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
		if(document == null)
		{
			return true;
		}
		// ignore unresolved imports errors
		PsiImportList importList = ((PsiJavaFile) file).getImportList();
		final TextRange importsRange = importList == null ? TextRange.EMPTY_RANGE : importList.getTextRange();
		boolean hasErrorsExceptUnresolvedImports = !DaemonCodeAnalyzerEx.processHighlights(document, file.getProject(), HighlightSeverity.ERROR, 0, document.getTextLength(), error ->
		{
			int infoStart = error.getActualStartOffset();
			int infoEnd = error.getActualEndOffset();

			return importsRange.containsRange(infoStart, infoEnd) && error.type.equals(HighlightInfoType.WRONG_REF);
		});

		return hasErrorsExceptUnresolvedImports;
	}

	@Nonnull
	@Override
	public IntentionAction createCollectionToArrayFix(@Nonnull PsiExpression collectionExpression, @Nonnull PsiArrayType arrayType)
	{
		return new ConvertCollectionToArrayFix(collectionExpression, arrayType);
	}

	@Nonnull
	@Override
	public IntentionAction createInsertMethodCallFix(@Nonnull PsiMethodCallExpression call, PsiMethod method)
	{
		return new InsertMethodCallFix(call, method);
	}

	@Nonnull
	@Override
	public LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(PsiReferenceExpression methodRef, JavaResolveResult result)
	{
		return new AccessStaticViaInstanceFix(methodRef, result, true);
	}

	@Nonnull
	@Override
	public IntentionAction createWrapStringWithFileFix(@Nullable PsiType type, @Nonnull PsiExpression expression)
	{
		return new WrapStringWithFileFix(type, expression);
	}

	@Nonnull
	@Override
	public IntentionAction createAddMissingEnumBranchesFix(@Nonnull PsiSwitchBlock switchBlock, @Nonnull Set<String> missingCases)
	{
		return new CreateMissingSwitchBranchesFix(switchBlock, missingCases);
	}

	@Nonnull
	@Override
	public IntentionAction createAddSwitchDefaultFix(@Nonnull PsiSwitchBlock switchBlock, @Nullable String message)
	{
		return new CreateDefaultBranchFix(switchBlock, message);
	}

	@Nonnull
	@Override
	public IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement rule)
	{
		return new WrapSwitchRuleStatementsIntoBlockFix(rule);
	}

	@Nonnull
	@Override
	public IntentionAction createDeleteSideEffectAwareFix(@Nonnull PsiExpressionStatement statement)
	{
		return new DeleteSideEffectsAwareFix(statement, statement.getExpression());
	}
}
