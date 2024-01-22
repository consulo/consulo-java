/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.testDiscovery;


import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.util.diff.FilesTooBigForDiffException;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.actions.FormatChangedTextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.*;

public class TestDiscoverySearchHelper {
  public static Set<String> search(final Project project,
                                   final Pair<String, String> position,
                                   final String changeList,
                                   final String frameworkPrefix) {
    final Set<String> patterns = new LinkedHashSet<>();
    if (position != null) {
      try {
        collectPatterns(project, patterns, position.first, position.second, frameworkPrefix);
      }
      catch (IOException ignore) {
      }
    }
    final List<VirtualFile> files = getAffectedFiles(changeList, project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final TestDiscoveryIndex discoveryIndex = TestDiscoveryIndex.getInstance(project);
    for (final VirtualFile file : files) {
      ApplicationManager.getApplication().runReadAction(() ->
                                                        {
                                                          final PsiFile psiFile = psiManager.findFile(file);
                                                          if (psiFile instanceof PsiClassOwner) {
                                                            if (position != null) {
                                                              final PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
                                                              if (classes.length == 0 || TestFrameworks.detectFramework(classes[0]) == null) {
                                                                return;
                                                              }
                                                            }
                                                            try {
                                                              final List<TextRange> changedTextRanges =
                                                                FormatChangedTextUtil.getInstance().getChangedTextRanges(project, psiFile);
                                                              for (TextRange textRange : changedTextRanges) {
                                                                final PsiElement start = psiFile.findElementAt(textRange.getStartOffset());
                                                                final PsiElement end = psiFile.findElementAt(textRange.getEndOffset());
                                                                final PsiElement parent = PsiTreeUtil.findCommonParent(new PsiElement[]{
                                                                  start,
                                                                  end
                                                                });
                                                                final Collection<PsiMethod> methods =
                                                                  new ArrayList<>(PsiTreeUtil.findChildrenOfType(parent, PsiMethod.class));
                                                                final PsiMethod containingMethod =
                                                                  PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
                                                                if (containingMethod != null) {
                                                                  methods.add(containingMethod);
                                                                }
                                                                for (PsiMethod changedMethod : methods) {
                                                                  final LinkedHashSet<String> detectedPatterns =
                                                                    position == null ? collectPatterns(changedMethod,
                                                                                                       frameworkPrefix) : null;
                                                                  if (detectedPatterns != null) {
                                                                    patterns.addAll(detectedPatterns);
                                                                  }
                                                                  final PsiClass containingClass = changedMethod.getContainingClass();
                                                                  if (containingClass != null && containingClass.getParent() == psiFile) {
                                                                    final String classQualifiedName = containingClass.getQualifiedName();
                                                                    final String changedMethodName = changedMethod.getName();
                                                                    try {
                                                                      if (classQualifiedName != null && (position == null && TestFrameworks.detectFramework(
                                                                        containingClass) != null || position != null && !discoveryIndex
                                                                        .hasTestTrace(frameworkPrefix + classQualifiedName + "-" + changedMethodName))) {
                                                                        patterns.add(classQualifiedName + "," + changedMethodName);
                                                                      }
                                                                    }
                                                                    catch (IOException ignore) {
                                                                    }
                                                                  }
                                                                }
                                                              }
                                                            }
                                                            catch (FilesTooBigForDiffException ignore) {
                                                            }
                                                          }
                                                        });
    }

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    return new HashSet<>(ContainerUtil.filter(patterns,
                                              fqn -> ReadAction.compute(() -> psiFacade.findClass(StringUtil.getPackageName(fqn, ','),
                                                                                                  searchScope) != null)));
  }

  private static void collectPatterns(final Project project,
                                      final Set<String> patterns,
                                      final String classFQName,
                                      final String methodName,
                                      final String frameworkId) throws IOException {
    final TestDiscoveryIndex discoveryIndex = TestDiscoveryIndex.getInstance(project);
    final Collection<String> testsByMethodName = discoveryIndex.getTestsByMethodName(classFQName, methodName);
    if (testsByMethodName != null) {
      for (String pattern : ContainerUtil.filter(testsByMethodName, s -> s.startsWith(frameworkId))) {
        patterns.add(pattern.substring(frameworkId.length()).replace('-', ','));
      }
    }
  }

  @Nonnull
  private static List<VirtualFile> getAffectedFiles(String changeListName, Project project) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if ("All".equals(changeListName)) {
      return changeListManager.getAffectedFiles();
    }
    final LocalChangeList changeList = changeListManager.findChangeList(changeListName);
    if (changeList != null) {
      List<VirtualFile> files = new ArrayList<>();
      for (Change change : changeList.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null) {
          final VirtualFile file = afterRevision.getFile().getVirtualFile();
          if (file != null) {
            files.add(file);
          }
        }
      }
      return files;
    }

    return Collections.emptyList();
  }

  @Nullable
  private static LinkedHashSet<String> collectPatterns(PsiMethod psiMethod, String frameworkId) {
    LinkedHashSet<String> patterns = new LinkedHashSet<>();
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass != null) {
      final String qualifiedName = containingClass.getQualifiedName();
      if (qualifiedName != null) {
        try {
          collectPatterns(psiMethod.getProject(), patterns, qualifiedName, psiMethod.getName(), frameworkId);
        }
        catch (IOException e) {
          return null;
        }
      }
    }
    return patterns;
  }
}