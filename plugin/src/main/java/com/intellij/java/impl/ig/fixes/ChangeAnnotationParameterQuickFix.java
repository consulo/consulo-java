package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 25.05.2024
 */
public class ChangeAnnotationParameterQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement implements SyntheticIntentionAction {
  private final String myName;
  private final String myNewValue;
  private final LocalizeValue myMessage;

  public ChangeAnnotationParameterQuickFix(PsiAnnotation annotation,
                                           String name,
                                           @Nullable String newValue,
                                           LocalizeValue message) {
    super(annotation);
    myName = name;
    myNewValue = newValue;
    myMessage = message;
  }

  public ChangeAnnotationParameterQuickFix(PsiAnnotation annotation, String name, @Nullable String newValue) {
    this(annotation, name, newValue,
         newValue == null
           ? InspectionGadgetsLocalize.removeAnnotationParameter0FixName(name)
           : InspectionGadgetsLocalize.setAnnotationParameter01FixName(name, newValue));
  }

  @Override
  public void invoke(Project project,
                     PsiFile psiFile,
                     @Nullable Editor editor,
                     PsiElement psiElement,
                     PsiElement psiElement1) {
    if (!(psiElement instanceof PsiAnnotation annotation)) {
      return;
    }

    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, myName);
    if (myNewValue != null) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      PsiAnnotation dummyAnnotation = elementFactory.createAnnotationFromText("@A" + "(" + myName + "=" + myNewValue + ")", null);
      annotation.setDeclaredAttributeValue(myName, dummyAnnotation.getParameterList().getAttributes()[0].getValue());
    }
    else if (attribute != null) {
      new CommentTracker().deleteAndRestoreComments(attribute);
    }
  }

  @Override
  public LocalizeValue getText() {
    return myMessage;
  }
}
