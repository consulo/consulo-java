/*
 * Copyright 2006-2017 Bas Leijdekkers
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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.intellij.java.impl.ig.fixes.RefactoringInspectionGadgetsFix;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.dataContext.DataContext;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandlerFactory;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

@ExtensionImpl
public class StaticMethodOnlyUsedInOneClassInspection extends BaseGlobalInspection {
    @SuppressWarnings("PublicField")
    public boolean ignoreTestClasses = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreAnonymousClasses = true;

    @SuppressWarnings("PublicField")
    public boolean ignoreOnConflicts = true;

    static final Key<SmartPsiElementPointer<PsiClass>> MARKER = Key.create("STATIC_METHOD_USED_IN_ONE_CLASS");

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassDisplayName();
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
        panel.addCheckbox(
            InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassIgnoreTestOption().get(),
            "ignoreTestClasses"
        );
        panel.addCheckbox(
            InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassIgnoreAnonymousOption().get(),
            "ignoreAnonymousClasses"
        );
        panel.addCheckbox(
            InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassIgnoreOnConflicts().get(),
            "ignoreOnConflicts"
        );
        return panel;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public CommonProblemDescriptor[] checkElement(
        @Nonnull RefEntity refEntity,
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull Object state
    ) {
        if (!(refEntity instanceof RefMethod method)) {
            return null;
        }
        if (!method.isStatic() || method.getAccessModifier() == PsiModifier.PRIVATE) {
            return null;
        }
        RefClass usageClass = null;
        for (RefElement reference : method.getInReferences()) {
            RefClass ownerClass = RefJavaUtil.getInstance().getOwnerClass(reference);
            if (usageClass == null) {
                usageClass = ownerClass;
            }
            else if (usageClass != ownerClass) {
                return null;
            }
        }
        RefClass containingClass = method.getOwnerClass();
        if (usageClass == containingClass) {
            return null;
        }
        if (usageClass == null) {
            PsiClass aClass = containingClass.getElement();
            if (aClass != null) {
                SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
                method.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(aClass));
            }
            return null;
        }
        if (ignoreAnonymousClasses
            && (usageClass.isAnonymous() || usageClass.isLocalClass()
            || usageClass.getOwner() instanceof RefClass && !usageClass.isStatic())) {
            return null;
        }
        if (ignoreTestClasses && usageClass.isTestCase()) {
            return null;
        }
        PsiClass psiClass = usageClass.getElement();
        if (psiClass == null) {
            return null;
        }
        PsiMethod psiMethod = (PsiMethod) method.getElement();
        if (psiMethod == null) {
            return null;
        }
        if (ignoreOnConflicts) {
            if (psiClass.findMethodsBySignature(psiMethod, true).length > 0 || !areReferenceTargetsAccessible(psiMethod, psiClass)) {
                return null;
            }
        }
        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
        method.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(psiClass));
        return new ProblemDescriptor[]{createProblemDescriptor(manager, psiMethod.getNameIdentifier(), psiClass)};
    }

    @Nonnull
    @RequiredReadAction
    static ProblemDescriptor createProblemDescriptor(@Nonnull InspectionManager manager, PsiElement problemElement, PsiClass usageClass) {
        LocalizeValue message = usageClass instanceof PsiAnonymousClass anonymousClass
            ? InspectionGadgetsLocalize.staticMethodOnlyUsedInOneAnonymousClassProblemDescriptor(
                anonymousClass.getBaseClassReference().getText()
            )
            : InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassProblemDescriptor(usageClass.getName());
        return manager.createProblemDescriptor(problemElement, message.get(), false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    @Override
    public boolean queryExternalUsagesRequests(
        @Nonnull final InspectionManager manager,
        @Nonnull final GlobalInspectionContext globalContext,
        @Nonnull final ProblemDescriptionsProcessor problemDescriptionsProcessor,
        Object state
    ) {
        globalContext.getRefManager().iterate(new RefJavaVisitor() {
            @Override
            @RequiredReadAction
            public void visitElement(@Nonnull RefEntity refEntity) {
                if (refEntity instanceof RefMethod refMethod) {
                    SmartPsiElementPointer<PsiClass> classPointer = refMethod.getUserData(MARKER);
                    if (classPointer != null) {
                        SimpleReference<PsiClass> ref = SimpleReference.create(classPointer.getElement());
                        GlobalJavaInspectionContext globalJavaContext = globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT);
                        globalJavaContext.enqueueMethodUsagesProcessor(
                            refMethod,
                            reference -> {
                                PsiClass containingClass = ClassUtils.getContainingClass(reference.getElement());
                                if (problemDescriptionsProcessor.getDescriptions(refMethod) != null) {
                                    if (containingClass != ref.get()) {
                                        problemDescriptionsProcessor.ignoreElement(refMethod);
                                        return false;
                                    }
                                    return true;
                                }
                                else {
                                    PsiIdentifier identifier = ((PsiMethod) refMethod.getElement()).getNameIdentifier();
                                    ProblemDescriptor problemDescriptor = createProblemDescriptor(manager, identifier, containingClass);
                                    problemDescriptionsProcessor.addProblemElement(refMethod, problemDescriptor);
                                    ref.set(containingClass);
                                    return true;
                                }
                            }
                        );
                    }
                }
            }
        });

        return false;
    }

    static boolean areReferenceTargetsAccessible(PsiElement elementToCheck, PsiElement place) {
        AccessibleVisitor visitor = new AccessibleVisitor(elementToCheck, place);
        elementToCheck.accept(visitor);
        return visitor.isAccessible();
    }

    private static class AccessibleVisitor extends JavaRecursiveElementWalkingVisitor {
        private final PsiElement myElementToCheck;
        private final PsiElement myPlace;
        private boolean myAccessible = true;

        public AccessibleVisitor(PsiElement elementToCheck, PsiElement place) {
            myElementToCheck = elementToCheck;
            myPlace = place;
        }

        @Override
        @RequiredReadAction
        public void visitCallExpression(@Nonnull PsiCallExpression callExpression) {
            if (!myAccessible) {
                return;
            }
            super.visitCallExpression(callExpression);
            PsiMethod method = callExpression.resolveMethod();
            if (callExpression instanceof PsiNewExpression newExpression && method == null) {
                PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
                if (reference != null) {
                    checkElement(reference.resolve());
                }
            }
            else {
                checkElement(method);
            }
        }

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            if (!myAccessible) {
                return;
            }
            super.visitReferenceExpression(expression);
            checkElement(expression.resolve());
        }

        private void checkElement(PsiElement element) {
            if (!(element instanceof PsiMember member)) {
                return;
            }
            if (PsiTreeUtil.isAncestor(myElementToCheck, element, false)) {
                return; // internal reference
            }
            myAccessible = PsiUtil.isAccessible(member, myPlace, null);
        }

        public boolean isAccessible() {
            return myAccessible;
        }
    }

    private static class UsageProcessor implements Processor<PsiReference> {
        private final AtomicReference<PsiClass> foundClass = new AtomicReference<>();

        @Override
        @RequiredReadAction
        public boolean process(PsiReference reference) {
            ProgressManager.checkCanceled();
            PsiElement element = reference.getElement();
            PsiClass usageClass = ClassUtils.getContainingClass(element);
            if (usageClass == null) {
                return true;
            }
            if (foundClass.compareAndSet(null, usageClass)) {
                return true;
            }
            PsiClass aClass = foundClass.get();
            PsiManager manager = usageClass.getManager();
            return manager.areElementsEquivalent(aClass, usageClass);
        }

        /**
         * @return the class the specified method is used from, or null if it is
         * used from 0 or more than 1 other classes.
         */
        @Nullable
        public PsiClass findUsageClass(PsiMethod method) {
            ProgressManager.getInstance().runProcess(
                () -> {
                    Query<PsiReference> query = MethodReferencesSearch.search(method);
                    if (!query.forEach(this)) {
                        foundClass.set(null);
                    }
                },
                null
            );
            return foundClass.get();
        }
    }

    @Nullable
    @Override
    public LocalInspectionTool getSharedLocalInspectionTool() {
        return new StaticMethodOnlyUsedInOneClassLocalInspection(this);
    }

    private static class StaticMethodOnlyUsedInOneClassLocalInspection
        extends BaseSharedLocalInspection<StaticMethodOnlyUsedInOneClassInspection> {
        public StaticMethodOnlyUsedInOneClassLocalInspection(StaticMethodOnlyUsedInOneClassInspection settingsDelegate) {
            super(settingsDelegate);
        }

        @Nonnull
        @Override
        @RequiredReadAction
        protected String buildErrorString(Object... infos) {
            PsiClass usageClass = (PsiClass) infos[0];
            if (usageClass instanceof PsiAnonymousClass anonymousClass) {
                String refText = anonymousClass.getBaseClassReference().getText();
                return InspectionGadgetsLocalize.staticMethodOnlyUsedInOneAnonymousClassProblemDescriptor(refText).get();
            }
            else {
                return InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassProblemDescriptor(usageClass.getName()).get();
            }
        }

        @Nullable
        @Override
        protected InspectionGadgetsFix buildFix(Object... infos) {
            PsiClass usageClass = (PsiClass) infos[0];
            return new StaticMethodOnlyUsedInOneClassFix(usageClass);
        }

        private static class StaticMethodOnlyUsedInOneClassFix extends RefactoringInspectionGadgetsFix {
            private final SmartPsiElementPointer<PsiClass> usageClass;

            public StaticMethodOnlyUsedInOneClassFix(PsiClass usageClass) {
                SmartPointerManager pointerManager = SmartPointerManager.getInstance(usageClass.getProject());
                this.usageClass = pointerManager.createSmartPsiElementPointer(usageClass);
            }

            @Override
            @Nonnull
            public LocalizeValue getName() {
                return InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassQuickfix();
            }

            @Nonnull
            @Override
            public RefactoringActionHandler getHandler() {
                return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
            }

            @Nonnull
            @Override
            @RequiredReadAction
            public DataContext enhanceDataContext(DataContext context) {
                return DataContext.builder().parent(context).add(LangDataKeys.TARGET_PSI_ELEMENT, usageClass.getElement()).build();
            }
        }

        @Override
        public BaseInspectionVisitor buildVisitor() {
            return new StaticMethodOnlyUsedInOneClassVisitor();
        }

        private class StaticMethodOnlyUsedInOneClassVisitor extends BaseInspectionVisitor {
            @Override
            public void visitMethod(@Nonnull PsiMethod method) {
                super.visitMethod(method);
                if (!method.isStatic() || method.isPrivate() || method.getNameIdentifier() == null) {
                    return;
                }
                if (DeclarationSearchUtils.isTooExpensiveToSearch(method, true)) {
                    return;
                }
                UsageProcessor usageProcessor = new UsageProcessor();
                PsiClass usageClass = usageProcessor.findUsageClass(method);
                if (usageClass == null) {
                    return;
                }
                PsiClass containingClass = method.getContainingClass();
                if (usageClass.equals(containingClass)) {
                    return;
                }
                if (mySettingsDelegate.ignoreTestClasses && TestUtils.isInTestCode(usageClass)) {
                    return;
                }
                if (usageClass.getContainingClass() != null && !usageClass.isStatic() || PsiUtil.isLocalOrAnonymousClass(usageClass)) {
                    if (mySettingsDelegate.ignoreAnonymousClasses) {
                        return;
                    }
                    if (PsiTreeUtil.isAncestor(containingClass, usageClass, true)) {
                        return;
                    }
                }
                if (mySettingsDelegate.ignoreOnConflicts) {
                    if (usageClass.findMethodsBySignature(method, true).length > 0 || !areReferenceTargetsAccessible(method, usageClass)) {
                        return;
                    }
                }
                registerMethodError(method, usageClass);
            }
        }
    }
}
