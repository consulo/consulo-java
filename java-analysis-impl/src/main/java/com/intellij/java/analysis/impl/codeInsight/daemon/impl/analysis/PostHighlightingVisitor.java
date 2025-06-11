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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.daemon.JavaImplicitUsageProvider;
import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionState;
import com.intellij.java.analysis.impl.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.java.analysis.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.component.ProcessCanceledException;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.language.Language;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.FileStatusMap;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.highlight.HighlightingLevelManager;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.EmptyIntentionAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoHolder;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.editor.util.CollectHighlightsUtil;
import consulo.language.file.FileViewProvider;
import consulo.language.pom.PomNamedTarget;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class PostHighlightingVisitor {
    private static final Logger LOG = Logger.getInstance(PostHighlightingVisitor.class);
    private final RefCountHolder myRefCountHolder;
    @Nonnull
    private final Project myProject;
    private final PsiFile myFile;
    @Nonnull
    private final Document myDocument;

    private boolean myHasRedundantImports;
    private int myCurrentEntryIndex;
    private boolean myHasMissortedImports;
    private final UnusedSymbolLocalInspectionBase myUnusedSymbolInspection;
    private final HighlightDisplayKey myDeadCodeKey;
    private final HighlightInfoType myDeadCodeInfoType;
    private final UnusedDeclarationInspectionBase myDeadCodeInspection;
    private final UnusedDeclarationInspectionState myDeadCodeState;

    private void optimizeImportsOnTheFlyLater(@Nonnull ProgressIndicator progress) {
        if ((myHasRedundantImports || myHasMissortedImports) && !progress.isCanceled()) {
            // schedule optimise action at the time of session disposal, which is after all applyInformation() calls
            Disposable invokeFixLater = () -> {
                // later because should invoke when highlighting is finished
                myProject.getUIAccess().give(() -> {
                    if (!myFile.isValid() || !myFile.isWritable()) {
                        return;
                    }
                    IntentionAction optimizeImportsFix = QuickFixFactory.getInstance().createOptimizeImportsFix(true);
                    if (optimizeImportsFix.isAvailable(myProject, null, myFile)) {
                        optimizeImportsFix.invoke(myProject, null, myFile);
                    }
                });
            };
            try {
                Disposer.register((Disposable)progress, invokeFixLater);
            }
            catch (Exception ignored) {
                // suppress "parent already has been disposed" exception here
            }
            if (progress.isCanceled()) {
                Disposer.dispose(invokeFixLater);
                Disposer.dispose((Disposable)progress);
                progress.checkCanceled();
            }
        }
    }

    @RequiredReadAction
    public PostHighlightingVisitor(
        @Nonnull PsiFile file,
        @Nonnull Document document,
        @Nonnull RefCountHolder refCountHolder
    ) throws ProcessCanceledException {
        myProject = file.getProject();
        myFile = file;
        myDocument = document;

        myCurrentEntryIndex = -1;

        myRefCountHolder = refCountHolder;

        myProject.getApplication().assertReadAccessAllowed();

        InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();

        myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspectionBase.SHORT_NAME);

        myDeadCodeInspection =
            (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, myFile);
        myDeadCodeState = profile.getToolState(UnusedDeclarationInspectionBase.SHORT_NAME, myFile);

        LOG.assertTrue(myDeadCodeInspection != null);

        myUnusedSymbolInspection = myDeadCodeInspection != null ? myDeadCodeInspection.getSharedLocalInspectionTool() : null;

        myDeadCodeInfoType = myDeadCodeKey == null
            ? HighlightInfoType.UNUSED_SYMBOL
            : new HighlightInfoType.HighlightInfoTypeImpl(
                profile.getErrorLevel(myDeadCodeKey, myFile).getSeverity(),
                HighlightInfoType.UNUSED_SYMBOL.getAttributesKey()
            );
    }

    @RequiredReadAction
    public void collectHighlights(@Nonnull HighlightInfoHolder result, @Nonnull ProgressIndicator progress) {
        DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
        FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
        InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();

        boolean unusedSymbolEnabled = profile.isToolEnabled(myDeadCodeKey, myFile);
        GlobalUsageHelper globalUsageHelper = myRefCountHolder.getGlobalUsageHelper(myFile, myDeadCodeInspection, myDeadCodeState);

        boolean errorFound = false;

        if (unusedSymbolEnabled) {
            FileViewProvider viewProvider = myFile.getViewProvider();
            Set<Language> relevantLanguages = viewProvider.getLanguages();
            for (Language language : relevantLanguages) {
                progress.checkCanceled();
                PsiElement psiRoot = viewProvider.getPsi(language);
                if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(psiRoot)) {
                    continue;
                }
                List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, 0, myFile.getTextLength());
                for (PsiElement element : elements) {
                    progress.checkCanceled();
                    if (element instanceof PsiIdentifier identifier) {
                        HighlightInfo info = processIdentifier(identifier, progress, globalUsageHelper);
                        if (info != null) {
                            errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
                            result.add(info);
                        }
                    }
                }
            }
        }

        HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME);
        if (isUnusedImportEnabled(unusedImportKey)) {
            PsiImportList importList = ((PsiJavaFile)myFile).getImportList();
            if (importList != null) {
                PsiImportStatementBase[] imports = importList.getAllImportStatements();
                for (PsiImportStatementBase statement : imports) {
                    progress.checkCanceled();
                    HighlightInfo info = processImport(statement, unusedImportKey);
                    if (info != null) {
                        errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
                        result.add(info);
                    }
                }
            }
        }

        if (errorFound) {
            fileStatusMap.setErrorFoundFlag(myProject, myDocument, true);
        }

        optimizeImportsOnTheFlyLater(progress);
    }

    private boolean isUnusedImportEnabled(HighlightDisplayKey unusedImportKey) {
        InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
        //noinspection SimplifiableIfStatement
        if (profile.isToolEnabled(unusedImportKey, myFile) && myFile instanceof PsiJavaFile
            && HighlightingLevelManager.getInstance(myProject).shouldInspect(myFile)) {
            return true;
        }

        return myProject.getExtensionPoint(ImplicitUsageProvider.class).anyMatchSafe(
            implicitUsageProvider -> implicitUsageProvider instanceof JavaImplicitUsageProvider javaImplicitUsageProvider
                && javaImplicitUsageProvider.isUnusedImportEnabled(myFile)
        );
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo processIdentifier(
        @Nonnull PsiIdentifier identifier,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        PsiElement parent = identifier.getParent();
        if (!(parent instanceof PsiVariable || parent instanceof PsiMember)) {
            return null;
        }

        if (SuppressionUtil.inspectionResultSuppressed(identifier, myUnusedSymbolInspection)) {
            return null;
        }

        if (parent instanceof PsiLocalVariable localVariable && myUnusedSymbolInspection.LOCAL_VARIABLE) {
            return processLocalVariable(localVariable, identifier, progress);
        }
        if (parent instanceof PsiField field && compareVisibilities(field, myUnusedSymbolInspection.getFieldVisibility())) {
            return processField(myProject, field, identifier, progress, helper);
        }
        if (parent instanceof PsiParameter parameter) {
            PsiElement declarationScope = parameter.getDeclarationScope();
            if (declarationScope instanceof PsiMethod method
                ? compareVisibilities(method, myUnusedSymbolInspection.getParameterVisibility())
                : myUnusedSymbolInspection.LOCAL_VARIABLE) {
                if (SuppressionUtil.isSuppressed(identifier, UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME)) {
                    return null;
                }
                return processParameter(myProject, parameter, identifier, progress);
            }
        }
        if (parent instanceof PsiMethod method) {
            if (myUnusedSymbolInspection.isIgnoreAccessors() && PropertyUtil.isSimplePropertyAccessor(method)) {
                return null;
            }
            if (compareVisibilities(method, myUnusedSymbolInspection.getMethodVisibility())) {
                return processMethod(myProject, method, identifier, progress, helper);
            }
        }
        if (parent instanceof PsiClass psiClass) {
            String acceptedVisibility = psiClass.getContainingClass() == null
                ? myUnusedSymbolInspection.getClassVisibility()
                : myUnusedSymbolInspection.getInnerClassVisibility();
            if (compareVisibilities(psiClass, acceptedVisibility)) {
                return processClass(myProject, psiClass, identifier, progress, helper);
            }
        }
        return null;
    }

    private static boolean compareVisibilities(PsiModifierListOwner listOwner, String visibility) {
        if (visibility != null) {
            while (listOwner != null) {
                if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(listOwner.getModifierList()), visibility) >= 0) {
                    return true;
                }
                listOwner = PsiTreeUtil.getParentOfType(listOwner, PsiModifierListOwner.class, true);
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo processLocalVariable(
        @Nonnull PsiLocalVariable variable,
        @Nonnull PsiIdentifier identifier,
        @Nonnull ProgressIndicator progress
    ) {
        if (variable instanceof PsiResourceVariable && PsiUtil.isIgnoredName(variable.getName())) {
            return null;
        }
        if (UnusedSymbolUtil.isImplicitUsage(myProject, variable, progress)) {
            return null;
        }

        if (!myRefCountHolder.isReferenced(variable)) {
            LocalizeValue message = JavaErrorLocalize.localVariableIsNeverUsed(identifier.getText());
            HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
            IntentionAction fix = variable instanceof PsiResourceVariable
                ? QuickFixFactory.getInstance().createRenameToIgnoredFix(variable)
                : QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable);
            QuickFixAction.registerQuickFixAction(highlightInfo, fix, myDeadCodeKey);
            return highlightInfo;
        }

        boolean referenced = myRefCountHolder.isReferencedForRead(variable);
        if (!referenced && !UnusedSymbolUtil.isImplicitRead(myProject, variable, progress)) {
            LocalizeValue message = JavaErrorLocalize.localVariableIsNotUsedForReading(identifier.getText());
            HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable),
                myDeadCodeKey
            );
            return highlightInfo;
        }

        if (!variable.hasInitializer()) {
            referenced = myRefCountHolder.isReferencedForWrite(variable);
            if (!referenced && !UnusedSymbolUtil.isImplicitWrite(myProject, variable, progress)) {
                LocalizeValue message = JavaErrorLocalize.localVariableIsNotAssigned(identifier.getText());
                HighlightInfo unusedSymbolInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
                QuickFixAction.registerQuickFixAction(
                    unusedSymbolInfo,
                    new EmptyIntentionAction(UnusedSymbolLocalInspectionBase.DISPLAY_NAME),
                    myDeadCodeKey
                );
                return unusedSymbolInfo;
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo processField(
        @Nonnull Project project,
        @Nonnull PsiField field,
        @Nonnull PsiIdentifier identifier,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
            return null;
        }
        if (field.isPrivate()) {
            QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
            if (!myRefCountHolder.isReferenced(field) && !UnusedSymbolUtil.isImplicitUsage(myProject, field, progress)) {
                LocalizeValue message = JavaErrorLocalize.privateFieldIsNotUsed(identifier.getText());

                HighlightInfo highlightInfo = suggestionsToMakeFieldUsed(field, identifier, message);
                if (!field.hasInitializer() && !field.isFinal()) {
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        HighlightMethodUtil.getFixRange(field),
                        quickFixFactory.createCreateConstructorParameterFromFieldFix(field)
                    );
                }
                return highlightInfo;
            }

            boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
            if (!readReferenced && !UnusedSymbolUtil.isImplicitRead(project, field, progress)) {
                LocalizeValue message = JavaErrorLocalize.privateFieldIsNotUsedForReading(identifier.getText());
                return suggestionsToMakeFieldUsed(field, identifier, message);
            }

            if (field.hasInitializer()) {
                return null;
            }
            boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
            if (!writeReferenced && !UnusedSymbolUtil.isImplicitWrite(project, field, progress)) {
                LocalizeValue message = JavaErrorLocalize.privateFieldIsNotAssigned(identifier.getText());
                HighlightInfo info = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);

                QuickFixAction.registerQuickFixAction(
                    info,
                    quickFixFactory.createCreateGetterOrSetterFix(false, true, field),
                    myDeadCodeKey
                );
                if (!field.isFinal()) {
                    QuickFixAction.registerQuickFixAction(
                        info,
                        HighlightMethodUtil.getFixRange(field),
                        quickFixFactory.createCreateConstructorParameterFromFieldFix(field)
                    );
                }
                SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(
                    field,
                    annoName -> {
                        QuickFixAction.registerQuickFixAction(
                            info,
                            quickFixFactory.createAddToImplicitlyWrittenFieldsFix(project, annoName)
                        );
                        return true;
                    }
                );
                return info;
            }
        }
        else if (UnusedSymbolUtil.isImplicitUsage(myProject, field, progress)
            && !UnusedSymbolUtil.isImplicitWrite(myProject, field, progress)) {
            return null;
        }
        else if (UnusedSymbolUtil.isFieldUnused(myProject, myFile, field, progress, helper)) {
            if (UnusedSymbolUtil.isImplicitWrite(myProject, field, progress)) {
                LocalizeValue message = JavaErrorLocalize.privateFieldIsNotUsedForReading(identifier.getText());
                HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createSafeDeleteFix(field),
                    myDeadCodeKey
                );
                return highlightInfo;
            }
            return formatUnusedSymbolHighlightInfo(
                project,
                "field.is.not.used",
                field,
                "fields",
                myDeadCodeKey,
                myDeadCodeInfoType,
                identifier
            );
        }
        return null;
    }

    @RequiredReadAction
    private HighlightInfo suggestionsToMakeFieldUsed(
        @Nonnull PsiField field,
        @Nonnull PsiIdentifier identifier,
        @Nonnull LocalizeValue message
    ) {
        HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            QuickFixFactory.getInstance().createRemoveUnusedVariableFix(field),
            myDeadCodeKey
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, false, field),
            myDeadCodeKey
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field),
            myDeadCodeKey
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, true, field),
            myDeadCodeKey
        );
        return highlightInfo;
    }

    private final Map<PsiMethod, Boolean> isOverriddenOrOverrides = ConcurrentFactoryMap.createMap(method -> {
        boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
        return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
    });

    private boolean isOverriddenOrOverrides(@Nonnull PsiMethod method) {
        return isOverriddenOrOverrides.get(method);
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo processParameter(
        @Nonnull Project project,
        @Nonnull PsiParameter parameter,
        @Nonnull PsiIdentifier identifier,
        @Nonnull ProgressIndicator progress
    ) {
        PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod method) {
            if (PsiUtilCore.hasErrorElementChild(method)) {
                return null;
            }
            if ((method.isConstructor() || method.isPrivate() || method.isStatic()
                || !method.isAbstract() && !isOverriddenOrOverrides(method))
                && !method.hasModifierProperty(PsiModifier.NATIVE)
                && !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass())
                && !PsiClassImplUtil.isMainOrPremainMethod(method)) {
                if (UnusedSymbolUtil.isInjected(project, method)) {
                    return null;
                }
                HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
                if (highlightInfo != null) {
                    QuickFixFactory.getInstance().registerFixesForUnusedParameter(parameter, highlightInfo);
                    return highlightInfo;
                }
            }
        }
        else if (declarationScope instanceof PsiForeachStatement && !PsiUtil.isIgnoredName(parameter.getName())) {
            HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
            if (highlightInfo != null) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createRenameToIgnoredFix(parameter),
                    myDeadCodeKey
                );
                return highlightInfo;
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo checkUnusedParameter(
        @Nonnull PsiParameter parameter,
        @Nonnull PsiIdentifier identifier,
        @Nonnull ProgressIndicator progress
    ) {
        if (!myRefCountHolder.isReferenced(parameter) && !UnusedSymbolUtil.isImplicitUsage(myProject, parameter, progress)) {
            LocalizeValue message = JavaErrorLocalize.parameterIsNotUsed(identifier.getText());
            return UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo processMethod(
        @Nonnull Project project,
        @Nonnull PsiMethod method,
        @Nonnull PsiIdentifier identifier,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        if (UnusedSymbolUtil.isMethodReferenced(myProject, myFile, method, progress, helper)) {
            return null;
        }
        Function<LocalizeValue, LocalizeValue> key;
        if (method.isPrivate()) {
            key = method.isConstructor() ? JavaErrorLocalize::privateConstructorIsNotUsed : JavaErrorLocalize::privateMethodIsNotUsed;
        }
        else {
            key = method.isConstructor() ? JavaErrorLocalize::constructorIsNotUsed : JavaErrorLocalize::methodIsNotUsed;
        }
        LocalizeValue symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
        LocalizeValue message = key.apply(symbolName);
        HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(method), myDeadCodeKey);
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(
            method,
            annoName -> {
                IntentionAction fix =
                    QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName, "methods");
                QuickFixAction.registerQuickFixAction(highlightInfo, fix);
                return true;
            }
        );
        return highlightInfo;
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo processClass(
        @Nonnull Project project,
        @Nonnull PsiClass aClass,
        @Nonnull PsiIdentifier identifier,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        if (UnusedSymbolUtil.isClassUsed(project, myFile, aClass, progress, helper)) {
            return null;
        }

        String pattern;
        if (aClass.getContainingClass() != null && aClass.isPrivate()) {
            pattern = aClass.isInterface() ? "private.inner.interface.is.not.used" : "private.inner.class.is.not.used";
        }
        else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
            pattern = "local.class.is.not.used";
        }
        else if (aClass instanceof PsiTypeParameter) {
            pattern = "type.parameter.is.not.used";
        }
        else {
            pattern = "class.is.not.used";
        }
        return formatUnusedSymbolHighlightInfo(myProject, pattern, aClass, "classes", myDeadCodeKey, myDeadCodeInfoType, identifier);
    }

    @RequiredReadAction
    private static HighlightInfo formatUnusedSymbolHighlightInfo(
        @Nonnull Project project,
        @Nonnull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String pattern,
        @Nonnull PsiNameIdentifierOwner aClass,
        @Nonnull String element,
        HighlightDisplayKey highlightDisplayKey,
        @Nonnull HighlightInfoType highlightInfoType,
        @Nonnull PsiElement identifier
    ) {
        String symbolName = aClass.getName();
        String message = JavaErrorBundle.message(pattern, symbolName);
        HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, highlightInfoType);
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            QuickFixFactory.getInstance().createSafeDeleteFix(aClass),
            highlightDisplayKey
        );
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(
            (PsiModifierListOwner)aClass,
            annoName -> {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName, element)
                );
                return true;
            }
        );
        return highlightInfo;
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo processImport(@Nonnull PsiImportStatementBase importStatement, @Nonnull HighlightDisplayKey unusedImportKey) {
        // jsp include directive hack
        if (importStatement.isForeignFileImport()) {
            return null;
        }

        if (PsiUtilCore.hasErrorElementChild(importStatement)) {
            return null;
        }

        boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
        if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
            //check import from same package
            String packageName = ((PsiClassOwner)importStatement.getContainingFile()).getPackageName();
            PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
            PsiElement resolved = reference == null ? null : reference.resolve();
            if (resolved instanceof PsiPackage psiPackage) {
                isRedundant = packageName.equals(psiPackage.getQualifiedName());
            }
            else if (resolved instanceof PsiClass psiClass && !importStatement.isOnDemand()) {
                String qName = psiClass.getQualifiedName();
                if (qName != null) {
                    String name = ((PomNamedTarget)resolved).getName();
                    isRedundant = qName.equals(packageName + '.' + name);
                }
            }
        }

        if (isRedundant) {
            return registerRedundantImport(importStatement, unusedImportKey);
        }

        int entryIndex = JavaCodeStyleManager.getInstance(myProject).findEntryIndex(importStatement);
        if (entryIndex < myCurrentEntryIndex) {
            myHasMissortedImports = true;
        }
        myCurrentEntryIndex = entryIndex;

        return null;
    }

    @RequiredReadAction
    private HighlightInfo registerRedundantImport(
        @Nonnull PsiImportStatementBase importStatement,
        @Nonnull HighlightDisplayKey unusedImportKey
    ) {
        String description = InspectionLocalize.unusedImportStatement().get();
        HighlightInfo info = HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.UNUSED_IMPORT)
            .range(importStatement)
            .descriptionAndTooltip(description)
            .create();

        QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createOptimizeImportsFix(false), unusedImportKey);
        QuickFixAction.registerQuickFixAction(
            info,
            QuickFixFactory.getInstance().createEnableOptimizeImportsOnTheFlyFix(),
            unusedImportKey
        );
        myHasRedundantImports = true;
        return info;
    }
}
