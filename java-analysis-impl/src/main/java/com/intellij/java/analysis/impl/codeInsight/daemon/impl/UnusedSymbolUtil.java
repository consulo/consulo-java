/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl;

import com.intellij.java.analysis.codeInspection.UnusedDeclarationFixProvider;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.analysis.impl.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.java.analysis.impl.find.findUsages.*;
import com.intellij.java.language.impl.psi.impl.FindSuperElementsHelper;
import com.intellij.java.language.impl.psi.impl.source.PsiClassImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.content.scope.SearchScope;
import consulo.find.FindUsagesOptions;
import consulo.java.language.impl.psi.augment.JavaEnumAugmentProvider;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.resolve.RefResolveService;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Predicate;

import static consulo.language.psi.search.PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;

public class UnusedSymbolUtil {
    public static boolean isInjected(@Nonnull Project project, @Nonnull PsiModifierListOwner modifierListOwner) {
        return EntryPointsManagerBase.getInstance(project).isEntryPoint(modifierListOwner);
    }

    public static boolean isImplicitUsage(
        @Nonnull Project project,
        @Nonnull PsiModifierListOwner element,
        @Nonnull ProgressIndicator progress
    ) {
        return isInjected(project, element) || project.getExtensionPoint(ImplicitUsageProvider.class).findFirstSafe(provider -> {
            progress.checkCanceled();
            return provider.isImplicitUsage(element);
        }) != null;
    }

    public static boolean isImplicitRead(@Nonnull PsiVariable variable) {
        return isImplicitRead(variable.getProject(), variable, null);
    }

    public static boolean isImplicitRead(@Nonnull Project project, @Nonnull PsiVariable element, @Nullable ProgressIndicator progress) {
        return project.getExtensionPoint(ImplicitUsageProvider.class).anyMatchSafe(provider -> {
            ProgressManager.checkCanceled();
            return provider.isImplicitRead(element);
        }) || isInjected(project, element);
    }

    public static boolean isImplicitWrite(@Nonnull PsiVariable variable) {
        return isImplicitWrite(variable.getProject(), variable, null);
    }

    public static boolean isImplicitWrite(@Nonnull Project project, @Nonnull PsiVariable element, @Nullable ProgressIndicator progress) {
        return project.getExtensionPoint(ImplicitUsageProvider.class).anyMatchSafe(provider -> {
            ProgressManager.checkCanceled();
            return provider.isImplicitWrite(element);
        }) || isInjected(project, element);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo createUnusedSymbolInfo(
        @Nonnull PsiElement element,
        @Nonnull String message,
        @Nonnull HighlightInfoType highlightInfoType
    ) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(highlightInfoType)
            .range(element)
            .descriptionAndTooltip(message).create();

        if (info == null) {
            return null; //filtered out
        }

        for (UnusedDeclarationFixProvider provider : UnusedDeclarationFixProvider.EP_NAME.getExtensionList()) {
            IntentionAction[] fixes = provider.getQuickFixes(element);
            for (IntentionAction fix : fixes) {
                info.registerFix(fix, null, null, null, null);
            }
        }
        return info;
    }

    @RequiredReadAction
    public static boolean isFieldUnused(
        @Nonnull Project project,
        @Nonnull PsiFile containingFile,
        @Nonnull PsiField field,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        if (helper.isLocallyUsed(field)) {
            return false;
        }
        //noinspection SimplifiableIfStatement
        if (field instanceof PsiEnumConstant && isEnumValuesMethodUsed(project, containingFile, field, progress, helper)) {
            return false;
        }
        return weAreSureThereAreNoUsages(project, containingFile, field, progress, helper);
    }

