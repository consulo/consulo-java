package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiField;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl
public class DefaultGenerateAccessorProvider implements GenerateAccessorProvider {
  @RequiredReadAction
  @Nonnull
  @Override
  public Collection<EncapsulatableClassMember> getEncapsulatableClassMembers(PsiClass s) {
    if (s.getLanguage() != JavaLanguage.INSTANCE) {
      return Collections.emptyList();
    }
    final List<EncapsulatableClassMember> result = new ArrayList<>();
    for (PsiField field : s.getFields()) {
      if (!(field instanceof PsiEnumConstant)) {
        result.add(new PsiFieldMember(field));
      }
    }
    return result;
  }
}
