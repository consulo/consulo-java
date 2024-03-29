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
package com.intellij.java.execution.impl.junit;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.SingleClassConfiguration;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.UndoRefactoringElementListener;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.Module;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

public class RefactoringListeners {
  public static RefactoringElementListener getListener(final PsiJavaPackage psiPackage, final Accessor<PsiJavaPackage> accessor) {
    final StringBuilder path = new StringBuilder();
    for (PsiJavaPackage parent = accessor.getPsiElement(); parent != null; parent = parent.getParentPackage()) {
      if (parent.equals(psiPackage)) {
        return new RefactorPackage(accessor, path.toString());
      }
      if (path.length() > 0) {
        path.insert(0, '.');
      }
      path.insert(0, parent.getName());
    }
    return null;
  }

  public static RefactoringElementListener getListeners(final PsiClass psiClass, final Accessor<PsiClass> accessor) {
    final PsiClass aClass = accessor.getPsiElement();
    if (aClass == null) {
      return null;
    }
    final StringBuilder path = new StringBuilder();
    for (PsiClass parent = aClass; parent != null; parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) {
      if (parent.equals(psiClass)) {
        return new RefactorClass(accessor, path.toString());
      }
      if (path.length() > 0) {
        path.insert(0, '$');
      }
      path.insert(0, parent.getName());
    }
    return null;
  }

  public static RefactoringElementListener getClassOrPackageListener(final PsiElement element, final Accessor<PsiClass> accessor) {
    if (element instanceof PsiClass) {
      return getListeners((PsiClass) element, accessor);
    }
    if (element instanceof PsiJavaPackage) {
      final PsiClass aClass = accessor.getPsiElement();
      if (aClass == null) {
        return null;
      }
      return getListener((PsiJavaPackage) element, new ClassPackageAccessor(accessor));
    }
    return null;

  }

  public interface Accessor<T extends PsiElement> {
    void setName(String qualifiedName);

    T getPsiElement();

    void setPsiElement(T psiElement);
  }

  public static class SingleClassConfigurationAccessor implements Accessor<PsiClass> {
    private final SingleClassConfiguration myConfiguration;

    public SingleClassConfigurationAccessor(final SingleClassConfiguration configuration) {
      myConfiguration = configuration;
    }

    public PsiClass getPsiElement() {
      return myConfiguration.getMainClass();
    }

    public void setPsiElement(final PsiClass psiClass) {
      myConfiguration.setMainClass(psiClass);
    }

    public void setName(final String qualifiedName) {
      myConfiguration.setMainClassName(qualifiedName);
    }
  }

  private static abstract class RenameElement<T extends PsiElement> extends RefactoringElementAdapter implements UndoRefactoringElementListener {
    private final Accessor<T> myAccessor;
    private final String myPath;

    public RenameElement(final Accessor<T> accessor, final String path) {
      myAccessor = accessor;
      myPath = path;
    }

    public void elementRenamedOrMoved(@Nonnull final PsiElement newElement) {
      T newElement1 = (T) newElement;
      String qualifiedName = getQualifiedName(newElement1);
      if (myPath.length() > 0) {
        qualifiedName = qualifiedName + "." + myPath;
        newElement1 = findNewElement(newElement1, qualifiedName);
      }
      if (newElement1 != null) {
        myAccessor.setPsiElement(newElement1);
      } else {
        myAccessor.setName(qualifiedName);
      }
    }

    protected abstract T findNewElement(T newParent, String qualifiedName);

    protected abstract String getQualifiedName(T element);

    @Override
    public void undoElementMovedOrRenamed(@Nonnull PsiElement newElement, @Nonnull String oldQualifiedName) {
      myAccessor.setName(oldQualifiedName);
    }
  }

  private static class RefactorPackage extends RenameElement<PsiJavaPackage> {
    public RefactorPackage(final Accessor<PsiJavaPackage> accessor, final String path) {
      super(accessor, path);
    }

    public PsiJavaPackage findNewElement(final PsiJavaPackage psiPackage, final String qualifiedName) {
      return JavaPsiFacade.getInstance(psiPackage.getProject()).findPackage(qualifiedName);
    }

    public String getQualifiedName(final PsiJavaPackage psiPackage) {
      return psiPackage.getQualifiedName();
    }
  }

  private static class RefactorClass extends RenameElement<PsiClass> {
    public RefactorClass(final Accessor<PsiClass> accessor, final String path) {
      super(accessor, path);
    }

    public PsiClass findNewElement(final PsiClass psiClass, final String qualifiedName) {
      return JavaPsiFacade.getInstance(psiClass.getProject()).findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.moduleScope(JavaExecutionUtil.findModule(psiClass)));
    }

    public String getQualifiedName(final PsiClass psiClass) {
      return psiClass.getQualifiedName();
    }
  }

  private static class ClassPackageAccessor implements RefactoringListeners.Accessor<PsiJavaPackage> {
    private final PsiJavaPackage myContainingPackage;
    private final Module myModule;
    private final RefactoringListeners.Accessor<PsiClass> myAccessor;
    private final String myInpackageName;

    public ClassPackageAccessor(final RefactoringListeners.Accessor<PsiClass> accessor) {
      myAccessor = accessor;
      PsiClass aClass = myAccessor.getPsiElement();
      aClass = (PsiClass) aClass.getOriginalElement();
      myContainingPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
      myModule = JavaExecutionUtil.findModule(aClass);
      final String classQName = aClass.getQualifiedName();
      final String classPackageQName = myContainingPackage.getQualifiedName();
      if (classQName.startsWith(classPackageQName)) {
        final String inpackageName = classQName.substring(classPackageQName.length());
        if (StringUtil.startsWithChar(inpackageName, '.')) {
          myInpackageName = inpackageName.substring(1);
        } else {
          myInpackageName = inpackageName;
        }
      } else {
        myInpackageName = null;
      }
    }

    public PsiJavaPackage getPsiElement() {
      return myContainingPackage;
    }

    public void setPsiElement(final PsiJavaPackage psiPackage) {
      if (myInpackageName == null) {
        return; //we can do nothing
      }
      final String classQName = getClassQName(psiPackage.getQualifiedName());
      final PsiClass newClass = JUnitUtil.findPsiClass(classQName, myModule, psiPackage.getProject());
      if (newClass != null) {
        myAccessor.setPsiElement(newClass);
      } else {
        myAccessor.setName(classQName);
      }
    }

    public void setName(final String qualifiedName) {
      myAccessor.setName(getClassQName(qualifiedName));
    }

    private String getClassQName(final String packageQName) {
      if (packageQName.length() > 0) {
        return packageQName + '.' + myInpackageName;
      } else {
        return myInpackageName;
      }
    }
  }
}
