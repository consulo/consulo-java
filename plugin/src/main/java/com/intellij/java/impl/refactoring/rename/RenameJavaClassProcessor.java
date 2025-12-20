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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author yole
 */
@ExtensionImpl
public class RenameJavaClassProcessor extends RenamePsiElementProcessor {
    private static final Logger LOG = Logger.getInstance(RenameJavaClassProcessor.class);

    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof PsiClass;
    }

    public void renameElement(
        PsiElement element,
        String newName,
        UsageInfo[] usages,
        @Nullable RefactoringElementListener listener
    ) throws IncorrectOperationException {
        PsiClass aClass = (PsiClass) element;
        ArrayList<UsageInfo> postponedCollisions = new ArrayList<UsageInfo>();
        List<MemberHidesOuterMemberUsageInfo> hidesOut = new ArrayList<MemberHidesOuterMemberUsageInfo>();
        // rename all references
        for (UsageInfo usage : usages) {
            if (usage instanceof ResolvableCollisionUsageInfo) {
                if (usage instanceof CollidingClassImportUsageInfo) {
                    ((CollidingClassImportUsageInfo) usage).getImportStatement().delete();
                }
                else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
                    hidesOut.add((MemberHidesOuterMemberUsageInfo) usage);
                }
                else {
                    postponedCollisions.add(usage);
                }
            }
        }

        // do actual rename
        ChangeContextUtil.encodeContextInfo(aClass.getContainingFile(), true, false);
        aClass.setName(newName);

        for (UsageInfo usage : usages) {
            if (!(usage instanceof ResolvableCollisionUsageInfo)) {
                PsiReference ref = usage.getReference();
                if (ref == null) {
                    continue;
                }
                try {
                    ref.bindToElement(aClass);
                }
                catch (IncorrectOperationException e) {//fall back to old scheme
                    ref.handleElementRename(newName);
                }
            }
        }

        ChangeContextUtil.decodeContextInfo(
            aClass.getContainingFile(),
            null,
            null
        ); //to make refs to other classes from this one resolve to their old referent

        // resolve collisions
        for (UsageInfo postponedCollision : postponedCollisions) {
            ClassHidesImportedClassUsageInfo collision = (ClassHidesImportedClassUsageInfo) postponedCollision;
            collision.resolveCollision();
        }

    /*for (MemberHidesOuterMemberUsageInfo usage : hidesOut) {
      PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)usage.getElement();
      PsiReferenceExpression ref = RenameJavaMemberProcessor.createQualifiedMemberReference(aClass, collidingRef);
      collidingRef.replace(ref);
    }*/


        if (listener != null) {
            listener.elementRenamed(aClass);
        }
    }

    @Nullable
    public Pair<String, String> getTextOccurrenceSearchStrings(@Nonnull PsiElement element, @Nonnull String newName) {
        if (element instanceof PsiClass) {
            PsiClass aClass = (PsiClass) element;
            if (aClass.getParent() instanceof PsiClass) {
                String dollaredStringToSearch = ClassUtil.getJVMClassName(aClass);
                String dollaredStringToReplace =
                    dollaredStringToSearch == null ? null : RefactoringUtil.getNewInnerClassName(aClass, dollaredStringToSearch, newName);
                if (dollaredStringToReplace != null) {
                    return new Pair<String, String>(dollaredStringToSearch, dollaredStringToReplace);
                }
            }
        }
        return null;
    }

    public String getQualifiedNameAfterRename(PsiElement element, String newName, boolean nonJava) {
        if (nonJava) {
            PsiClass aClass = (PsiClass) element;
            return PsiUtilCore.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
        }
        else {
            return newName;
        }
    }

    @Override
    public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames, SearchScope scope) {
        PsiMethod[] constructors = ((PsiClass) element).getConstructors();
        for (PsiMethod constructor : constructors) {
            if (constructor instanceof PsiMirrorElement) {
                PsiElement prototype = ((PsiMirrorElement) constructor).getPrototype();
                if (prototype instanceof PsiNamedElement) {
                    allRenames.put(prototype, newName);
                }
            }
            else if (!(constructor instanceof LightElement)) {
                allRenames.put(constructor, newName);
            }
        }
    }

    public void findCollisions(
        PsiElement element,
        final String newName,
        Map<? extends PsiElement, String> allRenames,
        List<UsageInfo> result
    ) {
        final PsiClass aClass = (PsiClass) element;
        ClassCollisionsDetector classCollisionsDetector = new ClassCollisionsDetector(aClass);
        Collection<UsageInfo> initialResults = new ArrayList<UsageInfo>(result);
        for (UsageInfo usageInfo : initialResults) {
            if (usageInfo instanceof MoveRenameUsageInfo) {
                classCollisionsDetector.addClassCollisions(usageInfo.getElement(), newName, result);
            }
        }
        findSubmemberHidesMemberCollisions(aClass, newName, result);

        if (aClass instanceof PsiTypeParameter) {
            PsiTypeParameterListOwner owner = ((PsiTypeParameter) aClass).getOwner();
            if (owner != null) {
                for (PsiTypeParameter typeParameter : owner.getTypeParameters()) {
                    if (Comparing.equal(newName, typeParameter.getName())) {
                        result.add(new UnresolvableCollisionUsageInfo(aClass, typeParameter) {
                            @Override
                            public String getDescription() {
                                return "There is already type parameter in " + RefactoringUIUtil.getDescription(
                                    aClass,
                                    false
                                ) + " with name " + newName;
                            }
                        });
                    }
                }
            }
        }
    }

    public static void findSubmemberHidesMemberCollisions(final PsiClass aClass, String newName, final List<UsageInfo> result) {
        if (aClass.getParent() instanceof PsiClass) {
            PsiClass parent = (PsiClass) aClass.getParent();
            Collection<PsiClass> inheritors = ClassInheritorsSearch.search(parent, true).findAll();
            for (PsiClass inheritor : inheritors) {
                if (newName.equals(inheritor.getName())) {
                    ClassCollisionsDetector classCollisionsDetector = new ClassCollisionsDetector(aClass);
                    for (PsiReference reference : ReferencesSearch.search(inheritor, new LocalSearchScope(inheritor))) {
                        classCollisionsDetector.addClassCollisions(reference.getElement(), newName, result);
                    }
                }
                PsiClass[] inners = inheritor.getInnerClasses();
                for (PsiClass inner : inners) {
                    if (newName.equals(inner.getName())) {
                        result.add(new SubmemberHidesMemberUsageInfo(inner, aClass));
                    }
                }
            }
        }
        else if (aClass instanceof PsiTypeParameter) {
            PsiTypeParameterListOwner owner = ((PsiTypeParameter) aClass).getOwner();
            if (owner instanceof PsiClass) {
                PsiClass[] supers = ((PsiClass) owner).getSupers();
                for (PsiClass superClass : supers) {
                    if (newName.equals(superClass.getName())) {
                        ClassCollisionsDetector classCollisionsDetector = new ClassCollisionsDetector(aClass);
                        for (PsiReference reference : ReferencesSearch.search(superClass, new LocalSearchScope(superClass))) {
                            classCollisionsDetector.addClassCollisions(reference.getElement(), newName, result);
                        }
                    }
                    PsiClass[] inners = superClass.getInnerClasses();
                    for (PsiClass inner : inners) {
                        if (newName.equals(inner.getName())) {
                            ReferencesSearch.search(inner).forEach(new Processor<PsiReference>() {
                                public boolean process(PsiReference reference) {
                                    PsiElement refElement = reference.getElement();
                                    if (refElement instanceof PsiReferenceExpression && ((PsiReferenceExpression) refElement).isQualified()) {
                                        return true;
                                    }
                                    MemberHidesOuterMemberUsageInfo info = new MemberHidesOuterMemberUsageInfo(refElement, aClass);
                                    result.add(info);
                                    return true;
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private static class ClassCollisionsDetector {
        final HashSet<PsiFile> myProcessedFiles = new HashSet<PsiFile>();
        final PsiClass myRenamedClass;
        private final String myRenamedClassQualifiedName;

        public ClassCollisionsDetector(PsiClass renamedClass) {
            myRenamedClass = renamedClass;
            myRenamedClassQualifiedName = myRenamedClass.getQualifiedName();
        }

        public void addClassCollisions(PsiElement referenceElement, String newName, List<UsageInfo> results) {
            PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(referenceElement.getProject()).getResolveHelper();
            PsiClass aClass = resolveHelper.resolveReferencedClass(newName, referenceElement);
            if (aClass == null) {
                return;
            }
            if (aClass instanceof PsiTypeParameter && myRenamedClass instanceof PsiTypeParameter) {
                PsiTypeParameterListOwner member = PsiTreeUtil.getParentOfType(referenceElement, PsiTypeParameterListOwner.class);
                if (member != null) {
                    PsiTypeParameterList typeParameterList = member.getTypeParameterList();
                    if (typeParameterList != null && ArrayUtil.find(typeParameterList.getTypeParameters(), myRenamedClass) > -1) {
                        if (member.hasModifierProperty(PsiModifier.STATIC)) {
                            return;
                        }
                    }
                }
            }
            PsiFile containingFile = referenceElement.getContainingFile();
            String text = referenceElement.getText();
            if (Comparing.equal(myRenamedClassQualifiedName, removeSpaces(text))) {
                return;
            }
            if (myProcessedFiles.contains(containingFile)) {
                return;
            }
            for (PsiReference reference : ReferencesSearch.search(aClass, new LocalSearchScope(containingFile))) {
                PsiElement collisionReferenceElement = reference.getElement();
                if (collisionReferenceElement instanceof PsiJavaCodeReferenceElement) {
                    PsiElement parent = collisionReferenceElement.getParent();
                    if (parent instanceof PsiImportStatement) {
                        results.add(new CollidingClassImportUsageInfo((PsiImportStatement) parent, myRenamedClass));
                    }
                    else {
                        if (aClass.getQualifiedName() != null) {
                            results.add(new ClassHidesImportedClassUsageInfo((PsiJavaCodeReferenceElement) collisionReferenceElement,
                                myRenamedClass, aClass
                            ));
                        }
                        else {
                            results.add(new ClassHidesUnqualifiableClassUsageInfo((PsiJavaCodeReferenceElement) collisionReferenceElement,
                                myRenamedClass, aClass
                            ));
                        }
                    }
                }
            }
            myProcessedFiles.add(containingFile);
        }
    }

    @NonNls
    private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s");

    private static String removeSpaces(String s) {
        return WHITE_SPACE_PATTERN.matcher(s).replaceAll("");
    }

    @Override
    public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, LocalizeValue> conflicts) {
        if (element instanceof PsiCompiledElement) {
            return;
        }
        PsiClass aClass = (PsiClass) element;
        if (newName.equals(aClass.getName())) {
            return;
        }
        PsiClass containingClass = aClass.getContainingClass();
        if (containingClass != null) { // innerClass
            PsiClass[] innerClasses = containingClass.getInnerClasses();
            for (PsiClass innerClass : innerClasses) {
                if (newName.equals(innerClass.getName())) {
                    conflicts.putValue(
                        innerClass,
                        RefactoringLocalize.innerClass0IsAlreadyDefinedInClass1(newName, containingClass.getQualifiedName())
                    );
                    break;
                }
            }
        }
        else if (!(aClass instanceof PsiTypeParameter)) {
            String qualifiedNameAfterRename = PsiUtilCore.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
            Project project = element.getProject();
            PsiClass conflictingClass =
                JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
            if (conflictingClass != null) {
                conflicts.putValue(conflictingClass, RefactoringLocalize.class0AlreadyExists(qualifiedNameAfterRename));
            }
        }
    }

    @Nullable
    @NonNls
    public String getHelpID(PsiElement element) {
        return HelpID.RENAME_CLASS;
    }

    public boolean isToSearchInComments(PsiElement psiElement) {
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
    }

    public void setToSearchInComments(PsiElement element, boolean enabled) {
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled;
    }

    public boolean isToSearchForTextOccurrences(PsiElement element) {
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS;
    }

    public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled;
    }
}
