// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.application.dumb.IndexNotReadyException;
import consulo.application.util.registry.Registry;
import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.fileEditor.structureView.tree.SortableTreeElement;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntheticElement;
import consulo.project.DumbService;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.java.language.psi.util.PsiFormatUtilBase.*;

public class PsiMethodTreeElement extends JavaClassTreeElementBase<PsiMethod> implements SortableTreeElement {
  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited, method);
  }

  @Override
  @Nonnull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final List<StructureViewTreeElement> emptyResult = Collections.emptyList();
    final PsiMethod element = getElement();
    if (element == null || element instanceof SyntheticElement || element instanceof LightElement) {
      return emptyResult;
    }

    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null || psiFile instanceof PsiCompiledElement) {
      return emptyResult;
    }

    final ArrayList<StructureViewTreeElement> result = new ArrayList<>();

    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass) && !(aClass instanceof PsiTypeParameter)) {
          result.add(new JavaClassTreeElement(aClass, isInherited()));
        }
      }
    });
    return result;
  }

  @Override
  public String getPresentableText() {
    final PsiMethod psiMethod = getElement();
    if (psiMethod == null) {
      return "";
    }
    final boolean dumb = DumbService.isDumb(psiMethod.getProject());
    String method = PsiFormatUtil.formatMethod(psiMethod,
        PsiSubstitutor.EMPTY,
        SHOW_NAME | TYPE_AFTER | SHOW_PARAMETERS | (dumb ? 0 : SHOW_TYPE),
        dumb ? SHOW_NAME : SHOW_TYPE);
    return StringUtil.replace(method, ":", ": ");
  }


  @Override
  public String getLocationString() {
    if (!Registry.is("show.method.base.class.in.java.file.structure")) {
      return null;
    }
    final PsiMethod method = getElement();
    if (myLocation == null && method != null && !DumbService.isDumb(method.getProject())) {
      if (isInherited()) {
        return super.getLocationString();
      } else {
        try {
          final MethodSignatureBackedByPsiMethod baseMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
          if (baseMethod != null && !method.isEquivalentTo(baseMethod.getMethod())) {
            PsiMethod base = baseMethod.getMethod();
            PsiClass baseClass = base.getContainingClass();
            if (baseClass != null /*&& !CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName())*/) {
              if (baseClass.getMethods().length > 1) {
                myLocation = baseClass.getName();
              }
            }
          }
        } catch (IndexNotReadyException e) {
          //some searchers (EJB) require indices. What shall we do?
        }

        if (StringUtil.isEmpty(myLocation)) {
          myLocation = "";
        } else {
          char upArrow = '\u2191';
          myLocation = UIUtil.getLabelFont().canDisplay(upArrow) ? upArrow + myLocation : myLocation;
        }
      }
    }
    return StringUtil.isEmpty(myLocation) ? null : myLocation;
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    if (isInherited()) {
      return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    }
    return super.getTextAttributesKey();
  }

  public PsiMethod getMethod() {
    return getElement();
  }

  @Nonnull
  @Override
  public String getAlphaSortKey() {
    final PsiMethod method = getElement();
    if (method != null) {
      return method.getName() + " " + StringUtil.join(method.getParameterList().getParameters(), psiParameter -> {
        PsiTypeElement typeElement = psiParameter.getTypeElement();
        return typeElement != null ? typeElement.getText() : "";
      }, " ");
    }
    return "";
  }

  @Override
  public String getLocationPrefix() {
    return " ";
  }

  @Override
  public String getLocationSuffix() {
    return "";
  }
}
