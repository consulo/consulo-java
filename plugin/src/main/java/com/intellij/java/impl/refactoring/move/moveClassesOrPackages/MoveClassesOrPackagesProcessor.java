/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classRefs.ClassInstanceScanner;
import com.intellij.java.impl.refactoring.util.classRefs.ClassReferenceScanner;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.PackageScope;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.*;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Jeka, dsl
 */
public class MoveClassesOrPackagesProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesProcessor.class);

    private final PsiElement[] myElementsToMove;
    private boolean mySearchInComments;
    private boolean mySearchInNonJavaFiles;
    private final PackageWrapper myTargetPackage;
    private final MoveCallback myMoveCallback;
    @Nonnull
    protected final MoveDestination myMoveDestination;
    protected NonCodeUsageInfo[] myNonCodeUsages;

    public MoveClassesOrPackagesProcessor(
        Project project,
        PsiElement[] elements,
        @Nonnull MoveDestination moveDestination,
        boolean searchInComments,
        boolean searchInNonJavaFiles,
        MoveCallback moveCallback
    ) {
        super(project);
        Set<PsiElement> toMove = new LinkedHashSet<>();
        for (PsiElement element : elements) {
            if (element instanceof PsiClassOwner classOwner) {
                Collections.addAll(toMove, classOwner.getClasses());
            }
            else {
                toMove.add(element);
            }
        }
        myElementsToMove = PsiUtilCore.toPsiElementArray(toMove);
        Arrays.sort(myElementsToMove, (o1, o2) -> {
            if (o1 instanceof PsiClass class1 && o2 instanceof PsiClass class2) {
                PsiFile containingFile = o1.getContainingFile();
                if (Comparing.equal(containingFile, o2.getContainingFile())) {
                    VirtualFile virtualFile = containingFile.getVirtualFile();
                    if (virtualFile != null) {
                        String fileName = virtualFile.getNameWithoutExtension();
                        if (Comparing.strEqual(fileName, class1.getName())) {
                            return -1;
                        }
                        if (Comparing.strEqual(fileName, class2.getName())) {
                            return 1;
                        }
                    }
                }
            }
            return 0;
        });
        myMoveDestination = moveDestination;
        myTargetPackage = myMoveDestination.getTargetPackage();
        mySearchInComments = searchInComments;
        mySearchInNonJavaFiles = searchInNonJavaFiles;
        myMoveCallback = moveCallback;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        PsiElement[] elements = new PsiElement[myElementsToMove.length];
        System.arraycopy(myElementsToMove, 0, elements, 0, myElementsToMove.length);
        return new MoveMultipleElementsViewDescriptor(elements, MoveClassesOrPackagesUtil.getPackageName(myTargetPackage));
    }

    @RequiredUIAccess
    public boolean verifyValidPackageName() {
        String qName = myTargetPackage.getQualifiedName();
        if (!StringUtil.isEmpty(qName)) {
            PsiNameHelper helper = PsiNameHelper.getInstance(myProject);
            if (!helper.isQualifiedName(qName)) {
                Messages.showMessageDialog(
                    myProject,
                    RefactoringLocalize.invalidTargetPackageNameSpecified().get(),
                    "Invalid Package Name",
                    UIUtil.getErrorIcon()
                );
                return false;
            }
        }
        return true;
    }

    private boolean hasClasses() {
        for (PsiElement element : getElements()) {
            if (element instanceof PsiClass) {
                return true;
            }
        }
        return false;
    }

    public boolean isSearchInComments() {
        return mySearchInComments;
    }

    public boolean isSearchInNonJavaFiles() {
        return mySearchInNonJavaFiles;
    }

    public void setSearchInComments(boolean searchInComments) {
        mySearchInComments = searchInComments;
    }

    public void setSearchInNonJavaFiles(boolean searchInNonJavaFiles) {
        mySearchInNonJavaFiles = searchInNonJavaFiles;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        List<UsageInfo> allUsages = new ArrayList<>();
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        for (PsiElement element : myElementsToMove) {
            String newName = getNewQName(element);
            if (newName == null) {
                continue;
            }
            UsageInfo[] usages = MoveClassesOrPackagesUtil.findUsages(element, mySearchInComments, mySearchInNonJavaFiles, newName);
            allUsages.addAll(new ArrayList<>(Arrays.asList(usages)));
            if (element instanceof PsiJavaPackage javaPackage) {
                for (PsiDirectory directory : javaPackage.getDirectories()) {
                    UsageInfo[] dirUsages =
                        MoveClassesOrPackagesUtil.findUsages(directory, mySearchInComments, mySearchInNonJavaFiles, newName);
                    allUsages.addAll(new ArrayList<>(Arrays.asList(dirUsages)));
                }
            }
        }
        myMoveDestination.analyzeModuleConflicts(
            Arrays.asList(myElementsToMove),
            conflicts,
            allUsages.toArray(new UsageInfo[allUsages.size()])
        );
        UsageInfo[] usageInfos = allUsages.toArray(new UsageInfo[allUsages.size()]);
        detectPackageLocalsMoved(usageInfos, conflicts);
        detectPackageLocalsUsed(conflicts);
        if (!conflicts.isEmpty()) {
            for (PsiElement element : conflicts.keySet()) {
                allUsages.add(new ConflictsUsageInfo(element, conflicts.get(element)));
            }
        }

        return UsageViewUtil.removeDuplicatedUsages(allUsages.toArray(new UsageInfo[allUsages.size()]));
    }

    public List<PsiElement> getElements() {
        return Collections.unmodifiableList(Arrays.asList(myElementsToMove));
    }

    public PackageWrapper getTargetPackage() {
        return myMoveDestination.getTargetPackage();
    }

    protected static class ConflictsUsageInfo extends UsageInfo {
        private final Collection<String> myConflicts;

        @RequiredReadAction
        public ConflictsUsageInfo(PsiElement pseudoElement, Collection<String> conflicts) {
            super(pseudoElement);
            myConflicts = conflicts;
        }

        public Collection<String> getConflicts() {
            return myConflicts;
        }
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usages = refUsages.get();
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        ArrayList<UsageInfo> filteredUsages = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (usage instanceof ConflictsUsageInfo info) {
                PsiElement element = info.getElement();
                conflicts.putValues(element, info.getConflicts());
            }
            else {
                filteredUsages.add(usage);
            }
        }

        refUsages.set(filteredUsages.toArray(new UsageInfo[filteredUsages.size()]));
        return showConflicts(conflicts, usages);
    }

    private boolean isInsideMoved(PsiElement place) {
        for (PsiElement element : myElementsToMove) {
            if (element instanceof PsiClass && PsiTreeUtil.isAncestor(element, place, false)) {
                return true;
            }
        }
        return false;
    }

    private void detectPackageLocalsUsed(MultiMap<PsiElement, String> conflicts) {
        PackageLocalsUsageCollector visitor = new PackageLocalsUsageCollector(myElementsToMove, myTargetPackage, conflicts);

        for (PsiElement element : myElementsToMove) {
            if (element instanceof PsiClass aClass) {
                aClass.accept(visitor);
            }
        }
    }

    @RequiredReadAction
    private void detectPackageLocalsMoved(UsageInfo[] usages, MultiMap<PsiElement, String> conflicts) {
//        Set reportedPackageLocalUsed = new HashSet();
        Set<PsiClass> movedClasses = new HashSet<>();
        Map<PsiClass, HashSet<PsiElement>> reportedClassToContainers = new HashMap<>();
        PackageWrapper aPackage = myTargetPackage;
        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element == null) {
                continue;
            }
            if (usage instanceof MoveRenameUsageInfo moveRenameUsageInfo && !(usage instanceof NonCodeUsageInfo)
                && moveRenameUsageInfo.getReferencedElement() instanceof PsiClass aClass) {
                if (!movedClasses.contains(aClass)) {
                    movedClasses.add(aClass);
                }
                String visibility = VisibilityUtil.getVisibilityModifier(aClass.getModifierList());
                if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
                    if (PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null) {
                        continue;
                    }
                    PsiElement container = ConflictsUtil.getContainer(element);
                    HashSet<PsiElement> reported = reportedClassToContainers.get(aClass);
                    if (reported == null) {
                        reported = new HashSet<>();
                        reportedClassToContainers.put(aClass, reported);
                    }

                    if (!reported.contains(container)) {
                        reported.add(container);
                        PsiFile containingFile = element.getContainingFile();
                        if (containingFile != null && !isInsideMoved(element)) {
                            PsiDirectory directory = containingFile.getContainingDirectory();
                            if (directory != null) {
                                PsiJavaPackage usagePackage = JavaDirectoryService.getInstance().getPackage(directory);
                                if (aPackage != null && usagePackage != null && !aPackage.equalToPackage(usagePackage)) {
                                    LocalizeValue message = RefactoringLocalize.aPackageLocalClass0WillNoLongerBeAccessibleFrom1(
                                        CommonRefactoringUtil.htmlEmphasize(aClass.getName()),
                                        RefactoringUIUtil.getDescription(container, true)
                                    );
                                    conflicts.putValue(aClass, message.get());
                                }
                            }
                        }
                    }
                }
            }
        }

        MyClassInstanceReferenceVisitor instanceReferenceVisitor = new MyClassInstanceReferenceVisitor(conflicts);
        for (PsiClass aClass : movedClasses) {
            String visibility = VisibilityUtil.getVisibilityModifier(aClass.getModifierList());
            if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
                findInstancesOfPackageLocal(aClass, usages, instanceReferenceVisitor);
            }
            else {
                // public classes
                findPublicClassConflicts(aClass, instanceReferenceVisitor);
            }
        }
    }

    static class ClassMemberWrapper {
        final PsiNamedElement myElement;
        final PsiModifierListOwner myMember;

        public ClassMemberWrapper(PsiNamedElement element) {
            myElement = element;
            myMember = (PsiModifierListOwner)element;
        }

        PsiModifierListOwner getMember() {
            return myMember;
        }

        @Override
        @RequiredReadAction
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClassMemberWrapper that)) {
                return false;
            }

            if (myElement instanceof PsiMethod thisMethod) {
                return that.myElement instanceof PsiMethod thatMethod
                    && MethodSignatureUtil.areSignaturesEqual(thisMethod, thatMethod);
            }

            return Objects.equals(myElement.getName(), that.myElement.getName());
        }

        @Override
        @RequiredReadAction
        public int hashCode() {
            String name = myElement.getName();
            return name != null ? name.hashCode() : 0;
        }
    }

    private static void findPublicClassConflicts(PsiClass aClass, MyClassInstanceReferenceVisitor instanceReferenceVisitor) {
        //noinspection MismatchedQueryAndUpdateOfCollection
        NonPublicClassMemberWrappersSet members = new NonPublicClassMemberWrappersSet();

        members.addElements(aClass.getFields());
        members.addElements(aClass.getMethods());
        members.addElements(aClass.getInnerClasses());

        RefactoringUtil.IsDescendantOf isDescendantOf = new RefactoringUtil.IsDescendantOf(aClass);
        PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
        GlobalSearchScope packageScope =
            aPackage == null ? aClass.getResolveScope() : PackageScope.packageScopeWithoutLibraries(aPackage, false);
        for (ClassMemberWrapper memberWrapper : members) {
            ReferencesSearch.search(memberWrapper.getMember(), packageScope, false).forEach(reference -> {
                PsiElement element = reference.getElement();
                if (element instanceof PsiReferenceExpression expression) {
                    PsiExpression qualifierExpression = expression.getQualifierExpression();
                    if (qualifierExpression != null) {
                        PsiType type = qualifierExpression.getType();
                        if (type != null) {
                            PsiClass resolvedTypeClass = PsiUtil.resolveClassInType(type);
                            if (isDescendantOf.value(resolvedTypeClass)) {
                                instanceReferenceVisitor.visitMemberReference(memberWrapper.getMember(), expression, isDescendantOf);
                            }
                        }
                    }
                    else {
                        instanceReferenceVisitor.visitMemberReference(memberWrapper.getMember(), expression, isDescendantOf);
                    }
                }
                return true;
            });
        }
    }

    private static void findInstancesOfPackageLocal(
        PsiClass aClass,
        UsageInfo[] usages,
        MyClassInstanceReferenceVisitor instanceReferenceVisitor
    ) {
        ClassReferenceScanner referenceScanner = new ClassReferenceScanner(aClass) {
            @Override
            @RequiredReadAction
            public PsiReference[] findReferences() {
                ArrayList<PsiReference> result = new ArrayList<>();
                for (UsageInfo usage : usages) {
                    if (usage instanceof MoveRenameUsageInfo moveRenameUsageInfo && moveRenameUsageInfo.getReferencedElement() == aClass) {
                        PsiReference reference = usage.getReference();
                        if (reference != null) {
                            result.add(reference);
                        }
                    }
                }
                return result.toArray(new PsiReference[result.size()]);
            }
        };
        referenceScanner.processReferences(new ClassInstanceScanner(aClass, instanceReferenceVisitor));
    }


    @Nullable
    @RequiredReadAction
    private String getNewQName(PsiElement element) {
        String qualifiedName = myTargetPackage.getQualifiedName();
        String newQName;
        String oldQName;
        if (element instanceof PsiClass psiClass) {
            newQName = StringUtil.getQualifiedName(qualifiedName, psiClass.getName());
            oldQName = psiClass.getQualifiedName();
        }
        else if (element instanceof PsiJavaPackage javaPackage) {
            newQName = StringUtil.getQualifiedName(qualifiedName, javaPackage.getName());
            oldQName = javaPackage.getQualifiedName();
        }
        else {
            LOG.assertTrue(false);
            newQName = null;
            oldQName = null;
        }
        if (Comparing.strEqual(newQName, oldQName)) {
            return null;
        }
        return newQName;
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        LOG.assertTrue(elements.length == myElementsToMove.length);
        System.arraycopy(elements, 0, myElementsToMove, 0, elements.length);
    }

    @Override
    protected boolean isPreviewUsages(@Nonnull UsageInfo[] usages) {
        if (UsageViewUtil.hasNonCodeUsages(usages)) {
            WindowManager.getInstance().getStatusBar(myProject)
                .setInfo(RefactoringLocalize.occurrencesFoundInCommentsStringsAndNonJavaFiles().get());
            return true;
        }
        else {
            return super.isPreviewUsages(usages);
        }
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        // If files are being moved then I need to collect some information to delete these
        // filese from CVS. I need to know all common parents of the moved files and releative
        // paths.

        // Move files with correction of references.

        try {
            Map<PsiClass, Boolean> allClasses = new HashMap<>();
            for (PsiElement element : myElementsToMove) {
                if (element instanceof PsiClass psiClass) {
                    if (allClasses.containsKey(psiClass)) {
                        continue;
                    }
                    myProject.getApplication().getExtensionPoint(MoveAllClassesInFileHandler.class)
                        .forEach(fileHandler -> fileHandler.processMoveAllClassesInFile(allClasses, psiClass, myElementsToMove));
                    }
                }
            Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<>();
            for (int idx = 0; idx < myElementsToMove.length; idx++) {
                PsiElement element = myElementsToMove[idx];
                RefactoringElementListener elementListener = getTransaction().getElementListener(element);
                if (element instanceof PsiJavaPackage javaPackage) {
                    PsiDirectory[] directories = javaPackage.getDirectories();
                    PsiJavaPackage newElement = MoveClassesOrPackagesUtil.doMovePackage(javaPackage, myMoveDestination);
                    LOG.assertTrue(newElement != null, element);
                    oldToNewElementsMapping.put(element, newElement);
                    int i = 0;
                    PsiDirectory[] newDirectories = newElement.getDirectories();
                    if (newDirectories.length == 1) {//everything is moved in one directory
                        for (PsiDirectory directory : directories) {
                            oldToNewElementsMapping.put(directory, newDirectories[0]);
                        }
                    }
                    else {
                        for (PsiDirectory directory : directories) {
                            oldToNewElementsMapping.put(directory, newDirectories[i++]);
                        }
                    }
                    element = newElement;
                }
                else if (element instanceof PsiClass psiClass) {
                    MoveClassesOrPackagesUtil.prepareMoveClass(psiClass);
                    PsiClass newElement = MoveClassesOrPackagesUtil.doMoveClass(
                        psiClass,
                        myMoveDestination.getTargetDirectory(element.getContainingFile()),
                        allClasses.get(psiClass)
                    );
                    oldToNewElementsMapping.put(element, newElement);
                    element = newElement;
                }
                else {
                    LOG.error("Unexpected element to move: " + element);
                }
                elementListener.elementMoved(element);
                myElementsToMove[idx] = element;
            }

            for (PsiElement element : myElementsToMove) {
                if (element instanceof PsiClass psiClass) {
                    MoveClassesOrPackagesUtil.finishMoveClass(psiClass);
                }
            }

            myNonCodeUsages = CommonMoveUtil.retargetUsages(usages, oldToNewElementsMapping);
        }
        catch (IncorrectOperationException e) {
            myNonCodeUsages = new NonCodeUsageInfo[0];
            RefactoringUIUtil.processIncorrectOperation(myProject, e);
        }
    }

    @Override
    @RequiredWriteAction
    protected void performPsiSpoilingRefactoring() {
        RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
        if (myMoveCallback != null) {
            if (myMoveCallback instanceof MoveClassesOrPackagesCallback moveClassesOrPackagesCallback) {
                moveClassesOrPackagesCallback.classesOrPackagesMoved(myMoveDestination);
            }
            myMoveCallback.refactoringCompleted();
        }
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        String elements = RefactoringUIUtil.calculatePsiElementDescriptionList(myElementsToMove);
        String target = myTargetPackage.getQualifiedName();
        return RefactoringLocalize.moveClassesCommand(elements, target).get();
    }

    private class MyClassInstanceReferenceVisitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
        private final MultiMap<PsiElement, String> myConflicts;
        private final HashMap<PsiModifierListOwner, HashSet<PsiElement>> myReportedElementToContainer = new HashMap<>();
        private final HashMap<PsiClass, RefactoringUtil.IsDescendantOf> myIsDescendantOfCache = new HashMap<>();

        public MyClassInstanceReferenceVisitor(MultiMap<PsiElement, String> conflicts) {
            myConflicts = conflicts;
        }

        @Override
        @RequiredReadAction
        public void visitQualifier(
            PsiReferenceExpression qualified,
            PsiExpression instanceRef,
            PsiElement referencedInstance
        ) {
            PsiElement resolved = qualified.resolve();

            if (resolved instanceof PsiMember member) {
                PsiClass containingClass = member.getContainingClass();
                RefactoringUtil.IsDescendantOf isDescendantOf = myIsDescendantOfCache.get(containingClass);
                if (isDescendantOf == null) {
                    isDescendantOf = new RefactoringUtil.IsDescendantOf(containingClass);
                    myIsDescendantOfCache.put(containingClass, isDescendantOf);
                }
                visitMemberReference(member, qualified, isDescendantOf);
            }
        }

        private synchronized void visitMemberReference(
            PsiModifierListOwner member,
            PsiReferenceExpression qualified,
            RefactoringUtil.IsDescendantOf descendantOf
        ) {
            if (member.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
                visitPackageLocalMemberReference(qualified, member);
            }
            else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
                PsiExpression qualifier = qualified.getQualifierExpression();
                if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
                    visitPackageLocalMemberReference(qualified, member);
                }
                else if (!isInInheritor(qualified, descendantOf)) {
                    visitPackageLocalMemberReference(qualified, member);
                }
            }
        }

        private boolean isInInheritor(PsiReferenceExpression qualified, RefactoringUtil.IsDescendantOf descendantOf) {
            PsiClass aClass = PsiTreeUtil.getParentOfType(qualified, PsiClass.class);
            while (aClass != null) {
                if (descendantOf.value(aClass)) {
                    return true;
                }
                aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
            }
            return false;
        }

        private void visitPackageLocalMemberReference(PsiJavaCodeReferenceElement qualified, PsiModifierListOwner member) {
            PsiElement container = ConflictsUtil.getContainer(qualified);
            HashSet<PsiElement> reportedContainers = myReportedElementToContainer.get(member);
            if (reportedContainers == null) {
                reportedContainers = new HashSet<>();
                myReportedElementToContainer.put(member, reportedContainers);
            }

            if (!reportedContainers.contains(container)) {
                reportedContainers.add(container);
                if (!isInsideMoved(container)) {
                    PsiFile containingFile = container.getContainingFile();
                    if (containingFile != null) {
                        PsiDirectory directory = containingFile.getContainingDirectory();
                        if (directory != null) {
                            PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
                            if (!myTargetPackage.equalToPackage(aPackage)) {
                                LocalizeValue message = RefactoringLocalize.zeroWillBeInaccessibleFrom1(
                                    RefactoringUIUtil.getDescription(member, true),
                                    RefactoringUIUtil.getDescription(container, true)
                                );
                                myConflicts.putValue(member, CommonRefactoringUtil.capitalize(message.get()));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void visitTypeCast(PsiTypeCastExpression typeCastExpression, PsiExpression instanceRef, PsiElement referencedInstance) {
        }

        @Override
        public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
        }

        @Override
        public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
        }
    }

    private static class NonPublicClassMemberWrappersSet extends HashSet<ClassMemberWrapper> {
        public void addElement(PsiMember member) {
            if (member.isPublic() || member.isPrivate()) {
                return;
            }
            PsiNamedElement namedElement = (PsiNamedElement)member;
            add(new ClassMemberWrapper(namedElement));
        }

        public void addElements(PsiMember[] members) {
            for (PsiMember member : members) {
                addElement(member);
            }
        }
    }
}
