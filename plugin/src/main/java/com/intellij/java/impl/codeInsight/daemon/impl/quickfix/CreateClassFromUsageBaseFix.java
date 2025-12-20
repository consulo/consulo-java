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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
public abstract class CreateClassFromUsageBaseFix extends BaseIntentionAction implements SyntheticIntentionAction {
  protected static final Logger LOG = Logger.getInstance(
    CreateClassFromUsageBaseFix.class);
  protected CreateClassKind myKind;
  private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRefElement;

  public CreateClassFromUsageBaseFix(CreateClassKind kind, PsiJavaCodeReferenceElement refElement) {
    myKind = kind;
    myRefElement = SmartPointerManager.getInstance(refElement.getProject()).createSmartPsiElementPointer(refElement);
  }

  protected abstract LocalizeValue getText(String varName);

  private boolean isAvailableInContext(@Nonnull PsiJavaCodeReferenceElement element) {
    PsiElement parent = element.getParent();

    if (myKind == CreateClassKind.ANNOTATION) {
      return parent instanceof PsiAnnotation;
    }

    if (parent instanceof PsiJavaCodeReferenceCodeFragment) return true;

    if (parent instanceof PsiTypeElement) {
      if (parent.getParent() instanceof PsiReferenceParameterList) return true;

      while (parent.getParent() instanceof PsiTypeElement) {
        parent = parent.getParent();
        if (parent.getParent() instanceof PsiReferenceParameterList) return true;
      }
      if (parent.getParent() instanceof PsiCodeFragment ||
        parent.getParent() instanceof PsiVariable ||
        parent.getParent() instanceof PsiMethod ||
        parent.getParent() instanceof PsiClassObjectAccessExpression ||
        parent.getParent() instanceof PsiTypeCastExpression ||
        (parent.getParent() instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent.getParent()).getCheckType() == parent)) {
        return true;
      }
    }
    else if (parent instanceof PsiReferenceList) {
      if (myKind == CreateClassKind.ENUM) return false;
      if (parent.getParent() instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)parent.getParent();
        if (psiClass.getExtendsList() == parent) {
          if (myKind == CreateClassKind.CLASS && !psiClass.isInterface()) return true;
          if (myKind == CreateClassKind.INTERFACE && psiClass.isInterface()) return true;
        }
        if (psiClass.getImplementsList() == parent && myKind == CreateClassKind.INTERFACE) return true;
      }
      else if (parent.getParent() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)parent.getParent();
        if (method.getThrowsList() == parent && myKind == CreateClassKind.CLASS) return true;
      }
    }
    else if (parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getBaseClassReference() == element) {
      return true;
    }

    if (element instanceof PsiReferenceExpression) {
      if (parent instanceof PsiMethodCallExpression) {
        return false;
      }
      return !(parent.getParent() instanceof PsiMethodCallExpression) || myKind == CreateClassKind.CLASS;
    }
    return false;
  }

  private static boolean checkClassName(String name) {
    return Character.isUpperCase(name.charAt(0));
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null ||
      !element.getManager().isInProject(element) ||
      CreateFromUsageUtils.isValidReference(element, true)) return false;
    String refName = element.getReferenceName();
    if (refName == null || !checkClassName(refName)) return false;
    PsiElement nameElement = element.getReferenceNameElement();
    if (nameElement == null) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression)) return false;
    if (!isAvailableInContext(element)) return false;
    String superClassName = getSuperClassName(element);
    if (superClassName != null) {
      if (superClassName.equals(CommonClassNames.JAVA_LANG_ENUM) && myKind != CreateClassKind.ENUM) return false;
      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(superClassName, GlobalSearchScope.allScope(project));
      if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.FINAL)) return false;
    }
    int offset = editor.getCaretModel().getOffset();
    if (CreateFromUsageUtils.shouldShowTag(offset, nameElement, element)) {
      setText(getText(nameElement.getText()));
      return true;
    }

    return false;
  }

  @Nullable
  protected PsiJavaCodeReferenceElement getRefElement() {
    return myRefElement.getElement();
  }

  @Nullable
  protected String getSuperClassName(PsiJavaCodeReferenceElement element) {
    String superClassName = null;
    PsiElement parent = element.getParent();
    PsiElement ggParent = parent.getParent();
    if (ggParent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)ggParent;
      if (method.getThrowsList() == parent) {
        superClassName = CommonClassNames.JAVA_LANG_EXCEPTION;
      }
    }
    else if (ggParent instanceof PsiClassObjectAccessExpression) {
      ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiExpression)ggParent, false);
      if (expectedTypes.length == 1) {
        PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(expectedTypes[0].getType());
        PsiClass psiClass = classResolveResult.getElement();
        if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
          PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          PsiType psiType = typeParameters.length == 1 ? classResolveResult.getSubstitutor().substitute(typeParameters[0]) : null;
          if (psiType instanceof PsiWildcardType && ((PsiWildcardType)psiType).isExtends()) {
            psiType = ((PsiWildcardType)psiType).getExtendsBound();
          }
          PsiClass aClass = PsiUtil.resolveClassInType(psiType);
          if (aClass != null) return aClass.getQualifiedName();
        }
      }
    }
    else if (ggParent instanceof PsiExpressionList && parent instanceof PsiExpression && myKind == CreateClassKind.ENUM) {
      ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiExpression)parent, false);
      if (expectedTypes.length == 1) {
        PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(expectedTypes[0].getType());
        PsiClass psiClass = classResolveResult.getElement();
        if (psiClass != null && psiClass.isInterface()) {
          return psiClass.getQualifiedName();
        }
      }
      return null;
    }

    return superClassName;
  }
}
