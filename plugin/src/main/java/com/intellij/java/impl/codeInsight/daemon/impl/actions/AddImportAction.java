// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.daemon.impl.actions;

import com.intellij.java.impl.application.options.editor.JavaAutoImportConfigurable;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.psi.statistics.JavaStatisticsManager;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.Document;
import consulo.ide.impl.idea.codeInsight.actions.OptimizeImportsProcessor;
import consulo.ide.impl.idea.ui.popup.list.ListPopupImpl;
import consulo.ide.impl.idea.ui.popup.list.PopupListElementRenderer;
import consulo.ide.impl.psi.statistics.StatisticsManager;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.hint.QuestionAction;
import consulo.language.editor.ui.DefaultPsiElementCellRenderer;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import consulo.util.lang.ObjectUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AddImportAction implements QuestionAction {
  private static final Logger LOG = Logger.getInstance(AddImportAction.class);

  private final Project myProject;
  private final PsiReference myReference;
  private final PsiClass[] myTargetClasses;
  private final Editor myEditor;

  public AddImportAction(@Nonnull Project project,
                         @Nonnull PsiReference ref,
                         @Nonnull Editor editor,
                         @Nonnull PsiClass... targetClasses) {
    myProject = project;
    myReference = ref;
    myTargetClasses = targetClasses;
    myEditor = editor;
  }

  @Override
  public boolean execute() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (!myReference.getElement().isValid()) {
      return false;
    }

    for (PsiClass myTargetClass : myTargetClasses) {
      if (!myTargetClass.isValid()) {
        return false;
      }
    }

    if (myTargetClasses.length == 1) {
      addImport(myReference, myTargetClasses[0]);
    } else {
      chooseClassAndImport();
    }
    return true;
  }

  private void chooseClassAndImport() {
    CodeInsightUtil.sortIdenticalShortNamedMembers(myTargetClasses, myReference);

    final BaseListPopupStep<PsiClass> step =
        new BaseListPopupStep<PsiClass>(JavaQuickFixBundle.message("class.to.import.chooser.title"), myTargetClasses) {
          @Override
          public boolean isAutoSelectionEnabled() {
            return false;
          }

          @Override
          public boolean isSpeedSearchEnabled() {
            return true;
          }

          @Override
          public PopupStep onChosen(PsiClass selectedValue, boolean finalChoice) {
            if (selectedValue == null) {
              return FINAL_CHOICE;
            }

            if (finalChoice) {
              return doFinalStep(() -> {
                PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                addImport(myReference, selectedValue);
              });
            }

            return getExcludesStep(selectedValue.getQualifiedName(), myProject);
          }

          @Override
          public boolean hasSubstep(PsiClass selectedValue) {
            return true;
          }

          @Nonnull
          @Override
          public String getTextFor(PsiClass value) {
            return ObjectUtil.assertNotNull(value.getQualifiedName());
          }

          @Override
          @RequiredUIAccess
          public Image getIconFor(PsiClass aValue) {
            return IconDescriptorUpdaters.getIcon(aValue, 0);
          }
        };
    ListPopupImpl popup = new ListPopupImpl(step) {
      @Override
      protected ListCellRenderer getListElementRenderer() {
        final PopupListElementRenderer baseRenderer = (PopupListElementRenderer) super.getListElementRenderer();
        final DefaultPsiElementCellRenderer psiRenderer = new DefaultPsiElementCellRenderer();
        return new ListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout());
            baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            panel.add(baseRenderer.getNextStepLabel(), BorderLayout.EAST);
            panel.add(psiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus));
            return panel;
          }
        };
      }
    };
    PopupNavigationUtil.hidePopupIfDumbModeStarts(popup, myProject);
    popup.showInBestPositionFor(myEditor);
  }

  @Nullable
  public static PopupStep getExcludesStep(String qname, final Project project) {
    if (qname == null) {
      return PopupStep.FINAL_CHOICE;
    }

    List<String> toExclude = getAllExcludableStrings(qname);

    return new BaseListPopupStep<String>(null, toExclude) {
      @Nonnull
      @Override
      public String getTextFor(String value) {
        return "Exclude '" + value + "' from auto-import";
      }

      @Override
      public PopupStep onChosen(String selectedValue, boolean finalChoice) {
        if (finalChoice) {
          excludeFromImport(project, selectedValue);
        }

        return super.onChosen(selectedValue, finalChoice);
      }
    };
  }

  public static void excludeFromImport(final Project project, final String prefix) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) {
        return;
      }

      final JavaAutoImportConfigurable configurable = new JavaAutoImportConfigurable(project);
      ShowSettingsUtil.getInstance().editConfigurable("Auto Import", project, configurable, () -> configurable.addExcludePackage(prefix));
    });
  }

  public static List<String> getAllExcludableStrings(@Nonnull String qname) {
    List<String> toExclude = new ArrayList<>();
    while (true) {
      toExclude.add(qname);
      final int i = qname.lastIndexOf('.');
      if (i < 0 || i == qname.indexOf('.')) {
        break;
      }
      qname = qname.substring(0, i);
    }
    return toExclude;
  }

  private void addImport(final PsiReference ref, final PsiClass targetClass) {
    DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
      if (!ref.getElement().isValid() || !targetClass.isValid() || ref.resolve() == targetClass) {
        return;
      }

      StatisticsManager.getInstance().incUseCount(JavaStatisticsManager.createInfo(null, targetClass));
      WriteCommandAction.runWriteCommandAction(myProject, JavaQuickFixBundle.message("add.import"), null,
          () -> _addImport(ref, targetClass),
          ref.getElement().getContainingFile());
    });
  }

  private void _addImport(PsiReference ref, PsiClass targetClass) {
    try {
      bindReference(ref, targetClass);
      if (CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) {
        Document document = myEditor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        new OptimizeImportsProcessor(myProject, psiFile).runWithoutProgress();
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  protected void bindReference(PsiReference ref, PsiClass targetClass) {
    ref.bindToElement(targetClass);
  }
}
