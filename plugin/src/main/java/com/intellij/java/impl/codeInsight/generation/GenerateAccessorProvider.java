package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.impl.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface GenerateAccessorProvider {
  @Nonnull
  @RequiredReadAction
  Collection<EncapsulatableClassMember> getEncapsulatableClassMembers(PsiClass psiClass);
}
