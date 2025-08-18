/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.debugger;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiForStatement;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.fileEditor.FileEditorManager;
import consulo.language.psi.*;
import consulo.logging.Logger;
import consulo.navigation.Navigatable;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * User: lex
 * Date: Oct 24, 2003
 * Time: 8:23:06 PM
 */
public abstract class SourcePosition implements Navigatable {
    private static final Logger LOG = Logger.getInstance(SourcePosition.class);

    @Nullable
    public VirtualFile getVirtualFile() {
        return PsiUtilCore.getVirtualFile(getFile());
    }

    @Nonnull
    public abstract PsiFile getFile();

    public abstract PsiElement getElementAt();

    /**
     * @return a zero-based line number
     */
    public abstract int getLine();

    public abstract int getOffset();

    public abstract Editor openEditor(boolean requestFocus);

    private abstract static class SourcePositionCache extends SourcePosition {
        @Nonnull
        private final PsiFile myFile;
        private long myModificationStamp = -1L;

        private PsiElement myPsiElement;
        private Integer myLine;
        private Integer myOffset;

        public SourcePositionCache(@Nonnull PsiFile file) {
            myFile = file;
            updateData();
        }

        @Override
        @Nonnull
        public PsiFile getFile() {
            return myFile;
        }

        @Override
        public boolean canNavigate() {
            return getFile().isValid();
        }

        @Override
        public boolean canNavigateToSource() {
            return canNavigate();
        }

        @Override
        public void navigate(final boolean requestFocus) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!canNavigate()) {
                    return;
                }
                openEditor(requestFocus);
            });
        }

        @Override
        public Editor openEditor(final boolean requestFocus) {
            final PsiFile psiFile = getFile();
            final Project project = psiFile.getProject();
            if (project.isDisposed()) {
                return null;
            }
            final VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile == null || !virtualFile.isValid()) {
                return null;
            }
            final int offset = getOffset();
            if (offset < 0) {
                return null;
            }
            return FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).offset(offset).build(), requestFocus);
        }

        private void updateData() {
            if (dataUpdateNeeded()) {
                myModificationStamp = myFile.getModificationStamp();
                myLine = null;
                myOffset = null;
                myPsiElement = null;
            }
        }

        private boolean dataUpdateNeeded() {
            if (myModificationStamp != myFile.getModificationStamp()) {
                return true;
            }
            final PsiElement psiElement = myPsiElement;
            return psiElement != null && !ReadAction.compute(psiElement::isValid);
        }

        @Override
        public int getLine() {
            updateData();
            if (myLine == null) {
                myLine = calcLine();
            }
            return myLine.intValue();
        }

        @Override
        public int getOffset() {
            updateData();
            if (myOffset == null) {
                myOffset = calcOffset();
            }
            return myOffset.intValue();
        }

        @Override
        public PsiElement getElementAt() {
            updateData();
            if (myPsiElement == null) {
                myPsiElement = calcPsiElement();
            }
            return myPsiElement;
        }

        protected int calcLine() {
            final PsiFile file = getFile();
            Document document = null;
            try {
                document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            }
            catch (Throwable e) {
                LOG.error(e);
            }
            if (document != null) {
                try {
                    return document.getLineNumber(calcOffset());
                }
                catch (IndexOutOfBoundsException e) {
                    // may happen if document has been changed since the this SourcePosition was created
                }
            }
            return -1;
        }

        protected int calcOffset() {
            final PsiFile file = getFile();
            final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document != null) {
                try {
                    return document.getLineStartOffset(calcLine());
                }
                catch (IndexOutOfBoundsException e) {
                    // may happen if document has been changed since the this SourcePosition was created
                }
            }
            return -1;
        }

        @Nullable
        protected PsiElement calcPsiElement() {
            PsiFile psiFile = getFile();
            int lineNumber = getLine();
            if (lineNumber < 0) {
                return psiFile;
            }

            final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
            if (document == null) {
                return null;
            }
            if (lineNumber >= document.getLineCount()) {
                return psiFile;
            }
            int startOffset = document.getLineStartOffset(lineNumber);
            if (startOffset == -1) {
                return null;
            }

            PsiElement rootElement = psiFile;

            List<PsiFile> allFiles = psiFile.getViewProvider().getAllFiles();
            if (allFiles.size() > 1) { // jsp & gsp
                PsiClassOwner owner = ContainerUtil.findInstance(allFiles, PsiClassOwner.class);
                if (owner != null) {
                    PsiClass[] classes = owner.getClasses();
                    if (classes.length == 1 && classes[0] instanceof SyntheticElement) {
                        rootElement = classes[0];
                    }
                }
            }

            PsiElement element;
            while (true) {
                final CharSequence charsSequence = document.getCharsSequence();
                for (; startOffset < charsSequence.length(); startOffset++) {
                    char c = charsSequence.charAt(startOffset);
                    if (c != ' ' && c != '\t') {
                        break;
                    }
                }
                element = rootElement.findElementAt(startOffset);

                if (element instanceof PsiComment) {
                    startOffset = element.getTextRange().getEndOffset() + 1;
                }
                else {
                    break;
                }
            }

            if (element != null && element.getParent() instanceof PsiForStatement) {
                return ((PsiForStatement) element.getParent()).getInitialization();
            }
            return element;
        }
    }

    public static SourcePosition createFromLineComputable(@Nonnull final PsiFile file, final Supplier<Integer> line) {
        return new SourcePositionCache(file) {
            @Override
            protected int calcLine() {
                return line.get();
            }
        };
    }

    public static SourcePosition createFromLine(@Nonnull final PsiFile file, final int line) {
        return new SourcePositionCache(file) {
            @Override
            protected int calcLine() {
                return line;
            }
        };
    }

    public static SourcePosition createFromOffset(@Nonnull final PsiFile file, final int offset) {
        return new SourcePositionCache(file) {

            @Override
            protected int calcOffset() {
                return offset;
            }
        };
    }

    public static SourcePosition createFromElement(PsiElement element) {
        final PsiElement navigationElement = element.getNavigationElement();
        final PsiFile psiFile;
    /*
    TODO [VISTALL] bad code
    if (JspPsiUtil.isInJspFile(navigationElement)) {
      psiFile = JspPsiUtil.getJspFile(navigationElement);
    }
    else { */
        psiFile = navigationElement.getContainingFile();
        //}
        return new SourcePositionCache(psiFile) {
            @Override
            protected PsiElement calcPsiElement() {
                return navigationElement;
            }

            @Override
            protected int calcOffset() {
                return navigationElement.getTextOffset();
            }
        };
    }

    public boolean equals(Object o) {
        if (o instanceof SourcePosition) {
            SourcePosition sourcePosition = (SourcePosition) o;
            return Comparing.equal(sourcePosition.getFile(), getFile()) && sourcePosition.getOffset() == getOffset();
        }

        return false;
    }
}
