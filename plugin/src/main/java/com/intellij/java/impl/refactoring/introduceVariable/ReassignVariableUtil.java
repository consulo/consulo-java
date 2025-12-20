/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.introduceVariable;

import com.intellij.java.language.impl.psi.scope.processor.VariablesProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.codeEditor.VisualPosition;
import consulo.document.RangeMarker;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.rename.inplace.InplaceRefactoring;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.TemplateState;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.popup.AWTPopupFactory;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 11/8/10
 */
public class ReassignVariableUtil {
  static final Key<SmartPsiElementPointer<PsiDeclarationStatement>> DECLARATION_KEY = Key.create("var.type");
  static final Key<RangeMarker[]> OCCURRENCES_KEY = Key.create("occurrences");

  private ReassignVariableUtil() {
  }

  static boolean reassign(final Editor editor) {
    SmartPsiElementPointer<PsiDeclarationStatement> pointer = editor.getUserData(DECLARATION_KEY);
    final PsiDeclarationStatement declaration = pointer != null ? pointer.getElement() : null;
    PsiType type = getVariableType(declaration);
    if (type != null) {
      VariablesProcessor proc = findVariablesOfType(declaration, type);
      if (proc.size() > 0) {

        if (proc.size() == 1) {
          replaceWithAssignment(declaration, proc.getResult(0), editor);
          return true;
        }

        DefaultListModel model = new DefaultListModel();
        for (int i = 0; i < proc.size(); i++) {
          model.addElement(proc.getResult(i));
        }
        final JList list = new JBList(model);
        list.setCellRenderer(new ListCellRendererWrapper() {
          @Override
          public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            if (value instanceof PsiVariable) {
              setText(((PsiVariable) value).getName());
              setIcon(TargetAWT.to(IconDescriptorUpdaters.getIcon(((PsiVariable) value), 0)));
            }
          }
        });


        VisualPosition visualPosition = editor.getCaretModel().getVisualPosition();
        Point point = editor.visualPositionToXY(new VisualPosition(visualPosition.line + 1, visualPosition.column));
        ((AWTPopupFactory) JBPopupFactory.getInstance()).createListPopupBuilder(list)
            .setItemChoosenCallback(new Runnable() {
              public void run() {
                replaceWithAssignment(declaration, (PsiVariable) list.getSelectedValue(), editor);
              }
            })
            .setTitle("Choose variable to reassign")
            .setRequestFocus(true)
            .createPopup().show(new RelativePoint(editor.getContentComponent(), point));
      }

      return true;
    }
    return false;
  }

  @Nullable
  static PsiType getVariableType(@Nullable PsiDeclarationStatement declaration) {
    if (declaration != null) {
      PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length > 0 && declaredElements[0] instanceof PsiVariable) {
        return ((PsiVariable) declaredElements[0]).getType();
      }
    }
    return null;
  }

  static VariablesProcessor findVariablesOfType(final PsiDeclarationStatement declaration, final PsiType type) {
    VariablesProcessor proc = new VariablesProcessor(false) {
      @Override
      protected boolean check(PsiVariable var, ResolveState state) {
        for (PsiElement element : declaration.getDeclaredElements()) {
          if (element == var) return false;
        }
        return TypeConversionUtil.isAssignable(var.getType(), type);
      }
    };
    PsiElement scope = declaration;
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return proc;
    PsiScopesUtil.treeWalkUp(proc, declaration, scope);
    return proc;
  }

  static void replaceWithAssignment(final PsiDeclarationStatement declaration, final PsiVariable variable, final Editor editor) {
    PsiVariable var = (PsiVariable) declaration.getDeclaredElements()[0];
    final PsiExpression initializer = var.getInitializer();
    new WriteCommandAction(declaration.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(variable.getProject());
        String chosenVariableName = variable.getName();
        //would generate red code for final variables
        PsiElement newDeclaration = elementFactory.createStatementFromText(chosenVariableName + " = " + initializer.getText() + ";",
            declaration);
        newDeclaration = declaration.replace(newDeclaration);
        PsiFile containingFile = newDeclaration.getContainingFile();
        RangeMarker[] occurrenceMarkers = editor.getUserData(OCCURRENCES_KEY);
        if (occurrenceMarkers != null) {
          for (RangeMarker marker : occurrenceMarkers) {
            PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
            PsiExpression expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
            if (expression != null) {
              expression.replace(elementFactory.createExpressionFromText(chosenVariableName, newDeclaration));
            }
          }
        }
      }
    }.execute();
    finishTemplate(var.getProject(), editor);
  }

  private static void finishTemplate(Project project, Editor editor) {
    TemplateState templateState = TemplateManager.getInstance(project).getTemplateState(editor);
    InplaceRefactoring renamer = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER);
    if (templateState != null && renamer != null) {
      templateState.gotoEnd(true);
      editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, null);
    }
  }
}
