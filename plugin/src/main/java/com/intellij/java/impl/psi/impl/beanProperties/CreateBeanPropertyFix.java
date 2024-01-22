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
import consulo.java.analysis.impl.JavaQuickFixBundle;
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
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateBeanPropertyFix implements LocalQuickFix, SyntheticIntentionAction
{

  private final static Logger LOG = Logger.getInstance(CreateBeanPropertyFix.class);
  private static final CreateBeanPropertyFix[] NO_FIXES = new CreateBeanPropertyFix[0];

  protected final String myPropertyName;
  @jakarta.annotation.Nonnull
  protected final PsiClass myPsiClass;
  @Nonnull
  protected final PsiType myType;

  public static LocalQuickFix[] createFixes(String propertyName, @Nonnull PsiClass psiClass, @Nullable PsiType type, final boolean createSetter) {
    return (LocalQuickFix[])create(propertyName, psiClass, type, createSetter);
  }

  public static IntentionAction[] createActions(String propertyName, @Nonnull PsiClass psiClass, @Nullable PsiType type, final boolean createSetter) {
    return (IntentionAction[])create(propertyName, psiClass, type, createSetter);
  }

  private static Object[] create(final String propertyName, final PsiClass psiClass, PsiType type, final boolean createSetter) {
    if (psiClass instanceof PsiCompiledElement) return NO_FIXES;
    if (type == null) {
      final Project project = psiClass.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass aClass = facade.findClass("java.lang.String", GlobalSearchScope.allScope(project));
      if (aClass == null) {
        return NO_FIXES;
      }
      type = facade.getElementFactory().createType(aClass);
    }
    if (psiClass.isInterface()) {
      return new CreateBeanPropertyFix[] { new CreateAccessorFix(propertyName, psiClass, type, createSetter) };
    }
    return new CreateBeanPropertyFix[] {
        new CreateBeanPropertyFix(propertyName, psiClass, type) {

          @Override
          @jakarta.annotation.Nonnull
          public String getName() {
            return JavaQuickFixBundle.message("create.readable.writable.property.with.field", myPropertyName);
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
          public String getName() {
            return JavaQuickFixBundle.message(createSetter ? "create.writable.property.with.field" : "create.readable.property.with.field", myPropertyName);
          }
        }
    };
  }

  protected CreateBeanPropertyFix(String propertyName, @Nonnull PsiClass psiClass, @jakarta.annotation.Nonnull PsiType type) {
    myPropertyName = propertyName;
    myPsiClass = psiClass;
    myType = type;
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@jakarta.annotation.Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    applyFix(project);
  }

  private void applyFix(final Project project) {
    new WriteCommandAction.Simple(project, getName(), myPsiClass.getContainingFile()) {
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
  @jakarta.annotation.Nonnull
  public String getText() {
    return getName();
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    applyFix(project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  protected abstract void doFix() throws IncorrectOperationException;

  private String getFieldName() {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myPsiClass.getProject());
    return styleManager.suggestVariableName(VariableKind.FIELD, myPropertyName, null, myType).names[0];
  }

  protected PsiElement createSetter(final boolean createField) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final String methodName = PropertyUtil.suggestSetterName(myPropertyName);
    final String typeName = myType.getCanonicalText();

    @NonNls final String text;
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
    final PsiMethod method = elementFactory.createMethodFromText(text, null);
    final PsiMethod psiElement = (PsiMethod)myPsiClass.add(method);
    if (!isInterface && !createField) {
      CreateFromUsageUtils.setupMethodBody(psiElement, myPsiClass);
    }
    return psiElement;
  }

  protected PsiElement createGetter(final boolean createField) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final String methodName = PropertyUtil.suggestGetterName(myPropertyName, myType);
    final String typeName = myType.getCanonicalText();
    @NonNls final String text;
    boolean isInterface = myPsiClass.isInterface();
    if (createField) {
      final String fieldName = getFieldName();
      text = "public " + typeName + " " + methodName + "() { return " + fieldName + "; }";
    } else {
      if (isInterface) {
        text = typeName + " " + methodName + "();";
      }
      else {
        text = "public " + typeName + " " + methodName + "() { return null; }";
      }
    }
    final PsiMethod method = elementFactory.createMethodFromText(text, null);
    final PsiMethod psiElement = (PsiMethod)myPsiClass.add(method);
    if (!createField && !isInterface) {
      CreateFromUsageUtils.setupMethodBody(psiElement);
    }
    return psiElement;
  }

  protected PsiElement createField() throws IncorrectOperationException {
    final String fieldName = getFieldName();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final PsiField psiField = elementFactory.createField(fieldName, myType);
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
    public String getName() {
      return JavaQuickFixBundle.message(myCreateSetter ? "create.writable.property" : "create.readable.property", myPropertyName);
    }
  }
}
