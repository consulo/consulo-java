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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author yole
 */
public class MoveClassToInnerProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(MoveClassToInnerProcessor.class);
    public static final Key<List<NonCodeUsageInfo>> ourNonCodeUsageKey = Key.create("MoveClassToInner.NonCodeUsage");

    private PsiClass[] myClassesToMove;
    private final PsiClass myTargetClass;
    private PsiJavaPackage[] mySourcePackage;
    private final PsiJavaPackage myTargetPackage;
    private String[] mySourceVisibility;
    private final boolean mySearchInComments;
    private final boolean mySearchInNonJavaFiles;
    private NonCodeUsageInfo[] myNonCodeUsages;
    private final MoveCallback myMoveCallback;

    public MoveClassToInnerProcessor(
        Project project,
        PsiClass[] classesToMove,
        @Nonnull PsiClass targetClass,
        boolean searchInComments,
        boolean searchInNonJavaFiles,
        MoveCallback moveCallback
    ) {
        super(project);
        setClassesToMove(classesToMove);
        myTargetClass = targetClass;
        mySearchInComments = searchInComments;
        mySearchInNonJavaFiles = searchInNonJavaFiles;
        myMoveCallback = moveCallback;
        myTargetPackage = JavaDirectoryService.getInstance().getPackage(myTargetClass.getContainingFile().getContainingDirectory());
    }

    private void setClassesToMove(PsiClass[] classesToMove) {
        myClassesToMove = classesToMove;
        mySourcePackage = new PsiJavaPackage[classesToMove.length];
        mySourceVisibility = new String[classesToMove.length];
        for (int i = 0; i < classesToMove.length; i++) {
            PsiClass psiClass = classesToMove[i];
            mySourceVisibility[i] = VisibilityUtil.getVisibilityModifier(psiClass.getModifierList());
            mySourcePackage[i] = JavaDirectoryService.getInstance().getPackage(psiClass.getContainingFile().getContainingDirectory());
        }
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new MoveMultipleElementsViewDescriptor(myClassesToMove, myTargetClass.getQualifiedName());
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public UsageInfo[] findUsages() {
        List<UsageInfo> usages = new ArrayList<>();
        for (PsiClass classToMove : myClassesToMove) {
            String newName = myTargetClass.getQualifiedName() + "." + classToMove.getName();
            Collections.addAll(
                usages,
                MoveClassesOrPackagesUtil.findUsages(classToMove, mySearchInComments, mySearchInNonJavaFiles, newName)
            );
        }
        return usages.toArray(new UsageInfo[usages.size()]);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usages = refUsages.get();
        return showConflicts(getConflicts(usages), usages);
    }

    @Override
    protected void refreshElements(@Nonnull PsiElement[] elements) {
        Application.get().runReadAction(() -> {
            PsiClass[] classesToMove = new PsiClass[elements.length];
            for (int i = 0; i < classesToMove.length; i++) {
                classesToMove[i] = (PsiClass)elements[i];
            }
            setClassesToMove(classesToMove);
        });
    }

    @Override
    @RequiredUIAccess
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        if (!prepareWritable(usages)) {
            return;
        }

        MoveClassToInnerHandler[] handlers = MoveClassToInnerHandler.EP_NAME.getExtensions();

        List<UsageInfo> usageList = new ArrayList<>(Arrays.asList(usages));
        List<PsiElement> importStatements = new ArrayList<>();
        for (MoveClassToInnerHandler handler : handlers) {
            importStatements.addAll(handler.filterImports(usageList, myProject));
        }

        usages = usageList.toArray(new UsageInfo[usageList.size()]);

        saveNonCodeUsages(usages);
        Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<>();
        try {
            for (PsiClass classToMove : myClassesToMove) {
                PsiClass newClass = null;
                for (MoveClassToInnerHandler handler : handlers) {
                    newClass = handler.moveClass(classToMove, myTargetClass);
                    if (newClass != null) {
                        break;
                    }
                }
                LOG.assertTrue(
                    newClass != null,
                    "There is no appropriate MoveClassToInnerHandler for " + myTargetClass + "; " + classToMove
                );
                oldToNewElementsMapping.put(classToMove, newClass);
            }

            myNonCodeUsages = CommonMoveUtil.retargetUsages(usages, oldToNewElementsMapping);
            for (MoveClassToInnerHandler handler : handlers) {
                handler.retargetNonCodeUsages(oldToNewElementsMapping, myNonCodeUsages);
            }

            for (MoveClassToInnerHandler handler : handlers) {
                handler.retargetClassRefsInMoved(oldToNewElementsMapping);
            }

            for (MoveClassToInnerHandler handler : handlers) {
                handler.removeRedundantImports(myTargetClass.getContainingFile());
            }

            for (PsiClass classToMove : myClassesToMove) {
                classToMove.delete();
            }

            for (PsiElement element : importStatements) {
                if (element.isValid()) {
                    element.delete();
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredUIAccess
    private boolean prepareWritable(UsageInfo[] usages) {
        Set<PsiElement> elementsToMakeWritable = new HashSet<>();
        Collections.addAll(elementsToMakeWritable, myClassesToMove);
        elementsToMakeWritable.add(myTargetClass);
        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element != null) {
                elementsToMakeWritable.add(element);
            }
        }
        return CommonRefactoringUtil.checkReadOnlyStatus(myProject, PsiUtilBase.toPsiElementArray(elementsToMakeWritable));
    }

    @RequiredReadAction
    private void saveNonCodeUsages(UsageInfo[] usages) {
        for (PsiClass classToMove : myClassesToMove) {
            for (UsageInfo usageInfo : usages) {
                if (usageInfo instanceof NonCodeUsageInfo nonCodeUsage) {
                    PsiElement element = nonCodeUsage.getElement();
                    if (element != null && PsiTreeUtil.isAncestor(classToMove, element, false)) {
                        List<NonCodeUsageInfo> list = element.getCopyableUserData(ourNonCodeUsageKey);
                        if (list == null) {
                            list = new ArrayList<>();
                            element.putCopyableUserData(ourNonCodeUsageKey, list);
                        }
                        list.add(nonCodeUsage);
                    }
                }
            }
        }
    }

    @Override
    @RequiredWriteAction
    protected void performPsiSpoilingRefactoring() {
        if (myNonCodeUsages != null) {
            RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
        }
        if (myMoveCallback != null) {
            if (myMoveCallback instanceof MoveClassesOrPackagesCallback moveClassesOrPackagesCallback) {
                moveClassesOrPackagesCallback.classesMovedToInner(myTargetClass);
            }
            myMoveCallback.refactoringCompleted();
        }
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return RefactoringLocalize.moveClassToInnerCommandName(
            (myClassesToMove.length > 1 ? "classes " : "class ") + StringUtil.join(
                myClassesToMove,
                PsiNamedElement::getName,
                ", "
            ),
            myTargetClass.getQualifiedName()
        ).get();
    }

    @Nonnull
    @Override
    protected Collection<? extends PsiElement> getElementsToWrite(@Nonnull UsageViewDescriptor descriptor) {
        List<PsiElement> result = new ArrayList<>();
        result.addAll(super.getElementsToWrite(descriptor));
        result.add(myTargetClass);
        return result;
    }

    @RequiredReadAction
    public MultiMap<PsiElement, String> getConflicts(UsageInfo[] usages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();

        for (PsiClass classToMove : myClassesToMove) {
            PsiClass innerClass = myTargetClass.findInnerClassByName(classToMove.getName(), false);
            if (innerClass != null) {
                conflicts.putValue(
                    innerClass,
                    RefactoringLocalize.moveToInnerDuplicateInnerClass(
                        CommonRefactoringUtil.htmlEmphasize(myTargetClass.getQualifiedName()),
                        CommonRefactoringUtil.htmlEmphasize(classToMove.getName())
                    ).get()
                );
            }
        }

        for (int i = 0; i < myClassesToMove.length; i++) {
            PsiClass classToMove = myClassesToMove[i];
            String classToMoveVisibility = VisibilityUtil.getVisibilityModifier(classToMove.getModifierList());
            String targetClassVisibility = VisibilityUtil.getVisibilityModifier(myTargetClass.getModifierList());

            boolean moveToOtherPackage = !Comparing.equal(mySourcePackage[i], myTargetPackage);
            if (moveToOtherPackage) {
                classToMove.accept(new PackageLocalsUsageCollector(myClassesToMove, new PackageWrapper(myTargetPackage), conflicts));
            }

            ConflictsCollector collector = new ConflictsCollector(classToMove, conflicts);
            if ((moveToOtherPackage &&
                (classToMoveVisibility.equals(PsiModifier.PACKAGE_LOCAL) || targetClassVisibility.equals(PsiModifier.PACKAGE_LOCAL))) ||
                targetClassVisibility.equals(PsiModifier.PRIVATE)) {
                detectInaccessibleClassUsages(usages, collector, mySourceVisibility[i]);
            }
            if (moveToOtherPackage) {
                detectInaccessibleMemberUsages(collector);
            }
        }

        return conflicts;
    }

    @RequiredReadAction
    private void detectInaccessibleClassUsages(UsageInfo[] usages, ConflictsCollector collector, String visibility) {
        for (UsageInfo usage : usages) {
            if (usage instanceof MoveRenameUsageInfo && !(usage instanceof NonCodeUsageInfo)) {
                PsiElement element = usage.getElement();
                if (element == null || PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null) {
                    continue;
                }
                if (isInaccessibleFromTarget(element, visibility)) {
                    collector.addConflict(collector.getClassToMove(), element);
                }
            }
        }
    }

    private boolean isInaccessibleFromTarget(PsiElement element, String visibility) {
        PsiJavaPackage elementPackage =
            JavaDirectoryService.getInstance().getPackage(element.getContainingFile().getContainingDirectory());
        return !PsiUtil.isAccessible(myTargetClass, element, null)
            || (!myTargetClass.isInterface() && visibility.equals(PsiModifier.PACKAGE_LOCAL)
            && !Comparing.equal(elementPackage, myTargetPackage));
    }

    private void detectInaccessibleMemberUsages(ConflictsCollector collector) {
        PsiElement[] members = collectPackageLocalMembers(collector.getClassToMove());
        for (PsiElement member : members) {
            ReferencesSearch.search(member).forEach(psiReference -> {
                PsiElement element = psiReference.getElement();
                for (PsiClass psiClass : myClassesToMove) {
                    if (PsiTreeUtil.isAncestor(psiClass, element, false)) {
                        return true;
                    }
                }
                if (isInaccessibleFromTarget(element, PsiModifier.PACKAGE_LOCAL)) {
                    collector.addConflict(psiReference.resolve(), element);
                }
                return true;
            });
        }
    }

    @RequiredReadAction
    private static PsiElement[] collectPackageLocalMembers(PsiElement classToMove) {
        return PsiTreeUtil.collectElements(
            classToMove,
            element -> element instanceof PsiMember member
                && VisibilityUtil.getVisibilityModifier(member.getModifierList()) == PsiModifier.PACKAGE_LOCAL
        );
    }

    private static class ConflictsCollector {
        private final PsiClass myClassToMove;
        private final MultiMap<PsiElement, String> myConflicts;
        private final Set<PsiElement> myReportedContainers = new HashSet<>();

        public ConflictsCollector(PsiClass classToMove, MultiMap<PsiElement, String> conflicts) {
            myClassToMove = classToMove;
            myConflicts = conflicts;
        }

        @RequiredReadAction
        public synchronized void addConflict(PsiElement targetElement, PsiElement sourceElement) {
            PsiElement container = ConflictsUtil.getContainer(sourceElement);
            if (!myReportedContainers.contains(container)) {
                myReportedContainers.add(container);
                String targetDescription = (targetElement == myClassToMove)
                    ? "Class " + CommonRefactoringUtil.htmlEmphasize(myClassToMove.getName())
                    : StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, true));
                LocalizeValue message = RefactoringLocalize.elementWillNoLongerBeAccessible(
                    targetDescription,
                    RefactoringUIUtil.getDescription(container, true)
                );
                myConflicts.putValue(targetElement, message.get());
            }
        }

        public PsiElement getClassToMove() {
            return myClassToMove;
        }
    }
}
