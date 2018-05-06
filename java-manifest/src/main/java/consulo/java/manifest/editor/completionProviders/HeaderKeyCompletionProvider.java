package consulo.java.manifest.editor.completionProviders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.Icon;

import org.osmorc.manifest.lang.ManifestFileType;
import consulo.awt.TargetAWT;
import consulo.java.manifest.lang.headerparser.HeaderParserEP;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;

/**
 * @author VISTALL
 * @since 13:55/05.05.13
 */
public class HeaderKeyCompletionProvider extends TextFieldWithAutoCompletionListProvider<String> {
  public HeaderKeyCompletionProvider() {
    super(null);
  }

  @Nonnull
  @Override
  public Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
    HeaderParserEP[] extensions = HeaderParserEP.EP_NAME.getExtensions();
    List<String> list = new ArrayList<String>(extensions.length - 1);
    for (HeaderParserEP ep : extensions) {
      if (ep.key.isEmpty()) {
        continue;
      }
      list.add(ep.key);
    }
    return list;
  }

  @javax.annotation.Nullable
  @Override
  protected Icon getIcon(@Nonnull String item) {
    return TargetAWT.to(ManifestFileType.INSTANCE.getIcon());
  }

  @Nonnull
  @Override
  protected String getLookupString(@Nonnull String item) {
    return item;
  }

  @javax.annotation.Nullable
  @Override
  protected String getTailText(@Nonnull String item) {
    return null;
  }

  @javax.annotation.Nullable
  @Override
  protected String getTypeText(@Nonnull String item) {
    return null;
  }

  @Override
  public int compare(String item1, String item2) {
    return item1.compareTo(item2);
  }
}
