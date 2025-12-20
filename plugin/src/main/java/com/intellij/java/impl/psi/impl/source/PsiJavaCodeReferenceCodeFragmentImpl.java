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
package com.intellij.java.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.impl.ast.CompositeElement;
import consulo.logging.Logger;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class PsiJavaCodeReferenceCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiJavaCodeReferenceCodeFragment {
  private static final Logger LOG = Logger.getInstance(PsiJavaCodeReferenceCodeFragmentImpl.class);
  private final boolean myIsClassesAccepted;

  public PsiJavaCodeReferenceCodeFragmentImpl(Project project,
                                              boolean isPhysical,
                                              @NonNls String name,
                                              CharSequence text,
                                              boolean isClassesAccepted,
                                              @Nullable PsiElement context) {
    super(project, JavaElementType.REFERENCE_TEXT, isPhysical, name, text, context);
    myIsClassesAccepted = isClassesAccepted;
  }

  @Override
  public PsiJavaCodeReferenceElement getReferenceElement() {
    CompositeElement treeElement = calcTreeElement();
    LOG.assertTrue(treeElement.getFirstChildNode().getElementType() == JavaElementType.JAVA_CODE_REFERENCE);
    return (PsiJavaCodeReferenceElement) SourceTreeToPsiMap.treeElementToPsi(treeElement.getFirstChildNode());
  }

  @Override
  public boolean isClassesAccepted() {
    return myIsClassesAccepted;
  }
}
