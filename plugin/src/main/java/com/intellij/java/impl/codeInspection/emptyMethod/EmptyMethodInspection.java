/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.emptyMethod;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.analysis.impl.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.BatchQuickFix;
import consulo.language.editor.refactoring.safeDelete.SafeDeleteHandler;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.BidirectionalMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.xml.serializer.JDOMExternalizableStringList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class EmptyMethodInspection extends GlobalJavaInspectionTool implements OldStyleInspection {
    private static final String SHORT_NAME = "EmptyMethod";

    private final BidirectionalMap<Boolean, QuickFix> myQuickFixes = new BidirectionalMap<>();

    public final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();
    private static final Logger LOG = Logger.getInstance(EmptyMethodInspection.class);

    @Nullable
    @Override
    @RequiredReadAction
    public CommonProblemDescriptor[] checkElement(
        @Nonnull RefEntity refEntity,
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull ProblemDescriptionsProcessor processor,
        @Nonnull Object state
    ) {
        if (!(refEntity instanceof RefMethod refMethod)) {
            return null;
        }
        if (!isBodyEmpty(refMethod)) {
            return null;
        }
        if (refMethod.isConstructor()) {
            return null;
        }
        if (refMethod.isSyntheticJSP()) {
            return null;
        }

        for (RefMethod refSuper : refMethod.getSuperMethods()) {
            if (checkElement(refSuper, scope, manager, globalContext, processor) != null) {
                return null;
            }
        }

        @Nonnull
        LocalizeValue message = LocalizeValue.empty();
        boolean needToDeleteHierarchy = false;
        if (refMethod.isOnlyCallsSuper() && !refMethod.isFinal()) {
            RefMethod refSuper = findSuperWithBody(refMethod);
            RefJavaUtil refUtil = RefJavaUtil.getInstance();
            if (refSuper != null && Comparing.strEqual(refMethod.getAccessModifier(), refSuper.getAccessModifier())) {
                //protected modificator gives access to method in another package
                if (Comparing.strEqual(refSuper.getAccessModifier(), PsiModifier.PROTECTED)
                    && !Comparing.strEqual(refUtil.getPackageName(refSuper), refUtil.getPackageName(refMethod))) {
                    return null;
                }
                PsiModifierListOwner modifierListOwner = refMethod.getElement();
                if (modifierListOwner != null) {
                    PsiModifierList list = modifierListOwner.getModifierList();
                    if (list != null) {
                        PsiModifierListOwner supMethod = refSuper.getElement();
                        if (supMethod != null && list.hasModifierProperty(PsiModifier.SYNCHRONIZED)
                            && !supMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                            return null;
                        }
                    }
                }
            }
            if (refSuper == null || refUtil.compareAccess(refMethod.getAccessModifier(), refSuper.getAccessModifier()) <= 0) {
                message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor();
            }
        }
        else if (refMethod.hasBody() && hasEmptySuperImplementation(refMethod)) {
            message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor1();
        }
        else if (areAllImplementationsEmpty(refMethod)) {
            if (refMethod.hasBody()) {
                if (refMethod.getDerivedMethods().isEmpty()) {
                    if (refMethod.getSuperMethods().isEmpty()) {
                        message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor2();
                    }
                }
                else {
                    needToDeleteHierarchy = true;
                    message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor3();
                }
            }
            else if (!refMethod.getDerivedMethods().isEmpty()) {
                needToDeleteHierarchy = true;
                message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor4();
            }
        }

        if (message.isNotEmpty()) {
            List<LocalQuickFix> fixes = new ArrayList<>();
            fixes.add(getFix(processor, needToDeleteHierarchy));
            SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(
                refMethod.getElement(),
                qualifiedName -> {
                    fixes.add(SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
                        JavaQuickFixLocalize.fixAddSpecialAnnotationText(qualifiedName),
                        JavaQuickFixLocalize.fixAddSpecialAnnotationFamily(),
                        EXCLUDE_ANNOS, qualifiedName, refMethod.getElement()
                    ));
                    return true;
                }
            );

            ProblemDescriptor descriptor = manager.newProblemDescriptor(message)
                .range(refMethod.getElement().getNavigationElement())
                .withFixes(fixes)
                .create();
            return new ProblemDescriptor[]{descriptor};
        }

        return null;
    }

    @RequiredReadAction
    private boolean isBodyEmpty(RefMethod refMethod) {
        if (!refMethod.isBodyEmpty()) {
            return false;
        }
        PsiModifierListOwner owner = refMethod.getElement();
        if (owner == null || AnnotationUtil.isAnnotated(owner, EXCLUDE_ANNOS)) {
            return false;
        }
        for (ImplicitMethodBodyProvider provider : owner.getApplication().getExtensionPoint(ImplicitMethodBodyProvider.class)) {
            if (provider.hasImplicitMethodBody(refMethod)) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    private static RefMethod findSuperWithBody(RefMethod refMethod) {
        for (RefMethod refSuper : refMethod.getSuperMethods()) {
            if (refSuper.hasBody()) {
                return refSuper;
            }
        }
        return null;
    }

    @RequiredReadAction
    private boolean areAllImplementationsEmpty(RefMethod refMethod) {
        if (refMethod.hasBody() && !isBodyEmpty(refMethod)) {
            return false;
        }

        for (RefMethod refDerived : refMethod.getDerivedMethods()) {
            if (!areAllImplementationsEmpty(refDerived)) {
                return false;
            }
        }

        return true;
    }

    @RequiredReadAction
    private boolean hasEmptySuperImplementation(RefMethod refMethod) {
        for (RefMethod refSuper : refMethod.getSuperMethods()) {
            if (refSuper.hasBody() && isBodyEmpty(refSuper)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean queryExternalUsagesRequests(
        @Nonnull RefManager manager,
        @Nonnull final GlobalJavaInspectionContext context,
        @Nonnull final ProblemDescriptionsProcessor descriptionsProcessor,
        Object state
    ) {
        manager.iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@Nonnull RefEntity refEntity) {
                if (refEntity instanceof RefElement && descriptionsProcessor.getDescriptions(refEntity) != null) {
                    refEntity.accept(new RefJavaVisitor() {
                        @Override
                        public void visitMethod(@Nonnull RefMethod refMethod) {
                            context.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
                                PsiCodeBlock body = derivedMethod.getBody();
                                if (body == null || body.getStatements().length == 0
                                    || RefJavaUtil.getInstance().isMethodOnlyCallsSuper(derivedMethod)) {
                                    return true;
                                }
                                descriptionsProcessor.ignoreElement(refMethod);
                                return false;
                            });
                        }
                    });
                }
            }
        });

        return false;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionEmptyMethodDisplayName();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return SHORT_NAME;
    }

    private LocalQuickFix getFix(ProblemDescriptionsProcessor processor, boolean needToDeleteHierarchy) {
        QuickFix fix = myQuickFixes.get(needToDeleteHierarchy);
        if (fix == null) {
            fix = new DeleteMethodQuickFix(processor, needToDeleteHierarchy);
            myQuickFixes.put(needToDeleteHierarchy, fix);
            return (LocalQuickFix) fix;
        }
        return (LocalQuickFix) fix;
    }

    @Override
    public String getHint(@Nonnull QuickFix fix) {
        List<Boolean> list = myQuickFixes.getKeysByValue(fix);
        if (list != null) {
            LOG.assertTrue(list.size() == 1);
            return String.valueOf(list.get(0));
        }
        return null;
    }

    @Nullable
    @Override
    public LocalQuickFix getQuickFix(String hint) {
        return new DeleteMethodIntention(hint);
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
            EXCLUDE_ANNOS,
            InspectionLocalize.specialAnnotationsAnnotationsList().get()
        );

        JPanel panel = new JPanel(new BorderLayout(2, 2));
        panel.add(listPanel, BorderLayout.CENTER);
        return panel;
    }

    private class DeleteMethodIntention implements LocalQuickFix {
        private final String myHint;

        public DeleteMethodIntention(String hint) {
            myHint = hint;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionEmptyMethodDeleteQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class, false);
            if (psiMethod != null) {
                List<PsiElement> psiElements = new ArrayList<>();
                psiElements.add(psiMethod);
                if (Boolean.valueOf(myHint)) {
                    Query<Pair<PsiMethod, PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
                    query.forEach(pair -> {
                        if (pair.first == psiMethod) {
                            psiElements.add(pair.second);
                        }
                        return true;
                    });
                }

                project.getApplication().invokeLater(
                    () -> SafeDeleteHandler.invoke(project, PsiUtilCore.toPsiElementArray(psiElements), false),
                    project.getDisposed()
                );
            }
        }
    }

    private class DeleteMethodQuickFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
        private final ProblemDescriptionsProcessor myProcessor;
        private final boolean myNeedToDeleteHierarchy;

        public DeleteMethodQuickFix(ProblemDescriptionsProcessor processor, boolean needToDeleteHierarchy) {
            myProcessor = processor;
            myNeedToDeleteHierarchy = needToDeleteHierarchy;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionEmptyMethodDeleteQuickfix();
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            applyFix(project, new ProblemDescriptor[]{descriptor}, new ArrayList<>(), null);
        }

        private void deleteHierarchy(RefMethod refMethod, List<PsiElement> result) {
            Collection<RefMethod> derivedMethods = refMethod.getDerivedMethods();
            RefMethod[] refMethods = derivedMethods.toArray(new RefMethod[derivedMethods.size()]);
            for (RefMethod refDerived : refMethods) {
                deleteMethod(refDerived, result);
            }
            deleteMethod(refMethod, result);
        }

        private void deleteMethod(RefMethod refMethod, List<PsiElement> result) {
            PsiElement psiElement = refMethod.getElement();
            if (psiElement == null) {
                return;
            }
            if (!result.contains(psiElement)) {
                result.add(psiElement);
            }
        }

        @Override
        public void applyFix(
            @Nonnull Project project,
            @Nonnull CommonProblemDescriptor[] descriptors,
            List<PsiElement> psiElementsToIgnore,
            Runnable refreshViews
        ) {
            for (CommonProblemDescriptor descriptor : descriptors) {
                RefElement refElement = (RefElement) myProcessor.getElement(descriptor);
                if (refElement.isValid() && refElement instanceof RefMethod refMethod) {
                    if (myNeedToDeleteHierarchy) {
                        deleteHierarchy(refMethod, psiElementsToIgnore);
                    }
                    else {
                        deleteMethod(refMethod, psiElementsToIgnore);
                    }
                }
            }
            project.getApplication().invokeLater(
                () -> SafeDeleteHandler.invoke(project, PsiUtilCore.toPsiElementArray(psiElementsToIgnore), false, refreshViews),
                project.getDisposed()
            );
        }
    }
}
