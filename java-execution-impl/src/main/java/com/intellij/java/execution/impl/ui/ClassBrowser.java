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
package com.intellij.java.execution.impl.ui;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.configurations.ConfigurationUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.application.ReadAction;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.awt.BrowseModuleValueActionListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.awt.MessagesEx;
import jakarta.annotation.Nullable;

public abstract class ClassBrowser extends BrowseModuleValueActionListener {
  private final String myTitle;

  public ClassBrowser(final Project project, final String title) {
    super(project);
    myTitle = title;
  }

  @Nullable
  protected String showDialog() {
    final ClassFilter.ClassFilterWithScope classFilter;
    try {
      classFilter = getFilter();
    } catch (NoFilterException e) {
      final MessagesEx.MessageInfo info = e.getMessageInfo();
      info.showNow();
      return null;
    }
    final TreeClassChooser dialog = createClassChooser(classFilter);
    configureDialog(dialog);
    dialog.showDialog();
    final PsiClass psiClass = dialog.getSelected();
    if (psiClass == null) return null;
    onClassChoosen(psiClass);
    return JavaExecutionUtil.getRuntimeQualifiedName(psiClass);
  }

  protected TreeClassChooser createClassChooser(ClassFilter.ClassFilterWithScope classFilter) {
    return TreeClassChooserFactory.getInstance(getProject())
        .createWithInnerClassesScopeChooser(myTitle, classFilter.getScope(), classFilter, null);
  }

  protected abstract ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException;

  protected void onClassChoosen(final PsiClass psiClass) {
  }

  private void configureDialog(final TreeClassChooser dialog) {
    final String className = getText();
    final PsiClass psiClass = findClass(className);
    if (psiClass == null) return;
    final PsiDirectory directory = psiClass.getContainingFile().getContainingDirectory();
    if (directory != null) dialog.selectDirectory(directory);
    dialog.select(psiClass);
  }

  protected abstract PsiClass findClass(String className);

  public static ClassBrowser createApplicationClassBrowser(final Project project,
                                                           final ConfigurationModuleSelector moduleSelector) {
    final ClassFilter applicationClass = new ClassFilter() {
      @Override
      public boolean isAccepted(final PsiClass aClass) {
        return ConfigurationUtil.MAIN_CLASS.test(aClass) && findMainMethod(aClass) != null;
      }

      @Nullable
      private PsiMethod findMainMethod(final PsiClass aClass) {
        return ReadAction.compute(() -> PsiMethodUtil.findMainMethod(aClass));
      }
    };
    return new MainClassBrowser(project, moduleSelector, ExecutionLocalize.chooseMainClassDialogTitle().get()) {
      @Override
      protected ClassFilter createFilter(final Module module) {
        return applicationClass;
      }
    };
  }

  public static ClassBrowser createAppletClassBrowser(final Project project, final ConfigurationModuleSelector moduleSelector) {
    final String title = ExecutionLocalize.chooseAppletClassDialogTitle().get();
    return new MainClassBrowser(project, moduleSelector, title) {
      @Override
      protected TreeClassChooser createClassChooser(ClassFilter.ClassFilterWithScope classFilter) {
        final Module module = moduleSelector.getModule();
        final GlobalSearchScope scope =
            module == null ? GlobalSearchScope.allScope(myProject) : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
        final PsiClass appletClass = JavaPsiFacade.getInstance(project).findClass("java.applet.Applet", scope);
        return TreeClassChooserFactory.getInstance(getProject())
            .createInheritanceClassChooser(title, classFilter.getScope(), appletClass, false, false,
                ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS);
      }
    };
  }

  private abstract static class MainClassBrowser extends ClassBrowser {
    protected final Project myProject;
    private final ConfigurationModuleSelector myModuleSelector;

    public MainClassBrowser(final Project project,
                            final ConfigurationModuleSelector moduleSelector,
                            final String title) {
      super(project, title);
      myProject = project;
      myModuleSelector = moduleSelector;
    }

    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      final Module module = myModuleSelector.getModule();
      final GlobalSearchScope scope;
      if (module == null) {
        scope = GlobalSearchScope.allScope(myProject);
      } else {
        scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      }
      final ClassFilter filter = createFilter(module);
      return new ClassFilter.ClassFilterWithScope() {
        public GlobalSearchScope getScope() {
          return scope;
        }

        public boolean isAccepted(final PsiClass aClass) {
          return filter == null || filter.isAccepted(aClass);
        }
      };
    }

    protected ClassFilter createFilter(final Module module) {
      return null;
    }
  }

  public static class NoFilterException extends Exception {
    private final MessagesEx.MessageInfo myMessageInfo;

    public NoFilterException(final MessagesEx.MessageInfo messageInfo) {
      super(messageInfo.getMessage());
      myMessageInfo = messageInfo;
    }

    public MessagesEx.MessageInfo getMessageInfo() {
      return myMessageInfo;
    }

    public static NoFilterException noJUnitInModule(final Module module) {
      return new NoFilterException(new MessagesEx.MessageInfo(
        module.getProject(),
        ExecutionLocalize.junitNotFoundInModuleErrorMessage(module.getName()).get(),
        ExecutionLocalize.cannotBrowseTestInheritorsDialogTitle().get()
      ));
    }

    public static NoFilterException moduleDoesntExist(final ConfigurationModuleSelector moduleSelector) {
      final Project project = moduleSelector.getProject();
      final String moduleName = moduleSelector.getModuleName();
      return new NoFilterException(new MessagesEx.MessageInfo(
        project,
        moduleName.isEmpty() ? "No module selected" : ExecutionLocalize.moduleDoesNotExists(moduleName, project.getName()).get(),
        ExecutionLocalize.cannotBrowseTestInheritorsDialogTitle().get()
      ));
    }
  }
}
