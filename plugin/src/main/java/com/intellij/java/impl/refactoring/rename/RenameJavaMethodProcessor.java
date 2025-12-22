/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.content.scope.SearchScope;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;

@ExtensionImpl(id = "javamethod")
public class RenameJavaMethodProcessor extends RenameJavaMemberProcessor {
    private static final Logger LOG = Logger.getInstance(RenameJavaMethodProcessor.class);

    @Override
    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof PsiMethod;
    }

    @Override
    @RequiredWriteAction
    public void renameElement(PsiElement psiElement, String newName, UsageInfo[] usages, @Nullable RefactoringElementListener listener)
        throws IncorrectOperationException {
        PsiMethod method = (PsiMethod) psiElement;
        Set<PsiMethod> methodAndOverriders = new HashSet<>();
        Set<PsiClass> containingClasses = new HashSet<>();
        Set<PsiElement> renamedReferences = new LinkedHashSet<>();
        List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<>();
        List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<>();

        methodAndOverriders.add(method);
        containingClasses.add(method.getContainingClass());

        // do actual rename of overriding/implementing methods and of references to all them
        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element == null) {
                continue;
            }

            if (usage instanceof MemberHidesStaticImportUsageInfo memberHidesStaticImportUsageInfo) {
                staticImportHides.add(memberHidesStaticImportUsageInfo);
            }
            else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
                PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement) element;
                PsiMethod resolved = (PsiMethod) collidingRef.resolve();
                outerHides.add(new MemberHidesOuterMemberUsageInfo(element, resolved));
            }
            else if (!(element instanceof PsiMethod)) {
                PsiReference ref = usage instanceof MoveRenameUsageInfo ? usage.getReference() : element.getReference();
                if (ref instanceof PsiImportStaticReferenceElement importStaticRef && importStaticRef.multiResolve(false).length > 1) {
                    continue;
                }
                if (ref != null) {
                    PsiElement e = processRef(ref, newName);
                    if (e != null) {
                        renamedReferences.add(e);
                    }
                }
            }
            else {
                PsiMethod overrider = (PsiMethod) element;
                methodAndOverriders.add(overrider);
                containingClasses.add(overrider.getContainingClass());
            }
        }

        // do actual rename of method
        method.setName(newName);
        for (UsageInfo usage : usages) {
            if (usage.getElement() instanceof PsiMethod usageMethod) {
                usageMethod.setName(newName);
            }
        }
        if (listener != null) {
            listener.elementRenamed(method);
        }

        for (PsiElement element : renamedReferences) {
            fixNameCollisionsWithInnerClassMethod(
                element,
                newName,
                methodAndOverriders,
                containingClasses,
                method.isStatic()
            );
        }
        qualifyOuterMemberReferences(outerHides);
        qualifyStaticImportReferences(staticImportHides);
    }

    /**
     * handles rename of refs
     */
    @Nullable
    @RequiredWriteAction
    protected PsiElement processRef(PsiReference ref, String newName) {
        return ref.handleElementRename(newName);
    }

    @RequiredWriteAction
    private static void fixNameCollisionsWithInnerClassMethod(
        PsiElement element,
        String newName,
        Set<PsiMethod> methodAndOverriders,
        Set<PsiClass> containingClasses,
        boolean isStatic
    ) throws IncorrectOperationException {
        if (element instanceof PsiReferenceExpression refExpr
            && refExpr.resolve() instanceof PsiMethod actualMethod
            && !methodAndOverriders.contains(actualMethod)) {
            PsiClass outerClass = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
            while (outerClass != null) {
                if (containingClasses.contains(outerClass)) {
                    qualifyMember(refExpr, newName, outerClass, isStatic);
                    break;
                }
                outerClass = PsiTreeUtil.getParentOfType(outerClass, PsiClass.class);
            }
        }
    }

    @Nonnull
    @Override
    public Collection<PsiReference> findReferences(PsiElement element) {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
        return MethodReferencesSearch.search((PsiMethod) element, projectScope, true).findAll();
    }

    @Override
    @RequiredReadAction
    public void findCollisions(
        PsiElement element,
        String newName,
        Map<? extends PsiElement, String> allRenames,
        List<UsageInfo> result
    ) {
        PsiMethod method = (PsiMethod) element;
        final PsiMethod methodToRename = method;
        findSubmemberHidesMemberCollisions(methodToRename, newName, result);
        findMemberHidesOuterMemberCollisions(method, newName, result);
        findCollisionsAgainstNewName(methodToRename, newName, result);
        findHidingMethodWithOtherSignature(methodToRename, newName, result);
        PsiClass containingClass = methodToRename.getContainingClass();
        if (containingClass != null) {
            PsiMethod patternMethod = (PsiMethod) methodToRename.copy();
            try {
                patternMethod.setName(newName);
                final PsiMethod methodInBaseClass = containingClass.findMethodBySignature(patternMethod, true);
                if (methodInBaseClass != null && methodInBaseClass.getContainingClass() != containingClass && methodInBaseClass.isFinal()) {
                    result.add(new UnresolvableCollisionUsageInfo(methodInBaseClass, methodToRename) {
                        @Nonnull
                        @Override
                        public LocalizeValue getDescription() {
                            return LocalizeValue.localizeTODO(
                                "Renaming method will override final " +
                                    "\"" + RefactoringUIUtil.getDescription(methodInBaseClass, true) + "\""
                            );
                        }
                    });
                }
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }
    }

    @RequiredReadAction
    private void findHidingMethodWithOtherSignature(final PsiMethod methodToRename, String newName, List<UsageInfo> result) {
        PsiClass containingClass = methodToRename.getContainingClass();
        if (containingClass != null) {
            PsiMethod prototype = getPrototypeWithNewName(methodToRename, newName);
            if (prototype == null || containingClass.findMethodBySignature(prototype, true) != null) {
                return;
            }

            PsiMethod[] methodsByName = containingClass.findMethodsByName(newName, true);
            if (methodsByName.length > 0) {
                for (UsageInfo info : result) {
                    if (info.getElement() instanceof PsiReferenceExpression refExpr && refExpr.resolve() == methodToRename) {
                        PsiMethodCallExpression copy = (PsiMethodCallExpression) JavaPsiFacade.getElementFactory(refExpr.getProject())
                            .createExpressionFromText(refExpr.getParent().getText(), refExpr);
                        @SuppressWarnings("RequiredXAction")
                        PsiReferenceExpression expression = (PsiReferenceExpression) processRef(copy.getMethodExpression(), newName);
                        if (expression == null) {
                            continue;
                        }
                        JavaResolveResult resolveResult = expression.advancedResolve(true);
                        final PsiMember resolveResultElement = (PsiMember) resolveResult.getElement();
                        if (resolveResult.isValidResult() && resolveResultElement != null) {
                            result.add(new UnresolvableCollisionUsageInfo(refExpr, methodToRename) {
                                @Nonnull
                                @Override
                                public LocalizeValue getDescription() {
                                    return LocalizeValue.localizeTODO(
                                        "Method call would be linked to " +
                                            "\"" + RefactoringUIUtil.getDescription(resolveResultElement, true) + "\" after rename"
                                    );
                                }
                            });
                            break;
                        }
                    }
                }
            }
        }
    }

    private static PsiMethod getPrototypeWithNewName(PsiMethod methodToRename, String newName) {
        PsiMethod prototype = (PsiMethod) methodToRename.copy();
        try {
            prototype.setName(newName);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
            return null;
        }
        return prototype;
    }

    @Override
    @RequiredReadAction
    public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, LocalizeValue> conflicts) {
        if (element instanceof PsiCompiledElement) {
            return;
        }
        PsiMethod refactoredMethod = (PsiMethod) element;
        if (newName.equals(refactoredMethod.getName())) {
            return;
        }
        PsiMethod prototype = getPrototypeWithNewName(refactoredMethod, newName);
        if (prototype == null) {
            return;
        }

        ConflictsUtil.checkMethodConflicts(
            refactoredMethod.getContainingClass(),
            refactoredMethod,
            prototype,
            conflicts
        );
    }

    @Override
    public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames, SearchScope scope) {
        PsiMethod method = (PsiMethod) element;
        OverridingMethodsSearch.search(method, scope, true).forEach(overrider -> {
            if (overrider instanceof PsiMirrorElement mirrorElem
                && mirrorElem.getPrototype() instanceof PsiMethod overridingMethod) {
                overrider = overridingMethod;
            }

            if (overrider instanceof SyntheticElement) {
                return true;
            }

            String overriderName = overrider.getName();
            String baseName = method.getName();
            String newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName);
            if (newOverriderName != null) {
                RenameProcessor.assertNonCompileElement(overrider);
                allRenames.put(overrider, newOverriderName);
            }
            return true;
        });
    }

    @Override
    public String getHelpID(PsiElement element) {
        return HelpID.RENAME_METHOD;
    }

    @Override
    public boolean isToSearchInComments(PsiElement psiElement) {
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    }

    @Override
    public void setToSearchInComments(PsiElement element, boolean enabled) {
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = enabled;
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public PsiElement substituteElementToRename(PsiElement element, Editor editor) {
        PsiMethod method = (PsiMethod) element;
        if (method.isConstructor()) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return null;
            }
            if (Comparing.strEqual(method.getName(), containingClass.getName())) {
                element = containingClass;
                if (!PsiElementRenameHandler.canRename(element.getProject(), editor, element)) {
                    return null;
                }
                return element;
            }
        }
        return SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRename());
    }

    @Override
    @RequiredUIAccess
    public void substituteElementToRename(
        @Nonnull PsiElement element,
        @Nonnull Editor editor,
        @Nonnull Consumer<PsiElement> renameCallback
    ) {
        PsiMethod psiMethod = (PsiMethod) element;
        if (psiMethod.isConstructor()) {
            PsiClass containingClass = psiMethod.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
                renameCallback.accept(psiMethod);
                return;
            }
            super.substituteElementToRename(element, editor, renameCallback);
        }
        else {
            SuperMethodWarningUtil.checkSuperMethod(
                psiMethod,
                LocalizeValue.localizeTODO("Rename"),
                method -> {
                    if (!PsiElementRenameHandler.canRename(method.getProject(), editor, method)) {
                        return false;
                    }
                    renameCallback.accept(method);
                    return false;
                },
                editor
            );
        }
    }

    private static void findSubmemberHidesMemberCollisions(PsiMethod method, String newName, List<UsageInfo> result) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        if (method.isPrivate()) {
            return;
        }
        Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass, true).findAll();

        MethodSignature oldSignature = method.getSignature(PsiSubstitutor.EMPTY);
        MethodSignature newSignature = MethodSignatureUtil.createMethodSignature(
            newName,
            oldSignature.getParameterTypes(),
            oldSignature.getTypeParameters(),
            oldSignature.getSubstitutor(),
            method.isConstructor()
        );
        for (PsiClass inheritor : inheritors) {
            PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, inheritor, PsiSubstitutor.EMPTY);
            PsiMethod[] methodsByName = inheritor.findMethodsByName(newName, false);
            for (PsiMethod conflictingMethod : methodsByName) {
                if (newSignature.equals(conflictingMethod.getSignature(superSubstitutor))) {
                    result.add(new SubmemberHidesMemberUsageInfo(conflictingMethod, method));
                    break;
                }
            }
        }
    }

    @Override
    public boolean isToSearchForTextOccurrences(PsiElement element) {
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
    }

    @Override
    public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD = enabled;
    }
}