    @RequiredReadAction
    public static boolean isMethodReferenced(
        @Nonnull Project project,
        @Nonnull PsiFile containingFile,
        @Nonnull PsiMethod method,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        if (helper.isLocallyUsed(method)) {
            return true;
        }

        boolean isPrivate = method.isPrivate();
        PsiClass containingClass = method.getContainingClass();
        if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) {
            return true;
        }
        if (isPrivate) {
            if (isIntentionalPrivateConstructor(method, containingClass) || isImplicitUsage(project, method, progress)) {
                return true;
            }
            if (!helper.isCurrentFileAlreadyChecked()) {
                return !weAreSureThereAreNoUsages(project, containingFile, method, progress, helper);
            }
        }
        else {
            //class maybe used in some weird way, e.g. from XML, therefore the only constructor is used too
            boolean isConstructor = method.isConstructor();
            if (containingClass != null && isConstructor && containingClass.getConstructors().length == 1 &&
                isClassUsed(project, containingFile, containingClass, progress, helper)) {
                return true;
            }
            if (isImplicitUsage(project, method, progress)) {
                return true;
            }

            if (!isConstructor && FindSuperElementsHelper.findSuperElements(method).length != 0) {
                return true;
            }
            if (!weAreSureThereAreNoUsages(project, containingFile, method, progress, helper)) {
                return true;
            }
        }
        return false;
    }

    @RequiredReadAction
    private static boolean weAreSureThereAreNoUsages(
        @Nonnull Project project,
        @Nonnull PsiFile containingFile,
        @Nonnull PsiMember member,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        log("* " + member.getName() + ": call wearesure");
        if (!helper.shouldCheckUsages(member)) {
            log("* " + member.getName() + ": should not check");
            return false;
        }

        PsiFile ignoreFile = helper.isCurrentFileAlreadyChecked() ? containingFile : null;

        boolean sure = processUsages(
            project,
            containingFile,
            member,
            progress,
            ignoreFile,
            info -> {
                PsiFile psiFile = info.getFile();
                if (psiFile == ignoreFile || psiFile == null) {
                    return true; // ignore usages in containingFile because isLocallyUsed() method would have caught
                    // that
                }
                int offset = info.getNavigationOffset();
                if (offset == -1) {
                    return true;
                }
                PsiElement element = psiFile.findElementAt(offset);
                boolean inComment = element instanceof PsiComment;
                log("*     " + member.getName() + ": usage :" + element);
                return inComment; // ignore comments
            }
        );
        log("*     " + member.getName() + ": result:" + sure);
        return sure;
    }

    private static void log(String s) {
        //System.out.println(s);
    }

    // return false if can't process usages (weird member of too may usages) or processor returned false
    @RequiredReadAction
    public static boolean processUsages(
        @Nonnull Project project,
        @Nonnull PsiFile containingFile,
        @Nonnull PsiMember member,
        @Nonnull ProgressIndicator progress,
        @Nullable PsiFile ignoreFile,
        @Nonnull @RequiredReadAction Predicate<UsageInfo> usageInfoProcessor
    ) {
        String name = member.getName();
        if (name == null) {
            log("* " + member.getName() + " no name; false");
            return false;
        }
        SearchScope useScope = member.getUseScope();
        PsiSearchHelper searchHelper = project.getInstance(PsiSearchHelper.class);
        if (useScope instanceof GlobalSearchScope globalSearchScope) {
            // some classes may have references from within XML outside dependent modules, e.g. our actions
            if (member instanceof PsiClass) {
                useScope = GlobalSearchScope.projectScope(project).uniteWith(globalSearchScope);
            }

            // if we've resolved all references, find usages will be fast
            PsiSearchHelper.SearchCostResult cheapEnough = RefResolveService.ENABLED && RefResolveService.getInstance
                (project).isUpToDate() ? PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES : searchHelper
                .isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, ignoreFile, progress);
            if (cheapEnough == TOO_MANY_OCCURRENCES) {
                log("* " + member.getName() + " too many usages; false");
                return false;
            }

            //search usages if it cheap
            //if count is 0 there is no usages since we've called myRefCountHolder.isReferenced() before
            if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES && !canBeReferencedViaWeirdNames
                (member, containingFile)) {
                log("* " + member.getName() + " 0 usages; true");
                return true;
            }

            if (member instanceof PsiMethod) {
                String propertyName = PropertyUtil.getPropertyName(member);
                if (propertyName != null) {
                    SearchScope fileScope = containingFile.getUseScope();
                    if (fileScope instanceof GlobalSearchScope
                        && searchHelper.isCheapEnoughToSearch(
                        propertyName,
                        (GlobalSearchScope)fileScope,
                        ignoreFile,
                        progress
                    ) == TOO_MANY_OCCURRENCES) {
                        log("* " + member.getName() + " too many prop usages; false");
                        return false;
                    }
                }
            }
        }
        FindUsagesOptions options;
        if (member instanceof PsiPackage) {
            options = new JavaPackageFindUsagesOptions(project);
            options.isSearchForTextOccurrences = true;
        }
        else if (member instanceof PsiClass) {
            options = new JavaClassFindUsagesOptions(project);
            options.isSearchForTextOccurrences = true;
        }
        else if (member instanceof PsiMethod method) {
            options = new JavaMethodFindUsagesOptions(project);
            options.isSearchForTextOccurrences = method.isConstructor();
        }
        else if (member instanceof PsiVariable) {
            options = new JavaVariableFindUsagesOptions(project);
            options.isSearchForTextOccurrences = false;
        }
        else {
            options = new FindUsagesOptions(project);
            options.isSearchForTextOccurrences = true;
        }
        options.isUsages = true;
        options.searchScope = useScope;
        return JavaFindUsagesHelper.processElementUsages(member, options, usageInfoProcessor);
    }

    @RequiredReadAction
    private static boolean isEnumValuesMethodUsed(
        @Nonnull Project project,
        @Nonnull PsiFile containingFile,
        @Nonnull PsiMember member,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        PsiClass containingClass = member.getContainingClass();
        if (!(containingClass instanceof PsiClassImpl)) {
            return true;
        }

        if (containingClass.isEnum()) {
            PsiMethod[] methodsByName = containingClass.findMethodsByName(
                JavaEnumAugmentProvider.VALUES_METHOD_NAME,
                false
            );

            PsiMethod valuesMethod = null;
            for (PsiMethod psiMethod : methodsByName) {
                if (psiMethod.getParameterList().getParametersCount() == 0 && psiMethod.isStatic()) {
                    valuesMethod = psiMethod;
                    break;
                }
            }
            return valuesMethod == null || isMethodReferenced(project, containingFile, valuesMethod, progress, helper);
        }
        else {
            return true;
        }
    }

    private static boolean canBeReferencedViaWeirdNames(@Nonnull PsiMember member, @Nonnull PsiFile containingFile) {
        if (member instanceof PsiClass) {
            return false;
        }
        if (!(containingFile instanceof PsiJavaFile)) {
            return true;  // Groovy field can be referenced from Java by getter
        }
        if (member instanceof PsiField) {
            return false;  //Java field cannot be referenced by anything but its name
        }
        //noinspection SimplifiableIfStatement
        if (member instanceof PsiMethod method) {
            return PropertyUtil.isSimplePropertyAccessor(method);  //Java accessors can be referenced by field name from Groovy
        }
        return false;
    }

    @RequiredReadAction
    public static boolean isClassUsed(
        @Nonnull Project project,
        @Nonnull PsiFile containingFile,
        @Nonnull PsiClass aClass,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        Boolean result = helper.unusedClassCache.get(aClass);
        if (result == null) {
            result = isReallyUsed(project, containingFile, aClass, progress, helper);
            helper.unusedClassCache.put(aClass, result);
        }
        return result;
    }

    @RequiredReadAction
    private static boolean isReallyUsed(
        @Nonnull Project project,
        @Nonnull PsiFile containingFile,
        @Nonnull PsiClass aClass,
        @Nonnull ProgressIndicator progress,
        @Nonnull GlobalUsageHelper helper
    ) {
        if (isImplicitUsage(project, aClass, progress) || helper.isLocallyUsed(aClass)) {
            return true;
        }
        if (helper.isCurrentFileAlreadyChecked()) {
            if (aClass.getContainingClass() != null && aClass.isPrivate()
                || aClass.getParent() instanceof PsiDeclarationStatement
                || aClass instanceof PsiTypeParameter) {
                return false;
            }
        }
        return !weAreSureThereAreNoUsages(project, containingFile, aClass, progress, helper);
    }

    private static boolean isIntentionalPrivateConstructor(@Nonnull PsiMethod method, PsiClass containingClass) {
        return method.isConstructor() &&
            containingClass != null &&
            containingClass.getConstructors().length == 1;
    }
}
