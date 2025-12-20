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
 * User: anna
 * Date: 21-Dec-2007
 */
package com.intellij.java.impl.codeInspection.ex;

import com.intellij.java.analysis.codeInspection.reference.*;
import com.intellij.java.impl.codeInspection.HTMLJavaHTMLComposer;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.impl.inspection.reference.RefElementImpl;
import consulo.language.editor.inspection.HTMLComposer;
import consulo.language.editor.inspection.HTMLComposerBase;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefFile;
import consulo.language.psi.PsiFile;
import consulo.util.lang.xml.XmlStringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static consulo.language.editor.inspection.HTMLComposerBase.*;

public class HTMLJavaHTMLComposerImpl extends HTMLJavaHTMLComposer {
  private final HTMLComposerBase myComposer;

  public HTMLJavaHTMLComposerImpl(HTMLComposerBase composer) {
    myComposer = composer;
  }

  @Override
  public void appendClassOrInterface(StringBuffer buf, RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      buf.append(
        capitalizeFirstLetter
          ? InspectionLocalize.inspectionExportResultsCapitalizedInterface()
          : InspectionLocalize.inspectionExportResultsInterface()
      );
    } else if (refClass.isAbstract()) {
      buf.append(
        capitalizeFirstLetter
          ? InspectionLocalize.inspectionExportResultsCapitalizedAbstractClass()
          : InspectionLocalize.inspectionExportResultsAbstractClass()
      );
    } else {
      buf.append(
        capitalizeFirstLetter
          ? InspectionLocalize.inspectionExportResultsCapitalizedClass()
          : InspectionLocalize.inspectionExportResultsClass()
      );
    }
  }

  @Override
  public void appendClassExtendsImplements(StringBuffer buf, RefClass refClass) {
    if (refClass.getBaseClasses().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionLocalize.inspectionExportResultsExtendsImplements().get());
      myComposer.startList(buf);
      for (RefClass refBase : refClass.getBaseClasses()) {
        myComposer.appendListItem(buf, refBase);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendDerivedClasses(StringBuffer buf, RefClass refClass) {
    if (refClass.getSubClasses().size() > 0) {
      HTMLComposer.appendHeading(
        buf,
        refClass.isInterface()
          ? InspectionLocalize.inspectionExportResultsExtendedImplemented().get()
          : InspectionLocalize.inspectionExportResultsExtended().get()
      );

      myComposer.startList(buf);
      for (RefClass refDerived : refClass.getSubClasses()) {
        myComposer.appendListItem(buf, refDerived);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendLibraryMethods(StringBuffer buf, RefClass refClass) {
    if (refClass.getLibraryMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionLocalize.inspectionExportResultsOverridesLibraryMethods().get());

      myComposer.startList(buf);
      for (RefMethod refMethod : refClass.getLibraryMethods()) {
        myComposer.appendListItem(buf, refMethod);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendSuperMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getSuperMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionLocalize.inspectionExportResultsOverridesImplements().get());

      myComposer.startList(buf);
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        myComposer.appendListItem(buf, refSuper);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendDerivedMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getDerivedMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionLocalize.inspectionExportResultsDerivedMethods().get());

      myComposer.startList(buf);
      for (RefMethod refDerived : refMethod.getDerivedMethods()) {
        myComposer.appendListItem(buf, refDerived);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendTypeReferences(StringBuffer buf, RefClass refClass) {
    if (refClass.getInTypeReferences().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionLocalize.inspectionExportResultsTypeReferences().get());

      myComposer.startList(buf);
      for (RefElement refElement : refClass.getInTypeReferences()) {
        myComposer.appendListItem(buf, refElement);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendShortName(RefEntity refElement, final StringBuffer buf) {
    if (refElement instanceof RefJavaElement refJavaElement) {
      String modifier = refJavaElement.getAccessModifier();
      if (modifier != null && modifier != PsiModifier.PACKAGE_LOCAL) {
        buf.append(modifier).append(NBSP);
      }
    }
    refElement.accept(new RefJavaVisitor() {
      @Override
      public void visitClass(@Nonnull RefClass refClass) {
        if (refClass.isStatic()) {
          buf.append(InspectionLocalize.inspectionExportResultsStatic());
          buf.append(NBSP);
        }

        appendClassOrInterface(buf, refClass, false);
        buf.append(NBSP).append(HTMLComposerBase.B_OPENING).append(CODE_OPENING);
        String name = refClass.getName();
        buf.append(refClass.isSyntheticJSP() ? XmlStringUtil.escapeString(name) : name);
        buf.append(CODE_CLOSING).append(HTMLComposerBase.B_CLOSING);
      }

      @Override
      public void visitField(@Nonnull RefField field) {
        PsiField psiField = field.getElement();
        if (psiField != null) {
          if (field.isStatic()) {
            buf.append(InspectionLocalize.inspectionExportResultsStatic()).append(NBSP);
          }

          buf.append(InspectionLocalize.inspectionExportResultsField());
          buf.append(NBSP).append(CODE_OPENING);

          buf.append(psiField.getType().getPresentableText());
          buf.append(NBSP).append(HTMLComposerBase.B_OPENING);
          buf.append(psiField.getName());
          buf.append(HTMLComposerBase.B_CLOSING).append(CODE_CLOSING);
        }
      }

      @Override
      public void visitMethod(@Nonnull RefMethod method) {
        PsiMethod psiMethod = (PsiMethod) method.getElement();
        if (psiMethod != null) {
          PsiType returnType = psiMethod.getReturnType();

          if (method.isStatic()) {
            buf.append(InspectionLocalize.inspectionExportResultsStatic()).append(NBSP);
          } else if (method.isAbstract()) {
            buf.append(InspectionLocalize.inspectionExportResultsAbstract().get()).append(NBSP);
          }
          buf.append(
            method.isConstructor()
              ? InspectionLocalize.inspectionExportResultsConstructor()
              : InspectionLocalize.inspectionExportResultsMethod()
          );
          buf.append(NBSP).append(CODE_OPENING);

          if (returnType != null) {
            buf.append(returnType.getPresentableText());
            buf.append(NBSP);
          }

          buf.append(HTMLComposerBase.B_OPENING);
          buf.append(psiMethod.getName());
          buf.append(HTMLComposerBase.B_CLOSING);
          appendMethodParameters(buf, psiMethod, true);
          buf.append(CODE_CLOSING);
        }
      }

      @Override
      @RequiredReadAction
      public void visitFile(@Nonnull RefFile file) {
        PsiFile psiFile = file.getElement();
        buf.append(B_OPENING).append(psiFile.getName()).append(B_CLOSING);
      }
    });
  }

  @Override
  public void appendLocation(RefEntity entity, StringBuffer buf) {
    RefEntity owner = entity.getOwner();
    if (owner instanceof RefPackage) {
      buf.append(InspectionLocalize.inspectionExportResultsPackage());
      buf.append(NBSP).append(CODE_OPENING);
      buf.append(RefJavaUtil.getInstance().getPackageName(entity));
      buf.append(CODE_CLOSING);
    } else if (owner instanceof RefMethod refMethod) {
      buf.append(InspectionLocalize.inspectionExportResultsMethod()).append(NBSP);
      myComposer.appendElementReference(buf, refMethod);
    } else if (owner instanceof RefField refField) {
      buf.append(InspectionLocalize.inspectionExportResultsField()).append(NBSP);
      myComposer.appendElementReference(buf, refField);
      buf.append(NBSP).append(InspectionLocalize.inspectionExportResultsInitializer());
    } else if (owner instanceof RefClass refClass) {
      appendClassOrInterface(buf, refClass, false);
      buf.append(NBSP);
      myComposer.appendElementReference(buf, refClass);
    }
  }

  @Override
  @Nullable
  public String getQualifiedName(RefEntity refEntity) {
    if (refEntity instanceof RefJavaElement refJavaElement && refJavaElement.isSyntheticJSP()) {
      return XmlStringUtil.escapeString(refEntity.getName());
    } else if (refEntity instanceof RefMethod refMethod) {
      PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
      return psiMethod != null ? psiMethod.getName() : refEntity.getName();
    }
    return null;
  }

  @Override
  public void appendReferencePresentation(RefEntity refElement, StringBuffer buf, boolean isPackageIncluded) {
    if (refElement instanceof RefImplicitConstructor implicitConstructor) {
      buf.append(InspectionLocalize.inspectionExportResultsImplicitConstructor().get());
      refElement = implicitConstructor.getOwnerClass();
    }

    buf.append(CODE_OPENING);

    if (refElement instanceof RefField field) {
      PsiField psiField = field.getElement();
      buf.append(psiField.getType().getPresentableText()).append(NBSP);
    } else if (refElement instanceof RefMethod method) {
      PsiMethod psiMethod = (PsiMethod) method.getElement();
      PsiType returnType = psiMethod.getReturnType();

      if (returnType != null) {
        buf.append(returnType.getPresentableText()).append(NBSP);
      }
    }

    buf.append(HTMLComposerBase.A_HREF_OPENING);

    if (myComposer.myExporter == null) {
      buf.append(((RefElementImpl) refElement).getURL());
    } else {
      buf.append(myComposer.myExporter.getURL(refElement));
    }

    buf.append("\">");

    if (refElement instanceof RefClass refClass && refClass.isAnonymous()) {
      buf.append(InspectionLocalize.inspectionReferenceAnonymous());
    } else if (refElement instanceof RefJavaElement javaElement && javaElement.isSyntheticJSP()) {
      buf.append(XmlStringUtil.escapeString(refElement.getName()));
    } else if (refElement instanceof RefMethod refMethod) {
      PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
      buf.append(psiMethod.getName());
    } else {
      buf.append(refElement.getName());
    }

    buf.append(HTMLComposerBase.A_CLOSING);

    if (refElement instanceof RefMethod refMethod) {
      PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
      appendMethodParameters(buf, psiMethod, false);
    }

    buf.append(CODE_CLOSING);

    if (refElement instanceof RefClass refClass && refClass.isAnonymous()) {
      buf.append(" ").append(InspectionLocalize.inspectionExportResultsAnonymousRefInOwner()).append(" ");
      myComposer.appendElementReference(buf, ((RefElement) refElement.getOwner()), isPackageIncluded);
    } else if (isPackageIncluded) {
      buf.append(" ").append(CODE_OPENING).append("(");
      myComposer.appendQualifiedName(buf, refElement.getOwner());
//      buf.append(RefUtil.getPackageName(refElement));
      buf.append(")").append(CODE_CLOSING);
    }
  }

  private static void appendMethodParameters(StringBuffer buf, PsiMethod method, boolean showNames) {
    PsiParameter[] params = method.getParameterList().getParameters();
    buf.append('(');
    for (int i = 0; i < params.length; i++) {
      if (i != 0) buf.append(", ");
      PsiParameter param = params[i];
      buf.append(param.getType().getPresentableText());
      if (showNames) {
        buf.append(' ').append(param.getName());
      }
    }
    buf.append(')');
  }
}
