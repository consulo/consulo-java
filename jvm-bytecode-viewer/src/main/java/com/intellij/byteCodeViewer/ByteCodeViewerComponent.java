package com.intellij.byteCodeViewer;

import consulo.annotation.access.RequiredWriteAction;
import consulo.application.ApplicationManager;
import consulo.codeEditor.*;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.disposer.Disposable;
import consulo.document.Document;
import consulo.execution.ui.console.LineNumbersMapping;
import consulo.language.editor.highlight.EditorHighlighterFactory;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.plain.PlainTextFileType;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.LightColors;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 5/7/12
 */
public class ByteCodeViewerComponent extends JPanel implements Disposable {

    private final Editor myEditor;

    public ByteCodeViewerComponent(Project project, AnAction[] additionalActions) {
        super(new BorderLayout());
        final EditorFactory factory = EditorFactory.getInstance();
        final Document doc = factory.createDocument("");
        doc.setReadOnly(true);
        myEditor = factory.createEditor(doc, project);
        EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
        final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(PlainTextFileType.INSTANCE, project, null);
        ((EditorEx) myEditor).setHighlighter(editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter,
            EditorColorsManager.getInstance().getGlobalScheme()));
        ((EditorEx) myEditor).setBackgroundColor(getBackgroundColor(myEditor));
        myEditor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, TargetAWT.from(LightColors.SLIGHTLY_GRAY));
        ((EditorEx) myEditor).setCaretVisible(true);

        final EditorSettings settings = myEditor.getSettings();
        settings.setLineMarkerAreaShown(false);
        settings.setIndentGuidesShown(false);
        settings.setLineNumbersShown(false);
        settings.setFoldingOutlineShown(false);

        myEditor.setBorder(null);
        add(myEditor.getComponent(), BorderLayout.CENTER);
        final ActionManager actionManager = ActionManager.getInstance();
        final DefaultActionGroup actions = new DefaultActionGroup();
        if (additionalActions != null) {
            for (final AnAction action : additionalActions) {
                actions.add(action);
            }
        }
        add(actionManager.createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, actions, true).getComponent(), BorderLayout.NORTH);
    }

    public static ColorValue getBackgroundColor(Editor editor) {
        EditorColorsScheme colorsScheme = editor.getColorsScheme();
        ColorValue color = colorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
        if (color == null) {
            color = colorsScheme.getDefaultBackground();
        }
        return color;
    }


    public void setText(final String bytecode) {
        setText(bytecode, 0);
    }

    public void setText(final String bytecode, PsiElement element) {
        int offset = 0;
        PsiFile psiFile = element.getContainingFile();
        final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(psiFile);
        if (document != null) {
            int lineNumber = document.getLineNumber(element.getTextOffset());
            VirtualFile file = psiFile.getVirtualFile();
            if (file != null) {
                LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
                if (mapping != null) {
                    int mappedLine = mapping.sourceToBytecode(lineNumber);
                    while (mappedLine == -1 && lineNumber < document.getLineCount()) {
                        mappedLine = mapping.sourceToBytecode(++lineNumber);
                    }
                    if (mappedLine > 0) {
                        lineNumber = mappedLine;
                    }
                }
            }
            offset = bytecode.indexOf("LINENUMBER " + lineNumber);
            while (offset == -1 && lineNumber < document.getLineCount()) {
                offset = bytecode.indexOf("LINENUMBER " + (lineNumber++));
            }
        }
        setText(bytecode, Math.max(0, offset));
    }

    public void setText(final String bytecode, final int offset) {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            @Override
            @RequiredWriteAction
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        Document fragmentDoc = myEditor.getDocument();
                        fragmentDoc.setReadOnly(false);
                        fragmentDoc.replaceString(0, fragmentDoc.getTextLength(), bytecode);
                        fragmentDoc.setReadOnly(true);
                        myEditor.getCaretModel().moveToOffset(offset);
                        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                    }
                });
            }
        });
    }

    public String getText() {
        return myEditor.getDocument().getText();
    }

    @Override
    public void dispose() {
        EditorFactory.getInstance().releaseEditor(myEditor);
    }
}
