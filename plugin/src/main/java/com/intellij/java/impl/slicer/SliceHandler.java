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
package com.intellij.java.impl.slicer;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiVariable;
import consulo.codeEditor.Editor;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.ui.awt.scope.BaseAnalysisActionDialog;
import consulo.language.editor.ui.scope.AnalysisUIOptions;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author cdr
 */
public class SliceHandler implements CodeInsightActionHandler {
  private final boolean myDataFlowToThis;

  public SliceHandler(boolean dataFlowToThis) {
    myDataFlowToThis = dataFlowToThis;
  }

  @Override
  public void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation
    PsiElement expression = getExpressionAtCaret(editor, file);
    if (expression == null) {
      HintManager.getInstance()
                 .showErrorHint(editor,
                                "Cannot find what to analyze. Please stand on the expression or variable or method parameter and try again.");
      return;
    }

    SliceManager sliceManager = SliceManager.getInstance(project);
    sliceManager.slice(expression, myDataFlowToThis, this);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public PsiElement getExpressionAtCaret(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    if (offset == 0) {
      return null;
    }
    PsiElement atCaret = file.findElementAt(offset);

    PsiElement element = PsiTreeUtil.getParentOfType(atCaret, PsiExpression.class, PsiVariable.class);
    if (myDataFlowToThis && element instanceof PsiLiteralExpression) return null;
    return element;
  }

  public SliceAnalysisParams askForParams(PsiElement element,
                                          boolean dataFlowToThis,
                                          SliceManager.StoredSettingsBean storedSettingsBean,
                                          String dialogTitle) {
    AnalysisScope analysisScope = new AnalysisScope(element.getContainingFile());
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    String name = module == null ? null : module.getName();

    Project myProject = element.getProject();
    AnalysisUIOptions analysisUIOptions = new AnalysisUIOptions();
    analysisUIOptions.save(storedSettingsBean.analysisUIOptions);

    BaseAnalysisActionDialog
      dialog = new BaseAnalysisActionDialog(dialogTitle, "Analyze scope", myProject, analysisScope, name, true, analysisUIOptions,
                                            element);
    dialog.show();
    if (!dialog.isOK()) return null;

    AnalysisScope scope = dialog.getScope(analysisUIOptions, analysisScope, myProject, module);
    storedSettingsBean.analysisUIOptions.save(analysisUIOptions);

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = scope;
    params.dataFlowToThis = dataFlowToThis;
    return params;
  }
}
