package com.intellij.java.impl.util.descriptors.impl;

import com.intellij.java.impl.util.descriptors.ConfigFile;
import com.intellij.java.impl.util.descriptors.ConfigFileInfo;
import com.intellij.java.impl.util.descriptors.ConfigFileMetaData;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.pointer.VirtualFilePointer;
import consulo.virtualFileSystem.pointer.VirtualFilePointerListener;
import consulo.virtualFileSystem.pointer.VirtualFilePointerManager;
import consulo.xml.psi.xml.XmlDocument;
import consulo.xml.psi.xml.XmlFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

/**
 * @author nik
 */
public class ConfigFileImpl implements ConfigFile {
  @Nonnull
  private ConfigFileInfo myInfo;
  private final VirtualFilePointer myFilePointer;
  private volatile Reference<PsiFile> myPsiFile;
  private final ConfigFileContainerImpl myContainer;
  private final Project myProject;
  private long myModificationCount;

  public ConfigFileImpl(@Nonnull final ConfigFileContainerImpl container, @Nonnull final ConfigFileInfo configuration) {
    myContainer = container;
    myInfo = configuration;
    final VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    myFilePointer = pointerManager.create(configuration.getUrl(), this, new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(@Nonnull final VirtualFilePointer[] pointers) {
      }

      @Override
      public void validityChanged(@Nonnull final VirtualFilePointer[] pointers) {
        myPsiFile = null;
        onChange();
      }
    });
    onChange();
    myProject = myContainer.getProject();
  }

  private void onChange() {
    myModificationCount++;
    myContainer.fireDescriptorChanged(this);
  }

  @Override
  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public void setInfo(@Nonnull final ConfigFileInfo info) {
    myInfo = info;
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    return myFilePointer.isValid() ? myFilePointer.getFile() : null;
  }

  @Override
  @Nullable
  public PsiFile getPsiFile() {
    Reference<PsiFile> ref = myPsiFile;
    PsiFile psiFile = ref == null ? null : ref.get();

    if (psiFile != null && psiFile.isValid()) {
      return psiFile;
    }

    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;

    psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);

    myPsiFile = new SoftReference<PsiFile>(psiFile);

    return psiFile;
  }

  @Override
  @Nullable
  public XmlFile getXmlFile() {
    final PsiFile file = getPsiFile();
    return file instanceof XmlFile ? (XmlFile) file : null;
  }

  @Override
  public void dispose() {
  }

  @Override
  @Nonnull
  public ConfigFileInfo getInfo() {
    return myInfo;
  }

  @Override
  public boolean isValid() {
    final PsiFile psiFile = getPsiFile();
    if (psiFile == null || !psiFile.isValid()) {
      return false;
    }
    if (psiFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile) psiFile).getDocument();
      return document != null && document.getRootTag() != null;
    }
    return true;
  }


  @Override
  @Nonnull
  public ConfigFileMetaData getMetaData() {
    return myInfo.getMetaData();
  }


  @Override
  public long getModificationCount() {
    return myModificationCount;
  }
}
