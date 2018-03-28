package consulo.java.manifest.editor;

import javax.annotation.Nonnull;

import org.jdom.Element;
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
  public boolean accept(@Nonnull Project project, @Nonnull VirtualFile file) {
    return file.getFileType() instanceof ManifestFileType;
  }

  @Nonnull
  @Override
  public FileEditor createEditor(@Nonnull Project project, @Nonnull VirtualFile file) {
    return new ManifestEditor(project, file);
  }

  @Override
  public void disposeEditor(@Nonnull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @Nonnull
  @Override
  public FileEditorState readState(@Nonnull Element sourceElement, @Nonnull Project project, @Nonnull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void writeState(@Nonnull FileEditorState state, @Nonnull Project project, @Nonnull Element targetElement) {
  }

  @Nonnull
  @Override
  public String getEditorTypeId() {
    return EDITOR_ID;
  }

  @Nonnull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
