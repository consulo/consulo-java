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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiManager;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class LightReferenceListBuilder extends LightElement implements PsiReferenceList {
  private final List<PsiJavaCodeReferenceElement> myRefs = new ArrayList<PsiJavaCodeReferenceElement>();
  private PsiJavaCodeReferenceElement[] myCachedRefs = null;
  private PsiClassType[] myCachedTypes = null;
  private final Role myRole;
  private final PsiElementFactory myFactory;

  public LightReferenceListBuilder(PsiManager manager, Role role) {
    this(manager, JavaLanguage.INSTANCE, role);
  }

  public LightReferenceListBuilder(PsiManager manager, Language language, Role role) {
    super(manager, language);
    myRole = role;
    myFactory = JavaPsiFacade.getElementFactory(getProject());
  }

  @Override
  public String toString() {
    return "light reference list";
  }

  public void addReference(PsiClass aClass) {
    addReference(aClass.getQualifiedName());
  }

  public void addReference(String qualifiedName) {
    final PsiJavaCodeReferenceElement ref = myFactory.createReferenceElementByFQClassName(qualifiedName, getResolveScope());
    myRefs.add(ref);
  }

  public void addReference(PsiClassType type) {
    final PsiClass resolved = type.resolve();
    if (resolved == null) return;

    final PsiJavaCodeReferenceElement ref = myFactory.createReferenceElementByType(type);
    myRefs.add(ref);
  }

  @Nonnull
  @Override
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    if (myCachedRefs == null) {
      if (myRefs.isEmpty()) {
        myCachedRefs = PsiJavaCodeReferenceElement.EMPTY_ARRAY;
      } else {
        myCachedRefs = myRefs.toArray(new PsiJavaCodeReferenceElement[myRefs.size()]);
      }
    }
    return myCachedRefs;
  }

  @Nonnull
  @Override
  public PsiClassType[] getReferencedTypes() {
    if (myCachedTypes == null) {
      if (myRefs.isEmpty()) {
        myCachedTypes = PsiClassType.EMPTY_ARRAY;
      } else {
        final int size = myRefs.size();
        myCachedTypes = new PsiClassType[size];
        for (int i = 0; i < size; i++) {
          myCachedTypes[i] = myFactory.createType(myRefs.get(i));
        }
      }
    }

    return myCachedTypes;
  }

  @Override
  public Role getRole() {
    return myRole;
  }
}
