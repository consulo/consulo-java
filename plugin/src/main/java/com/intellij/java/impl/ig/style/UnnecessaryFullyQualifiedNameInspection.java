/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.impl.ig.psiutils.HighlightUtils;
import com.intellij.java.impl.ig.psiutils.ImportUtils;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.WindowManager;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * com.siyeh.ipp.fqnames.ReplaceFullyQualifiedNameWithImportIntention
 */
@ExtensionImpl
public class UnnecessaryFullyQualifiedNameInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreJavadoc = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryFullyQualifiedNameDisplayName().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.ignore.option"),
                                          this, "m_ignoreJavadoc");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final boolean inSameFile = (Boolean)infos[0];
    return inSameFile
      ? InspectionGadgetsLocalize.unnecessaryFullyQualifiedNameProblemDescriptor2().get()
      : InspectionGadgetsLocalize.unnecessaryFullyQualifiedNameProblemDescriptor1().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryFullyQualifiedNameFix(((Boolean)infos[0]).booleanValue());
  }

  private static class UnnecessaryFullyQualifiedNameFix extends InspectionGadgetsFix {

    private final boolean inSameFile;

    public UnnecessaryFullyQualifiedNameFix(boolean inSameFile) {
      this.inSameFile = inSameFile;
    }

    @Override
    @Nonnull
    public String getName() {
      return inSameFile
        ? InspectionGadgetsLocalize.unnecessaryFullyQualifiedNameRemoveQuickfix().get()
        : InspectionGadgetsLocalize.unnecessaryFullyQualifiedNameReplaceQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
      final PsiFile file = referenceElement.getContainingFile();
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      ImportUtils.addImportIfNeeded(aClass, referenceElement);
      final String fullyQualifiedText = referenceElement.getText();
      final QualificationRemover qualificationRemover = new QualificationRemover(fullyQualifiedText);
      file.accept(qualificationRemover);
      if (isOnTheFly()) {
        final Collection<PsiElement> shortenedElements = qualificationRemover.getShortenedElements();
        HighlightUtils.highlightElements(shortenedElements);
      }
    }

    private static class QualificationRemover extends JavaRecursiveElementVisitor {

      private final String fullyQualifiedText;
      private final List<PsiElement> shortenedElements = new ArrayList();

      QualificationRemover(String fullyQualifiedText) {
        this.fullyQualifiedText = fullyQualifiedText;
      }

      public Collection<PsiElement> getShortenedElements() {
        return Collections.unmodifiableCollection(shortenedElements);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement parent = PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class);
        if (parent != null) {
          return;
        }
        final String text = reference.getText();
        if (!text.equals(fullyQualifiedText)) {
          return;
        }
        final PsiElement qualifier = reference.getQualifier();
        if (qualifier == null) {
          return;
        }
        try {
          qualifier.delete();
        }
        catch (IncorrectOperationException e) {
          final Class<? extends QualificationRemover> aClass = getClass();
          final String className = aClass.getName();
          final Logger logger = Logger.getInstance(className);
          logger.error(e);
        }
        shortenedElements.add(reference);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryFullyQualifiedNameVisitor();
  }

  private class UnnecessaryFullyQualifiedNameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      checkReference(expression);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      checkReference(reference);
    }

    private void checkReference(PsiJavaCodeReferenceElement reference) {
      final PsiElement qualifier = reference.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
        return;
      }
      final PsiElement parent = reference.getParent();
      if (parent instanceof PsiMethodCallExpression || parent instanceof PsiAssignmentExpression || parent instanceof PsiVariable) {
        return;
      }
      final PsiElement element =
        PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class, PsiPackageStatement.class, JavaCodeFragment.class);
      if (element != null) {
        return;
      }
      if (m_ignoreJavadoc) {
        final PsiElement containingComment = PsiTreeUtil.getParentOfType(reference, PsiDocComment.class);
        if (containingComment != null) {
          return;
        }
      }
      final PsiFile containingFile = reference.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile)) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement)qualifier;
      final PsiElement qualifierTarget = qualifierReference.resolve();
      if (!(qualifierTarget instanceof PsiJavaPackage)) {
        return;
      }
      final List<PsiJavaCodeReferenceElement> references = new ArrayList(2);
      references.add(reference);
      final JavaCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(reference.getProject()).getCustomSettings(JavaCodeStyleSettings.class);
      if (styleSettings.INSERT_INNER_CLASS_IMPORTS) {
        collectInnerClassNames(reference, references);
      }
      Collections.reverse(references);
      for (int i = 0, size = references.size(); i < size; i++) {
        final PsiJavaCodeReferenceElement aReference = references.get(i);
        final PsiElement referenceTarget = aReference.resolve();
        if (!(referenceTarget instanceof PsiClass)) {
          continue;
        }
        final PsiClass aClass = (PsiClass)referenceTarget;
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
          continue;
        }
        if (!ImportUtils.nameCanBeImported(qualifiedName, reference)) {
          continue;
        }
        final boolean inSameFile = aClass.getContainingFile() == containingFile;
        registerError(aReference, Boolean.valueOf(inSameFile));
        break;
      }
    }

    private void collectInnerClassNames(PsiJavaCodeReferenceElement reference, List<PsiJavaCodeReferenceElement> references) {
      PsiElement rParent = reference.getParent();
      while (rParent instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement parentReference = (PsiJavaCodeReferenceElement)rParent;
        if (!reference.equals(parentReference.getQualifier())) {
          break;
        }
        references.add(parentReference);
        rParent = rParent.getParent();
      }
    }
  }
}