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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.daemon.impl.quickfix.RenameElementFix;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
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
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.undoRedo.ProjectUndoManager;
import consulo.undoRedo.UndoManager;
import consulo.undoRedo.util.UndoUtil;
import consulo.util.lang.Comparing;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Singleton;

import java.util.*;

/**
 * @author cdr
 */
@Singleton
@ServiceImpl
public class QuickFixFactoryImpl extends QuickFixFactory {
    private final class ModifierFixBuilderImpl implements ModifierFixBuilder {
        private final PsiModifierList myModifierList;
        private final PsiModifierListOwner myOwner;
        private @PsiModifier.ModifierConstant String myModifier = null;
        private boolean myShouldHave;
        private boolean myShowContainingClass;

        public ModifierFixBuilderImpl(PsiModifierListOwner owner) {
            myModifierList = null;
            myOwner = owner;
        }

        public ModifierFixBuilderImpl(PsiModifierList modifierList) {
            myModifierList = modifierList;
            myOwner = null;
        }

        @Override
        public ModifierFixBuilder add(@PsiModifier.ModifierConstant String modifier) {
            return toggle(modifier, true);
        }

        @Override
        public ModifierFixBuilder remove(@PsiModifier.ModifierConstant String modifier) {
            return toggle(modifier, false);
        }

        @Override
        public ModifierFixBuilder toggle(@PsiModifier.ModifierConstant String modifier, boolean shouldHave) {
            if (myModifier != null) {
                throw new IllegalStateException();
            }
            myModifier = modifier;
            myShouldHave = shouldHave;
            return this;
        }

        @Override
        public ModifierFixBuilder showContainingClass() {
            if (myShowContainingClass) {
                throw new IllegalStateException();
            }
            myShowContainingClass = true;
            return this;
        }

        @Override
        @RequiredReadAction
        public LocalQuickFixAndIntentionActionOnPsiElement create() {
            if (myModifier == null) {
                throw new IllegalStateException("Should have called add/remove/toggle() to specify modifier");
            }
            if (myOwner != null) {
                return new ModifierFix(myOwner, myModifier, myShouldHave, myShowContainingClass);
            }
            else {
                assert myModifierList != null;
                return new ModifierFix(myModifierList, myModifier, myShouldHave, myShowContainingClass);
            }
        }
    }

    private static final Logger LOG = Logger.getInstance(QuickFixFactoryImpl.class);

    @Override
    public ModifierFixBuilder createModifierFixBuilder(PsiModifierList modifierList) {
        return new ModifierFixBuilderImpl(modifierList);
    }

