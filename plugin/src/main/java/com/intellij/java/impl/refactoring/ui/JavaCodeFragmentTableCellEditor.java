/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.ui;

import com.intellij.java.impl.codeInsight.daemon.impl.JavaReferenceImporter;
import com.intellij.java.language.impl.JavaFileType;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.ide.impl.idea.refactoring.ui.CodeFragmentTableCellEditorBase;

/**
 * @author dsl
 */
public class JavaCodeFragmentTableCellEditor extends CodeFragmentTableCellEditorBase {

  public JavaCodeFragmentTableCellEditor(final Project project) {
    super(project, JavaFileType.INSTANCE);
  }

  public boolean stopCellEditing() {
    final Editor editor = myEditorTextField.getEditor();
    if (editor != null) {
      JavaReferenceImporter.autoImportReferenceAtCursor(editor, myCodeFragment, true);
    }
    return super.stopCellEditing();
  }
}
