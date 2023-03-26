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

/*
 * User: anna
 * Date: 29-Dec-2008
 */
package com.intellij.java.impl.codeInsight.highlighting;

import com.intellij.java.language.psi.*;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.ide.impl.idea.codeInsight.daemon.impl.LocalInspectionsPass;
import consulo.ide.impl.idea.codeInspection.ex.GlobalInspectionContextImpl;
import consulo.ide.impl.idea.codeInspection.ex.InspectionManagerEx;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.impl.highlight.HighlightInfoProcessor;
import consulo.language.editor.impl.inspection.reference.RefManagerImpl;
import consulo.language.editor.impl.inspection.scheme.LocalInspectionToolWrapper;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.util.CollectHighlightsUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class HighlightSuppressedWarningsHandler extends HighlightUsagesHandlerBase<PsiLiteralExpression> {
  private static final Logger LOG = Logger.getInstance(HighlightSuppressedWarningsHandler.class);

  private final PsiAnnotation myTarget;
  private final PsiLiteralExpression mySuppressedExpression;

  protected HighlightSuppressedWarningsHandler(Editor editor, PsiFile file, PsiAnnotation target, PsiLiteralExpression suppressedExpression) {
    super(editor, file);
    myTarget = target;
    mySuppressedExpression = suppressedExpression;
  }

  @Override
  public List<PsiLiteralExpression> getTargets() {
    final List<PsiLiteralExpression> result = new ArrayList<PsiLiteralExpression>();
    if (mySuppressedExpression != null) {
      result.add(mySuppressedExpression);
      return result;
    }
    final PsiAnnotationParameterList list = myTarget.getParameterList();
    final PsiNameValuePair[] attributes = list.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      final PsiAnnotationMemberValue value = attribute.getValue();
      if (value instanceof PsiArrayInitializerMemberValue) {
        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) value).getInitializers();
        for (PsiAnnotationMemberValue initializer : initializers) {
          if (initializer instanceof PsiLiteralExpression) {
            result.add((PsiLiteralExpression) initializer);
          }
        }
      }
    }
    return result;
  }

  @Override
  protected void selectTargets(List<PsiLiteralExpression> targets, final Consumer<List<PsiLiteralExpression>> selectionConsumer) {
    if (targets.size() == 1) {
      selectionConsumer.accept(targets);
    } else {
      ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PsiLiteralExpression>("Choose Inspections to Highlight Suppressed Problems from", targets) {
        @Override
        public PopupStep onChosen(PsiLiteralExpression selectedValue, boolean finalChoice) {
          selectionConsumer.accept(Collections.singletonList(selectedValue));
          return FINAL_CHOICE;
        }

        @Nonnull
        @Override
        public String getTextFor(PsiLiteralExpression value) {
          final Object o = value.getValue();
          LOG.assertTrue(o instanceof String);
          return (String) o;
        }
      });

      EditorPopupHelper.getInstance().showPopupInBestPositionFor(myEditor, popup);
    }
  }

  @Override
  public void computeUsages(List<PsiLiteralExpression> targets) {
    final Project project = myTarget.getProject();
    final PsiElement parent = myTarget.getParent().getParent();
    final LocalInspectionsPass pass = new LocalInspectionsPass(myFile, myFile.getViewProvider().getDocument(),
        parent.getTextRange().getStartOffset(), parent.getTextRange().getEndOffset(), LocalInspectionsPass.EMPTY_PRIORITY_RANGE,
        false, HighlightInfoProcessor.getEmpty());
    final InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    for (PsiLiteralExpression target : targets) {
      final Object value = target.getValue();
      if (!(value instanceof String)) {
        continue;
      }
      InspectionToolWrapper toolWrapperById = inspectionProfile.getToolById((String) value, target);
      if (!(toolWrapperById instanceof LocalInspectionToolWrapper)) {
        continue;
      }
      final LocalInspectionToolWrapper toolWrapper = ((LocalInspectionToolWrapper) toolWrapperById).createCopy();
      final InspectionManagerEx managerEx = (InspectionManagerEx) InspectionManager.getInstance(project);
      final GlobalInspectionContextImpl context = managerEx.createNewGlobalContext(false);
      toolWrapper.initialize(context);
      ((RefManagerImpl) context.getRefManager()).inspectionReadActionStarted();
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      Runnable inspect = new Runnable() {
        @Override
        public void run() {
          pass.doInspectInBatch(context, managerEx, Collections.<LocalInspectionToolWrapper>singletonList(toolWrapper));
        }
      };
      if (indicator == null) {
        ProgressManager.getInstance().executeProcessUnderProgress(inspect, DaemonCodeAnalyzer.getInstance(project).createDaemonProgressIndicator());
      } else {
        inspect.run();
      }

      for (HighlightInfo info : pass.getInfos()) {
        final PsiElement element = CollectHighlightsUtil.findCommonParent(myFile, info.getStartOffset(), info.getEndOffset());
        if (element != null) {
          addOccurrence(element);
        }
      }
    }
  }
}
