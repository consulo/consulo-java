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
import consulo.document.util.TextRange;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.impl.inspection.InspectionEngine;
import consulo.language.editor.impl.inspection.reference.RefManagerImpl;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    List<PsiLiteralExpression> result = new ArrayList<PsiLiteralExpression>();
    if (mySuppressedExpression != null) {
      result.add(mySuppressedExpression);
      return result;
    }
    PsiAnnotationParameterList list = myTarget.getParameterList();
    PsiNameValuePair[] attributes = list.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      PsiAnnotationMemberValue value = attribute.getValue();
      if (value instanceof PsiArrayInitializerMemberValue) {
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) value).getInitializers();
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

        @Override
        public String getTextFor(PsiLiteralExpression value) {
          Object o = value.getValue();
          LOG.assertTrue(o instanceof String);
          return (String) o;
        }
      });

      EditorPopupHelper.getInstance().showPopupInBestPositionFor(myEditor, popup);
    }
  }

  @Override
  public void computeUsages(List<PsiLiteralExpression> targets) {
    Project project = myTarget.getProject();
    PsiElement parent = myTarget.getParent().getParent();
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    for (PsiLiteralExpression target : targets) {
      Object value = target.getValue();
      if (!(value instanceof String)) {
        continue;
      }
      InspectionToolWrapper toolWrapperById = inspectionProfile.getToolById((String) value, target);
      if (!(toolWrapperById instanceof LocalInspectionToolWrapper localToolWrapper)) {
        continue;
      }
      List<LocalInspectionToolWrapper> toolsCopy = Collections.singletonList(localToolWrapper.createCopy());
      GlobalInspectionContext context = InspectionManager.getInstance(project).createNewGlobalContext(false);
      for (InspectionToolWrapper toolWrapper : toolsCopy) {
        toolWrapper.initialize(context);
      }
      ((RefManagerImpl) context.getRefManager()).runInsideInspectionReadAction(() -> {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) {
          indicator = DaemonCodeAnalyzer.getInstance(project).createDaemonProgressIndicator();
        }
        Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
            InspectionEngine.inspectEx(toolsCopy, myFile, parent.getTextRange(), TextRange.EMPTY_RANGE, false, true, false,
                indicator, (toolWrapper, descriptor) -> true);

        for (List<ProblemDescriptor> descriptors : map.values()) {
          for (ProblemDescriptor descriptor : descriptors) {
            PsiElement element = descriptor.getPsiElement();
            if (element != null) {
              addOccurrence(element);
            }
          }
        }
      });
    }
  }
}
