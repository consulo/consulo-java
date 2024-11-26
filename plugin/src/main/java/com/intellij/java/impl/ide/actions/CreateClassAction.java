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

package com.intellij.java.impl.ide.actions;

import com.intellij.java.impl.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.java.language.JavaCoreBundle;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiNameHelper;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.AllIcons;
import consulo.application.dumb.DumbAware;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.action.CreateFileFromTemplateDialog;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.module.extension.ModuleExtension;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.InputValidatorEx;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.Map;

/**
 * The standard "New Class" action.
 *
 * @since 5.1
 */
public class CreateClassAction extends JavaCreateTemplateInPackageAction<PsiClass> implements DumbAware {
  public CreateClassAction() {
    super(null, null, AllIcons.Nodes.Class, true);
  }

  @Override
  protected void buildDialog(final Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder.setTitle(JavaCoreBundle.message("action.create.new.class"));
    builder.addKind("Class", AllIcons.Nodes.Class, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME);
    builder.addKind("Interface", AllIcons.Nodes.Interface, JavaTemplateUtil.INTERNAL_INTERFACE_TEMPLATE_NAME);

    LanguageLevel level = PsiUtil.getLanguageLevel(directory);
    if (level.isAtLeast(LanguageLevel.JDK_16)) {
      builder.addKind("Record", PlatformIconGroup.nodesRecord(), JavaTemplateUtil.INTERNAL_RECORD_TEMPLATE_NAME);
    }
    if (level.isAtLeast(LanguageLevel.JDK_1_5)) {
      builder.addKind("Enum", AllIcons.Nodes.Enum, JavaTemplateUtil.INTERNAL_ENUM_TEMPLATE_NAME);
      builder.addKind("Annotation", AllIcons.Nodes.Annotationtype, JavaTemplateUtil.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME);
    }

    for (FileTemplate template : FileTemplateManager.getInstance(project).getAllTemplates()) {
      final JavaCreateFromTemplateHandler handler = new JavaCreateFromTemplateHandler();
      if (handler.handlesTemplate(template) && JavaCreateFromTemplateHandler.canCreate(directory)) {
        builder.addKind(template.getName(), JavaFileType.INSTANCE.getIcon(), template.getName());
      }
    }

    builder.setValidator(new InputValidatorEx() {
      @Override
      public String getErrorText(String inputString) {
        if (inputString.length() > 0 && !PsiNameHelper.getInstance(project).isQualifiedName(inputString)) {
          return "This is not a valid Java qualified name";
        }
        return null;
      }

      @RequiredUIAccess
      @Override
      public boolean checkInput(String inputString) {
        return true;
      }

      @RequiredUIAccess
      @Override
      public boolean canClose(String inputString) {
        return !StringUtil.isEmptyOrSpaces(inputString) && getErrorText(inputString) == null;
      }
    });
  }

  @Override
  protected Class<? extends ModuleExtension> getModuleExtensionClass() {
    return JavaModuleExtension.class;
  }

  @Override
  protected String getErrorTitle() {
    return JavaCoreBundle.message("title.cannot.create.class");
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return JavaCoreBundle.message("progress.creating.class", StringUtil.getQualifiedName(JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName(), newName));
  }

  @Override
  protected final PsiClass doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    return JavaDirectoryService.getInstance().createClass(dir, className, templateName, true);
  }

  @Override
  protected String removeExtension(String templateName, String className) {
    return StringUtil.trimEnd(className, ".java");
  }

  @Override
  protected PsiElement getNavigationElement(@Nonnull PsiClass createdElement) {
    return createdElement.getLBrace();
  }

  @Override
  protected void postProcess(PsiClass createdElement, String templateName, Map<String, String> customProperties) {
    super.postProcess(createdElement, templateName, customProperties);

    moveCaretAfterNameIdentifier(createdElement);
  }
}
