package com.intellij.java.impl.util.xml.ui;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.ex.awt.AbstractTableCellEditor;
import consulo.ui.ex.awt.FixedSizeButton;
import consulo.ui.ex.localize.UILocalize;
import consulo.util.lang.function.Conditions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;

/**
 * @author peter
 */
public class PsiClassTableCellEditor extends AbstractTableCellEditor {
  private final Project myProject;
  private final GlobalSearchScope mySearchScope;
  private EditorTextField myEditor;

  public PsiClassTableCellEditor(final Project project, final GlobalSearchScope searchScope) {
    myProject = project;
    mySearchScope = searchScope;
  }

  public final Object getCellEditorValue() {
    return myEditor.getText();
  }

  public final boolean stopCellEditing() {
    final boolean b = super.stopCellEditing();
    myEditor = null;
    return b;
  }

  public boolean isCellEditable(EventObject e) {
    return !(e instanceof MouseEvent) || ((MouseEvent)e).getClickCount() >= 2;
  }

  @RequiredReadAction
  public final Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final Document document = JavaReferenceEditorUtil.createDocument(value == null ? "" : (String)value, myProject, true);
    myEditor = new EditorTextField(document, myProject, JavaFileType.INSTANCE){
      protected boolean shouldHaveBorder() {
        return false;
      }

      public void addNotify() {
        super.addNotify();
        final JComponent editorComponent = getEditor().getContentComponent();
        editorComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER");
        editorComponent.getActionMap().put("ENTER", new AbstractAction() {
          public void actionPerformed(ActionEvent e) {
            stopCellEditing();
          }
        });
      }
    };
    final JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(myEditor);
    final FixedSizeButton button = new FixedSizeButton(myEditor);
    panel.add(button, BorderLayout.EAST);
    button.addActionListener(e -> {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createInheritanceClassChooser(
        UILocalize.chooseClass().get(),
        mySearchScope,
        null,
        true,
        true,
        Conditions.alwaysTrue()
      );
      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        myEditor.setText(psiClass.getQualifiedName());
      }
    });
    panel.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        if (!e.isTemporary() && myEditor != null) {
          myEditor.requestFocus();
        }
      }

      public void focusLost(FocusEvent e) {
      }
    });
    myEditor.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
      }

      public void focusLost(FocusEvent e) {
        if (!e.isTemporary()) {
          stopCellEditing();
        }
      }
    });

    //ComponentWithBrowseButton.MyDoClickAction.addTo(button, myEditor);

    return panel;
  }
}
