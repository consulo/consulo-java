/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.intention.impl.TypeExpression;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.language.impl.psi.scope.processor.VariablesProcessor;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.application.ui.NonFocusableSetting;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import consulo.language.editor.refactoring.introduce.inplace.InplaceVariableIntroducer;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.ex.action.Shortcut;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.keymap.Keymap;
import consulo.ui.ex.keymap.KeymapManager;
import consulo.ui.ex.keymap.util.KeymapUtil;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 12/8/10
 */
public class JavaVariableInplaceIntroducer extends InplaceVariableIntroducer<PsiExpression> {
  protected final Project myProject;
  private final SmartPsiElementPointer<PsiDeclarationStatement> myPointer;

  private CheckBox myCanBeFinalCb;

  private final boolean myCantChangeFinalModifier;
  private final String myTitle;
  private String myExpressionText;
  protected final SmartTypePointer myDefaultType;
  protected final TypeExpression myExpression;

  private ResolveSnapshotProvider.ResolveSnapshot myConflictResolver;

  public JavaVariableInplaceIntroducer(Project project,
                                       TypeExpression expression,
                                       Editor editor,
                                       @Nonnull PsiVariable elementToRename,
                                       boolean cantChangeFinalModifier,
                                       boolean hasTypeSuggestion,
                                       RangeMarker exprMarker,
                                       List<RangeMarker> occurrenceMarkers,
                                       String title) {
    super(elementToRename, editor, project, title, new PsiExpression[0], null);
    myProject = project;
    myCantChangeFinalModifier = cantChangeFinalModifier;
    myTitle = title;
    setExprMarker(exprMarker);
    setOccurrenceMarkers(occurrenceMarkers);
    PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementToRename, PsiDeclarationStatement.class);
    myPointer = declarationStatement != null ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declarationStatement) : null;
    editor.putUserData(ReassignVariableUtil.DECLARATION_KEY, myPointer);
    if (occurrenceMarkers != null) {
      ArrayList<RangeMarker> rangeMarkers = new ArrayList<RangeMarker>(occurrenceMarkers);
      rangeMarkers.add(exprMarker);
      editor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY,
          rangeMarkers.toArray(new RangeMarker[rangeMarkers.size()]));
    }
    myExpression = expression;
    PsiType defaultType = elementToRename.getType();
    myDefaultType = SmartTypePointerManager.getInstance(project).createSmartTypePointer(defaultType);
    setAdvertisementText(getAdvertisementText(declarationStatement, defaultType, hasTypeSuggestion));
  }

  public void initInitialText(String text) {
    myExpressionText = text;
  }

  @Override
  protected void beforeTemplateStart() {
    super.beforeTemplateStart();
    ResolveSnapshotProvider resolveSnapshotProvider = ResolveSnapshotProvider.forLanguage(myScope.getLanguage());
    myConflictResolver = resolveSnapshotProvider != null ? resolveSnapshotProvider.createSnapshot(myScope) : null;
  }

  @Override
  @Nullable
  protected PsiVariable getVariable() {
    PsiDeclarationStatement declarationStatement = myPointer.getElement();
    if (declarationStatement != null) {
      PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      return declaredElements.length == 0 ? null : (PsiVariable) declaredElements[0];
    }
    return null;
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    try {
      if (success) {
        Document document = myEditor.getDocument();
        @Nullable final PsiVariable psiVariable = getVariable();
        if (psiVariable == null) {
          return;
        }
        LOG.assertTrue(psiVariable.isValid());
        TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultType.getType());
        if (myCanBeFinalCb != null) {
          JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = psiVariable.hasModifierProperty(PsiModifier.FINAL);
        }
        adjustLine(psiVariable, document);

        int startOffset = getExprMarker() != null && getExprMarker().isValid() ? getExprMarker().getStartOffset() : psiVariable.getTextOffset();
        final PsiFile file = psiVariable.getContainingFile();
        PsiReference referenceAt = file.findReferenceAt(startOffset);
        if (referenceAt != null && referenceAt.resolve() instanceof PsiVariable) {
          startOffset = referenceAt.getElement().getTextRange().getEndOffset();
        } else {
          PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(psiVariable, PsiDeclarationStatement.class);
          if (declarationStatement != null) {
            startOffset = declarationStatement.getTextRange().getEndOffset();
          }
        }
        myEditor.getCaretModel().moveToOffset(startOffset);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (psiVariable.getInitializer() != null) {
              appendTypeCasts(getOccurrenceMarkers(), file, myProject, psiVariable);
            }
            if (myConflictResolver != null && myInsertedName != null && isIdentifier(myInsertedName, psiVariable.getLanguage())) {
              myConflictResolver.apply(psiVariable.getName());
            }
          }
        });
      } else {
        RangeMarker exprMarker = getExprMarker();
        if (exprMarker != null && exprMarker.isValid()) {
          myEditor.getCaretModel().moveToOffset(exprMarker.getStartOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
        if (myExpressionText != null) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            PsiDeclarationStatement element = myPointer.getElement();
            if (element != null) {
              PsiElement[] vars = element.getDeclaredElements();
              if (vars.length > 0 && vars[0] instanceof PsiVariable) {
                PsiFile containingFile = element.getContainingFile();
                //todo pull up method restore state
                PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
                RangeMarker exprMarker1 = getExprMarker();
                if (exprMarker1 != null) {
                  myExpr = AbstractJavaInplaceIntroducer.restoreExpression(containingFile, (PsiVariable) vars[0], elementFactory, exprMarker1, myExpressionText);
                  if (myExpr != null && myExpr.isPhysical()) {
                    myExprMarker = createMarker(myExpr);
                  }
                }
                List<RangeMarker> markers = getOccurrenceMarkers();
                for (RangeMarker occurrenceMarker : markers) {
                  if (getExprMarker() != null && occurrenceMarker.getStartOffset() == getExprMarker().getStartOffset() && myExpr != null) {
                    continue;
                  }
                  AbstractJavaInplaceIntroducer
                      .restoreExpression(containingFile, (PsiVariable) vars[0], elementFactory, occurrenceMarker, myExpressionText);
                }
                PsiExpression initializer = ((PsiVariable) vars[0]).getInitializer();
                if (initializer != null && Comparing.strEqual(initializer.getText(), myExpressionText) && myExpr == null) {
                  element.replace(JavaPsiFacade.getInstance(myProject).getElementFactory().createStatementFromText(myExpressionText, element));
                } else {
                  element.delete();
                }
              }
            }
          });
        }
      }
    } finally {
      myEditor.putUserData(ReassignVariableUtil.DECLARATION_KEY, null);
      for (RangeMarker occurrenceMarker : getOccurrenceMarkers()) {
        occurrenceMarker.dispose();
      }
      myEditor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY, null);
      if (getExprMarker() != null) getExprMarker().dispose();
    }
  }


  @Override
  @Nullable
  protected JComponent getComponent() {
    if (!myCantChangeFinalModifier) {
      myCanBeFinalCb = CheckBox.create(CodeInsightLocalize.dialogCreateFieldFromParameterDeclareFinalCheckbox());
      NonFocusableSetting.initFocusability(myCanBeFinalCb);
      myCanBeFinalCb.setValue(createFinals());
      final FinalListener finalListener = new FinalListener(myEditor);
      myCanBeFinalCb.addValueListener(e -> new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
        @Override
        protected void run(Result result) throws Throwable {
          PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
          PsiVariable variable = getVariable();
          if (variable != null) {
            finalListener.perform(myCanBeFinalCb.getValueOrError(), variable);
          }
        }
      }.execute());
    } else {
      return null;
    }
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(null);

    if (myCanBeFinalCb != null) {
      panel.add(TargetAWT.to(myCanBeFinalCb), new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insets(5), 0, 0));
    }

    panel.add(Box.createVerticalBox(), new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));

    return panel;
  }

  @Override
  protected void addAdditionalVariables(TemplateBuilder builder) {
    PsiTypeElement typeElement = getVariable().getTypeElement();
    builder.replaceElement(typeElement, "Variable_Type", AbstractJavaInplaceIntroducer.createExpression(myExpression, typeElement.getText()), true, true);
  }

  private static void appendTypeCasts(List<RangeMarker> occurrenceMarkers,
                                      PsiFile file,
                                      Project project,
                                      @Nullable PsiVariable psiVariable) {
    if (occurrenceMarkers != null) {
      for (RangeMarker occurrenceMarker : occurrenceMarkers) {
        PsiElement refVariableElement = file.findElementAt(occurrenceMarker.getStartOffset());
        PsiReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
        if (referenceExpression != null) {
          PsiElement parent = referenceExpression.getParent();
          if (parent instanceof PsiVariable) {
            createCastInVariableDeclaration(project, (PsiVariable) parent);
          } else if (parent instanceof PsiReferenceExpression && psiVariable != null) {
            PsiExpression initializer = psiVariable.getInitializer();
            LOG.assertTrue(initializer != null);
            PsiType type = initializer.getType();
            if (((PsiReferenceExpression) parent).resolve() == null && type != null) {
              PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
              PsiExpression castedExpr =
                  elementFactory.createExpressionFromText("((" + type.getCanonicalText() + ")" + referenceExpression.getText() + ")", parent);
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(referenceExpression.replace(castedExpr));
            }
          }
        }
      }
    }
    if (psiVariable != null && psiVariable.isValid()) {
      createCastInVariableDeclaration(project, psiVariable);
    }
  }

  private static void createCastInVariableDeclaration(Project project, PsiVariable psiVariable) {
    PsiExpression initializer = psiVariable.getInitializer();
    LOG.assertTrue(initializer != null);
    PsiType type = psiVariable.getType();
    PsiType initializerType = initializer.getType();
    if (initializerType != null && !TypeConversionUtil.isAssignable(type, initializerType)) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      PsiExpression castExpr =
          elementFactory.createExpressionFromText("(" + psiVariable.getType().getCanonicalText() + ")" + initializer.getText(), psiVariable);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(initializer.replace(castExpr));
    }
  }

  @Nullable
  private static String getAdvertisementText(PsiDeclarationStatement declaration,
                                             PsiType type,
                                             boolean hasTypeSuggestion) {
    VariablesProcessor processor = ReassignVariableUtil.findVariablesOfType(declaration, type);
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    if (processor.size() > 0) {
      Shortcut[] shortcuts = keymap.getShortcuts("IntroduceVariable");
      if (shortcuts.length > 0) {
        return "Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to reassign existing variable";
      }
    }
    if (hasTypeSuggestion) {
      Shortcut[] shortcuts = keymap.getShortcuts("PreviousTemplateVariable");
      if (shortcuts.length > 0) {
        return "Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to change type";
      }
    }
    return null;
  }


  protected boolean createFinals() {
    return IntroduceVariableBase.createFinals(myProject);
  }

  public static void adjustLine(PsiVariable psiVariable, Document document) {
    int modifierListOffset = psiVariable.getTextRange().getStartOffset();
    int varLineNumber = document.getLineNumber(modifierListOffset);

    //adjust line indent if final was inserted and then deleted
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiDocumentManager.getInstance(psiVariable.getProject()).doPostponedOperationsAndUnblockDocument(document);
      CodeStyleManager.getInstance(psiVariable.getProject()).adjustLineIndent(document, document.getLineStartOffset(varLineNumber));
    });
  }

  protected String getTitle() {
    return myTitle;
  }
}
