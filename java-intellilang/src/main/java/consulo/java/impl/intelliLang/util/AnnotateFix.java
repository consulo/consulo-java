/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.util;

import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AnnotateFix implements LocalQuickFix {
  private final PsiModifierListOwner myElement;
  private final String myAnnotationName;
  private final String myArgList;

  public AnnotateFix(@Nonnull PsiModifierListOwner owner, String annotationClassname) {
    this(owner, annotationClassname, null);
  }

  public AnnotateFix(@Nonnull PsiModifierListOwner owner, String annotationClassname, @Nullable String argList) {
    myElement = owner;
    myAnnotationName = annotationClassname;
    myArgList = argList;
  }

  @Nonnull
  public String getName() {
    return "Annotate with @" + StringUtil.getShortName(myAnnotationName);
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public boolean canApply() {
    return PsiUtilEx.isInSourceContent(myElement) && myElement.getModifierList() != null;
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myElement)) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    try {
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(myAnnotationName, myElement.getResolveScope());
      final InitializerRequirement requirement = InitializerRequirement.calcInitializerRequirement(psiClass);

      final String argList;
      if (myArgList == null) {
        switch (requirement) {
          case VALUE_REQUIRED:
          case OTHER_REQUIRED:
            argList = "(\"\")";
            break;
          default:
            argList = "";
        }
      }
      else {
        argList = myArgList;
      }

      PsiAnnotation annotation = factory.createAnnotationFromText("@" + myAnnotationName + argList, myElement);
      final PsiModifierList modifierList = myElement.getModifierList();

      if (modifierList != null) {
        annotation = (PsiAnnotation)modifierList.addBefore(annotation, modifierList.getFirstChild());
        annotation = (PsiAnnotation)JavaCodeStyleManager.getInstance(project).shortenClassReferences(annotation);

        final PsiAnnotationParameterList list = annotation.getParameterList();
        if (requirement != InitializerRequirement.NONE_REQUIRED && myArgList == null) {
          ((NavigationItem)list).navigate(true);
        }
      }
    }
    catch (IncorrectOperationException e) {
      Logger.getInstance(getClass().getName()).error(e);
    }
  }
}
