/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.unneededThrows;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.impl.codeInspection.DeleteThrowsFix;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 * @since 15-Nov-2005
 */
@ExtensionImpl
public class RedundantThrowsDeclaration extends BaseJavaBatchLocalInspectionTool
{
  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("redundant.throws.declaration");
  }

  @Override
  @jakarta.annotation.Nonnull
  @NonNls
  public String getShortName() {
    return "RedundantThrowsDeclaration";
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@jakarta.annotation.Nonnull PsiFile file, @jakarta.annotation.Nonnull final InspectionManager manager, final boolean isOnTheFly, Object state) {
    final Set<ProblemDescriptor> problems = new HashSet<ProblemDescriptor>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        final ProblemDescriptor descriptor = checkExceptionsNeverThrown(reference, manager, isOnTheFly);
        if (descriptor != null) {
          problems.add(descriptor);
        }
      }

    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static ProblemDescriptor checkExceptionsNeverThrown(PsiJavaCodeReferenceElement referenceElement,
                                                              InspectionManager inspectionManager,
                                                              boolean onTheFly) {
    if (!(referenceElement.getParent() instanceof PsiReferenceList)) return null;
    PsiReferenceList referenceList = (PsiReferenceList)referenceElement.getParent();
    if (!(referenceList.getParent() instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)referenceList.getParent();
    if (referenceList != method.getThrowsList()) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    PsiManager manager = referenceElement.getManager();
    PsiClassType exceptionType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(referenceElement);
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(exceptionType)) return null;

    PsiCodeBlock body = method.getBody();
    if (body == null) return null;

    PsiModifierList modifierList = method.getModifierList();
    if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)
        && !modifierList.hasModifierProperty(PsiModifier.STATIC)
        && !modifierList.hasModifierProperty(PsiModifier.FINAL)
        && !method.isConstructor()
        && !(containingClass instanceof PsiAnonymousClass)
        && !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }

    Collection<PsiClassType> types = ExceptionUtil.collectUnhandledExceptions(body, method);
    Collection<PsiClassType> unhandled = new HashSet<PsiClassType>(types);
    if (method.isConstructor()) {
      // there may be field initializer throwing exception
      // that exception must be caught in the constructor
      PsiField[] fields = containingClass.getFields();
      for (final PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) continue;
        unhandled.addAll(ExceptionUtil.collectUnhandledExceptions(initializer, field));
      }
    }

    for (PsiClassType unhandledException : unhandled) {
      if (unhandledException.isAssignableFrom(exceptionType) || exceptionType.isAssignableFrom(unhandledException)) {
        return null;
      }
    }

    if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return null;

    String description = JavaErrorBundle.message("exception.is.never.thrown", JavaHighlightUtil.formatType(exceptionType));
    LocalQuickFix quickFixes = new DeleteThrowsFix(method, exceptionType);
    return inspectionManager.createProblemDescriptor(referenceElement, description, quickFixes, ProblemHighlightType.LIKE_UNUSED_SYMBOL, onTheFly);
  }
}
