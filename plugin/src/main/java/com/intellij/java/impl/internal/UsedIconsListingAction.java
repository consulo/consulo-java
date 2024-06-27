/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 * @author max
 */
package com.intellij.java.impl.internal;

import com.intellij.java.impl.psi.search.searches.AnnotationTargetsSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Result;
import consulo.application.util.function.Processor;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.collection.MultiMap;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.ide.highlighter.XmlFileType;
import consulo.xml.psi.XmlRecursiveElementVisitor;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.psi.xml.XmlTag;
import jakarta.annotation.Nonnull;

import java.util.*;

public class UsedIconsListingAction extends AnAction {
  @Override
  @RequiredReadAction
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(Project.KEY);

    final MultiMap<String, PsiExpression> calls = new MultiMap<>();

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    Processor<PsiReference> consumer = new Processor<>() {
      @Override
      @RequiredReadAction
      public boolean process(PsiReference reference) {
        PsiCallExpression call = PsiTreeUtil.getParentOfType(reference.getElement(), PsiCallExpression.class, false);
        if (call == null) return true;
        if (call.getArgumentList() == null) return true;
        if (call.getArgumentList().getExpressions() == null) return true;

        PsiFile file = reference.getElement().getContainingFile();
        if ("AllIcons.java".equals(file.getName())) return true;

        PsiClass container = PsiUtil.getTopLevelClass(reference.getElement());
        if (container != null && container.getQualifiedName().startsWith("icons.")) return true;

        for (PsiExpression arg : call.getArgumentList().getExpressions()) {
          if (arg instanceof PsiLiteralExpression literalExpression) {
            Object value = literalExpression.getValue();
            processValue(value, call, file);
          }
          else {
            Object value = psiFacade.getConstantEvaluationHelper().computeConstantExpression(arg, false);
            processValue(value, call, file);
          }
        }


        return true;
      }

      private void processValue(Object value, PsiCallExpression call, PsiFile file) {
        if (value instanceof String str) {
          if (str.startsWith("\"")) {
            str = str.substring(0);
            if (str.endsWith("\"")) {
              str = str.substring(0, str.length() - 1);
            }
          }

          if (!str.startsWith("/") && file instanceof PsiClassOwner) {
            str = "/" + ((PsiClassOwner)file).getPackageName().replace('.', '/') + "/" + str;
          }

          calls.putValue(str, call);
        }
      }
    };

    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    PsiClass iconLoader = psiFacade.findClass("com.intellij.openapi.util.IconLoader", allScope);

    PsiMethod getIconMethod = iconLoader.findMethodsByName("getIcon", false)[0];
    PsiMethod findIconMethod = iconLoader.findMethodsByName("findIcon", false)[0];
    MethodReferencesSearch.search(getIconMethod, false).forEach(consumer);
    MethodReferencesSearch.search(findIconMethod, false).forEach(consumer);

    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    PsiClass javaeeIcons = psiFacade.findClass("com.intellij.javaee.oss.JavaeeIcons", allScope);
    MethodReferencesSearch.search(javaeeIcons.findMethodsByName("getIcon", false)[0], false).forEach(consumer);

    MethodReferencesSearch.search(findIconMethod, false).forEach(consumer);

    final List<XmlAttribute> xmlAttributes = new ArrayList<>();

