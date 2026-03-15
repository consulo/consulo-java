package consulo.java.manifest.editor;

import consulo.disposer.Disposer;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorPolicy;
import consulo.fileEditor.FileEditorProvider;
import consulo.fileEditor.FileEditorState;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
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
  public boolean accept(Project project, VirtualFile file) {
    return file.getFileType() instanceof ManifestFileType;
  }

  @Override
  public FileEditor createEditor(Project project, VirtualFile file) {
    return new ManifestEditor(project, file);
  }

  @Override
  public void disposeEditor(FileEditor editor) {
    Disposer.dispose(editor);
  }

  @Override
  public FileEditorState readState(Element sourceElement, Project project, VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void writeState(FileEditorState state, Project project, Element targetElement) {
  }

  @Override
  public String getEditorTypeId() {
    return EDITOR_ID;
  }

  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
