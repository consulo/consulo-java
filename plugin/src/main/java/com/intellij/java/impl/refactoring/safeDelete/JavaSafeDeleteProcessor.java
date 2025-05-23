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
package com.intellij.java.impl.refactoring.safeDelete;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.java.impl.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.safeDelete.usageInfo.*;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.ide.impl.find.PsiElement2UsageTargetAdapter;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.safeDelete.NonCodeUsageSearchInfo;
import consulo.language.editor.refactoring.safeDelete.SafeDeleteHandler;
import consulo.language.editor.refactoring.safeDelete.SafeDeleteProcessor;
import consulo.language.editor.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.*;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nullable;

import java.util.*;

@ExtensionImpl(id = "javaProcessor")
public class JavaSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {
    private static final Logger LOG = Logger.getInstance(JavaSafeDeleteProcessor.class);

    public boolean handlesElement(final PsiElement element) {
        return element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField
            || element instanceof PsiParameter || element instanceof PsiLocalVariable || element instanceof PsiPackage;
    }

    @Nullable
    @RequiredUIAccess
    public NonCodeUsageSearchInfo findUsages(
        final PsiElement element,
        final PsiElement[] allElementsToDelete,
        final List<UsageInfo> usages
    ) {
        Condition<PsiElement> insideDeletedCondition = getUsageInsideDeletedFilter(allElementsToDelete);
        if (element instanceof PsiClass psiClass) {
            findClassUsages(psiClass, allElementsToDelete, usages);
            if (element instanceof PsiTypeParameter typeParameter) {
                findTypeParameterExternalUsages(typeParameter, usages);
            }
        }
        else if (element instanceof PsiMethod method) {
            insideDeletedCondition = findMethodUsages(method, allElementsToDelete, usages);
        }
        else if (element instanceof PsiField field) {
            insideDeletedCondition = findFieldUsages(field, usages, allElementsToDelete);
        }
        else if (element instanceof PsiParameter parameter) {
            LOG.assertTrue(parameter.getDeclarationScope() instanceof PsiMethod);
            findParameterUsages(parameter, usages);
        }
        else if (element instanceof PsiLocalVariable localVariable) {
            for (PsiReference reference : ReferencesSearch.search(element)) {
                PsiReferenceExpression referencedElement = (PsiReferenceExpression)reference.getElement();
                final PsiStatement statement = PsiTreeUtil.getParentOfType(referencedElement, PsiStatement.class);

                boolean isSafeToDelete = PsiUtil.isAccessedForWriting(referencedElement);
                boolean hasSideEffects = false;
                if (PsiUtil.isOnAssignmentLeftHand(referencedElement)) {
                    hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(
                        ((PsiAssignmentExpression)referencedElement.getParent()).getRExpression(),
                        localVariable,
                        new ArrayList<>()
                    );
                }
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(statement, element, isSafeToDelete && !hasSideEffects));
            }
        }
        return new NonCodeUsageSearchInfo(insideDeletedCondition, element);
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public Collection<? extends PsiElement> getElementsToSearch(
        PsiElement element,
        @Nullable Module module,
        Collection<PsiElement> allElementsToDelete
    ) {
        Project project = element.getProject();
        if (element instanceof PsiPackage psiPackage && module != null) {
            final PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
            if (directories.length == 0) {
                return null;
            }
            return Arrays.asList(directories);
        }
        else if (element instanceof PsiMethod method) {
            final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods(
                method,
                RefactoringLocalize.toDeleteWithUsageSearch().get(),
                allElementsToDelete
            );
            if (methods.length == 0) {
                return null;
            }
            final ArrayList<PsiMethod> psiMethods = new ArrayList<>(Arrays.asList(methods));
            psiMethods.add(method);
            return psiMethods;
        }
        else if (element instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
            final Set<PsiParameter> parametersToDelete = new HashSet<>();
            parametersToDelete.add(parameter);
            final int parameterIndex = method.getParameterList().getParameterIndex((PsiParameter)element);
            final List<PsiMethod> superMethods = new ArrayList<>(Arrays.asList(method.findDeepestSuperMethods()));
            if (superMethods.isEmpty()) {
                superMethods.add(method);
            }
            for (PsiMethod superMethod : superMethods) {
                parametersToDelete.add(superMethod.getParameterList().getParameters()[parameterIndex]);
                OverridingMethodsSearch.search(superMethod).forEach(overrider -> {
                    parametersToDelete.add(overrider.getParameterList().getParameters()[parameterIndex]);
                    return true;
                });
            }

            if (parametersToDelete.size() > 1 && !project.getApplication().isUnitTestMode()) {
                LocalizeValue message =
                    RefactoringLocalize.zeroIsAPartOfMethodHierarchyDoYouWantToDeleteMultipleParameters(UsageViewUtil.getLongName(method));
                if (Messages.showYesNoDialog(
                    project,
                    message.get(),
                    SafeDeleteHandler.REFACTORING_NAME.get(),
                    UIUtil.getQuestionIcon()
                ) != DialogWrapper.OK_EXIT_CODE) {
                    return null;
                }
            }
            return parametersToDelete;
        }
        else {
            return Collections.singletonList(element);
        }
    }

