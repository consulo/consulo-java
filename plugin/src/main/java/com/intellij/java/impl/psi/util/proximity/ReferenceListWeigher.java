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
package com.intellij.java.impl.psi.util.proximity;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.module.content.ProjectFileIndex;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.ide.impl.psi.util.ProximityLocation;
import consulo.ide.impl.psi.util.proximity.ProximityWeigher;
import consulo.java.language.module.util.JavaClassNames;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.intellij.java.impl.psi.util.proximity.ReferenceListWeigher.ReferenceListApplicability.*;

/**
 * @author peter
 */
@ExtensionImpl(id = "referenceList", order = "before samePsiMember")
public class ReferenceListWeigher extends ProximityWeigher {
  public static final ReferenceListWeigher INSTANCE = new ReferenceListWeigher();

  public static final ElementPattern<PsiElement> INSIDE_REFERENCE_LIST = PlatformPatterns.psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiReferenceList.class);

  protected enum Preference {
    Interfaces,
    Classes,
    Exceptions
  }

  @Nullable
  protected Preference getPreferredCondition(@Nonnull final PsiElement position) {
    if (INSIDE_REFERENCE_LIST.accepts(position)) {
      PsiReferenceList list = (PsiReferenceList) position.getParent().getParent();
      PsiReferenceList.Role role = list.getRole();
      if (shouldContainInterfaces(list, role)) {
        return Preference.Interfaces;
      }
      if (role == PsiReferenceList.Role.EXTENDS_LIST) {
        return Preference.Classes;
      }
      if (role == PsiReferenceList.Role.THROWS_LIST) {
        return Preference.Exceptions;

      }
    }
    return null;
  }

  private static boolean shouldContainInterfaces(PsiReferenceList list, PsiReferenceList.Role role) {
    if (role == PsiReferenceList.Role.EXTENDS_LIST) {
      PsiElement parent = list.getParent();
      return parent instanceof PsiClass && ((PsiClass) parent).isInterface();
    }
    if (role == PsiReferenceList.Role.IMPLEMENTS_LIST) {
      return true;
    }
    return false;
  }

  public enum ReferenceListApplicability {
    inapplicable,
    unknown,
    applicableByKind,
    applicableByName
  }

  @Override
  public ReferenceListApplicability weigh(@Nonnull PsiElement element, @Nonnull ProximityLocation location) {
    if (element instanceof PsiClass && location.getPosition() != null) {
      return getApplicability((PsiClass) element, location.getPosition());
    }
    return unknown;
  }

  @Nonnull
  public ReferenceListApplicability getApplicability(@Nonnull PsiClass aClass, @Nonnull PsiElement position) {
    Preference condition = getPreferredCondition(position);
    if (condition == Preference.Interfaces) {
      return aClass.isInterface() ? applicableByKind : inapplicable;
    }
    if (condition == Preference.Classes) {
      if (aClass.isInterface()) {
        return inapplicable;
      }
      String name = aClass.getName();
      if (name != null && name.endsWith("TestCase")) {
        VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
        if (vFile != null && ProjectFileIndex.SERVICE.getInstance(aClass.getProject()).isInTestSourceContent(vFile)) {
          return applicableByName;
        }
      }
      return applicableByKind;
    }
    if (condition == Preference.Exceptions) {
      return InheritanceUtil.isInheritor(aClass, JavaClassNames.JAVA_LANG_THROWABLE) ? applicableByKind : inapplicable;
    }
    return unknown;
  }
}
