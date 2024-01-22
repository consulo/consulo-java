package consulo.java.manifest.editor;

import consulo.disposer.Disposer;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorPolicy;
import consulo.fileEditor.FileEditorProvider;
import consulo.fileEditor.FileEditorState;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.osmorc.manifest.lang.ManifestFileType;

/**
 * @author VISTALL
 * @since 12:29/03.05.13
 */
//@ExtensionImpl disabled due editor tabs problem
public abstract class ManifestEditorProvider implements FileEditorProvider {
  public static final String EDITOR_ID = ManifestEditorProvider.class.getName();

  @Override
  public boolean accept(@jakarta.annotation.Nonnull Project project, @jakarta.annotation.Nonnull VirtualFile file) {
    return file.getFileType() instanceof ManifestFileType;
  }

  @jakarta.annotation.Nonnull
  @Override
  public FileEditor createEditor(@jakarta.annotation.Nonnull Project project, @jakarta.annotation.Nonnull VirtualFile file) {
    return new ManifestEditor(project, file);
  }

  @Override
  public void disposeEditor(@jakarta.annotation.Nonnull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @jakarta.annotation.Nonnull
  @Override
  public FileEditorState readState(@jakarta.annotation.Nonnull Element sourceElement, @jakarta.annotation.Nonnull Project project, @jakarta.annotation.Nonnull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void writeState(@jakarta.annotation.Nonnull FileEditorState state, @Nonnull Project project, @jakarta.annotation.Nonnull Element targetElement) {
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
