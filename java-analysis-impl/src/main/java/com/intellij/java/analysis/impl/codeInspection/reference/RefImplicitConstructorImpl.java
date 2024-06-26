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
 * Date: Nov 28, 2001
 * Time: 4:17:17 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.analysis.impl.codeInspection.reference;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefImplicitConstructor;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.application.Application;
import consulo.application.util.function.Computable;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class RefImplicitConstructorImpl extends RefMethodImpl implements RefImplicitConstructor {

  RefImplicitConstructorImpl(RefClass ownerClass) {
    super(InspectionLocalize.inspectionReferenceImplicitConstructorName(ownerClass.getName()).get(), ownerClass);
  }

  @Override
  public void buildReferences() {
    getRefManager().fireBuildReferences(this);
  }

  @Override
  public boolean isSuspicious() {
    return ((RefClassImpl) getOwnerClass()).isSuspicious();
  }

  @Override
  @Nonnull
  public String getName() {
    return InspectionLocalize.inspectionReferenceImplicitConstructorName(getOwnerClass().getName()).get();
  }

  @Override
  public String getExternalName() {
    return getOwnerClass().getExternalName();
  }

  @Override
  public boolean isValid() {
    return Application.get().runReadAction((Computable<Boolean>)() -> getOwnerClass().isValid());
  }

  @Override
  public String getAccessModifier() {
    return getOwnerClass().getAccessModifier();
  }

  @Override
  public void setAccessModifier(String am) {
    RefJavaUtil.getInstance().setAccessModifier(getOwnerClass(), am);
  }

  @Override
  public PsiModifierListOwner getElement() {
    return getOwnerClass().getElement();
  }

  @Override
  @Nullable
  public PsiFile getContainingFile() {
    return getOwnerClass().getContainingFile();
  }

  @Override
  public RefClass getOwnerClass() {
    return myOwnerClass == null ? super.getOwnerClass() : myOwnerClass;
  }
}
