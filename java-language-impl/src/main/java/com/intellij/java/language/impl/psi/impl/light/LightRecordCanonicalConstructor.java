// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntheticElement;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

// Consulo LightMethod has no substitutor: the parameters use PsiSubstitutor.EMPTY, as JB LightMethod(manager, method, containingClass) does
public class LightRecordCanonicalConstructor extends LightMethod implements SyntheticElement {
    public LightRecordCanonicalConstructor(PsiMethod method, PsiClass containingClass) {
        super(method.getManager(), method, containingClass);
    }

    @Override
    public int getTextOffset() {
        return getNavigationElement().getTextOffset();
    }

    @Override
    public PsiElement getNavigationElement() {
        return getContainingClass();
    }

    @Override
    public PsiFile getContainingFile() {
        PsiClass containingClass = getContainingClass();
        return containingClass.getContainingFile();
    }

//	@Override
//	public Icon getElementIcon(final int flags) {
//		final RowIcon baseIcon =
//	        IconManager.getInstance().createLayeredIcon(this, PlatformIconGroup.nodesMethod(), ElementPresentationUtil.getFlags(this, false));
//		if(BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
//			VisibilityIcons.setVisibilityIcon(getContainingClass().getModifierList(), baseIcon);
//		}
//		return baseIcon;
//	}

    @Override
    public PsiElement getContext() {
        return getContainingClass();
    }


  @Override
  public PsiElement getParent() {
    return getContainingClass();
  }


  @Override
  public PsiIdentifier getNameIdentifier() {
    return getContainingClass().getNameIdentifier();
  }


  @Override
  public PsiParameterList getParameterList() {
    PsiParameterList parameterList = super.getParameterList();
    return new LightParameterListWrapper(parameterList, PsiSubstitutor.EMPTY) {
      @Override
      public PsiElement getParent() {
        return LightRecordCanonicalConstructor.this;
      }

      @Override
      public PsiFile getContainingFile() {
        return getParent().getContainingFile();
      }

      @Override
      public PsiParameter [] getParameters() {
        return ContainerUtil.map2Array(super.getParameters(), PsiParameter.class,
                                       p -> new LightRecordConstructorParameter(p, this, PsiSubstitutor.EMPTY));
      }
    };
  }


  @Override
  public PsiElement findSameElementInCopy(PsiFile copy) {
    PsiClass copyClass = PsiTreeUtil.findSameElementInCopy(myContainingClass, copy);
    return Objects.requireNonNull(JavaPsiRecordUtil.findCanonicalConstructor(copyClass));
  }


  public static class LightRecordConstructorParameter extends LightParameterWrapper {
    private final LightParameterListWrapper myWrapper;

    private LightRecordConstructorParameter(PsiParameter p,
                                            LightParameterListWrapper wrapper,
                                            PsiSubstitutor substitutor) {
      super(p, substitutor);
      myWrapper = wrapper;
    }

    @Override
    public PsiElement getDeclarationScope() {
      return myWrapper.getParent();
    }

    public @Nullable PsiRecordComponent getRecordComponent() {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(this, PsiClass.class);
      if (psiClass == null) return null;
      PsiRecordComponent[] recordComponents = psiClass.getRecordComponents();
      for (PsiRecordComponent recordComponent : recordComponents) {
        if (Objects.equals(recordComponent.getName(), this.getName())) {
          return recordComponent;
        }
      }
      return null;
    }

    @Override
    public @Nullable PsiModifierList getModifierList() {
      PsiRecordComponent recordComponent = getRecordComponent();
      if (recordComponent == null) return super.getModifierList();

      return new LightRecordComponentModifierList(this, getPrototype(), recordComponent);
    }

    @Override
    public PsiElement getNavigationElement() {
      PsiRecordComponent recordComponent = getRecordComponent();
      if (recordComponent != null) return recordComponent;
      return super.getNavigationElement();
    }

    @Override
    public PsiElement getParent() {
      return myWrapper;
    }


    @Override
    public PsiFile getContainingFile() {
      return getParent().getContainingFile();
    }

    @Override
    public PsiElement findSameElementInCopy(PsiFile copy) {
      PsiParameterList parameterList = (PsiParameterList)getParent();
      int index = parameterList.getParameterIndex(this);
      PsiClass recordClass = PsiTreeUtil.getParentOfType(this, PsiClass.class);
      PsiClass copyClass = PsiTreeUtil.findSameElementInCopy(recordClass, copy);
      assert copyClass != null;
      PsiMethod copyConstructor = JavaPsiRecordUtil.findCanonicalConstructor(copyClass);
      assert copyConstructor != null;
      PsiParameter copyParameter = copyConstructor.getParameterList().getParameter(index);
      assert copyParameter != null;
      return copyParameter;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LightRecordConstructorParameter parameter = (LightRecordConstructorParameter)o;

      return getPrototype().equals(parameter.getPrototype());
    }

    @Override
    public int hashCode() {
      return getPrototype().hashCode();
    }

    @Override
    public String toString() {
      return "LightRecordConstructorParameter(" + super.toString() + ")";
    }
  }


  @Override
  public String toString() {
    return "LightRecordCanonicalConstructor(" + super.toString() + ")";
  }
}
