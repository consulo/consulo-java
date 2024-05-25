package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 25.05.2024
 */
public class ChangeAnnotationParameterQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement implements SyntheticIntentionAction {
  private final String myName;
  private final String myNewValue;
  private final String myMessage;

  public ChangeAnnotationParameterQuickFix(@Nonnull PsiAnnotation annotation,
                                           @Nonnull String name,
                                           @Nullable String newValue,
                                           @Nonnull String message) {
    super(annotation);
    myName = name;
    myNewValue = newValue;
    myMessage = message;
  }

  public ChangeAnnotationParameterQuickFix(@Nonnull PsiAnnotation annotation, @Nonnull String name, @Nullable String newValue) {
    this(annotation, name, newValue,
         newValue == null
           ? InspectionGadgetsBundle.message("remove.annotation.parameter.0.fix.name", name)
           : InspectionGadgetsBundle.message("set.annotation.parameter.0.1.fix.name", name, newValue));
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return myMessage;
  }

  @Override
  public void invoke(@Nonnull Project project,
                     @Nonnull PsiFile psiFile,
                     @Nullable Editor editor,
                     @Nonnull PsiElement psiElement,
                     @Nonnull PsiElement psiElement1) {
    if (!(psiElement instanceof PsiAnnotation annotation)) {
      return;
    }

    final PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, myName);
    if (myNewValue != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation dummyAnnotation = elementFactory.createAnnotationFromText("@A" + "(" + myName + "=" + myNewValue + ")", null);
      annotation.setDeclaredAttributeValue(myName, dummyAnnotation.getParameterList().getAttributes()[0].getValue());
    }
    else if (attribute != null) {
      new CommentTracker().deleteAndRestoreComments(attribute);
    }
  }

  @Nonnull
  @Override
  public String getText() {
    return myMessage;
  }
}
