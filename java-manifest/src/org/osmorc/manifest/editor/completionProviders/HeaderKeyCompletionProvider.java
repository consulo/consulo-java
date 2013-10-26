package org.osmorc.manifest.editor.completionProviders;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osmorc.manifest.lang.ManifestFileType;
import org.osmorc.manifest.lang.headerparser.HeaderParserEP;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author VISTALL
 * @since 13:55/05.05.13
 */
public class HeaderKeyCompletionProvider extends TextFieldWithAutoCompletionListProvider<String> {
  public HeaderKeyCompletionProvider() {
    super(null);
  }

  @NotNull
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

  @Nullable
  @Override
  protected Icon getIcon(@NotNull String item) {
    return ManifestFileType.INSTANCE.getIcon();
  }

  @NotNull
  @Override
  protected String getLookupString(@NotNull String item) {
    return item;
  }

  @Nullable
  @Override
  protected String getTailText(@NotNull String item) {
    return null;
  }

  @Nullable
  @Override
  protected String getTypeText(@NotNull String item) {
    return null;
  }

  @Override
  public int compare(String item1, String item2) {
    return item1.compareTo(item2);
  }
}
