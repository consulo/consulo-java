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
package com.intellij.psi.ref;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiChildLink;
import com.intellij.psi.PsiElementRef;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public class AnnotationChildLink extends PsiChildLink<PsiModifierListOwner, PsiAnnotation> {
  private final String myAnnoFqn;

  public AnnotationChildLink(String fqn) {
    myAnnoFqn = fqn;
  }

  public String getAnnotationQualifiedName() {
    return myAnnoFqn;
  }

  public static PsiElementRef<PsiAnnotation> createRef(@Nonnull PsiModifierListOwner parent, @NonNls String fqn) {
    return new AnnotationChildLink(fqn).createChildRef(parent);
  }

  @Override
  public PsiAnnotation findLinkedChild(@javax.annotation.Nullable PsiModifierListOwner member) {
    if (member == null) return null;

    final PsiModifierList modifierList = member.getModifierList();
    return modifierList != null ? modifierList.findAnnotation(myAnnoFqn) : null;
  }

  @Override
  @Nonnull
  public PsiAnnotation createChild(@Nonnull PsiModifierListOwner member) throws IncorrectOperationException {
    final PsiModifierList modifierList = member.getModifierList();
    assert modifierList != null;
    return modifierList.addAnnotation(myAnnoFqn);
  }

  @Override
  public String toString() {
    return "AnnotationChildLink{" + "myAnnoFqn='" + myAnnoFqn + '\'' + '}';
  }
}
