/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.QualifiedNameProvider;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaQualifiedNameProvider implements QualifiedNameProvider {
  private static final Logger LOG = Logger.getInstance(JavaQualifiedNameProvider.class);

  @Nullable
  public PsiElement adjustElementToCopy(final PsiElement element) {
    if (element != null && !(element instanceof PsiMember) && element.getParent() instanceof PsiMember) {
      return element.getParent();
    }
    return null;
  }

  @Nullable
  public String getQualifiedName(PsiElement element) {
    element = getMember(element);
    if (element instanceof PsiClass) {
      return ((PsiClass) element).getQualifiedName();
    } else if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember) element;
      PsiClass containingClass = member.getContainingClass();
      if (containingClass instanceof PsiAnonymousClass)
        containingClass = ((PsiAnonymousClass) containingClass).getBaseClassType().resolve();
      if (containingClass == null) return null;
      String classFqn = containingClass.getQualifiedName();
      if (classFqn == null) return member.getName();  // refer to member of anonymous class by simple name
      return classFqn + "#" + member.getName();
    }
    return null;
  }

  public PsiElement qualifiedNameToElement(final String fqn, final Project project) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    if (aClass != null) {
      return aClass;
    }
    final int endIndex = fqn.indexOf('#');
    if (endIndex != -1) {
      String className = fqn.substring(0, endIndex);
      if (className != null) {
        aClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (aClass != null) {
          String memberName = fqn.substring(endIndex + 1);
          PsiField field = aClass.findFieldByName(memberName, false);
          if (field != null) {
            return field;
          }
          PsiMethod[] methods = aClass.findMethodsByName(memberName, false);
          if (methods.length != 0) {
            return methods[0];
          }
        }
      }
    }

    VirtualFile file = findFile(fqn, project);
    if (file != null) {
      return PsiManager.getInstance(project).findFile(file);
    }
    return null;
  }

  private static VirtualFile findFile(String fqn, Project project) {
    for (VirtualFile root : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
      VirtualFile rel = root.findFileByRelativePath(fqn);
      if (rel != null) {
        return rel;
      }
    }
    for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
      VirtualFile rel = root.findFileByRelativePath(fqn);
      if (rel != null) {
        return rel;
      }
    }
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fqn);
    if (file != null) return file;
    PsiFile[] files = PsiShortNamesCache.getInstance(project).getFilesByName(fqn);
    for (PsiFile psiFile : files) {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) return virtualFile;
    }
    return null;
  }

  public void insertQualifiedName(String fqn, final PsiElement element, final Editor editor, final Project project) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();

    final PsiFile file = documentManager.getPsiFile(document);

    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);

    fqn = fqn.replace('#', '.');
    String toInsert;
    String suffix = "";

    if (!(element instanceof PsiMember)) {
      toInsert = fqn;
    } else if (elementAtCaret != null && (element instanceof PsiMethod || element instanceof PsiField) && PsiUtil.isInsideJavadocComment(elementAtCaret)) {
      // use fqn#methodName(ParamType)
      PsiMember member = (PsiMember) element;
      PsiClass aClass = member.getContainingClass();
      String className = aClass == null ? "" : aClass.getQualifiedName();
      toInsert = className == null ? "" : className;
      if (toInsert.length() != 0) toInsert += "#";
      toInsert += member.getName();
      if (member instanceof PsiMethod) {
        toInsert += "(";
        PsiParameter[] parameters = ((PsiMethod) member).getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          if (i != 0) toInsert += ", ";
          toInsert += parameter.getType().getCanonicalText();
        }
        toInsert += ")";
      }
    } else if (elementAtCaret == null ||
        PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiLiteralExpression.class, PsiComment.class) != null ||
        PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiJavaFile.class) == null) {
      toInsert = fqn;
    } else {
      PsiMember targetElement = (PsiMember) element;

      toInsert = targetElement.getName();
      if (targetElement instanceof PsiMethod) {
        suffix = "()";
        if (((PsiMethod) targetElement).isConstructor()) {
          targetElement = targetElement.getContainingClass();
        }
      } else if (targetElement instanceof PsiClass) {
        if (isAfterNew(file, elementAtCaret)) {
          // pasting reference to default constructor of the class after new
          suffix = "()";
        } else if (toInsert != null && toInsert.length() != 0 && Character.isJavaIdentifierPart(toInsert.charAt(toInsert.length() - 1)) && Character.isJavaIdentifierPart(elementAtCaret.getText().charAt(0))) {
          //separate identifiers with space
          suffix = " ";
        }
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiExpression expression;
      try {
        expression = factory.createExpressionFromText(toInsert + suffix, elementAtCaret);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }
      final PsiReferenceExpression referenceExpression = expression instanceof PsiMethodCallExpression
          ? ((PsiMethodCallExpression) expression).getMethodExpression()
          : expression instanceof PsiReferenceExpression
          ? (PsiReferenceExpression) expression
          : null;
      if (referenceExpression == null || !referenceExpression.isValid()) {
        toInsert = fqn;
      } else if (!isReferencedTo(referenceExpression, targetElement)) {
        try {
          referenceExpression.bindToElement(targetElement);
        } catch (IncorrectOperationException e) {
          // failed to bind
        }
        if (referenceExpression.isValid() && !isReferencedTo(referenceExpression, targetElement)) {
          toInsert = fqn;
        }
      }
    }
    if (toInsert == null) toInsert = "";

    document.insertString(offset, toInsert + suffix);
    documentManager.commitAllDocuments();
    int endOffset = offset + toInsert.length() + suffix.length();
    RangeMarker rangeMarker = document.createRangeMarker(endOffset, endOffset);
    elementAtCaret = file.findElementAt(offset);

    if (elementAtCaret != null && elementAtCaret.isValid()) {
      try {
        shortenReference(elementAtCaret, element);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(file);
    try {
      CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    int caretOffset = rangeMarker.getEndOffset();
    if (element instanceof PsiMethod && ((PsiMethod) element).getParameterList().getParametersCount() != 0 && StringUtil.endsWithChar(suffix, ')')) {
      caretOffset--;
    }
    editor.getCaretModel().moveToOffset(caretOffset);
  }

  private static boolean isReferencedTo(PsiReferenceExpression referenceExpression, PsiMember targetElement) {
    PsiElement resolved = referenceExpression.advancedResolve(true).getElement();
    if (!(resolved instanceof PsiMember)) return false;
    PsiClass aClass = ((PsiMember) resolved).getContainingClass();
    if (aClass instanceof PsiAnonymousClass) aClass = ((PsiAnonymousClass) aClass).getBaseClassType().resolve();
    return aClass == targetElement.getContainingClass();
  }

  @Nullable
  private static PsiElement getMember(final PsiElement element) {
    if (element instanceof PsiMember) return element;
    if (element instanceof PsiReference) {
      PsiElement resolved = ((PsiReference) element).resolve();
      if (resolved instanceof PsiMember) return resolved;
    }
    if (!(element instanceof PsiIdentifier)) return null;
    final PsiElement parent = element.getParent();
    PsiMember member = null;
    if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement) parent).resolve();
      if (resolved instanceof PsiMember) {
        member = (PsiMember) resolved;
      }
    } else if (parent instanceof PsiMember) {
      member = (PsiMember) parent;
    } else {
      //todo show error
      //return;
    }
    return member;
  }

  private static boolean isAfterNew(PsiFile file, PsiElement elementAtCaret) {
    PsiElement prevSibling = elementAtCaret.getPrevSibling();
    if (prevSibling == null) return false;
    int offset = prevSibling.getTextRange().getStartOffset();
    PsiElement prevElement = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(prevElement, PsiNewExpression.class) != null;
  }

  private static void shortenReference(PsiElement element, PsiElement elementToInsert) throws IncorrectOperationException {
    while (element.getParent() instanceof PsiJavaCodeReferenceElement) {
      element = element.getParent();
      if (element == null) return;
    }
    //if (element instanceof PsiJavaCodeReferenceElement && elementToInsert != null) {
    //  try {
    //    element = ((PsiJavaCodeReferenceElement)element).bindToElement(elementToInsert);
    //  }
    //  catch (IncorrectOperationException e) {
    //    // failed to bind
    //  }
    //}
    final JavaCodeStyleManager codeStyleManagerEx = JavaCodeStyleManager.getInstance(element.getProject());
    codeStyleManagerEx.shortenClassReferences(element, JavaCodeStyleManager.INCOMPLETE_CODE);
  }
}
