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
package com.intellij.java.impl.testIntegration;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateDescriptor;
import consulo.fileTemplate.FileTemplateManager;
import consulo.language.editor.template.ConstantNode;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class TestIntegrationUtils {
  private static final Logger LOG = Logger.getInstance(TestIntegrationUtils.class);

  public enum MethodKind {
    SET_UP("setUp") {
      public FileTemplateDescriptor getFileTemplateDescriptor(@Nonnull TestFramework framework) {
        return framework.getSetUpMethodFileTemplateDescriptor();
      }
    },
    TEAR_DOWN("tearDown") {
      public FileTemplateDescriptor getFileTemplateDescriptor(@Nonnull TestFramework framework) {
        return framework.getTearDownMethodFileTemplateDescriptor();
      }
    },
    TEST("test") {
      public FileTemplateDescriptor getFileTemplateDescriptor(@Nonnull TestFramework framework) {
        return framework.getTestMethodFileTemplateDescriptor();
      }
    },
    DATA("data") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@Nonnull TestFramework framework) {
        if (framework instanceof JavaTestFramework) {
          return ((JavaTestFramework)framework).getParametersMethodFileTemplateDescriptor();
        }
        return null;
      }
    };
    private String myDefaultName;

    MethodKind(String defaultName) {
      myDefaultName = defaultName;
    }

    public String getDefaultName() {
      return myDefaultName;
    }

    public abstract FileTemplateDescriptor getFileTemplateDescriptor(@Nonnull TestFramework framework);
  }

  public static boolean isTest(@jakarta.annotation.Nonnull PsiElement element) {
    PsiClass klass = findOuterClass(element);
    return klass != null && TestFrameworks.getInstance().isTestClass(klass);
  }

  @Nullable
  public static PsiClass findOuterClass(@jakarta.annotation.Nonnull PsiElement element) {
    PsiClass result = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (result == null) {
       final PsiFile containingFile = element.getContainingFile();
       if (containingFile instanceof PsiClassOwner){
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if (classes.length == 1) {
          result = classes[0];
        }
      }
    }
    if (result == null) return null;
    do {
      PsiClass nextParent = PsiTreeUtil.getParentOfType(result, PsiClass.class, true);
      if (nextParent == null) return result;
      result = nextParent;
    }
    while (true);
  }

  public static List<MemberInfo> extractClassMethods(PsiClass clazz, boolean includeInherited) {
    List<MemberInfo> result = new ArrayList<MemberInfo>();

    do {
      MemberInfo.extractClassMembers(clazz, result, new MemberInfo.Filter<PsiMember>() {
        public boolean includeMember(PsiMember member) {
          if (!(member instanceof PsiMethod)) return false;
          PsiModifierList list = member.getModifierList();
          return !list.hasModifierProperty(PsiModifier.PRIVATE);
        }
      }, false);
      clazz = clazz.getSuperClass();
    }
    while (clazz != null
           && clazz.getSuperClass() != null // not the Object
           && includeInherited);

    return result;
  }

  public static void runTestMethodTemplate(MethodKind methodKind,
                                           TestFramework framework,
                                           final Editor editor,
                                           final PsiClass targetClass,
                                           final PsiMethod method,
                                           @jakarta.annotation.Nullable String name,
                                           boolean automatic) {
    Template template = createTestMethodTemplate(methodKind, framework, targetClass, name, automatic);

    final TextRange range = method.getTextRange();
    editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "");
    editor.getCaretModel().moveToOffset(range.getStartOffset());

    final Project project = targetClass.getProject();

    TemplateEditingAdapter adapter = null;

    if (!automatic) {
      adapter = new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
              PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
              PsiElement el = PsiTreeUtil.findElementOfClassAtOffset(psi, editor.getCaretModel().getOffset() - 1, PsiMethod.class, false);

              if (el != null) {
                PsiMethod method = PsiTreeUtil.getParentOfType(el, PsiMethod.class, false);
                if (method != null) {
                  if (method.findDeepestSuperMethods().length > 0) {
                    GenerateMembersUtil.setupGeneratedMethod(method);
                  }
                  CreateFromUsageUtils.setupEditor(method, editor);
                }
              }
            }
          });
        }
      };
    }

    TemplateManager.getInstance(project).startTemplate(editor, template, adapter);
  }

  private static Template createTestMethodTemplate(MethodKind methodKind,
                                                   TestFramework descriptor,
                                                   PsiClass targetClass,
                                                   @jakarta.annotation.Nullable String name,
                                                   boolean automatic) {
    FileTemplateDescriptor templateDesc = methodKind.getFileTemplateDescriptor(descriptor);
    String templateName = templateDesc.getFileName();
    FileTemplate fileTemplate = FileTemplateManager.getInstance(targetClass.getProject()).getCodeTemplate(templateName);
    Template template = TemplateManager.getInstance(targetClass.getProject()).createTemplate("", "");

    String templateText;
    try {
      templateText = fileTemplate.getText(new Properties());
    }
    catch (IOException e) {
      LOG.warn(e);
      templateText = fileTemplate.getText();
    }

    if (name == null) name = methodKind.getDefaultName();

    templateText = StringUtil.replace(templateText, "${BODY}", "");

    int from = 0;
    while (true) {
      int index = templateText.indexOf("${NAME}", from);
      if (index == -1) break;

      template.addTextSegment(templateText.substring(from, index));

      if (index > 0 && !Character.isWhitespace(templateText.charAt(index - 1))) {
        name = StringUtil.capitalize(name);
      }
      else {
        name = StringUtil.decapitalize(name);
      }
      if (from == 0) {
        Expression nameExpr = new ConstantNode(name);
        template.addVariable("name", nameExpr, nameExpr, !automatic);
      }
      else {
        template.addVariableSegment("name");
      }

      from = index + "${NAME}".length();
    }
    template.addTextSegment(templateText.substring(from, templateText.length()));

    template.setToIndent(true);
    template.setToReformat(true);
    template.setToShortenLongNames(true);

    return template;
  }

  public static PsiMethod createDummyMethod(@Nonnull PsiElement context) {
    JVMElementFactory factory = JVMElementFactories.getFactory(context.getLanguage(), context.getProject());
    if (factory == null) factory = JavaPsiFacade.getElementFactory(context.getProject());
    return factory.createMethod("dummy", PsiType.VOID);
  }

  public static List<TestFramework> findSuitableFrameworks(PsiClass targetClass) {
    for (TestFramework each : TestFramework.EXTENSION_NAME.getExtensionList()) {
      if (each.isTestClass(targetClass)) {
        return Collections.singletonList(each);
      }
    }

    List<TestFramework> result = new ArrayList<>();
    for (TestFramework each : TestFramework.EXTENSION_NAME.getExtensionList()) {
      if (each.isPotentialTestClass(targetClass)) {
        result.add(each);
      }
    }
    return result;
  }

  private TestIntegrationUtils() {
  }
}
