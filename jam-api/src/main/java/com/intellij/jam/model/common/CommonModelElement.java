/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.jam.model.common;

import javax.annotation.Nonnull;

import com.intellij.jam.JamElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;

import javax.annotation.Nullable;

public interface CommonModelElement {
  boolean isValid();

  XmlTag getXmlTag();

  PsiManager getPsiManager();

  @Nullable
  Module getModule();

  @Nullable
  PsiElement getIdentifyingPsiElement();

  @Nullable
  PsiFile getContainingFile();

  abstract class PsiBase implements CommonModelElement {
    @Nonnull
    public abstract PsiElement getPsiElement();

    public boolean isValid() {
      return getPsiElement().isValid();
    }

    @Nullable
    public XmlTag getXmlTag() {
      return null;
    }

    public PsiManager getPsiManager() {
      return getPsiElement().getManager();
    }

    public Module getModule() {
      return ModuleUtil.findModuleForPsiElement(getPsiElement());
    }

    public PsiElement getIdentifyingPsiElement() {
      return getPsiElement();
    }

    public PsiFile getContainingFile() {
      return getPsiElement().getContainingFile();
    }
  }

  abstract class ModuleBase implements CommonModelElement{

    @Nonnull
    public abstract Module getModule();

    public boolean isValid() {
      return !getModule().isDisposed();
    }

    @Nullable
    public XmlTag getXmlTag() {
      return null;
    }

    public PsiManager getPsiManager() {
      return PsiManager.getInstance(getModule().getProject());
    }

    public PsiElement getIdentifyingPsiElement() {
      return null;
    }

    public PsiFile getContainingFile() {
      return null;
    }
  }
  
  interface CommonModelNewJamElement extends CommonModelElement, JamElement {

  }
}
