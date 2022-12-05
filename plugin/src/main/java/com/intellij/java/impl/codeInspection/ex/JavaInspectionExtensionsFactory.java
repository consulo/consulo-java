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
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.java.impl.codeInspection.ex;

import com.intellij.java.analysis.codeInspection.BatchSuppressManager;
import com.intellij.java.impl.codeInspection.reference.RefJavaManagerImpl;
import consulo.language.editor.impl.inspection.reference.RefManagerImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefManagerExtension;
import consulo.language.psi.PsiElement;
import consulo.project.Project;

import javax.annotation.Nullable;

public class JavaInspectionExtensionsFactory extends InspectionExtensionsFactory {

  @Override
  public GlobalInspectionContextExtension createGlobalInspectionContextExtension() {
    return new GlobalJavaInspectionContextImpl();
  }

  @Override
  public RefManagerExtension createRefManagerExtension(final RefManager refManager) {
    return new RefJavaManagerImpl((RefManagerImpl) refManager);
  }

  @Override
  public HTMLComposerExtension createHTMLComposerExtension(final HTMLComposer composer) {
    return new HTMLJavaHTMLComposerImpl((HTMLComposerImpl) composer);
  }

  @Override
  public boolean isToCheckMember(final PsiElement element, final String id) {
    return BatchSuppressManager.getInstance().getElementToolSuppressedIn(element, id) == null;
  }

  @Override
  @Nullable
  public String getSuppressedInspectionIdsIn(final PsiElement element) {
    return BatchSuppressManager.getInstance().getSuppressedInspectionIdsIn(element);
  }

  @Override
  public boolean isProjectConfiguredToRunInspections(final Project project, final boolean online) {
    return GlobalJavaInspectionContextImpl.isInspectionsEnabled(online, project);
  }
}