    @Override
    public UsageView showUsages(UsageInfo[] usages, UsageViewPresentation presentation, UsageViewManager manager, PsiElement[] elements) {
        final List<PsiElement> overridingMethods = new ArrayList<>();
        final List<UsageInfo> others = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (usage instanceof SafeDeleteOverridingMethodUsageInfo safeDeleteOverridingMethodUsageInfo) {
                overridingMethods.add(safeDeleteOverridingMethodUsageInfo.getOverridingMethod());
            }
            else {
                others.add(usage);
            }
        }

        UsageTarget[] targets = new UsageTarget[elements.length + overridingMethods.size()];
        for (int i = 0; i < targets.length; i++) {
            if (i < elements.length) {
                targets[i] = new PsiElement2UsageTargetAdapter(elements[i]);
            }
            else {
                targets[i] = new PsiElement2UsageTargetAdapter(overridingMethods.get(i - elements.length));
            }
        }

        return manager.showUsages(
            targets,
            UsageInfoToUsageConverter.convert(
                new UsageInfoToUsageConverter.TargetElementsDescriptor(elements),
                others.toArray(new UsageInfo[others.size()])
            ),
            presentation
        );
    }

    @RequiredUIAccess
    public Collection<PsiElement> getAdditionalElementsToDelete(
        final PsiElement element,
        final Collection<PsiElement> allElementsToDelete,
        final boolean askUser
    ) {
        if (element instanceof PsiField field) {
            final Project project = element.getProject();
            String propertyName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);

            PsiClass aClass = field.getContainingClass();
            if (aClass != null) {
                boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
                PsiMethod[] getters = GetterSetterPrototypeProvider.findGetters(aClass, propertyName, isStatic);
                if (getters != null) {
                    final List<PsiMethod> validGetters = new ArrayList<>(1);
                    for (PsiMethod getter : getters) {
                        if (!allElementsToDelete.contains(getter) && (getter != null && getter.isPhysical())) {
                            validGetters.add(getter);
                        }
                    }
                    getters = validGetters.isEmpty() ? null : validGetters.toArray(new PsiMethod[validGetters.size()]);
                }

                PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);
                if (allElementsToDelete.contains(setter) || setter != null && !setter.isPhysical()) {
                    setter = null;
                }
                if (askUser && (getters != null || setter != null)) {
                    final String message =
                        RefactoringMessageUtil.getGetterSetterMessage(
                            field.getName(),
                            RefactoringLocalize.deleteTitle().get(),
                            getters != null ? getters[0] : null,
                            setter
                        );
                    if (!project.getApplication().isUnitTestMode()
                        && Messages.showYesNoDialog(
                        project,
                        message,
                        RefactoringLocalize.safeDeleteTitle().get(),
                        UIUtil.getQuestionIcon()
                    ) != 0) {
                        getters = null;
                        setter = null;
                    }
                }
                List<PsiElement> elements = new ArrayList<>();
                if (setter != null) {
                    elements.add(setter);
                }
                if (getters != null) {
                    Collections.addAll(elements, getters);
                }
                return elements;
            }
        }
        return null;
    }

    public Collection<String> findConflicts(final PsiElement element, final PsiElement[] allElementsToDelete) {
        if (element instanceof PsiMethod method) {
            final PsiClass containingClass = method.getContainingClass();

            if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                final PsiMethod[] superMethods = method.findSuperMethods();
                for (PsiMethod superMethod : superMethods) {
                    if (isInside(superMethod, allElementsToDelete)) {
                        continue;
                    }
                    if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                        LocalizeValue message = RefactoringLocalize.zeroImplements1(
                            RefactoringUIUtil.getDescription(element, true),
                            RefactoringUIUtil.getDescription(superMethod, true)
                        );
                        return Collections.singletonList(message.get());
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    @RequiredUIAccess
    public UsageInfo[] preprocessUsages(final Project project, final UsageInfo[] usages) {
        ArrayList<UsageInfo> result = new ArrayList<>();
        ArrayList<UsageInfo> overridingMethods = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (usage.isNonCodeUsage) {
                result.add(usage);
            }
            else if (usage instanceof SafeDeleteOverridingMethodUsageInfo) {
                overridingMethods.add(usage);
            }
            else {
                result.add(usage);
            }
        }

        if (!overridingMethods.isEmpty()) {
            if (project.getApplication().isUnitTestMode()) {
                result.addAll(overridingMethods);
            }
            else {
                OverridingMethodsDialog dialog = new OverridingMethodsDialog(project, overridingMethods);
                dialog.show();
                if (!dialog.isOK()) {
                    return null;
                }
                result.addAll(dialog.getSelected());
            }
        }

        return result.toArray(new UsageInfo[result.size()]);
    }

    public void prepareForDeletion(final PsiElement element) throws IncorrectOperationException {
        if (element instanceof PsiVariable variable) {
            variable.normalizeDeclaration();
        }
    }

    @Override
    public boolean isToSearchInComments(PsiElement element) {
        if (element instanceof PsiClass) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
        }
        else if (element instanceof PsiMethod) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
        }
        else if (element instanceof PsiVariable) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
        }
        else if (element instanceof PsiPackage) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
        }
        return false;
    }

    @Override
    public void setToSearchInComments(PsiElement element, boolean enabled) {
        if (element instanceof PsiClass) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled;
        }
        else if (element instanceof PsiMethod) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = enabled;
        }
        else if (element instanceof PsiVariable) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
        }
        else if (element instanceof PsiPackage) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
        }
    }

    @Override
    public boolean isToSearchForTextOccurrences(PsiElement element) {
        if (element instanceof PsiClass) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS;
        }
        else if (element instanceof PsiMethod) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
        }
        else if (element instanceof PsiVariable) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE;
        }
        else if (element instanceof PsiPackage) {
            return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
        }
        return false;
    }

    @Override
    public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
        if (element instanceof PsiClass) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled;
        }
        else if (element instanceof PsiMethod) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD = enabled;
        }
        else if (element instanceof PsiVariable) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled;
        }
        else if (element instanceof PsiPackage) {
            JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
        }
    }

    public static Condition<PsiElement> getUsageInsideDeletedFilter(final PsiElement[] allElementsToDelete) {
        return usage -> !(usage instanceof PsiFile) && isInside(usage, allElementsToDelete);
    }

    private static void findClassUsages(final PsiClass psiClass, final PsiElement[] allElementsToDelete, final List<UsageInfo> usages) {
        final boolean justPrivates = containsOnlyPrivates(psiClass);

        ReferencesSearch.search(psiClass).forEach(reference -> {
            final PsiElement element = reference.getElement();

            if (!isInside(element, allElementsToDelete)) {
                PsiElement parent = element.getParent();
                if (parent instanceof PsiReferenceList && parent.getParent() instanceof PsiClass inheritor) {
                    //If psiClass contains only private members, then it is safe to remove it and change inheritor's extends/implements accordingly
                    if (justPrivates && (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList()))) {
                        usages.add(new SafeDeleteExtendsClassUsageInfo((PsiJavaCodeReferenceElement)element, psiClass, inheritor));
                        return true;
                    }
                }
                LOG.assertTrue(element.getTextRange() != null);
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiClass, isInNonStaticImport(element)));
            }
            return true;
        });
    }

    private static boolean isInNonStaticImport(PsiElement element) {
        return ImportSearcher.getImport(element, true) != null;
    }

    @RequiredReadAction
    private static boolean containsOnlyPrivates(final PsiClass aClass) {
        final PsiField[] fields = aClass.getFields();
        for (PsiField field : fields) {
            if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
                return false;
            }
        }

        final PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
            if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
                if (method.isConstructor()) { //skip non-private constructors with call to super only
                    final PsiCodeBlock body = method.getBody();
                    if (body != null) {
                        final PsiStatement[] statements = body.getStatements();
                        if (statements.length == 0) {
                            continue;
                        }
                        if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement expressionStatement) {
                            final PsiExpression expression = expressionStatement.getExpression();
                            if (expression instanceof PsiMethodCallExpression methodCallExpression) {
                                PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
                                if (methodExpression.getText().equals(PsiKeyword.SUPER)) {
                                    continue;
                                }
                            }
                        }
                    }
                }
                return false;
            }
        }

        final PsiClass[] inners = aClass.getInnerClasses();
        for (PsiClass inner : inners) {
            if (!inner.hasModifierProperty(PsiModifier.PRIVATE)) {
                return false;
            }
        }

        return true;
    }

    private static void findTypeParameterExternalUsages(final PsiTypeParameter typeParameter, final Collection<UsageInfo> usages) {
        PsiTypeParameterListOwner owner = typeParameter.getOwner();
        if (owner != null) {
            final PsiTypeParameterList parameterList = owner.getTypeParameterList();
            if (parameterList != null) {
                final int paramsCount = parameterList.getTypeParameters().length;
                final int index = parameterList.getTypeParameterIndex(typeParameter);

                ReferencesSearch.search(owner).forEach(reference -> {
                    if (reference instanceof PsiJavaCodeReferenceElement javaCodeReferenceElement) {
                        final PsiReferenceParameterList parameterList1 = javaCodeReferenceElement.getParameterList();
                        if (parameterList1 != null) {
                            PsiTypeElement[] typeArgs = parameterList1.getTypeParameterElements();
                            if (typeArgs.length > index) {
                                if (typeArgs.length == 1 && paramsCount > 1 && typeArgs[0].getType() instanceof PsiDiamondType) {
                                    return true;
                                }
                                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(typeArgs[index], typeParameter, true));
                            }
                        }
                    }
                    return true;
                });
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private static Condition<PsiElement> findMethodUsages(
        PsiMethod psiMethod,
        final PsiElement[] allElementsToDelete,
        List<UsageInfo> usages
    ) {
        final Collection<PsiReference> references = ReferencesSearch.search(psiMethod).findAll();

        if (psiMethod.isConstructor()) {
            return findConstructorUsages(psiMethod, references, usages, allElementsToDelete);
        }
        final PsiMethod[] overridingMethods =
            removeDeletedMethods(OverridingMethodsSearch.search(psiMethod, true).toArray(PsiMethod.EMPTY_ARRAY), allElementsToDelete);

        final HashMap<PsiMethod, Collection<PsiReference>> methodToReferences = new HashMap<>();
        for (PsiMethod overridingMethod : overridingMethods) {
            final Collection<PsiReference> overridingReferences = ReferencesSearch.search(overridingMethod).findAll();
            methodToReferences.put(overridingMethod, overridingReferences);
        }
        final Set<PsiMethod> validOverriding = validateOverridingMethods(
            psiMethod,
            references,
            Arrays.asList(overridingMethods),
            methodToReferences,
            usages,
            allElementsToDelete
        );
        for (PsiReference reference : references) {
            final PsiElement element = reference.getElement();
            if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(
                    element,
                    psiMethod,
                    PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) != null
                ));
            }
        }
        return usage -> !(usage instanceof PsiFile) && (isInside(usage, allElementsToDelete) || isInside(usage, validOverriding));
    }

    private static PsiMethod[] removeDeletedMethods(PsiMethod[] methods, final PsiElement[] allElementsToDelete) {
        ArrayList<PsiMethod> list = new ArrayList<>();
        for (PsiMethod method : methods) {
            boolean isDeleted = false;
            for (PsiElement element : allElementsToDelete) {
                if (element == method) {
                    isDeleted = true;
                    break;
                }
            }
            if (!isDeleted) {
                list.add(method);
            }
        }
        return list.toArray(new PsiMethod[list.size()]);
    }

    @Nullable
    @RequiredReadAction
    private static Condition<PsiElement> findConstructorUsages(
        PsiMethod constructor, Collection<PsiReference> originalReferences, List<UsageInfo> usages,
        final PsiElement[] allElementsToDelete
    ) {
        HashMap<PsiMethod, Collection<PsiReference>> constructorsToRefs = new HashMap<>();
        HashSet<PsiMethod> newConstructors = new HashSet<>();
        if (isTheOnlyEmptyDefaultConstructor(constructor)) {
            return null;
        }

        newConstructors.add(constructor);
        constructorsToRefs.put(constructor, originalReferences);
        HashSet<PsiMethod> passConstructors = new HashSet<>();
        do {
            passConstructors.clear();
            for (PsiMethod method : newConstructors) {
                final Collection<PsiReference> references = constructorsToRefs.get(method);
                for (PsiReference reference : references) {
                    PsiMethod overridingConstructor = getOverridingConstructorOfSuperCall(reference.getElement());
                    if (overridingConstructor != null && !constructorsToRefs.containsKey(overridingConstructor)) {
                        Collection<PsiReference> overridingConstructorReferences = ReferencesSearch.search(overridingConstructor).findAll();
                        constructorsToRefs.put(overridingConstructor, overridingConstructorReferences);
                        passConstructors.add(overridingConstructor);
                    }
                }
            }
            newConstructors.clear();
            newConstructors.addAll(passConstructors);
        }
        while (!newConstructors.isEmpty());

        final Set<PsiMethod> validOverriding = validateOverridingMethods(
            constructor,
            originalReferences,
            constructorsToRefs.keySet(),
            constructorsToRefs,
            usages,
            allElementsToDelete
        );

        return usage -> !(usage instanceof PsiFile) && (isInside(usage, allElementsToDelete) || isInside(usage, validOverriding));
    }

    private static boolean isTheOnlyEmptyDefaultConstructor(final PsiMethod constructor) {
        if (constructor.getParameterList().getParameters().length > 0) {
            return false;
        }
        final PsiCodeBlock body = constructor.getBody();
        return !(body != null && body.getStatements().length > 0) && constructor.getContainingClass().getConstructors().length == 1;
    }

    @RequiredReadAction
    private static Set<PsiMethod> validateOverridingMethods(
        PsiMethod originalMethod,
        final Collection<PsiReference> originalReferences,
        Collection<PsiMethod> overridingMethods,
        HashMap<PsiMethod, Collection<PsiReference>> methodToReferences,
        List<UsageInfo> usages,
        final PsiElement[] allElementsToDelete
    ) {
        Set<PsiMethod> validOverriding = new LinkedHashSet<>(overridingMethods);
        Set<PsiMethod> multipleInterfaceImplementations = new HashSet<>();
        boolean anyNewBadRefs;
        do {
            anyNewBadRefs = false;
            for (PsiMethod overridingMethod : overridingMethods) {
                if (validOverriding.contains(overridingMethod)) {
                    final Collection<PsiReference> overridingReferences = methodToReferences.get(overridingMethod);
                    boolean anyOverridingRefs = false;
                    for (final PsiReference overridingReference : overridingReferences) {
                        final PsiElement element = overridingReference.getElement();
                        if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
                            anyOverridingRefs = true;
                            break;
                        }
                    }
                    if (!anyOverridingRefs && isMultipleInterfacesImplementation(overridingMethod, originalMethod, allElementsToDelete)) {
                        anyOverridingRefs = true;
                        multipleInterfaceImplementations.add(overridingMethod);
                    }

                    if (anyOverridingRefs) {
                        validOverriding.remove(overridingMethod);
                        anyNewBadRefs = true;

                        for (PsiReference reference : originalReferences) {
                            final PsiElement element = reference.getElement();
                            if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods)) {
                                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, originalMethod, false));
                                validOverriding.clear();
                            }
                        }
                    }
                }
            }
        }
        while (anyNewBadRefs && !validOverriding.isEmpty());

        for (PsiMethod method : validOverriding) {
            if (method != originalMethod) {

                usages.add(new SafeDeleteOverridingMethodUsageInfo(method, originalMethod));
            }
        }

        for (PsiMethod method : overridingMethods) {
            if (!validOverriding.contains(method) &&
                !multipleInterfaceImplementations.contains(method) &&
                canBePrivate(method, methodToReferences.get(method), validOverriding, allElementsToDelete)) {
                usages.add(new SafeDeletePrivatizeMethod(method, originalMethod));
            }
            else {
                usages.add(new SafeDeleteOverrideAnnotation(method, originalMethod));
            }
        }
        return validOverriding;
    }

    private static boolean isMultipleInterfacesImplementation(
        final PsiMethod method,
        PsiMethod originalMethod,
        final PsiElement[] allElementsToDelete
    ) {
        final PsiMethod[] methods = method.findSuperMethods();
        for (PsiMethod superMethod : methods) {
            if (ArrayUtil.find(allElementsToDelete, superMethod) < 0 && !MethodSignatureUtil.isSuperMethod(originalMethod, superMethod)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    private static PsiMethod getOverridingConstructorOfSuperCall(final PsiElement element) {
        if (element instanceof PsiReferenceExpression refExpr && "super".equals(element.getText())
            && refExpr.getParent() instanceof PsiMethodCallExpression methodCall
            && methodCall.getParent() instanceof PsiExpressionStatement expr
            && expr.getParent() instanceof PsiCodeBlock codeBlock
            && codeBlock.getParent() instanceof PsiMethod method
            && method.isConstructor()) {
            return method;
        }
        return null;
    }

    @RequiredReadAction
    private static boolean canBePrivate(
        PsiMethod method,
        Collection<PsiReference> references,
        Collection<? extends PsiElement> deleted,
        final PsiElement[] allElementsToDelete
    ) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        PsiManager manager = method.getManager();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
        final PsiElementFactory factory = facade.getElementFactory();
        final PsiModifierList privateModifierList;
        try {
            final PsiMethod newMethod = factory.createMethod("x3", PsiType.VOID);
            privateModifierList = newMethod.getModifierList();
            privateModifierList.setModifierProperty(PsiModifier.PRIVATE, true);
        }
        catch (IncorrectOperationException e) {
            LOG.assertTrue(false);
            return false;
        }
        for (PsiReference reference : references) {
            final PsiElement element = reference.getElement();
            if (!isInside(element, allElementsToDelete) && !isInside(element, deleted)
                && !facade.getResolveHelper().isAccessible(method, privateModifierList, element, null, null)) {
                return false;
            }
        }
        return true;
    }

    private static Condition<PsiElement> findFieldUsages(
        final PsiField psiField,
        final List<UsageInfo> usages,
        final PsiElement[] allElementsToDelete
    ) {
        final Condition<PsiElement> isInsideDeleted = getUsageInsideDeletedFilter(allElementsToDelete);
        ReferencesSearch.search(psiField).forEach(reference -> {
            if (!isInsideDeleted.value(reference.getElement())) {
                final PsiElement element = reference.getElement();
                final PsiElement parent = element.getParent();
                if (parent instanceof PsiAssignmentExpression assignment && element == assignment.getLExpression()) {
                    usages.add(new SafeDeleteFieldWriteReference(assignment, psiField));
                }
                else {
                    TextRange range = reference.getRangeInElement();
                    usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(
                        reference.getElement(),
                        psiField,
                        range.getStartOffset(),
                        range.getEndOffset(),
                        false,
                        PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) != null
                    ));
                }
            }

            return true;
        });

        return isInsideDeleted;
    }

    private static void findParameterUsages(final PsiParameter parameter, final List<UsageInfo> usages) {
        final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
        //search for refs to current method only, do not search for refs to overriding methods, they'll be searched separately
        ReferencesSearch.search(method).forEach(reference -> {
            PsiElement element = reference.getElement();
            if (element != null) {
                JavaSafeDeleteDelegate.forLanguage(element.getLanguage()).createUsageInfoForParameter(reference, usages, parameter, method);
            }
            return true;
        });

        ReferencesSearch.search(parameter).forEach(reference -> {
            PsiElement element = reference.getElement();
            final PsiDocTag docTag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
            if (docTag != null) {
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(docTag, parameter, true));
                return true;
            }

            boolean isSafeDelete = false;
            if (element.getParent().getParent() instanceof PsiMethodCallExpression call) {
                PsiReferenceExpression methodExpression = call.getMethodExpression();
                if (methodExpression.getText().equals(PsiKeyword.SUPER)) {
                    isSafeDelete = true;
                }
                else if (methodExpression.getQualifierExpression() instanceof PsiSuperExpression) {
                    final PsiMethod superMethod = call.resolveMethod();
                    if (superMethod != null && MethodSignatureUtil.isSuperMethod(superMethod, method)) {
                        isSafeDelete = true;
                    }
                }
            }

            usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, isSafeDelete));
            return true;
        });
    }

    private static boolean isInside(PsiElement place, PsiElement[] ancestors) {
        return isInside(place, Arrays.asList(ancestors));
    }

    private static boolean isInside(PsiElement place, Collection<? extends PsiElement> ancestors) {
        for (PsiElement element : ancestors) {
            if (isInside(place, element)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInside(PsiElement place, PsiElement ancestor) {
        if (SafeDeleteProcessor.isInside(place, ancestor)) {
            return true;
        }
        if (PsiTreeUtil.getParentOfType(place, PsiComment.class, false) != null && ancestor instanceof PsiClass aClass) {
            if (aClass.getParent() instanceof PsiJavaFile file) {
                if (PsiTreeUtil.isAncestor(file, place, false)) {
                    if (file.getClasses().length == 1) { // file will be deleted on class deletion
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
