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
package com.intellij.java.impl.psi.impl.beanProperties;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateBeanPropertyFix implements LocalQuickFix, SyntheticIntentionAction {
    private final static Logger LOG = Logger.getInstance(CreateBeanPropertyFix.class);
    private static final CreateBeanPropertyFix[] NO_FIXES = new CreateBeanPropertyFix[0];

    protected final String myPropertyName;
    @Nonnull
    protected final PsiClass myPsiClass;
    @Nonnull
    protected final PsiType myType;

    public static LocalQuickFix[] createFixes(String propertyName, @Nonnull PsiClass psiClass, @Nullable PsiType type, boolean createSetter) {
        return (LocalQuickFix[]) create(propertyName, psiClass, type, createSetter);
    }

    public static IntentionAction[] createActions(String propertyName, @Nonnull PsiClass psiClass, @Nullable PsiType type, boolean createSetter) {
        return (IntentionAction[]) create(propertyName, psiClass, type, createSetter);
    }

    private static Object[] create(final String propertyName, final PsiClass psiClass, PsiType type, final boolean createSetter) {
        if (psiClass instanceof PsiCompiledElement) {
            return NO_FIXES;
        }
        if (type == null) {
            Project project = psiClass.getProject();
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiClass aClass = facade.findClass(CommonClassNames.JAVA_LANG_STRING, GlobalSearchScope.allScope(project));
            if (aClass == null) {
                return NO_FIXES;
            }
            type = facade.getElementFactory().createType(aClass);
        }
        if (psiClass.isInterface()) {
            return new CreateBeanPropertyFix[]{new CreateAccessorFix(propertyName, psiClass, type, createSetter)};
        }
        return new CreateBeanPropertyFix[]{
            new CreateBeanPropertyFix(propertyName, psiClass, type) {

                @Override
                @Nonnull
                public LocalizeValue getName() {
                    return JavaQuickFixLocalize.createReadableWritablePropertyWithField(myPropertyName);
                }

                @Override
                protected void doFix() throws IncorrectOperationException {
                    createField();
                    createSetter(true);
                    createGetter(true);
                }
            },
            new CreateAccessorFix(propertyName, psiClass, type, createSetter),
            new CreateBeanPropertyFix(propertyName, psiClass, type) {
                @Override
                protected void doFix() throws IncorrectOperationException {
                    createField();
                    if (createSetter) {
                        createSetter(true);
                    }
                    else {
                        createGetter(true);
                    }
                }

                @Override
                @Nonnull
                public LocalizeValue getName() {
                    if (createSetter) {
                        return JavaQuickFixLocalize.createWritablePropertyWithField(myPropertyName);
                    }
                    else {
                        return JavaQuickFixLocalize.createReadablePropertyWithField(myPropertyName);
                    }
                }
            }
        };
    }

    protected CreateBeanPropertyFix(String propertyName, @Nonnull PsiClass psiClass, @Nonnull PsiType type) {
        myPropertyName = propertyName;
        myPsiClass = psiClass;
        myType = type;
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        applyFix(project);
    }

    private void applyFix(final Project project) {
        new WriteCommandAction.Simple(project, getName().get(), myPsiClass.getContainingFile()) {
            @Override
            protected void run() throws Throwable {
                try {
                    doFix();
                }
                catch (IncorrectOperationException e) {
                    LOG.error("Cannot create property", e);
                }
            }
        }.execute();
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        return getName();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        applyFix(project);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    protected abstract void doFix() throws IncorrectOperationException;

    private String getFieldName() {
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myPsiClass.getProject());
        return styleManager.suggestVariableName(VariableKind.FIELD, myPropertyName, null, myType).names[0];
    }

    protected PsiElement createSetter(boolean createField) throws IncorrectOperationException {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
        String methodName = PropertyUtil.suggestSetterName(myPropertyName);
        String typeName = myType.getCanonicalText();

        @NonNls String text;
        boolean isInterface = myPsiClass.isInterface();
        if (isInterface) {
            text = "public void " + methodName + "(" + typeName + " " + myPropertyName + ");";
        }
        else if (createField) {
            @NonNls String fieldName = getFieldName();
            if (fieldName.equals(myPropertyName)) {
                fieldName = "this." + fieldName;
            }
            text = "public void " + methodName + "(" + typeName + " " + myPropertyName + ") {" + fieldName + "=" + myPropertyName + ";}";
        }
        else {
            text = "public void " + methodName + "(" + typeName + " " + myPropertyName + ") {}";
        }
        PsiMethod method = elementFactory.createMethodFromText(text, null);
        PsiMethod psiElement = (PsiMethod) myPsiClass.add(method);
        if (!isInterface && !createField) {
            CreateFromUsageUtils.setupMethodBody(psiElement, myPsiClass);
        }
        return psiElement;
    }

    protected PsiElement createGetter(boolean createField) throws IncorrectOperationException {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
        String methodName = PropertyUtil.suggestGetterName(myPropertyName, myType);
        String typeName = myType.getCanonicalText();
        @NonNls String text;
        boolean isInterface = myPsiClass.isInterface();
        if (createField) {
            String fieldName = getFieldName();
            text = "public " + typeName + " " + methodName + "() { return " + fieldName + "; }";
        }
        else {
            if (isInterface) {
                text = typeName + " " + methodName + "();";
            }
            else {
                text = "public " + typeName + " " + methodName + "() { return null; }";
            }
        }
        PsiMethod method = elementFactory.createMethodFromText(text, null);
        PsiMethod psiElement = (PsiMethod) myPsiClass.add(method);
        if (!createField && !isInterface) {
            CreateFromUsageUtils.setupMethodBody(psiElement);
        }
        return psiElement;
    }

    protected PsiElement createField() throws IncorrectOperationException {
        String fieldName = getFieldName();
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
        PsiField psiField = elementFactory.createField(fieldName, myType);
        return myPsiClass.add(psiField);
    }

    private static class CreateAccessorFix extends CreateBeanPropertyFix {
        private final boolean myCreateSetter;

        public CreateAccessorFix(String propertyName, PsiClass psiClass, PsiType type, boolean createSetter) {
            super(propertyName, psiClass, type);
            myCreateSetter = createSetter;
        }

        @Override
        protected void doFix() throws IncorrectOperationException {
            if (myCreateSetter) {
                createSetter(false);
            }
            else {
                createGetter(false);
            }
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            if (myCreateSetter) {
                return JavaQuickFixLocalize.createWritableProperty(myPropertyName);
            }
            else {
                return JavaQuickFixLocalize.createReadableProperty(myPropertyName);
            }
        }
    }
}
