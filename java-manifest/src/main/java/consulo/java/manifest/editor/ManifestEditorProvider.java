package consulo.java.manifest.editor;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.osmorc.manifest.lang.ManifestFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author VISTALL
 * @since 12:29/03.05.13
 */
public class ManifestEditorProvider implements FileEditorProvider {
  public static final String EDITOR_ID = ManifestEditorProvider.class.getName();

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file.getFileType() instanceof ManifestFileType;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new ManifestEditor(project, file);
  }

  @Override
  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return EDITOR_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
