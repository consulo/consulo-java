package com.intellij.java.impl.refactoring.introduceParameter;

import com.intellij.java.impl.codeInsight.intention.impl.TypeExpression;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.inject.EditorWindow;
import consulo.language.editor.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * User: anna
 */
public abstract class AbstractJavaInplaceIntroducer extends AbstractInplaceIntroducer<PsiVariable, PsiExpression> {
  protected TypeSelectorManagerImpl myTypeSelectorManager;

  public AbstractJavaInplaceIntroducer(final Project project,
                                       Editor editor,
                                       PsiExpression expr,
                                       PsiVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager, String title) {
    super(project, EditorWindow.getTopLevelEditor(editor), expr, localVariable, occurrences, title, JavaFileType.INSTANCE);
    myTypeSelectorManager = typeSelectorManager;
  }

  protected abstract PsiVariable createFieldToStartTemplateOn(String[] names, PsiType psiType);
  protected abstract String[] suggestNames(PsiType defaultType, String propName);
  protected abstract VariableKind getVariableKind();



  @Override
  protected String[] suggestNames(boolean replaceAll, PsiVariable variable) {
    myTypeSelectorManager.setAllOccurrences(replaceAll);
    final PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
    final String propertyName = variable != null
                                ? JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(variable.getName(), VariableKind.LOCAL_VARIABLE)
                                : null;
    final String[] names = suggestNames(defaultType, propertyName);
    if (propertyName != null && names.length > 1) {
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
      final String paramName = javaCodeStyleManager.propertyNameToVariableName(propertyName, getVariableKind());
      final int idx = ArrayUtil.find(names, paramName);
      if (idx > -1) {
        ArrayUtil.swap(names, 0, idx);
      }
    }
    return names;
  }

  @Override
  protected PsiVariable createFieldToStartTemplateOn(boolean replaceAll, String[] names) {
    myTypeSelectorManager.setAllOccurrences(replaceAll);
    return createFieldToStartTemplateOn(names, getType());
  }

  @Override
  protected void correctExpression() {
    final PsiElement parent = getExpr().getParent();
    if (parent instanceof PsiExpressionStatement && parent.getLastChild() instanceof PsiErrorElement) {
      myExpr = ((PsiExpressionStatement)ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
        @Override
        public PsiElement compute() {
          return parent.replace(JavaPsiFacade.getElementFactory(myProject).createStatementFromText(parent.getText() + ";", parent));
        }
      })).getExpression();
      myEditor.getCaretModel().moveToOffset(myExpr.getTextRange().getStartOffset());
    }
  }

  @Override
  public PsiExpression restoreExpression(PsiFile containingFile, PsiVariable psiVariable, RangeMarker marker, String exprText) {
    return restoreExpression(containingFile, psiVariable, JavaPsiFacade.getElementFactory(myProject), marker, exprText);
  }

  @Override
  protected void restoreState(PsiVariable psiField) {
    final SmartTypePointer typePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(getType());
    super.restoreState(psiField);
    for (PsiExpression occurrence : myOccurrences) {
      if (!occurrence.isValid()) return;
    }
    try {
      myTypeSelectorManager = myExpr != null
                              ? new TypeSelectorManagerImpl(myProject, typePointer.getType(), myExpr, myOccurrences)
                              : new TypeSelectorManagerImpl(myProject, typePointer.getType(), myOccurrences);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  protected void saveSettings(@Nonnull PsiVariable psiVariable) {
    TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), getType());//myDefaultType.getType());
    myTypeSelectorManager = null;
  }

  public PsiType getType() {
    return myTypeSelectorManager.getDefaultType();
  }

  public static String[] appendUnresolvedExprName(String[] names, final PsiExpression expr) {
    if (expr instanceof PsiReferenceExpression && ((PsiReferenceExpression)expr).resolve() == null) {
      final String name = expr.getText();
      if (PsiNameHelper.getInstance(expr.getProject()).isIdentifier(name, LanguageLevel.HIGHEST)) {
        names = ArrayUtil.mergeArrays(new String[]{name}, names);
      }
    }
    return names;
  }

  @Nullable
  public static PsiExpression restoreExpression(PsiFile containingFile,
                                                PsiVariable psiVariable,
                                                PsiElementFactory elementFactory,
                                                RangeMarker marker, String exprText) {
    if (exprText == null) return null;
    if (psiVariable == null || !psiVariable.isValid()) return null;
    final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
    final PsiElement refVariableElementParent = refVariableElement != null ? refVariableElement.getParent() : null;
    PsiExpression expression = refVariableElement instanceof PsiKeyword && refVariableElementParent instanceof PsiNewExpression
                               ? (PsiNewExpression)refVariableElementParent 
                               : PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
    if (expression instanceof PsiReferenceExpression && !(expression.getParent() instanceof PsiMethodCallExpression)) {
      final String referenceName = ((PsiReferenceExpression)expression).getReferenceName();
      if (((PsiReferenceExpression)expression).resolve() == psiVariable ||
          Comparing.strEqual(psiVariable.getName(), referenceName) ||
          Comparing.strEqual(exprText, referenceName)) {
        return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(exprText, psiVariable));
      }
    }
    if (expression == null) {
      expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiExpression.class);
    }
    while (expression instanceof PsiReferenceExpression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        if (parent.getText().equals(exprText)) return (PsiExpression)parent;
      }
      if (parent instanceof PsiExpression) {
        expression = (PsiExpression)parent;
      } else {
        return null;
      }
    }
    if (expression != null && expression.isValid() && expression.getText().equals(exprText)) {
      return expression;
    }

    if (refVariableElementParent instanceof PsiExpression && refVariableElementParent.getText().equals(exprText)) {
      return (PsiExpression)refVariableElementParent;
    }

    return null;
  }

   public static Expression createExpression(final TypeExpression expression, final String defaultType) {
     return new Expression() {
       @Override
       public Result calculateResult(ExpressionContext context) {
         return new TextResult(defaultType);
       }

       @Override
       public Result calculateQuickResult(ExpressionContext context) {
         return new TextResult(defaultType);
       }

       @Override
       public LookupElement[] calculateLookupItems(ExpressionContext context) {
         return expression.calculateLookupItems(context);
       }

       @Override
       public String getAdvertisingText() {
         return null;
       }
     };
   }

}
