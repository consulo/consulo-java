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
package com.intellij.java.impl.psi.util.proximity;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.util.NotNullFunction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.proximity.ProximityLocation;
import consulo.language.util.proximity.ProximityWeigher;
import consulo.util.dataholder.NotNullLazyKey;
import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
@ExtensionImpl(id = "samePsiMember", order = "before explicitlyImported")
public class SamePsiMemberWeigher extends ProximityWeigher {
  private static final NotNullLazyKey<Boolean, ProximityLocation> INSIDE_PSI_MEMBER = NotNullLazyKey.create("insidePsiMember", new NotNullFunction<ProximityLocation, Boolean>() {
    @Override
    @Nonnull
    public Boolean apply(ProximityLocation proximityLocation) {
      return PsiTreeUtil.getContextOfType(proximityLocation.getPosition(), PsiMember.class, false) != null;
    }
  });
  private static final NotNullLazyKey<PsiElement, ProximityLocation> PHYSICAL_POSITION = NotNullLazyKey.create("physicalPosition", new NotNullFunction<ProximityLocation, PsiElement>() {
    @Override
    @Nonnull
    public PsiElement apply(ProximityLocation location) {
      PsiElement position = location.getPosition();
      assert position != null;
      if (!position.isPhysical()) {
        PsiFile file = position.getContainingFile();
        if (file != null) {
          PsiFile originalFile = file.getOriginalFile();
          int offset = position.getTextRange().getStartOffset();
          PsiElement candidate = originalFile.findElementAt(offset);
          if (candidate == null) {
            candidate = originalFile.findElementAt(offset - 1);
          }
          if (candidate != null) {
            return candidate;
          }
        }
      }
      return position;
    }
  });

  @Override
  public Comparable weigh(@Nonnull PsiElement element, @Nonnull ProximityLocation location) {
    PsiElement position = location.getPosition();
    if (position == null) {
      return null;
    }
    if (!INSIDE_PSI_MEMBER.getValue(location)) {
      return 0;
    }
    if (element.isPhysical()) {
      position = PHYSICAL_POSITION.getValue(location);
    }

    PsiMember member = PsiTreeUtil.getContextOfType(PsiTreeUtil.findCommonContext(position, element), PsiMember.class, false);
    if (member instanceof PsiClass) {
      return 1;
    }
    if (member != null) {
      return 2;
    }
    return 0;
  }

}
