/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * @author max
 */
package com.intellij.java.impl.generate.view;

import com.intellij.java.impl.generate.element.ClassElement;
import com.intellij.java.impl.generate.element.FieldElement;
import com.intellij.java.impl.generate.element.GenerationHelper;
import com.intellij.java.impl.generate.template.TemplateResource;
import com.intellij.java.impl.generate.template.TemplatesManager;
import com.intellij.java.language.psi.PsiType;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorFactory;
import consulo.configurable.ConfigurationException;
import consulo.configurable.UnnamedConfigurable;
import consulo.document.Document;
import consulo.language.editor.WriteCommandAction;
import consulo.language.file.FileTypeManager;
import consulo.language.plain.PlainTextFileType;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.project.Project;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.MultiLineLabel;
import consulo.util.lang.Comparing;
import consulo.util.lang.LocalTimeCounter;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class GenerateTemplateConfigurable implements UnnamedConfigurable {
  private final TemplateResource template;
  private final Editor myEditor;
  private final List<String> myAvailableImplicits = new ArrayList<String>();

  public GenerateTemplateConfigurable(TemplateResource template, Map<String, PsiType> contextMap, Project project) {
    this(template, contextMap, project, true);
  }

  public GenerateTemplateConfigurable(TemplateResource template, Map<String, PsiType> contextMap, Project project, boolean multipleFields) {
    this.template = template;
    final EditorFactory factory = EditorFactory.getInstance();
    Document doc = factory.createDocument(template.getTemplate());
    final FileType ftl = FileTypeManager.getInstance().findFileTypeByName("VTL");
    if (project != null && ftl != null) {
      final PsiFile file = PsiFileFactory.getInstance(project)
                                         .createFileFromText(template.getFileName(),
                                                             ftl,
                                                             template.getTemplate(),
                                                             LocalTimeCounter.currentTime(),
                                                             true);
      if (!template.isDefault()) {
        final HashMap<String, PsiType> map = new LinkedHashMap<String, PsiType>();
        map.put("java_version", PsiType.INT);
        map.put("class", TemplatesManager.createElementType(project, ClassElement.class));
        if (multipleFields) {
          map.put("fields", TemplatesManager.createFieldListElementType(project));
        }
        else {
          map.put("field", TemplatesManager.createElementType(project, FieldElement.class));
        }
        map.put("helper", TemplatesManager.createElementType(project, GenerationHelper.class));
        map.put("settings", PsiType.NULL);
        map.putAll(contextMap);
        myAvailableImplicits.addAll(map.keySet());
        file.getViewProvider().putUserData(TemplatesManager.TEMPLATE_IMPLICITS, map);
      }
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document != null) {
        doc = document;
      }
    }
    myEditor = factory.createEditor(doc, project, ftl != null ? ftl : PlainTextFileType.INSTANCE, template.isDefault());
  }

  @Override
  public JComponent createComponent() {
    final JComponent component = myEditor.getComponent();
    if (myAvailableImplicits.isEmpty()) {
      return component;
    }
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);
    MultiLineLabel label =
      new MultiLineLabel("<html>Available implicit variables:\n" + StringUtil.join(myAvailableImplicits, ", ") + "</html>");
    label.setPreferredSize(JBUI.size(250, 30));
    panel.add(label, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(myEditor.getDocument().getText(), template.getTemplate());
  }

  @Override
  public void apply() throws ConfigurationException {
    template.setTemplate(myEditor.getDocument().getText());
  }

  @Override
  public void reset() {
    new WriteCommandAction(null) {
      @Override
      protected void run(@Nonnull Result result) throws Throwable {
        myEditor.getDocument().setText(template.getTemplate());
      }
    }.execute();
  }

  @Override
  public void disposeUIResources() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }
}