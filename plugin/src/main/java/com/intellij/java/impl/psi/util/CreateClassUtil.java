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
package com.intellij.java.impl.psi.util;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import jakarta.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import com.intellij.java.impl.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.impl.codeInsight.PackageUtil;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.PsiManager;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.text.StringTokenizer;

/**
 * author: lesya
 */
public class CreateClassUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.CreateClassUtil");

  @NonNls public static final String DEFAULT_CLASS_TEMPLATE = "#DEFAULT_CLASS_TEMPLATE";
  @NonNls private static final String DO_NOT_CREATE_CLASS_TEMPLATE = "#DO_NOT_CREATE_CLASS_TEMPLATE";
  @NonNls private static final String CLASS_NAME_PROPERTY = "Class_Name";
  @NonNls private static final String INTERFACE_NAME_PROPERTY = "Interface_Name";

  private CreateClassUtil() {}

  @Nullable
  private static PsiClass createClassFromTemplate(@Nonnull final Properties attributes, @Nullable final String templateName,
                                                  @Nonnull final PsiDirectory directoryRoot,
                                                  @Nonnull final String className) throws IncorrectOperationException {
    if (templateName == null) return null;
    if (templateName.equals(DO_NOT_CREATE_CLASS_TEMPLATE)) return null;

    final Project project = directoryRoot.getProject();
    try {
      final PsiDirectory directory = createParentDirectories(directoryRoot, className);
      final PsiFile psiFile = directory.findFile(className + "." + JavaFileType.INSTANCE.getDefaultExtension());
      if (psiFile != null) {
        psiFile.delete();
      }

      final String rawClassName = extractClassName(className);
      final PsiFile existing = directory.findFile(rawClassName + ".java");
      if (existing instanceof PsiJavaFile) {
        final PsiClass[] classes = ((PsiJavaFile)existing).getClasses();
        if (classes.length > 0) {
          return classes[0];
        }
        return null;
      }

      final PsiClass aClass;
      if (templateName.equals(DEFAULT_CLASS_TEMPLATE)) {
        aClass = JavaDirectoryService.getInstance().createClass(directory, rawClassName);
      }
      else {
        final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(project);
        FileTemplate fileTemplate = fileTemplateManager.getJ2eeTemplate(templateName);
        LOG.assertTrue(fileTemplate != null, templateName + " not found");
        final String text = fileTemplate.getText(attributes);
        aClass = JavaCreateFromTemplateHandler.createClassOrInterface(project, directory, text, true, fileTemplate.getExtension());
      }
      return (PsiClass)JavaCodeStyleManager.getInstance(project).shortenClassReferences(aClass);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(), (Throwable) e);
    }
  }

  @Nonnull
  private static PsiDirectory createParentDirectories(@Nonnull PsiDirectory directoryRoot, @Nonnull String className) throws IncorrectOperationException {
    final PsiJavaPackage currentPackage = JavaDirectoryService.getInstance().getPackage(directoryRoot);
    final String packagePrefix = currentPackage == null? null : currentPackage.getQualifiedName() + ".";
    final String packageName = extractPackage(packagePrefix != null && className.startsWith(packagePrefix)?
                                              className.substring(packagePrefix.length()) : className);
    final StringTokenizer tokenizer = new StringTokenizer(packageName, ".");
    while (tokenizer.hasMoreTokens()) {
      String packagePart = tokenizer.nextToken();
      PsiDirectory subdirectory = directoryRoot.findSubdirectory(packagePart);
      if (subdirectory == null) {
        directoryRoot.checkCreateSubdirectory(packagePart);
        subdirectory = directoryRoot.createSubdirectory(packagePart);
      }
      directoryRoot = subdirectory;
    }
    return directoryRoot;
  }

  @Nullable
  public static PsiDirectory getRoot(Module module, String className) {
    String aPackage = extractPackage(className);
    PsiManager psiManager = PsiManager.getInstance(module.getProject());
    PsiJavaPackage psiPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(aPackage);
    if (psiPackage == null) return null;
    PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
    if (directories.length == 0) return null;

    return directories[0];
  }

  public static String extractClassName(String fqName) {
    return StringUtil.getShortName(fqName);
  }

  public static String extractPackage(String fqName) {
    int i = fqName.lastIndexOf('.');
    return i == -1 ? "" : fqName.substring(0, i);
  }

  public static String makeFQName(String aPackage, String className) {
    String fq = aPackage;
    if (!"".equals(aPackage)) {
      fq += ".";
    }
    fq += className;
    return fq;
  }

  @Nullable
  public static PsiClass createClassNamed(String newClassName, String templateName, @Nonnull PsiDirectory directory) throws IncorrectOperationException {
    return createClassNamed(newClassName, FileTemplateManager.getInstance(directory.getProject()).getDefaultProperties(), templateName, directory);
  }

  @Nullable
  public static PsiClass createClassNamed(String newClassName, Map classProperties, String templateName, @Nonnull PsiDirectory directory)
    throws IncorrectOperationException {
    Properties defaultProperties = FileTemplateManager.getInstance(directory.getProject()).getDefaultProperties();
    Properties properties = new Properties(defaultProperties);
    properties.putAll(classProperties);

    return createClassNamed(newClassName, properties, templateName, directory);
  }

  @Nullable
  private static PsiClass createClassNamed(@Nullable String newClassName,
                                           @Nonnull Properties properties,
                                           String templateName,
                                           @Nonnull PsiDirectory directory) throws IncorrectOperationException {
    if (newClassName == null) {
      return null;
    }
    final String className = extractClassName(newClassName);
    properties.setProperty(CLASS_NAME_PROPERTY, className);
    properties.setProperty(INTERFACE_NAME_PROPERTY, className);

    return createClassFromTemplate(properties, templateName, directory, newClassName);
  }

  @Nullable
  public static PsiClass createClassFromCustomTemplate(@Nullable PsiDirectory classDirectory,
                                                       @Nullable final Module module,
                                                       final String className,
                                                       final String templateName) {
    if (classDirectory == null && module != null) {
      try {
        classDirectory = PackageUtil.findOrCreateDirectoryForPackage(module, "", null, false);
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }
    if (classDirectory == null) {
      return null;
    }
    try {
      final Properties properties = ApplicationManager.getApplication().isUnitTestMode() ?
                                    new Properties() :
                                    FileTemplateManager.getInstance(classDirectory.getProject()).getDefaultProperties();
      return createClassNamed(className, new Properties(properties), templateName, classDirectory);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }
}
