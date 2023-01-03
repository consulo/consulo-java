/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace;
import com.intellij.java.indexing.impl.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.java.indexing.impl.stubs.index.JavaModuleNameIndex;
import com.intellij.java.indexing.impl.stubs.index.JavaSourceModuleNameIndex;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.AllIcons;
import consulo.java.language.impl.JavaIcons;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.*;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.java.impl.codeInsight.completion.BasicExpressionCompletionContributor.createKeywordLookupItem;

class JavaModuleCompletion {
  static boolean isModuleFile(@Nonnull PsiFile file) {
    return PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) && PsiUtil.isLanguageLevel9OrHigher(file);
  }

  static void addVariants(@Nonnull PsiElement position, @Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet resultSet) {
    Consumer<LookupElement> result = element ->
    {
      if (element.getLookupString().startsWith(resultSet.getPrefixMatcher().getPrefix())) {
        resultSet.addElement(element);
      }
    };

    if (position instanceof PsiIdentifier) {
      PsiElement context = position.getParent();
      if (context instanceof PsiErrorElement) {
        context = context.getParent();
      }

      if (context instanceof PsiJavaFile) {
        addFileHeaderKeywords(position, result);
      } else if (context instanceof PsiJavaModule) {
        addModuleStatementKeywords(position, result);
      } else if (context instanceof PsiProvidesStatement) {
        addProvidesStatementKeywords(position, result);
      } else if (context instanceof PsiJavaModuleReferenceElement) {
        addRequiresStatementKeywords(context, position, result);
        addModuleReferences(context, parameters.getOriginalFile(), resultSet);
      } else if (context instanceof PsiJavaCodeReferenceElement) {
        addClassOrPackageReferences(context, result, resultSet);
      }
    }
  }

  private static void addFileHeaderKeywords(PsiElement position, Consumer<LookupElement> result) {
    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    if (prev == null) {
      result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.OPEN), TailType.HUMBLE_SPACE_BEFORE_WORD));
    } else if (PsiUtil.isJavaToken(prev, JavaTokenType.OPEN_KEYWORD)) {
      result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addModuleStatementKeywords(PsiElement position, Consumer<LookupElement> result) {
    result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.REQUIRES), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.EXPORTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.OPENS), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.USES), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.PROVIDES), TailType.HUMBLE_SPACE_BEFORE_WORD));
  }

  private static void addProvidesStatementKeywords(PsiElement position, Consumer<LookupElement> result) {
    result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.WITH), TailType.HUMBLE_SPACE_BEFORE_WORD));
  }

  private static void addRequiresStatementKeywords(PsiElement context, PsiElement position, Consumer<LookupElement> result) {
    if (context.getParent() instanceof PsiRequiresStatement) {
      result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.TRANSITIVE), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.accept(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.STATIC), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addModuleReferences(PsiElement moduleRef, PsiFile originalFile, CompletionResultSet result) {
    PsiElement statement = moduleRef.getParent();
    boolean requires;
    if ((requires = statement instanceof PsiRequiresStatement) || statement instanceof PsiPackageAccessibilityStatement) {
      PsiElement parent = statement.getParent();
      if (parent != null) {
        Project project = moduleRef.getProject();
        Set<String> filter = new HashSet<>();
        filter.add(((PsiJavaModule) parent).getName());

        JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
        GlobalSearchScope scope = (GlobalSearchScope) ProjectScopes.getAllScope(project);
        for (String name : index.getAllKeys(project)) {
          if (index.get(name, project, scope).size() > 0 && filter.add(name)) {
            LookupElement lookup = LookupElementBuilder.create(name).withIcon(JavaIcons.Nodes.JavaModule);
            if (requires)
              lookup = TailTypeDecorator.withTail(lookup, TailType.SEMICOLON);
            result.addElement(lookup);
          }
        }

        if (requires) {
          Module module = ModuleUtilCore.findModuleForFile(originalFile);
          if (module != null) {
            scope = GlobalSearchScope.projectScope(project);
            for (String name : JavaSourceModuleNameIndex.getAllKeys(project)) {
              if (JavaSourceModuleNameIndex.getFilesByKey(name, scope).size() > 0) {
                addAutoModuleReference(name, parent, filter, result);
              }
            }
            VirtualFile[] roots = ModuleRootManager.getInstance(module).orderEntries().withoutSdk().librariesOnly().getClassesRoots();
            scope = GlobalSearchScope.filesScope(project, Arrays.asList(roots));
            for (String name : JavaAutoModuleNameIndex.getAllKeys(project)) {
              if (JavaAutoModuleNameIndex.getFilesByKey(name, scope).size() > 0) {
                addAutoModuleReference(name, parent, filter, result);
              }
            }
          }
        }
      }
    }
  }

  private static void addAutoModuleReference(String name, PsiElement parent, Set<? super String> filter, CompletionResultSet result) {
    if (PsiNameHelper.isValidModuleName(name, parent) && filter.add(name)) {
      LookupElement lookup = LookupElementBuilder.create(name).withIcon(AllIcons.FileTypes.Archive);
      lookup = TailTypeDecorator.withTail(lookup, TailType.SEMICOLON);
      lookup = PrioritizedLookupElement.withPriority(lookup, -1);
      result.addElement(lookup);
    }
  }

  private static void addClassOrPackageReferences(PsiElement context, Consumer<LookupElement> result, CompletionResultSet resultSet) {
    PsiElement refOwner = context.getParent();
    if (refOwner instanceof PsiPackageAccessibilityStatement) {
      Module module = ModuleUtilCore.findModuleForPsiElement(context);
      PsiPackage topPackage = JavaPsiFacade.getInstance(context.getProject()).findPackage("");
      if (module != null && topPackage != null) {
        processPackage(topPackage, GlobalSearchScope.moduleScope(module, false), result);
      }
    } else if (refOwner instanceof PsiUsesStatement) {
      processClasses(context.getProject(), null, resultSet, SERVICE_FILTER, TailType.SEMICOLON);
    } else if (refOwner instanceof PsiProvidesStatement) {
      processClasses(context.getProject(), null, resultSet, SERVICE_FILTER, TailType.HUMBLE_SPACE_BEFORE_WORD);
    } else if (refOwner instanceof PsiReferenceList) {
      PsiElement statement = refOwner.getParent();
      if (statement instanceof PsiProvidesStatement) {
        PsiJavaCodeReferenceElement intRef = ((PsiProvidesStatement) statement).getInterfaceReference();
        if (intRef != null) {
          PsiElement service = intRef.resolve();
          Module module = ModuleUtilCore.findModuleForPsiElement(context);
          if (service instanceof PsiClass && module != null) {
            Predicate<PsiClass> filter = psiClass -> !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) && InheritanceUtil.isInheritorOrSelf(psiClass, (PsiClass) service, true);
            processClasses(context.getProject(), GlobalSearchScope.moduleScope(module, false), resultSet, filter, TailType.SEMICOLON);
          }
        }
      }
    }
  }

  private static void processPackage(PsiPackage pkg, GlobalSearchScope scope, Consumer<LookupElement> result) {
    String packageName = pkg.getQualifiedName();
    if (isQualified(packageName) && !PsiUtil.isPackageEmpty(pkg.getDirectories(scope), packageName)) {
      result.accept(new OverrideableSpace(lookupElement(pkg), TailType.SEMICOLON));
    }
    for (PsiPackage subPackage : pkg.getSubPackages(scope)) {
      processPackage(subPackage, scope, result);
    }
  }

  private static final Predicate<PsiClass> SERVICE_FILTER = psiClass -> !psiClass.isEnum() && psiClass.hasModifierProperty(PsiModifier.PUBLIC);

  private static void processClasses(Project project, GlobalSearchScope scope, CompletionResultSet resultSet, Predicate<PsiClass> filter, TailType tail) {
    GlobalSearchScope _scope = scope != null ? scope : (GlobalSearchScope) ProjectScopes.getAllScope(project);
    AllClassesGetter.processJavaClasses(resultSet.getPrefixMatcher(), project, _scope, psiClass ->
    {
      if (isQualified(psiClass.getQualifiedName()) && filter.test(psiClass)) {
        resultSet.addElement(new OverrideableSpace(lookupElement(psiClass), tail));
      }
      return true;
    });
  }

  private static LookupElementBuilder lookupElement(PsiNamedElement e) {
    LookupElementBuilder lookup = LookupElementBuilder.create(e).withInsertHandler(FQN_INSERT_HANDLER);
    String fqn = e instanceof PsiClass ? ((PsiClass) e).getQualifiedName() : ((PsiQualifiedNamedElement) e).getQualifiedName();
    return fqn != null ? lookup.withPresentableText(fqn) : lookup;
  }

  private static boolean isQualified(String name) {
    return name != null && name.indexOf('.') > 0;
  }

  private static final InsertHandler<LookupElement> FQN_INSERT_HANDLER = new InsertHandler<LookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      Object e = item.getObject();
      String fqn = e instanceof PsiClass ? ((PsiClass) e).getQualifiedName() : ((PsiQualifiedNamedElement) e).getQualifiedName();
      if (fqn != null) {
        int start = JavaCompletionUtil.findQualifiedNameStart(context);
        context.getDocument().replaceString(start, context.getTailOffset(), fqn);
      }
    }
  };
}