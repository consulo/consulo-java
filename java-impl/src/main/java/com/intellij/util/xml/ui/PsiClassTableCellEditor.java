package com.intellij.util.xml.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Conditions;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.AbstractTableCellEditor;

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
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
          .createInheritanceClassChooser(UIBundle.message("choose.class"), mySearchScope, null, true, true, Conditions.alwaysTrue());
        chooser.showDialog();
        final PsiClass psiClass = chooser.getSelected();
        if (psiClass != null) {
          myEditor.setText(psiClass.getQualifiedName());
        }
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
