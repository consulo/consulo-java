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
package com.intellij.java.impl.codeInspection.reference;

import com.intellij.java.analysis.codeInspection.reference.RefJavaManager;
import com.intellij.java.analysis.impl.codeInspection.reference.RefPackageImpl;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.editor.impl.inspection.reference.RefFileImpl;
import consulo.language.editor.inspection.reference.RefManager;

public class RefJavaFileImpl extends RefFileImpl {
  public RefJavaFileImpl(PsiJavaFile elem, RefManager manager) {
    super(elem, manager);
    ((RefPackageImpl)getRefManager().getExtension(RefJavaManager.MANAGER).getPackage(elem.getPackageName())).add(this);
  }
}