    @Override
    public ModifierFixBuilder createModifierFixBuilder(PsiModifierListOwner owner) {
        return new ModifierFixBuilderImpl(owner);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(
        PsiMethod method,
        PsiType toReturn,
        boolean fixWholeHierarchy
    ) {
        return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(PsiMethod method, PsiClass toClass) {
        return new AddMethodFix(method, toClass);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(
        String methodText,
        PsiClass toClass,
        String... exceptions
    ) {
        return new AddMethodFix(methodText, toClass, exceptions);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(PsiClass aClass) {
        return new ImplementMethodsFix(aClass);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(PsiElement psiElement) {
        return new ImplementMethodsFix(psiElement);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(PsiAssignmentExpression expr) {
        return new ReplaceAssignmentWithComparisonFix(expr);
    }

    @Override
    public LocalQuickFixOnPsiElement createMethodThrowsFix(
        PsiMethod method,
        PsiClassType exceptionClass,
        boolean shouldThrow,
        boolean showContainingClass
    ) {
        return new MethodThrowsFix(method, exceptionClass, shouldThrow, showContainingClass);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(PsiClass aClass) {
        return new AddDefaultConstructorFix(aClass);
    }

    @Override
    @RequiredReadAction
    public LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(PsiClass aClass, String modifier) {
        return aClass.getName() != null ? new AddDefaultConstructorFix(aClass, modifier) : null;
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(
        PsiMethod method,
        int index,
        PsiType newType,
        boolean fixWholeHierarchy
    ) {
        return new MethodParameterFix(method, newType, index, fixWholeHierarchy);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(PsiClass aClass) {
        return new MakeClassInterfaceFix(aClass, true);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(PsiClass aClass, boolean makeInterface) {
        return new MakeClassInterfaceFix(aClass, makeInterface);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(
        PsiClass aClass,
        PsiClassType typeToExtendFrom,
        boolean toAdd
    ) {
        return new ExtendsListFix(aClass, typeToExtendFrom, toAdd);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(PsiParameter parameter) {
        return new RemoveUnusedParameterFix(parameter);
    }

    @Override
    public IntentionAction createRemoveUnusedVariableFix(PsiVariable variable) {
        return new RemoveUnusedVariableFix(variable);
    }

    @Override
    @Nullable
    public IntentionAction createCreateClassOrPackageFix(
        PsiElement context,
        String qualifiedName,
        boolean createClass,
        String superClass
    ) {
        return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : null, superClass);
    }

    @Override
    @Nullable
    public IntentionAction createCreateClassOrInterfaceFix(
        PsiElement context,
        String qualifiedName,
        boolean createClass,
        String superClass
    ) {
        return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : ClassKind.INTERFACE, superClass);
    }

    @Override
    public IntentionAction createCreateFieldOrPropertyFix(
        PsiClass aClass,
        String name,
        PsiType type,
        PropertyMemberType targetMember,
        PsiAnnotation... annotations
    ) {
        return new CreateFieldOrPropertyFix(aClass, name, type, targetMember, annotations);
    }

    @Override
    public IntentionAction createSetupJDKFix() {
        return SetupJDKFix.getInstance();
    }

    @Override
    public IntentionAction createAddExceptionToCatchFix() {
        return new AddExceptionToCatchFix();
    }

    @Override
    public IntentionAction createAddExceptionToThrowsFix(PsiElement element) {
        return new AddExceptionToThrowsFix(element);
    }

    @Override
    public IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(PsiElement element) {
        return new AddExceptionFromFieldInitializerToConstructorThrowsFix(element);
    }

    @Override
    public IntentionAction createSurroundWithTryCatchFix(PsiElement element) {
        return new SurroundWithTryCatchFix(element);
    }

    @Override
    public IntentionAction createGeneralizeCatchFix(PsiElement element, PsiClassType type) {
        return new GeneralizeCatchFix(element, type);
    }

    @Override
    public IntentionAction createChangeToAppendFix(
        IElementType sign,
        PsiType type,
        PsiAssignmentExpression assignment
    ) {
        return new ChangeToAppendFix(sign, type, assignment);
    }

    @Override
    public IntentionAction createAddTypeCastFix(PsiType type, PsiExpression expression) {
        return new AddTypeCastFix(type, expression);
    }

    @Override
    public IntentionAction createWrapExpressionFix(PsiType type, PsiExpression expression) {
        return new WrapExpressionFix(type, expression);
    }

    @Override
    public IntentionAction createReuseVariableDeclarationFix(PsiLocalVariable variable) {
        return new ReuseVariableDeclarationFix(variable);
    }

    @Override
    public IntentionAction createConvertToStringLiteralAction() {
        return new ConvertToStringLiteralAction();
    }

    @Override
    public IntentionAction createDeleteCatchFix(PsiParameter parameter) {
        return new DeleteCatchFix(parameter);
    }

    @Override
    public IntentionAction createDeleteMultiCatchFix(PsiTypeElement element) {
        return new DeleteMultiCatchFix(element);
    }

    @Override
    public IntentionAction createConvertSwitchToIfIntention(PsiSwitchStatement statement) {
        return new ConvertSwitchToIfIntention(statement);
    }

    @Override
    public IntentionAction createNegationBroadScopeFix(PsiPrefixExpression expr) {
        return new NegationBroadScopeFix(expr);
    }

    @Override
    public IntentionAction createCreateFieldFromUsageFix(PsiReferenceExpression place) {
        return new CreateFieldFromUsageFix(place);
    }

    @Override
    public IntentionAction createReplaceWithListAccessFix(PsiArrayAccessExpression expression) {
        return new ReplaceWithListAccessFix(expression);
    }

    @Override
    public IntentionAction createAddNewArrayExpressionFix(PsiArrayInitializerExpression expression) {
        return new AddNewArrayExpressionFix(expression);
    }

    @Override
    public IntentionAction createMoveCatchUpFix(PsiCatchSection section, PsiCatchSection section1) {
        return new MoveCatchUpFix(section, section1);
    }

    @Override
    public IntentionAction createRenameWrongRefFix(PsiReferenceExpression ref) {
        return new RenameWrongRefFix(ref);
    }

    @Override
    public IntentionAction createRemoveQualifierFix(
        PsiExpression qualifier,
        PsiReferenceExpression expression,
        PsiClass resolved
    ) {
        return new RemoveQualifierFix(qualifier, expression, resolved);
    }

    @Override
    public IntentionAction createRemoveParameterListFix(PsiMethod parent) {
        return new RemoveParameterListFix(parent);
    }

    @Override
    public IntentionAction createShowModulePropertiesFix(PsiElement element) {
        return new ShowModulePropertiesFix(element);
    }

    @Override
    public IntentionAction createShowModulePropertiesFix(Module module) {
        return new ShowModulePropertiesFix(module);
    }

    @Override
    public IntentionAction createIncreaseLanguageLevelFix(LanguageLevel level) {
        return new IncreaseLanguageLevelFix(level);
    }

    @Override
    public IntentionAction createChangeParameterClassFix(PsiClass aClass, PsiClassType type) {
        return new ChangeParameterClassFix(aClass, type);
    }

    @Override
    public IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(
        PsiElement element,
        PsiMethod getter,
        boolean isSetter
    ) {
        return new ReplaceInaccessibleFieldWithGetterSetterFix(element, getter, isSetter);
    }

    @Override
    public IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression) {
        return new SurroundWithArrayFix(methodCall, expression);
    }

    @Override
    public IntentionAction createImplementAbstractClassMethodsFix(PsiElement elementToHighlight) {
        return new ImplementAbstractClassMethodsFix(elementToHighlight);
    }

    @Override
    public IntentionAction createMoveClassToSeparateFileFix(PsiClass aClass) {
        return new MoveClassToSeparateFileFix(aClass);
    }

    @Override
    public IntentionAction createRenameFileFix(String newName) {
        return new RenameFileFix(newName);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(PsiNamedElement element) {
        return new RenameElementFix(element);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(PsiNamedElement element, String newName) {
        return new RenameElementFix(element, newName);
    }

    @Override
    public IntentionAction createChangeExtendsToImplementsFix(PsiClass aClass, PsiClassType classToExtendFrom) {
        return new ChangeExtendsToImplementsFix(aClass, classToExtendFrom);
    }

    @Override
    public IntentionAction createCreateConstructorMatchingSuperFix(PsiClass aClass) {
        return new CreateConstructorMatchingSuperFix(aClass);
    }

    @Override
    public IntentionAction createRemoveNewQualifierFix(PsiNewExpression expression, PsiClass aClass) {
        return new RemoveNewQualifierFix(expression, aClass);
    }

    @Override
    public IntentionAction createSuperMethodReturnFix(PsiMethod superMethod, PsiType superMethodType) {
        return new SuperMethodReturnFix(superMethod, superMethodType);
    }

    @Override
    public IntentionAction createInsertNewFix(PsiMethodCallExpression call, PsiClass aClass) {
        return new InsertNewFix(call, aClass);
    }

    @Override
    public IntentionAction createAddMethodBodyFix(PsiMethod method) {
        return new AddMethodBodyFix(method);
    }

    @Override
    public IntentionAction createDeleteMethodBodyFix(PsiMethod method) {
        return new DeleteMethodBodyFix(method);
    }

    @Override
    public IntentionAction createInsertSuperFix(PsiMethod constructor) {
        return new InsertSuperFix(constructor);
    }

    @Override
    public IntentionAction createInsertThisFix(PsiMethod constructor) {
        return new InsertThisFix(constructor);
    }

    @Override
    public IntentionAction createChangeMethodSignatureFromUsageFix(
        PsiMethod targetMethod,
        PsiExpression[] expressions,
        PsiSubstitutor substitutor,
        PsiElement context,
        boolean changeAllUsages,
        int minUsagesNumberToShowDialog
    ) {
        return new ChangeMethodSignatureFromUsageFix(
            targetMethod,
            expressions,
            substitutor,
            context,
            changeAllUsages,
            minUsagesNumberToShowDialog
        );
    }

    @Override
    public IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(
        PsiMethod targetMethod,
        PsiExpression[] expressions,
        PsiSubstitutor substitutor,
        PsiElement context,
        boolean changeAllUsages,
        int minUsagesNumberToShowDialog
    ) {
        return new ChangeMethodSignatureFromUsageReverseOrderFix(
            targetMethod,
            expressions,
            substitutor,
            context,
            changeAllUsages,
            minUsagesNumberToShowDialog
        );
    }

    @Override
    public IntentionAction createCreateMethodFromUsageFix(PsiMethodCallExpression call) {
        return new CreateMethodFromUsageFix(call);
    }

    @Override
    public IntentionAction createCreateMethodFromUsageFix(PsiMethodReferenceExpression methodReferenceExpression) {
        return new CreateMethodFromMethodReferenceFix(methodReferenceExpression);
    }

    @Override
    public IntentionAction createCreateAbstractMethodFromUsageFix(PsiMethodCallExpression call) {
        return new CreateAbstractMethodFromUsageFix(call);
    }

    @Override
    public IntentionAction createCreatePropertyFromUsageFix(PsiMethodCallExpression call) {
        return new CreatePropertyFromUsageFix(call);
    }

    @Override
    public IntentionAction createCreateConstructorFromSuperFix(PsiMethodCallExpression call) {
        return new CreateConstructorFromSuperFix(call);
    }

    @Override
    public IntentionAction createCreateConstructorFromThisFix(PsiMethodCallExpression call) {
        return new CreateConstructorFromThisFix(call);
    }

    @Override
    public IntentionAction createCreateGetterSetterPropertyFromUsageFix(PsiMethodCallExpression call) {
        return new CreateGetterSetterPropertyFromUsageFix(call);
    }

    @Override
    public IntentionAction createStaticImportMethodFix(PsiMethodCallExpression call) {
        return new StaticImportMethodFix(call);
    }

    @Override
    public IntentionAction createReplaceAddAllArrayToCollectionFix(PsiMethodCallExpression call) {
        return new ReplaceAddAllArrayToCollectionFix(call);
    }

    @Override
    public IntentionAction createCreateConstructorFromCallFix(PsiConstructorCall call) {
        return new CreateConstructorFromCallFix(call);
    }

    @Override
    public List<IntentionAction> getVariableTypeFromCallFixes(PsiMethodCallExpression call, PsiExpressionList list) {
        return VariableTypeFromCallFix.getQuickFixActions(call, list);
    }

    @Override
    public IntentionAction createAddReturnFix(PsiMethod method) {
        return new AddReturnFix(method);
    }

    @Override
    public IntentionAction createAddVariableInitializerFix(PsiVariable variable) {
        return new AddVariableInitializerFix(variable);
    }

    @Override
    public IntentionAction createDeferFinalAssignmentFix(PsiVariable variable, PsiReferenceExpression expression) {
        return new DeferFinalAssignmentFix(variable, expression);
    }

    @Override
    public IntentionAction createVariableAccessFromInnerClassFix(PsiVariable variable, PsiElement scope) {
        return new VariableAccessFromInnerClassFix(variable, scope);
    }

    @Override
    public IntentionAction createCreateConstructorParameterFromFieldFix(PsiField field) {
        return new CreateConstructorParameterFromFieldFix(field);
    }

    @Override
    public IntentionAction createInitializeFinalFieldInConstructorFix(PsiField field) {
        return new InitializeFinalFieldInConstructorFix(field);
    }

    @Override
    public IntentionAction createRemoveTypeArgumentsFix(PsiElement variable) {
        return new RemoveTypeArgumentsFix(variable);
    }

    @Override
    public IntentionAction createChangeClassSignatureFromUsageFix(
        PsiClass owner,
        PsiReferenceParameterList parameterList
    ) {
        return new ChangeClassSignatureFromUsageFix(owner, parameterList);
    }

    @Override
    public IntentionAction createReplacePrimitiveWithBoxedTypeAction(
        PsiTypeElement element,
        String typeName,
        String boxedTypeName
    ) {
        return new ReplacePrimitiveWithBoxedTypeAction(element, typeName, boxedTypeName);
    }

    @Override
    public IntentionAction createMakeVarargParameterLastFix(PsiParameter parameter) {
        return new MakeVarargParameterLastFix(parameter);
    }

    @Override
    public IntentionAction createMoveBoundClassToFrontFix(PsiClass aClass, PsiClassType type) {
        return new MoveBoundClassToFrontFix(aClass, type);
    }

    @Override
    public void registerPullAsAbstractUpFixes(PsiMethod method, QuickFixActionRegistrar registrar) {
        PullAsAbstractUpFix.registerQuickFix(method, registrar);
    }

    @Override
    public IntentionAction createCreateAnnotationMethodFromUsageFix(PsiNameValuePair pair) {
        return new CreateAnnotationMethodFromUsageFix(pair);
    }

    @Override
    public IntentionAction createOptimizeImportsFix(final boolean onTheFly) {
        final OptimizeImportsFix fix = new OptimizeImportsFix();

        return new SyntheticIntentionAction() {
            @Override
            public LocalizeValue getText() {
                return fix.getText();
            }

            @Override
            @RequiredReadAction
            public boolean isAvailable(Project project, Editor editor, PsiFile file) {
                return (!onTheFly || timeToOptimizeImports(file)) && fix.isAvailable(project, editor, file);
            }

            @Override
            public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
                invokeOnTheFlyImportOptimizer(() -> fix.invoke(project, editor, file), file);
            }

            @Override
            public boolean startInWriteAction() {
                return fix.startInWriteAction();
            }
        };
    }

    @Override
    public void registerFixesForUnusedParameter(PsiParameter parameter, Object highlightInfo) {
        Project myProject = parameter.getProject();
        InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
        UnusedDeclarationInspectionBase unusedParametersInspection =
            (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedSymbolLocalInspectionBase.SHORT_NAME, parameter);
        LOG.assertTrue(parameter.getApplication().isUnitTestMode() || unusedParametersInspection != null);
        List<IntentionAction> options = new ArrayList<>();
        HighlightDisplayKey myUnusedSymbolKey = HighlightDisplayKey.find(UnusedSymbolLocalInspectionBase.SHORT_NAME);
        options.addAll(IntentionManager.getInstance().getStandardIntentionOptions(myUnusedSymbolKey, parameter));
        if (unusedParametersInspection != null) {
            SuppressQuickFix[] batchSuppressActions = unusedParametersInspection.getBatchSuppressActions(parameter);
            Collections.addAll(options, SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions));
        }
        //need suppress from Unused Parameters but settings from Unused Symbol
        QuickFixAction.registerQuickFixAction(
            (HighlightInfo)highlightInfo,
            new SafeDeleteFix(parameter),
            options,
            unusedParametersInspection.getDisplayName()
        );
    }

    @Override
    public IntentionAction createAddToDependencyInjectionAnnotationsFix(
        Project project,
        String qualifiedName,
        String element
    ) {
        EntryPointsManagerBase entryPointsManager = EntryPointsManagerBase.getInstance(project);
        return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(
            JavaQuickFixLocalize.fixUnusedSymbolInjectionText(element, qualifiedName),
            JavaQuickFixLocalize.fixUnusedSymbolInjectionFamily(),
            entryPointsManager.ADDITIONAL_ANNOTATIONS,
            qualifiedName
        );
    }

    @Override
    public IntentionAction createAddToImplicitlyWrittenFieldsFix(Project project, String qualifiedName) {
        EntryPointsManagerBase entryPointsManagerBase = EntryPointsManagerBase.getInstance(project);
        return entryPointsManagerBase.new AddImplicitlyWriteAnnotation(qualifiedName);
    }

    @Override
    public IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, PsiField field) {
        return new CreateGetterOrSetterFix(createGetter, createSetter, field);
    }

    @Override
    public IntentionAction createRenameToIgnoredFix(PsiNamedElement namedElement) {
        return new RenameToIgnoredFix(namedElement);
    }

    @Override
    public IntentionAction createEnableOptimizeImportsOnTheFlyFix() {
        return new EnableOptimizeImportsOnTheFlyFix();
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(PsiElement element) {
        return new DeleteElementFix(element);
    }

    public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(PsiElement element, LocalizeValue text) {
        return new DeleteElementFix(element, text);
    }

    @Override
    public IntentionAction createSafeDeleteFix(PsiElement element) {
        return new SafeDeleteFix(element);
    }

    @Nullable
    @Override
    @RequiredReadAction
    public List<LocalQuickFix> registerOrderEntryFixes(PsiReference reference) {
        return OrderEntryFix.registerFixes(reference);
    }

    private static void invokeOnTheFlyImportOptimizer(Runnable runnable, PsiFile file) {
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return;
        }
        long stamp = document.getModificationStamp();
        DumbService.getInstance(file.getProject()).smartInvokeLater(() -> {
            if (project.isDisposed() || document.getModificationStamp() != stamp) {
                return;
            }
            //no need to optimize imports on the fly during undo/redo
            UndoManager undoManager = ProjectUndoManager.getInstance(project);
            if (undoManager.isUndoInProgress() || undoManager.isRedoInProgress()) {
                return;
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            String beforeText = file.getText();
            long oldStamp = document.getModificationStamp();
            UndoUtil.writeInRunUndoTransparentAction(runnable);
            if (oldStamp != document.getModificationStamp()) {
                String afterText = file.getText();
                if (Comparing.strEqual(beforeText, afterText)) {
                    LOG.error(
                        "Import optimizer hasn't optimized any imports",
                        file.getViewProvider().getVirtualFile().getPath(),
                        AttachmentFactoryUtil.createAttachment(file.getViewProvider().getVirtualFile())
                    );
                }
            }
        });
    }

    @Override
    public IntentionAction createAddMissingRequiredAnnotationParametersFix(
        PsiAnnotation annotation,
        PsiMethod[] annotationMethods,
        Collection<String> missedElements
    ) {
        return new AddMissingRequiredAnnotationParametersFix(annotation, annotationMethods, missedElements);
    }

    @Override
    public IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(
        PsiAnnotationMemberValue value,
        PsiType expectedType
    ) {
        return new SurroundWithQuotesAnnotationParameterValueFix(value, expectedType);
    }

    @Override
    public IntentionAction addMethodQualifierFix(PsiMethodCallExpression methodCall) {
        return new AddMethodQualifierFix(methodCall);
    }

    @Override
    public IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, PsiExpression expression) {
        return new WrapLongWithMathToIntExactFix(type, expression);
    }

    @Override
    public IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, PsiExpression expression) {
        return WrapObjectWithOptionalOfNullableFix.createFix(type, expression);
    }

    @Nullable
    @Override
    public IntentionAction createNotIterableForEachLoopFix(PsiExpression expression) {
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiForeachStatement) {
            PsiType type = expression.getType();
            if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_ITERATOR)) {
                return new ReplaceIteratorForEachLoopWithIteratorForLoopFix((PsiForeachStatement)parent);
            }
        }
        return null;
    }

    @Override
    public List<IntentionAction> createAddAnnotationAttributeNameFixes(PsiNameValuePair pair) {
        return AddAnnotationAttributeNameFix.createFixes(pair);
    }

    @RequiredReadAction
    private static boolean timeToOptimizeImports(PsiFile file) {
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

    @RequiredReadAction
    private static boolean containsErrorsPreventingOptimize(PsiFile file) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            return true;
        }
        // ignore unresolved imports errors
        PsiImportList importList = ((PsiJavaFile)file).getImportList();
        TextRange importsRange = importList == null ? TextRange.EMPTY_RANGE : importList.getTextRange();
        boolean hasErrorsExceptUnresolvedImports = !DaemonCodeAnalyzer.processHighlights(
            document,
            file.getProject(),
            HighlightSeverity.ERROR,
            0,
            document.getTextLength(),
            error -> {
                int infoStart = error.getActualStartOffset();
                int infoEnd = error.getActualEndOffset();

                return importsRange.containsRange(infoStart, infoEnd) && error.getType().equals(HighlightInfoType.WRONG_REF);
            }
        );

        return hasErrorsExceptUnresolvedImports;
    }

    @Override
    public IntentionAction createCollectionToArrayFix(PsiExpression collectionExpression, PsiArrayType arrayType) {
        return new ConvertCollectionToArrayFix(collectionExpression, arrayType);
    }

    @Override
    public IntentionAction createInsertMethodCallFix(PsiMethodCallExpression call, PsiMethod method) {
        return new InsertMethodCallFix(call, method);
    }

    @Override
    public LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(
        PsiReferenceExpression methodRef,
        JavaResolveResult result
    ) {
        return new AccessStaticViaInstanceFix(methodRef, result, true);
    }

    @Override
    public IntentionAction createWrapStringWithFileFix(@Nullable PsiType type, PsiExpression expression) {
        return new WrapStringWithFileFix(type, expression);
    }

    @Override
    public IntentionAction createAddMissingEnumBranchesFix(PsiSwitchBlock switchBlock, Set<String> missingCases) {
        return new CreateMissingSwitchBranchesFix(switchBlock, missingCases);
    }

    @Override
    public IntentionAction createAddSwitchDefaultFix(PsiSwitchBlock switchBlock, LocalizeValue message) {
        return new CreateDefaultBranchFix(switchBlock, message);
    }

    @Override
    public IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement rule) {
        return new WrapSwitchRuleStatementsIntoBlockFix(rule);
    }

    @Override
    public IntentionAction createDeleteSideEffectAwareFix(PsiExpressionStatement statement) {
        return new DeleteSideEffectsAwareFix(statement, statement.getExpression());
    }
}
