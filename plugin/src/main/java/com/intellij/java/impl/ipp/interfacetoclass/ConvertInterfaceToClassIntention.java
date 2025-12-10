/*
 * Copyright 2006-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.interfacetoclass;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessToken;
import consulo.application.WriteAction;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertInterfaceToClassIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class ConvertInterfaceToClassIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.convertInterfaceToClassIntentionName();
    }

    @RequiredWriteAction
    private static void changeInterfaceToClass(PsiClass anInterface) throws IncorrectOperationException {
        PsiIdentifier nameIdentifier = anInterface.getNameIdentifier();
        assert nameIdentifier != null;
        PsiElement whiteSpace = nameIdentifier.getPrevSibling();
        assert whiteSpace != null;
        PsiElement interfaceToken = whiteSpace.getPrevSibling();
        assert interfaceToken != null;
        PsiKeyword interfaceKeyword = (PsiKeyword) interfaceToken.getOriginalElement();
        Project project = anInterface.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiKeyword classKeyword = factory.createKeyword("class");
        interfaceKeyword.replace(classKeyword);

        PsiModifierList classModifierList = anInterface.getModifierList();
        if (classModifierList == null) {
            return;
        }
        classModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);

        if (anInterface.getParent() instanceof PsiClass) {
            classModifierList.setModifierProperty(PsiModifier.STATIC, true);
        }

        PsiMethod[] methods = anInterface.getMethods();
        for (PsiMethod method : methods) {
            PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
            if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
                PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, false);
            }
            else {
                PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true);
            }
        }

        PsiField[] fields = anInterface.getFields();
        for (PsiField field : fields) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList != null) {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
                modifierList.setModifierProperty(PsiModifier.STATIC, true);
                modifierList.setModifierProperty(PsiModifier.FINAL, true);
            }
        }

        PsiClass[] innerClasses = anInterface.getInnerClasses();
        for (PsiClass innerClass : innerClasses) {
            PsiModifierList modifierList = innerClass.getModifierList();
            if (modifierList != null) {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
                if (!innerClass.isInterface()) {
                    modifierList.setModifierProperty(PsiModifier.STATIC, true);
                }
            }
        }
    }

    @Override
    @RequiredWriteAction
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiClass anInterface = (PsiClass) element.getParent();
        SearchScope searchScope = anInterface.getUseScope();
        Query<PsiClass> query = ClassInheritorsSearch.search(anInterface, searchScope, false);
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        query.forEach(aClass -> {
            PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList == null) {
                return true;
            }
            PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
            if (referenceElements.length > 0) {
                PsiElement target = referenceElements[0].resolve();
                if (target != null) {
                    conflicts.putValue(
                        aClass,
                        IntentionPowerPackLocalize.zeroAlreadyExtends1AndWillNotCompileAfterConverting2ToAClass(
                            RefactoringUIUtil.getDescription(
                                aClass,
                                true
                            ),
                            RefactoringUIUtil.getDescription(target, true),
                            RefactoringUIUtil.getDescription(anInterface, false)
                        )
                    );
                }
            }
            return true;
        });
        boolean conflictsDialogOK;
        if (conflicts.isEmpty()) {
            conflictsDialogOK = true;
        }
        else {
            ConflictsDialog conflictsDialog = new ConflictsDialog(
                anInterface.getProject(),
                conflicts,
                () -> {
                    AccessToken token = WriteAction.start();
                    try {
                        convertInterfaceToClass(anInterface);
                    }
                    finally {
                        token.finish();
                    }
                }
            );
            conflictsDialog.show();
            conflictsDialogOK = conflictsDialog.isOK();
        }
        if (conflictsDialogOK) {
            convertInterfaceToClass(anInterface);
        }
    }

    @RequiredWriteAction
    private static void convertInterfaceToClass(PsiClass anInterface) {
        boolean success = moveSubClassImplementsToExtends(anInterface);
        if (!success) {
            return;
        }
        changeInterfaceToClass(anInterface);
        moveExtendsToImplements(anInterface);
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertInterfaceToClassPredicate();
    }

    @RequiredWriteAction
    private static void moveExtendsToImplements(PsiClass anInterface) throws IncorrectOperationException {
        PsiReferenceList extendsList = anInterface.getExtendsList();
        PsiReferenceList implementsList = anInterface.getImplementsList();
        assert extendsList != null;
        PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
        for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
            assert implementsList != null;
            implementsList.add(referenceElement);
            referenceElement.delete();
        }
    }

    @RequiredWriteAction
    private static boolean moveSubClassImplementsToExtends(PsiClass oldInterface) throws IncorrectOperationException {
        Project project = oldInterface.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory elementFactory = psiFacade.getElementFactory();
        PsiJavaCodeReferenceElement oldInterfaceReference = elementFactory.createClassReferenceElement(oldInterface);
        SearchScope searchScope = oldInterface.getUseScope();
        Query<PsiClass> query = ClassInheritorsSearch.search(oldInterface, searchScope, false);
        Collection<PsiClass> inheritors = query.findAll();
        boolean success = CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, inheritors, false);
        if (!success) {
            return false;
        }
        for (PsiClass inheritor : inheritors) {
            PsiReferenceList implementsList = inheritor.getImplementsList();
            PsiReferenceList extendsList = inheritor.getExtendsList();
            if (implementsList != null) {
                moveReference(implementsList, extendsList, oldInterfaceReference);
            }
        }
        return true;
    }

    @RequiredWriteAction
    private static void moveReference(
        @Nonnull PsiReferenceList source,
        @Nullable PsiReferenceList target,
        @Nonnull PsiJavaCodeReferenceElement reference
    ) throws IncorrectOperationException {
        PsiJavaCodeReferenceElement[] implementsReferences = source.getReferenceElements();
        String qualifiedName = reference.getQualifiedName();
        for (PsiJavaCodeReferenceElement implementsReference : implementsReferences) {
            String implementsReferenceQualifiedName = implementsReference.getQualifiedName();
            if (qualifiedName.equals(implementsReferenceQualifiedName)) {
                if (target != null) {
                    target.add(implementsReference);
                }
                implementsReference.delete();
            }
        }
    }
}