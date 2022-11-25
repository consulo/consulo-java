
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
package com.intellij.testFramework;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nullable;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiReference;

public abstract class ResolveTestCase extends PsiTestCase {
  @NonNls protected static final String MARKER = "<ref>";

  protected PsiReference configureByFile(@NonNls String filePath) throws Exception{
    return configureByFile(filePath, null);
  }
  
  protected PsiReference configureByFile(@TestDataFile @NonNls String filePath, @Nullable VirtualFile parentDir) throws Exception{
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile));

    final String fileName = vFile.getName();

    return configureByFileText(fileText, fileName, parentDir);
  }

  protected PsiReference configureByFileText(String fileText, String fileName) throws Exception {
    return configureByFileText(fileText, fileName, null);
  }
  
  protected PsiReference configureByFileText(String fileText, String fileName, @Nullable final VirtualFile parentDir) throws Exception {
    int offset = fileText.indexOf(MARKER);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    myFile = parentDir == null? createFile(myModule, fileName, fileText) : createFile(myModule, parentDir, fileName, fileText);
    PsiReference ref = myFile.findReferenceAt(offset);

    assertNotNull(ref);

    return ref;
  }

  @Override
  protected String getTestDataPath() {
    return "/psi/resolve/";
  }
}