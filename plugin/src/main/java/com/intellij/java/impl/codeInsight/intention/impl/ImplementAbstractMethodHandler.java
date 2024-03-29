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
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 4, 2002
 * Time: 6:26:27 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import consulo.codeEditor.EditorPopupHelper;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.FileModificationService;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.popup.JBPopup;
import consulo.undoRedo.CommandProcessor;
import consulo.logging.Logger;
import consulo.codeEditor.Editor;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.application.util.function.Computable;
import consulo.language.psi.*;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import consulo.language.psi.PsiUtilCore;
import consulo.ui.ex.awt.JBList;
import consulo.language.util.IncorrectOperationException;

import javax.swing.*;
import java.util.*;

public class ImplementAbstractMethodHandler {
  private static final Logger LOG = Logger.getInstance(ImplementAbstractMethodHandler.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiMethod myMethod;
  private JList myList;

  public ImplementAbstractMethodHandler(Project project, Editor editor, PsiMethod method) {
    myProject = project;
    myEditor = editor;
    myMethod = method;
  }

  public void invoke() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final PsiElement[][] result = new PsiElement[1][];
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            final PsiClass psiClass = myMethod.getContainingClass();
            if (!psiClass.isValid()) return;
            if (!psiClass.isEnum()) {
              result[0] = getClassImplementations(psiClass);
            }
            else {
              final List<PsiElement> enumConstants = new ArrayList<PsiElement>();
              for (PsiField field : psiClass.getFields()) {
                if (field instanceof PsiEnumConstant) {
                  final PsiEnumConstantInitializer initializingClass = ((PsiEnumConstant)field).getInitializingClass();
                  if (initializingClass != null) {
                    PsiMethod method = initializingClass.findMethodBySignature(myMethod, true);
                    if (method == null || !method.getContainingClass().equals(initializingClass)) {
                      enumConstants.add(initializingClass);
                    }
                  }
                  else {
                    enumConstants.add(field);
                  }
                }
              }
              result[0] = PsiUtilCore.toPsiElementArray(enumConstants);
            }
          }
        });
      }
    }, CodeInsightBundle.message("intention.implement.abstract.method.searching.for.descendants.progress"), true, myProject);

    if (result[0] == null) return;

    if (result[0].length == 0) {
      Messages.showMessageDialog(myProject,
                                 CodeInsightBundle.message("intention.implement.abstract.method.error.no.classes.message"),
                                 CodeInsightBundle.message("intention.implement.abstract.method.error.no.classes.title"),
                                 Messages.getInformationIcon());
      return;
    }

    if (result[0].length == 1) {
      implementInClass(new Object[] {result[0][0]});
      return;
    }

    final MyPsiElementListCellRenderer elementListCellRenderer = new MyPsiElementListCellRenderer();
    elementListCellRenderer.sort(result[0]);
    myList = new JBList(result[0]);
    myList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    final Runnable runnable = new Runnable(){
      @Override
      public void run() {
        int index = myList.getSelectedIndex();
        if (index < 0) return;
        implementInClass(myList.getSelectedValues());
      }
    };
    myList.setCellRenderer(elementListCellRenderer);
    final PopupChooserBuilder builder = new PopupChooserBuilder(myList);
    elementListCellRenderer.installSpeedSearch(builder);

    JBPopup popup = builder.
        setTitle(CodeInsightBundle.message("intention.implement.abstract.method.class.chooser.title")).
        setItemChoosenCallback(runnable).
        createPopup();

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(myEditor, popup);
  }

  public void implementInClass(final Object[] selection) {
    for (Object o : selection) {
      if (!((PsiElement)o).isValid()) return;
    }
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        final LinkedHashSet<PsiClass> classes = new LinkedHashSet<PsiClass>();
        for (final Object o : selection) {
          if (o instanceof PsiEnumConstant) {
            classes.add(ApplicationManager.getApplication().runWriteAction(new Computable<PsiClass>(){
              @Override
              public PsiClass compute() {
                return ((PsiEnumConstant) o).getOrCreateInitializingClass();
              }
            }));
          }
          else {
            classes.add((PsiClass)o);
          }
        }
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(classes)) return;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            for (PsiClass psiClass : classes) {
              try {
                OverrideImplementUtil.overrideOrImplement(psiClass, myMethod);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
        });
      }
    }, CodeInsightBundle.message("intention.implement.abstract.method.command.name"), null);
  }

  private PsiClass[] getClassImplementations(final PsiClass psiClass) {
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(psiClass, true)) {
      if (!inheritor.isInterface()) {
        PsiMethod method = inheritor.findMethodBySignature(myMethod, true);
        if (method == null || !method.getContainingClass().equals(psiClass)) continue;
        list.add(inheritor);
      }
    }

    return list.toArray(new PsiClass[list.size()]);
  }

  private static class MyPsiElementListCellRenderer extends PsiElementListCellRenderer<PsiElement> {
    private final PsiClassListCellRenderer myRenderer;

    public MyPsiElementListCellRenderer() {
      myRenderer = new PsiClassListCellRenderer();
    }

    void sort(PsiElement[] result) {
      final Comparator<PsiClass> comparator = myRenderer.getComparator();
      Arrays.sort(result, new Comparator<PsiElement>() {
        @Override
        public int compare(PsiElement o1, PsiElement o2) {
          if (o1 instanceof PsiEnumConstant && o2 instanceof PsiEnumConstant) {
            return ((PsiEnumConstant)o1).getName().compareTo(((PsiEnumConstant)o2).getName());
          }
          if (o1 instanceof PsiEnumConstant) return -1;
          if (o2 instanceof PsiEnumConstant) return 1;
          return comparator.compare((PsiClass)o1, (PsiClass)o2);
        }
      });
    }

    @Override
    public String getElementText(PsiElement element) {
      return element instanceof PsiClass ? myRenderer.getElementText((PsiClass)element)
                                         : ((PsiEnumConstant)element).getName();
    }

    @Override
    protected String getContainerText(PsiElement element, String name) {
      return element instanceof PsiClass ? PsiClassListCellRenderer.getContainerTextStatic(element)
                                         : ((PsiEnumConstant)element).getContainingClass().getQualifiedName();
    }

    @Override
    protected int getIconFlags() {
      return 0;
    }
  }
}
