package org.osmorc.manifest.editor.models;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.osmorc.manifest.lang.psi.Clause;
import org.osmorc.manifest.lang.psi.Directive;

/**
 * @author VISTALL
 * @since 15:16/03.05.13
 */
public class ClauseTableModel extends ListTableModel<String> {
  private Clause myClause;
  private final boolean myIsReadonlyFile;

  public ClauseTableModel(boolean isReadonlyFile) {
    super(new ColumnInfo.StringColumn("Key"), new ColumnInfo.StringColumn("Value"));
    myIsReadonlyFile = isReadonlyFile;
  }

  @Override
  public int getRowCount() {
    return myClause == null ? 0 : myClause.getDirectives().length;
  }

  @Override
  public Directive getRowValue(int row) {
    return myClause == null ? null : myClause.getDirectives()[row];
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return !myIsReadonlyFile;
  }

  @Override
  public void setValueAt(final Object value, int rowIndex, final int columnIndex) {
    final Directive rowValue = getRowValue(rowIndex);
    if (rowValue == null) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (columnIndex == 0) {
          rowValue.setName((String)value);
        }
        else if (columnIndex == 1) {
          rowValue.setValue((String)value);
        }
      }
    });
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Directive rowValue = getRowValue(rowIndex);
    if (rowValue == null) {
      return "";
    }
    else {
      if (columnIndex == 0) {
        return rowValue.getName();
      }
      else if (columnIndex == 1) {
        return rowValue.getValue();
      }
    }
    throw new UnsupportedOperationException();
  }

  public void setClause(Clause clause) {
    myClause = clause;
  }
}
