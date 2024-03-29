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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 15, 2001
 * Time: 5:17:38 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.analysis.impl.codeInspection.reference;

import com.intellij.java.analysis.codeInspection.reference.RefJavaManager;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.language.editor.impl.inspection.reference.RefEntityImpl;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefVisitor;
import consulo.ui.image.Image;


public class RefPackageImpl extends RefEntityImpl implements RefPackage {
  private final String myQualifiedName;

  public RefPackageImpl(String name, RefManager refManager) {
    super(getPackageSuffix(name), refManager);
    myQualifiedName = name;
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  private static String getPackageSuffix(String fullName) {
    int dotIndex = fullName.lastIndexOf('.');
    return (dotIndex >= 0) ? fullName.substring(dotIndex + 1) : fullName;
  }


  @Override
  public void accept(final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          ((RefJavaVisitor) visitor).visitPackage(RefPackageImpl.this);
        }
      });
    } else {
      super.accept(visitor);
    }
  }

  @Override
  public String getExternalName() {
    return getQualifiedName();
  }

  public static RefEntity packageFromFQName(final RefManager manager, final String name) {
    return manager.getExtension(RefJavaManager.MANAGER).getPackage(name);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Image getIcon(final boolean expanded) {
    return AllIcons.Nodes.Package;
  }
}