    PsiSearchHelper.SERVICE.getInstance(project).processAllFilesWithWordInText(
      "icon",
      new DelegatingGlobalSearchScope(GlobalSearchScope.projectScope(project)) {
        @Override
        public boolean contains(@Nonnull VirtualFile file) {
          return super.contains(file) && file.getFileType() == XmlFileType.INSTANCE && index.isInSource(file);
        }
      },

      new Processor<>() {
        @Override
        public boolean process(PsiFile file) {
          file.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              super.visitXmlTag(tag);

              String icon = tag.getAttributeValue("icon");
              if (icon != null) {
                xmlAttributes.add(tag.getAttribute("icon"));
              }
            }
          });
          return true;
        }
      },

      true
    );

    PsiClass presentation = psiFacade.findClass("com.intellij.ide.presentation.Presentation", allScope);
    final MultiMap<String, PsiAnnotation> annotations = new MultiMap<>();
    AnnotationTargetsSearch.search(presentation).forEach(owner -> {
      PsiAnnotation annotation = owner.getModifierList().findAnnotation("com.intellij.ide.presentation.Presentation");

      PsiAnnotationMemberValue icon = annotation.findAttributeValue("icon");
      if (icon instanceof PsiLiteralExpression literalExpression) {
        Object value = literalExpression.getValue();
        if (value instanceof String stringValue) {
          annotations.putValue(stringValue, annotation);
        }
      }

      return true;
    });

    doReplacements(project, calls, xmlAttributes, annotations, psiFacade.findClass("com.intellij.icons.AllIcons", allScope));
    for (PsiClass iconClass : psiFacade.findPackage("icons").getClasses(allScope)) {
      if (iconClass.getName().endsWith("Icons")) {
        doReplacements(project, calls, xmlAttributes, annotations, iconClass);
      }
    }
  }

  private static void doReplacements(
    final Project project,
    MultiMap<String, PsiExpression> calls,
    List<XmlAttribute> xmlAttributes,
    MultiMap<String, PsiAnnotation> annotations,
    PsiClass iconClass
  ) {
    final HashMap<String, String> mappings = new HashMap<>();
    int size = mappings.size();
    collectFields(iconClass, "", mappings);
    System.out.println("Found " + (mappings.size() - size) + " icons in " + iconClass.getQualifiedName());

    GlobalSearchScope useScope = (GlobalSearchScope)iconClass.getUseScope();

    for (final XmlAttribute att : xmlAttributes) {
      if (!att.isValid()) continue;
      String value = att.getValue();
      final String replacement = mappings.get(value);
      if (replacement != null) {
        final PsiFile file = att.getContainingFile();
        if (useScope.contains(file.getVirtualFile())) {
          new WriteCommandAction<Void>(project, file) {
            @Override
            protected void run(Result<Void> result) throws Throwable {
              att.setValue(replacement);
            }
          }.execute();
        }
      }
    }

    final JVMElementFactory factory = JVMElementFactories.getFactory(JavaLanguage.INSTANCE, project);
    for (Map.Entry<String, Collection<PsiExpression>> entry : calls.entrySet()) {
      String path = entry.getKey();
      final String replacement = mappings.get(path);
      if (replacement != null) {
        for (final PsiExpression call : entry.getValue()) {
          if (!call.isValid()) continue;

          final PsiFile file = call.getContainingFile();
          if (useScope.contains(file.getVirtualFile())) {
            new WriteCommandAction(project, file) {
              @Override
              protected void run(Result result) throws Throwable {
                if (call instanceof PsiLiteralExpression) {
                  call.replace(factory.createExpressionFromText("\"" + replacement + "\"", call));
                }
                else {
                  JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
                  String packageName = replacement.startsWith("AllIcons.") ? "com.intellij.icons." : "icons.";
                  PsiElement expr = factory.createExpressionFromText(packageName + replacement, call);
                  styleManager.shortenClassReferences(call.replace(expr));
                }
              }
            }.execute();
          }
        }
      }
    }

    for (Map.Entry<String, Collection<PsiAnnotation>> entry : annotations.entrySet()) {
      String path = entry.getKey();
      final String replacement = mappings.get(path);
      if (replacement != null) {
        for (final PsiAnnotation annotation : entry.getValue()) {
          if (annotation instanceof PsiCompiledElement) continue;
          if (!annotation.isValid()) continue;

          PsiFile file = annotation.getContainingFile();
          if (useScope.contains(file.getVirtualFile())) {
            new WriteCommandAction(project, file) {
              @Override
              protected void run(Result result) throws Throwable {
                annotation.getNode();
                annotation.setDeclaredAttributeValue(
                  "icon",
                  JavaPsiFacade.getInstance(annotation.getProject()).getElementFactory()
                    .createAnnotationFromText("@A(\"" + replacement + "\")", null).findDeclaredAttributeValue(null));
              }
            }.execute();
          }
        }
      }
    }
  }

  private static void collectFields(PsiClass klass, String prefix, Map<String, String> mapping) {
    String thePrefix = prefix + klass.getName() + ".";

    for (PsiClass inner : klass.getInnerClasses()) {
      collectFields(inner, thePrefix, mapping);
    }

    for (PsiField field : klass.getFields()) {
      PsiCallExpression initializer = (PsiCallExpression)field.getInitializer();
      PsiLiteralExpression arg = (PsiLiteralExpression)initializer.getArgumentList().getExpressions()[0];

      mapping.put((String)arg.getValue(), thePrefix + field.getName());
    }
  }
}
