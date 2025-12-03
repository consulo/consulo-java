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
package com.intellij.java.impl.codeInspection.unneededThrows;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearch;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.BidirectionalMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class RedundantThrows extends GlobalJavaInspectionTool {
    private static final Logger LOG = Logger.getInstance(RedundantThrows.class);
    private final BidirectionalMap<String, QuickFix> myQuickFixes = new BidirectionalMap<>();
    private static final String SHORT_NAME = "RedundantThrows";

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
        if (refEntity instanceof RefMethod refMethod) {
            if (refMethod.isSyntheticJSP() || refMethod.hasSuperMethods() || refMethod.isEntry()) {
                return null;
            }

            PsiClass[] unThrown = refMethod.getUnThrownExceptions();
            if (unThrown == null) {
                return null;
            }

            PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
            PsiClassType[] throwsList = psiMethod.getThrowsList().getReferencedTypes();
            PsiJavaCodeReferenceElement[] throwsRefs = psiMethod.getThrowsList().getReferenceElements();
            List<ProblemDescriptor> problems = null;

            PsiManager psiManager = psiMethod.getManager();
            for (int i = 0; i < throwsList.length; i++) {
                PsiClassType throwsType = throwsList[i];
                String throwsClassName = throwsType.getClassName();
                PsiJavaCodeReferenceElement throwsRef = throwsRefs[i];
                if (ExceptionUtil.isUncheckedException(throwsType) || declaredInRemotableMethod(psiMethod, throwsType)) {
                    continue;
                }

                for (PsiClass s : unThrown) {
                    PsiClass throwsResolvedType = throwsType.resolve();
                    if (psiManager.areElementsEquivalent(s, throwsResolvedType)) {
                        if (problems == null) {
                            problems = new ArrayList<>(1);
                        }

                        if (refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) {
                            problems.add(
                                manager.newProblemDescriptor(
                                        InspectionLocalize.inspectionRedundantThrowsProblemDescriptor("<code>#ref</code>")
                                    )
                                    .range(throwsRef)
                                    .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                                    .withFix(getFix(processor, throwsClassName))
                                    .create()
                            );
                        }
                        else if (!refMethod.getDerivedMethods().isEmpty()) {
                            problems.add(
                                manager.newProblemDescriptor(
                                        InspectionLocalize.inspectionRedundantThrowsProblemDescriptor1("<code>#ref</code>")
                                    )
                                    .range(throwsRef)
                                    .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                                    .withFix(getFix(processor, throwsClassName))
                                    .create()
                            );
                        }
                        else {
                            problems.add(
                                manager.newProblemDescriptor(
                                        InspectionLocalize.inspectionRedundantThrowsProblemDescriptor2("<code>#ref</code>")
                                    )
                                    .range(throwsRef)
                                    .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                                    .withFix(getFix(processor, throwsClassName))
                                    .create()
                            );
                        }
                    }
                }
            }

            if (problems != null) {
                return problems.toArray(new ProblemDescriptorBase[problems.size()]);
            }
        }

        return null;
    }

    private static boolean declaredInRemotableMethod(PsiMethod psiMethod, PsiClassType throwsType) {
        if (!throwsType.equalsToText("java.rmi.RemoteException")) {
            return false;
        }
        PsiClass aClass = psiMethod.getContainingClass();
        if (aClass == null) {
            return false;
        }
        PsiClass remote =
            JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.rmi.Remote", GlobalSearchScope.allScope(aClass.getProject()));
        return remote != null && aClass.isInheritor(remote, true);
    }

    @Override
    protected boolean queryExternalUsagesRequests(
        RefManager manager,
        final GlobalJavaInspectionContext globalContext,
        final ProblemDescriptionsProcessor processor,
        Object state
    ) {
        manager.iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@Nonnull RefEntity refEntity) {
                if (processor.getDescriptions(refEntity) != null) {
                    refEntity.accept(new RefJavaVisitor() {
                        @Override
                        public void visitMethod(@Nonnull RefMethod refMethod) {
                            globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
                                processor.ignoreElement(refMethod);
                                return true;
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
        return InspectionLocalize.inspectionRedundantThrowsDisplayName();
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

    @Nonnull
    private LocalQuickFix getFix(ProblemDescriptionsProcessor processor, String hint) {
        QuickFix fix = myQuickFixes.get(hint);
        if (fix == null) {
            fix = new MyQuickFix(processor, hint);
            if (hint != null) {
                myQuickFixes.put(hint, fix);
            }
        }
        return (LocalQuickFix) fix;
    }

    @Nullable
    @Override
    public QuickFix getQuickFix(String hint) {
        return getFix(null, hint);
    }

    @Nullable
    @Override
    public String getHint(@Nonnull QuickFix fix) {
        List<String> hints = myQuickFixes.getKeysByValue(fix);
        LOG.assertTrue(hints != null && hints.size() == 1);
        assert hints != null;
        return hints.get(0);
    }

    private static class MyQuickFix implements LocalQuickFix {
        private final ProblemDescriptionsProcessor myProcessor;
        private final String myHint;

        public MyQuickFix(ProblemDescriptionsProcessor processor, String hint) {
            myProcessor = processor;
            myHint = hint;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionRedundantThrowsRemoveQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            if (myProcessor != null) {
                RefElement refElement = (RefElement) myProcessor.getElement(descriptor);
                if (refElement instanceof RefMethod refMethod && refElement.isValid()) {
                    CommonProblemDescriptor[] problems = myProcessor.getDescriptions(refMethod);
                    if (problems != null) {
                        removeExcessiveThrows(refMethod, null, problems);
                    }
                }
            }
            else {
                PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
                if (psiMethod != null) {
                    removeExcessiveThrows(null, psiMethod, new CommonProblemDescriptor[]{descriptor});
                }
            }
        }

        @RequiredWriteAction
        private void removeExcessiveThrows(
            @Nullable RefMethod refMethod,
            @Nullable PsiModifierListOwner element,
            CommonProblemDescriptor[] problems
        ) {
            try {
                @Nullable PsiMethod psiMethod;
                if (element == null) {
                    LOG.assertTrue(refMethod != null);
                    psiMethod = (PsiMethod) refMethod.getElement();
                }
                else {
                    psiMethod = (PsiMethod) element;
                }
                if (psiMethod == null) {
                    return; //invalid refMethod
                }
                Project project = psiMethod.getProject();
                PsiManager psiManager = PsiManager.getInstance(project);
                List<PsiJavaCodeReferenceElement> refsToDelete = new ArrayList<>();
                for (CommonProblemDescriptor problem : problems) {
                    PsiElement psiElement = ((ProblemDescriptor) problem).getPsiElement();
                    if (psiElement instanceof PsiJavaCodeReferenceElement classRef) {
                        PsiType psiType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(classRef);
                        removeException(refMethod, psiType, refsToDelete, psiMethod);
                    }
                    else {
                        PsiReferenceList throwsList = psiMethod.getThrowsList();
                        PsiClassType[] classTypes = throwsList.getReferencedTypes();
                        for (PsiClassType classType : classTypes) {
                            String text = classType.getClassName();
                            if (Comparing.strEqual(myHint, text)) {
                                removeException(refMethod, classType, refsToDelete, psiMethod);
                                break;
                            }
                        }
                    }
                }

                //check read-only status for derived methods
                if (!FileModificationService.getInstance().preparePsiElementsForWrite(refsToDelete)) {
                    return;
                }

                for (PsiJavaCodeReferenceElement aRefsToDelete : refsToDelete) {
                    aRefsToDelete.delete();
                }
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }

        private static void removeException(
            RefMethod refMethod,
            PsiType exceptionType,
            List<PsiJavaCodeReferenceElement> refsToDelete,
            PsiMethod psiMethod
        ) {
            PsiManager psiManager = psiMethod.getManager();

            PsiJavaCodeReferenceElement[] refs = psiMethod.getThrowsList().getReferenceElements();
            for (PsiJavaCodeReferenceElement ref : refs) {
                PsiType refType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(ref);
                if (exceptionType.isAssignableFrom(refType)) {
                    refsToDelete.add(ref);
                }
            }

            if (refMethod != null) {
                for (RefMethod refDerived : refMethod.getDerivedMethods()) {
                    removeException(refDerived, exceptionType, refsToDelete, (PsiMethod) refDerived.getElement());
                }
            }
            else {
                Query<Pair<PsiMethod, PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
                query.forEach(pair -> {
                    if (pair.first == psiMethod) {
                        removeException(null, exceptionType, refsToDelete, pair.second);
                    }
                    return true;
                });
            }
        }
    }
}
