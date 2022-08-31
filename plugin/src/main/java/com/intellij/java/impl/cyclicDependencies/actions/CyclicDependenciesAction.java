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
package com.intellij.java.impl.cyclicDependencies.actions;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import javax.annotation.Nullable;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.java.impl.analysis.JavaAnalysisScope;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesAction extends AnAction{
  private final String myAnalysisVerb;
  private final String myAnalysisNoun;
  private final String myTitle;

  public CyclicDependenciesAction() {
    myAnalysisVerb = AnalysisScopeBundle.message("action.analyze.verb");
    myAnalysisNoun = AnalysisScopeBundle.message("action.analysis.noun");
    myTitle = AnalysisScopeBundle.message("action.cyclic.dependency.title");
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(
      getInspectionScope(event.getDataContext()) != null || 
      event.getData(CommonDataKeys.PROJECT) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Module module = e.getData(LangDataKeys.MODULE);
    if (project != null) {
      AnalysisScope scope = getInspectionScope(dataContext);
      if (scope == null || scope.getScopeType() != AnalysisScope.MODULES){
        ProjectModuleOrPackageDialog dlg = null;
        if (module != null) {
          dlg = new ProjectModuleOrPackageDialog(ModuleManager.getInstance(project).getModules().length == 1 ? null : ModuleUtilCore
            .getModuleNameInReadAction(module), scope);
          dlg.show();
          if (!dlg.isOK()) return;
        }
        if (dlg == null || dlg.isProjectScopeSelected()) {
          scope = getProjectScope(dataContext);
        }
        else {
          if (dlg.isModuleScopeSelected()) {
            scope = getModuleScope(dataContext);
          }
        }
        if (scope != null) {
          scope.setIncludeTestSource(dlg != null && dlg.isIncludeTestSources());
        }
      }

      FileDocumentManager.getInstance().saveAllDocuments();

      new CyclicDependenciesHandler(project, scope).analyze();
    }
  }


  @javax.annotation.Nullable
  private static AnalysisScope getInspectionScope(final DataContext dataContext) {
    final Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  @Nullable
  private static AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: package, project, module.
    Project projectContext = dataContext.getData(PlatformDataKeys.PROJECT_CONTEXT);
    if (projectContext != null) {
      return null;
    }

    Module moduleContext = dataContext.getData(LangDataKeys.MODULE_CONTEXT);
    if (moduleContext != null) {
      return null;
    }

    Module [] modulesArray = dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray);
    }

    PsiElement psiTarget = dataContext.getData(LangDataKeys.PSI_ELEMENT);
    if (psiTarget instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiTarget;
      if (!psiDirectory.getManager().isInProject(psiDirectory)) return null;
      return new AnalysisScope(psiDirectory);
    }
    else if (psiTarget instanceof PsiJavaPackage) {
      PsiJavaPackage pack = (PsiJavaPackage)psiTarget;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs.length == 0) return null;
      return new JavaAnalysisScope(pack, dataContext.getData(LangDataKeys.MODULE));
    }

    return null;
  }

  @javax.annotation.Nullable
  private static AnalysisScope getProjectScope(DataContext dataContext) {
    final Project data = dataContext.getData(CommonDataKeys.PROJECT);
    if (data == null) {
      return null;
    }
    return new AnalysisScope(data);
  }

  @javax.annotation.Nullable
  private static AnalysisScope getModuleScope(DataContext dataContext) {
    final Module data = dataContext.getData(LangDataKeys.MODULE);
    if (data == null) {
      return null;
    }
    return new AnalysisScope(data);
  }

  private class ProjectModuleOrPackageDialog extends DialogWrapper {
    private final String myModuleName;
    private AnalysisScope mySelectedScope;
    private JRadioButton myProjectButton;
    private JRadioButton myModuleButton;
    private JRadioButton mySelectedScopeButton;

    private JPanel myScopePanel;
    private JPanel myWholePanel;
    private JCheckBox myIncludeTestSourcesCb;


    public ProjectModuleOrPackageDialog(String moduleName, AnalysisScope selectedScope) {
      super(true);
      myModuleName = moduleName;
      mySelectedScope = selectedScope;
      init();
      setTitle(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.title", myTitle));
      setHorizontalStretch(1.75f);
    }

    public boolean isIncludeTestSources() {
      return myIncludeTestSourcesCb.isSelected();
    }

    protected JComponent createCenterPanel() {
      myScopePanel.setBorder(IdeBorderFactory.createTitledBorder(
        AnalysisScopeBundle.message("analysis.scope.title", myAnalysisNoun), true));
      myProjectButton.setText(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.project.button", myAnalysisVerb));
      ButtonGroup group = new ButtonGroup();
      group.add(myProjectButton);
      if (myModuleName != null) {
        myModuleButton.setText(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.module.button", myAnalysisVerb, myModuleName));
        group.add(myModuleButton);
      }
      myModuleButton.setVisible(myModuleName != null);
      mySelectedScopeButton.setVisible(mySelectedScope != null);
      if (mySelectedScope != null) {
        mySelectedScopeButton.setText(mySelectedScope.getShortenName());
        group.add(mySelectedScopeButton);
      }
      if (mySelectedScope != null) {
        mySelectedScopeButton.setSelected(true);
      } else if (myModuleName != null) {
        myModuleButton.setSelected(true);
      } else {
        myProjectButton.setSelected(true);
      }
      return myWholePanel;
    }

    public boolean isProjectScopeSelected() {
      return myProjectButton.isSelected();
    }

    public boolean isModuleScopeSelected() {
      return myModuleButton != null ? myModuleButton.isSelected() : false;
    }

  }
}
