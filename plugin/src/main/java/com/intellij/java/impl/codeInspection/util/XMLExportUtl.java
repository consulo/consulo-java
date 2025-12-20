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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 23, 2002
 * Time: 2:36:58 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInspection.util;

import com.intellij.java.analysis.codeInspection.reference.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.impl.inspection.reference.SmartRefElementPointerImpl;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefFile;
import consulo.language.editor.inspection.reference.RefModule;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.virtualFileSystem.VirtualFile;
import org.jdom.Element;

@SuppressWarnings({"HardCodedStringLiteral"})
@Deprecated
public class XMLExportUtl {
  private static final Logger LOG = Logger.getInstance(XMLExportUtl.class);

  private XMLExportUtl() {
  }

  @RequiredReadAction
  public static Element createElement(RefEntity refEntity, Element parentNode, int actualLine, TextRange range) {
    refEntity = refEntity.getRefManager().getRefinedElement(refEntity);

    Element problem = new Element("problem");

    if (refEntity instanceof RefElement refElement) {
      PsiElement psiElement = refElement.getElement();
      PsiFile psiFile = psiElement.getContainingFile();

      Element fileElement = new Element("file");
      Element lineElement = new Element("line");
      VirtualFile virtualFile = psiFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      fileElement.addContent(virtualFile.getUrl());

      if (actualLine == -1) {
        Document document = PsiDocumentManager.getInstance(refElement.getRefManager().getProject()).getDocument(psiFile);
        LOG.assertTrue(document != null);
        lineElement.addContent(String.valueOf(document.getLineNumber(psiElement.getTextOffset()) + 1));
      }
      else {
        lineElement.addContent(String.valueOf(actualLine));
      }

      problem.addContent(fileElement);
      problem.addContent(lineElement);
      appendModule(problem, refElement.getModule());
    }
    else if (refEntity instanceof RefModule refModule) {
      VirtualFile moduleDir = refModule.getModule().getModuleDir();
      Element fileElement = new Element("file");
      fileElement.addContent(moduleDir != null ? moduleDir.getUrl() : refEntity.getName());
      problem.addContent(fileElement);
      appendModule(problem, refModule);
      appendFakePackage(problem);
    } else if (refEntity instanceof RefPackage) {
      Element packageElement = new Element("package");
      packageElement.addContent(refEntity.getName());
      problem.addContent(packageElement);
    }

    new SmartRefElementPointerImpl(refEntity, true).writeExternal(problem);

    if (refEntity instanceof RefMethod refMethod) {
      appendMethod(refMethod, problem);
    }
    else if (refEntity instanceof RefField refField) {
      appendField(refField, problem);
    }
    else if (refEntity instanceof RefClass refClass) {
      appendClass(refClass, problem);
    } else if (refEntity instanceof RefFile) {
      appendFakePackage(problem);
    }
    parentNode.addContent(problem);

    return problem;
  }

  private static void appendModule(Element problem, RefModule refModule) {
    if (refModule != null) {
      Element moduleElement = new Element("module");
      moduleElement.addContent(refModule.getName());
      problem.addContent(moduleElement);
    }
  }

  private static void appendFakePackage(Element problem) {
    Element fakePackage = new Element("package");
    fakePackage.addContent(InspectionLocalize.inspectionExportResultsDefault().get());
    problem.addContent(fakePackage);
  }

  @RequiredReadAction
  private static void appendClass(RefClass refClass, Element parentNode) {
    PsiClass psiClass = refClass.getElement();
    PsiDocComment psiDocComment = psiClass.getDocComment();

    PsiFile psiFile = psiClass.getContainingFile();

    if (psiFile instanceof PsiJavaFile javaFile) {
      String packageName = javaFile.getPackageName();
      Element packageElement = new Element("package");
      packageElement.addContent(packageName.length() > 0 ? packageName : InspectionLocalize.inspectionExportResultsDefault().get());
      parentNode.addContent(packageElement);
    }

    Element classElement = new Element("class");
    if (psiDocComment != null) {
      PsiDocTag[] tags = psiDocComment.getTags();
      for (PsiDocTag tag : tags) {
        if ("author".equals(tag.getName()) && tag.getValueElement() != null) {
          classElement.setAttribute("author", tag.getValueElement().getText());
        }
      }
    }

    String name = PsiFormatUtil.formatClass(psiClass, PsiFormatUtil.SHOW_NAME);
    Element nameElement = new Element("name");
    nameElement.addContent(name);
    classElement.addContent(nameElement);

    Element displayName = new Element("display_name");
    displayName.addContent(refClass.getQualifiedName());
    classElement.addContent(displayName);

    parentNode.addContent(classElement);

    RefClass topClass = RefJavaUtil.getInstance().getTopLevelClass(refClass);
    if (topClass != refClass) {
      appendClass(topClass, classElement);
    }
  }

  @RequiredReadAction
  private static void appendMethod(RefMethod refMethod, Element parentNode) {
    Element methodElement = new Element(refMethod.isConstructor() ? "constructor" : "method");

    PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
    String name = PsiFormatUtil.formatMethod(
      psiMethod,
      PsiSubstitutor.EMPTY,
      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE
    );

    Element shortNameElement = new Element("name");
    shortNameElement.addContent(name);
    methodElement.addContent(shortNameElement);

    Element displayName = new Element("name");
    displayName.addContent(refMethod.getQualifiedName());
    methodElement.addContent(displayName);

    appendClass(RefJavaUtil.getInstance().getTopLevelClass(refMethod), methodElement);

    parentNode.addContent(methodElement);
  }

  @RequiredReadAction
  private static void appendField(RefField refField, Element parentNode) {
    Element fieldElement = new Element("field");
    PsiField psiField = refField.getElement();
    String name = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);

    Element shortNameElement = new Element("name");
    shortNameElement.addContent(name);
    fieldElement.addContent(shortNameElement);

    Element displayName = new Element("display_name");
    displayName.addContent(refField.getQualifiedName());
    fieldElement.addContent(displayName);

    appendClass(RefJavaUtil.getInstance().getTopLevelClass(refField), fieldElement);

    parentNode.addContent(fieldElement);
  }
}
