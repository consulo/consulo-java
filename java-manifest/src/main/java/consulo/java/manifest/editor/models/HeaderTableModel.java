package consulo.java.manifest.editor.models;

import org.osmorc.manifest.lang.headerparser.HeaderParser;
import org.osmorc.manifest.lang.psi.Clause;
import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.HeaderValuePart;
import consulo.language.editor.WriteCommandAction;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.ui.ex.awt.table.ListTableModel;

/**
 * @author VISTALL
 * @since 15:47/03.05.13
 */
public class HeaderTableModel extends ListTableModel<Clause> {
  private final Header myHeader;
  private final boolean myIsReadonlyFile;

  public HeaderTableModel(final Header header, final HeaderParser headerParser, boolean isReadonlyFile) {
    super(new ColumnInfo.StringColumn(""));
    myHeader = header;
    myIsReadonlyFile = isReadonlyFile;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return !myIsReadonlyFile;
  }

  @Override
  public void setValueAt(final Object aValue, int rowIndex, int columnIndex) {
    final Clause rowValue = getRowValue(rowIndex);
    if (rowValue == null) {
      return;
    }

    final HeaderValuePart value = rowValue.getValue();
    if (value == null) {
      return;
    }

    new WriteCommandAction.Simple<Object>(myHeader.getProject(), myHeader.getContainingFile()) {
      @Override
      public void run() {
        value.setText((String)aValue);
      }
    }.execute();
  }

  @Override
  public int getRowCount() {
    return myHeader.getClauses().length;
  }

  @Override
  public Clause getRowValue(int row) {
    return myHeader.getClauses()[row];
  }

  @Override
  public String getValueAt(int rowIndex, int columnIndex) {
    Clause clause = myHeader.getClauses()[rowIndex];
    HeaderValuePart value = clause.getValue();
    if (value == null) {
      return "";
    }
    else {
      return value.getUnwrappedText();
    }
  }
}
