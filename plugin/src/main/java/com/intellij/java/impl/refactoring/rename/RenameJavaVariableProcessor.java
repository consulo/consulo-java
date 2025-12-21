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
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@ExtensionImpl(id = "javaVariable")
public class RenameJavaVariableProcessor extends RenameJavaMemberProcessor {
    private static final Logger LOG = Logger.getInstance(RenameJavaVariableProcessor.class);

    @Override
    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof PsiVariable;
    }

    @Override
    @RequiredWriteAction
    public void renameElement(
        PsiElement psiElement,
        String newName,
        UsageInfo[] usages,
        @Nullable RefactoringElementListener listener
    ) throws IncorrectOperationException {
        PsiVariable variable = (PsiVariable) psiElement;
        List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<>();
        List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<>();

        List<PsiElement> occurrencesToCheckForConflict = new ArrayList<>();
        // rename all references
        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element == null) {
                continue;
            }

            if (usage instanceof MemberHidesStaticImportUsageInfo memberHidesStaticImportUsageInfo) {
                staticImportHides.add(memberHidesStaticImportUsageInfo);
            }
            else if (usage instanceof LocalHidesFieldUsageInfo) {
                PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement) element;

                if (collidingRef.resolve() instanceof PsiField field) {
                    qualifyMember(field, collidingRef, newName);
                }
                else {
                    // do nothing
                }
            }
            else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
                PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement) element;
                PsiField resolved = (PsiField) collidingRef.resolve();
                outerHides.add(new MemberHidesOuterMemberUsageInfo(element, resolved));
            }
            else {
                PsiReference ref;
                if (usage instanceof MoveRenameUsageInfo) {
                    ref = usage.getReference();
                }
                else {
                    ref = element.getReference();
                }
                if (ref != null) {
                    PsiElement newElem = ref.handleElementRename(newName);
                    if (variable instanceof PsiField) {
                        occurrencesToCheckForConflict.add(newElem);
                    }
                }
            }
        }
        // do actual rename
        variable.setName(newName);
        if (listener != null) {
            listener.elementRenamed(variable);
        }

        if (variable instanceof PsiField field) {
            for (PsiElement occurrence : occurrencesToCheckForConflict) {
                fixPossibleNameCollisionsForFieldRenaming(field, newName, occurrence);
            }
        }

        qualifyOuterMemberReferences(outerHides);
        qualifyStaticImportReferences(staticImportHides);
    }

    @RequiredWriteAction
    private static void fixPossibleNameCollisionsForFieldRenaming(PsiField field, String newName, PsiElement replacedOccurrence)
        throws IncorrectOperationException {
        if (!(replacedOccurrence instanceof PsiReferenceExpression refExpr)) {
            return;
        }
        PsiElement elem = refExpr.resolve();

        if (elem == null || elem == field || elem.isEquivalentTo(field)) {
            // If reference is unresolved, then field is not hidden by anyone...
            return;
        }

        if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter || (elem instanceof PsiField && elem != replacedOccurrence)) {
            qualifyMember(field, replacedOccurrence, newName);
        }
    }

    @Override
    @RequiredUIAccess
    public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
        if (element instanceof PsiField field && JavaLanguage.INSTANCE.equals(element.getLanguage())) {
            prepareFieldRenaming(field, newName, allRenames);
        }
    }

    @RequiredUIAccess
    private static void prepareFieldRenaming(PsiField field, String newName, Map<PsiElement, String> allRenames) {
        // search for getters/setters
        PsiClass aClass = field.getContainingClass();

        Project project = field.getProject();
        JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);

        String propertyName = manager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
        String newPropertyName = manager.variableNameToPropertyName(newName, VariableKind.FIELD);

        boolean isStatic = field.isStatic();
        PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
        PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);

        boolean shouldRenameSetterParameter = false;

        if (setter != null) {
            String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
            PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
            shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
        }

        String newGetterName = "";

        if (getter != null) {
            String getterId = getter.getName();
            newGetterName = PropertyUtil.suggestGetterName(newPropertyName, field.getType(), getterId);
            if (newGetterName.equals(getterId)) {
                getter = null;
                newGetterName = null;
            }
            else {
                for (PsiMethod method : getter.findDeepestSuperMethods()) {
                    if (method instanceof PsiCompiledElement) {
                        getter = null;
                        break;
                    }
                }
            }
        }

        String newSetterName = "";
        if (setter != null) {
            newSetterName = PropertyUtil.suggestSetterName(newPropertyName);
            String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
            if (newSetterName.equals(setter.getName())) {
                setter = null;
                newSetterName = null;
                shouldRenameSetterParameter = false;
            }
            else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
                shouldRenameSetterParameter = false;
            }
            else {
                for (PsiMethod method : setter.findDeepestSuperMethods()) {
                    if (method instanceof PsiCompiledElement) {
                        setter = null;
                        shouldRenameSetterParameter = false;
                        break;
                    }
                }
            }
        }

        if ((getter != null || setter != null) && askToRenameAccessors(getter, setter, newName, project)) {
            getter = null;
            setter = null;
            shouldRenameSetterParameter = false;
        }

        if (getter != null) {
            addOverriddenAndImplemented(getter, newGetterName, allRenames);
        }

        if (setter != null) {
            addOverriddenAndImplemented(setter, newSetterName, allRenames);
        }

        if (shouldRenameSetterParameter) {
            PsiParameter parameter = setter.getParameterList().getParameters()[0];
            allRenames.put(parameter, manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
        }
    }

    @RequiredUIAccess
    private static boolean askToRenameAccessors(PsiMethod getter, PsiMethod setter, String newName, Project project) {
        if (project.getApplication().isUnitTestMode()) {
            return false;
        }
        String text = RefactoringMessageUtil.getGetterSetterMessage(newName, RefactoringLocalize.renameTitle().get(), getter, setter);
        return Messages.showYesNoDialog(project, text, RefactoringLocalize.renameTitle().get(), UIUtil.getQuestionIcon()) != 0;
    }

    private static void addOverriddenAndImplemented(PsiMethod methodPrototype, String newName, Map<PsiElement, String> allRenames) {
        allRenames.put(methodPrototype, newName);
        PsiMethod[] methods = methodPrototype.findDeepestSuperMethods();
        if (methods.length == 0) {
            methods = new PsiMethod[]{methodPrototype};
        }
        for (PsiMethod method : methods) {
            OverridingMethodsSearch.search(method).forEach(psiMethod -> {
                RenameProcessor.assertNonCompileElement(psiMethod);
                allRenames.put(psiMethod, newName);
                return true;
            });
            allRenames.put(method, newName);
        }
    }

    @Override
    @RequiredReadAction
    public void findCollisions(
        PsiElement element,
        String newName,
        Map<? extends PsiElement, String> allRenames,
        List<UsageInfo> result
    ) {
        if (element instanceof PsiField field) {
            findMemberHidesOuterMemberCollisions(field, newName, result);
            findSubmemberHidesFieldCollisions(field, newName, result);
            findCollisionsAgainstNewName(field, newName, result);
        }
        else if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
            JavaUnresolvableLocalCollisionDetector.findCollisions(element, newName, result);
            findLocalHidesFieldCollisions(element, newName, allRenames, result);
        }
    }

    @Override
    public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, LocalizeValue> conflicts) {
        if (element instanceof PsiCompiledElement) {
            return;
        }
        if (element instanceof PsiField refactoredField) {
            if (newName.equals(refactoredField.getName())) {
                return;
            }
            ConflictsUtil.checkFieldConflicts(refactoredField.getContainingClass(), newName, conflicts);
        }
    }

    @Nullable
    @Override
    public String getHelpID(PsiElement element) {
        if (element instanceof PsiField) {
            return HelpID.RENAME_FIELD;
        }
        else if (element instanceof PsiLocalVariable) {
            return HelpID.RENAME_VARIABLE;
        }
        else if (element instanceof PsiParameter) {
            return HelpID.RENAME_PARAMETER;
        }
        return null;
    }

    @Override
    public boolean isToSearchInComments(PsiElement element) {
        if (element instanceof PsiField) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
        }
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
    }

    @Override
    public void setToSearchInComments(PsiElement element, boolean enabled) {
        if (element instanceof PsiField) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = enabled;
        }
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
    }

    @Override
    public boolean isToSearchForTextOccurrences(PsiElement element) {
        if (element instanceof PsiField) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
        }
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE;
    }

    @Override
    public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
        if (element instanceof PsiField) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = enabled;
        }
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled;
    }

    private static void findSubmemberHidesFieldCollisions(PsiField field, String newName, List<UsageInfo> result) {
        if (field.getContainingClass() == null) {
            return;
        }
        if (field.isPrivate()) {
            return;
        }
        PsiClass containingClass = field.getContainingClass();
        Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass, true).findAll();
        for (PsiClass inheritor : inheritors) {
            PsiField conflictingField = inheritor.findFieldByName(newName, false);
            if (conflictingField != null) {
                result.add(new SubmemberHidesMemberUsageInfo(conflictingField, field));
            }
        }
    }

    private static void findLocalHidesFieldCollisions(
        final PsiElement element,
        final String newName,
        final Map<? extends PsiElement, String> allRenames,
        final List<UsageInfo> result
    ) {
        if (!(element instanceof PsiLocalVariable) && !(element instanceof PsiParameter)) {
            return;
        }

        PsiClass topLevel = PsiUtil.getTopLevelClass(element);
        if (topLevel == null) {
            return;
        }

        PsiElement scopeElement;
        if (element instanceof PsiLocalVariable) {
            scopeElement = RefactoringUtil.getVariableScope((PsiLocalVariable) element);
        }
        else { // Parameter
            scopeElement = ((PsiParameter) element).getDeclarationScope();
        }

        LOG.assertTrue(scopeElement != null);
        scopeElement.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (!expression.isQualified() && expression.resolve() instanceof PsiField field) {
                    String fieldNewName = allRenames.containsKey(field) ? allRenames.get(field) : field.getName();
                    if (newName.equals(fieldNewName)) {
                        result.add(new LocalHidesFieldUsageInfo(expression, element));
                    }
                }
            }
        });
    }
}
