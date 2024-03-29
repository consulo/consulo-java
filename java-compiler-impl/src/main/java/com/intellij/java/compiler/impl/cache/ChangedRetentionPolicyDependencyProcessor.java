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
package com.intellij.java.compiler.impl.cache;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.compiler.impl.util.cls.ClsUtil;
import consulo.application.ApplicationManager;
import consulo.compiler.CacheCorruptedException;
import consulo.component.ProcessCanceledException;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;

import java.util.Collection;

public class ChangedRetentionPolicyDependencyProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.ChangedConstantsDependencyProcessor");
  private final Project myProject;
  private final CachingSearcher mySearcher;
  private final JavaDependencyCache myDependencyCache;

  public ChangedRetentionPolicyDependencyProcessor(Project project, CachingSearcher searcher, JavaDependencyCache dependencyCache) {
    myProject = project;
    mySearcher = searcher;
    myDependencyCache = dependencyCache;
  }

  public void checkAnnotationRetentionPolicyChanges(final int annotationQName) throws CacheCorruptedException {
    final Cache oldCache = myDependencyCache.getCache();
    if (!ClsUtil.isAnnotation(oldCache.getFlags(annotationQName))) {
      return;
    }
    if (!hasRetentionPolicyChanged(annotationQName, oldCache, myDependencyCache.getNewClassesCache(), myDependencyCache.getSymbolTable())) {
      return;
    }
    final CacheCorruptedException[] _ex = new CacheCorruptedException[] {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          final String qName = myDependencyCache.resolve(annotationQName);
          PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(qName.replace('$', '.'), GlobalSearchScope.allScope(myProject));
          for (final PsiClass aClass : classes) {
            if (!aClass.isAnnotationType()) {
              continue;
            }
            final Collection<PsiReference> references = mySearcher.findReferences(aClass, true);
            for (PsiReference reference : references) {
              final PsiClass ownerClass = getOwnerClass(reference.getElement());
              if (ownerClass != null && !ownerClass.equals(aClass)) {
                int qualifiedName = myDependencyCache.getSymbolTable().getId(ownerClass.getQualifiedName());
                if (myDependencyCache.markClass(qualifiedName, false)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Marked dependent class " + myDependencyCache.resolve(qualifiedName) +
                              "; reason: annotation's retention policy changed from SOURCE to CLASS or RUNTIME " +
                              myDependencyCache.resolve(annotationQName));
                  }
                }
              }
            }
          }
        }
        catch (CacheCorruptedException e) {
          _ex[0] = e;
        }
        catch (ProcessCanceledException e) {
          // supressed deliberately
        }
      }
    });
    if (_ex[0] != null) {
      throw _ex[0];
    }
  }

  private boolean hasRetentionPolicyChanged(int annotationQName, final Cache oldCache, final Cache newCache, SymbolTable symbolTable) throws CacheCorruptedException {
    // if retention policy changed from SOURCE to CLASS or RUNTIME, all sources should be recompiled to propagate changes
    final int oldPolicy = JavaMakeUtil.getAnnotationRetentionPolicy(annotationQName, oldCache, symbolTable);
    final int newPolicy = JavaMakeUtil.getAnnotationRetentionPolicy(annotationQName, newCache, symbolTable);
    if ((oldPolicy == RetentionPolicies.SOURCE) && (newPolicy == RetentionPolicies.CLASS || newPolicy == RetentionPolicies.RUNTIME)) {
      return true;
    }
    return false;
  }

  private static PsiClass getOwnerClass(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass && element.getParent() instanceof PsiJavaFile) { // top-level class
        return (PsiClass)element;
      }
      element = element.getParent();
    }
    return null;
  }
}
