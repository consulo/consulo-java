/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.jam.reflect;

import com.intellij.jam.JamElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class JamAnnotatedChildrenQuery<Jam extends JamElement> extends JamChildrenQuery<Jam> {
  private final String myAnnoName;

  public JamAnnotatedChildrenQuery(@NonNls JamAnnotationMeta meta) {
    myAnnoName = meta.getAnnoName();
  }

  @Nullable
  protected abstract JamMemberMeta<?, ? extends Jam> getMemberMeta(@Nonnull PsiModifierListOwner member);

  @Override
  public JamMemberMeta<?, ? extends Jam> getMeta(@Nonnull PsiModifierListOwner member) {
    final JamMemberMeta<?, ? extends Jam> memberMeta = getMemberMeta(member);
    return memberMeta != null && isAnnotated(member, myAnnoName) ? memberMeta : null;
  }

  public String getAnnoName() {
    return myAnnoName;
  }

  protected abstract PsiModifierListOwner[] getAllChildren(@Nonnull PsiMember parent);

  public List<Jam> findChildren(@Nonnull PsiMember parent) {
    final ArrayList<Jam> list = ContainerUtil.newArrayList();
    for (final PsiModifierListOwner child : getAllChildren(parent)) {
      if (isAnnotated(child, myAnnoName)) {
        final JamMemberMeta meta = getMemberMeta(child);
        if (meta != null) {
          ContainerUtil.addIfNotNull((Jam)meta.getJamElement(child), list);
        }
      }
    }
    return list;
  }
}
