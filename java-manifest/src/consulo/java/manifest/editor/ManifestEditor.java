package consulo.java.manifest.editor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.util.EventObject;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import consulo.java.manifest.editor.completionProviders.HeaderKeyCompletionProvider;
import consulo.java.manifest.editor.completionProviders.HeaderValueCompletionProvider;
import consulo.java.manifest.editor.models.ClauseTableModel;
import consulo.java.manifest.editor.models.FileTableModel;
import consulo.java.manifest.editor.models.HeaderTableModel;
import org.osmorc.manifest.lang.headerparser.HeaderParser;
import consulo.java.manifest.lang.headerparser.HeaderUtil;
import org.osmorc.manifest.lang.psi.Clause;
import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.HeaderValuePart;
import org.osmorc.manifest.lang.psi.ManifestFile;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.UIUtil;

/**
 * @author VISTALL
 * @since 12:33/03.05.13
 */
public class ManifestEditor extends UserDataHolderBase implements FileEditor
{
	private final JPanel myRoot;
	private final Project myProject;
	private final VirtualFile myVirtualFile;
	private final JPanel myContentPanel;
	private final ManifestFile myManifestFile;
	private final boolean myIsReadonlyFile;

	public ManifestEditor(final Project project, VirtualFile file)
	{
		myProject = project;
		myVirtualFile = file;
		myRoot = new JPanel(new BorderLayout());
		myRoot.setBorder(IdeBorderFactory.createEmptyBorder(0, 5, 0, 0));
		myIsReadonlyFile = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>()
		{
			@Override
			public Boolean compute()
			{
				return !ReadonlyStatusHandler.ensureFilesWritable(myProject, myVirtualFile);
			}
		});

		JBSplitter splitter = new JBSplitter();
		splitter.setSplitterProportionKey(getClass().getName());
		myContentPanel = new JPanel(new BorderLayout());
		myContentPanel.setBorder(IdeBorderFactory.createEmptyBorder(5));

		Document document = FileDocumentManager.getInstance().getDocument(file);

		assert document != null;

		final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

		assert psiFile instanceof ManifestFile;

		myManifestFile = (ManifestFile) psiFile;

		final FileTableModel fileTableModel = new FileTableModel(myManifestFile);
		final JBTable headersTable = new JBTable(fileTableModel);
		headersTable.getColumnModel().getColumn(0).setCellEditor(new AbstractTableCellEditor()
		{
			private TextFieldWithAutoCompletion<String> myTextField;

			@Override
			public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
			{
				myTextField = new TextFieldWithAutoCompletion<String>(myProject, new HeaderKeyCompletionProvider(), false, null);
				myTextField.setText((String) value);
				return myTextField;
			}

			@Override
			public boolean isCellEditable(EventObject e)
			{
				return !myIsReadonlyFile;
			}

			@Override
			public Object getCellEditorValue()
			{
				return myTextField.getText();
			}
		});
		headersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		headersTable.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{
			@Override
			public void valueChanged(ListSelectionEvent e)
			{
				Header value = fileTableModel.getRowValue(headersTable.getSelectedRow());

				selectHeader(value);
			}
		});


		ToolbarDecorator decorator = ToolbarDecorator.createDecorator(headersTable);
		decorator.setAddAction(new AnActionButtonRunnable()
		{
			@Override
			public void run(AnActionButton anActionButton)
			{
				new WriteCommandAction.Simple<Object>(project, psiFile)
				{
					@Override
					public void run()
					{
						myManifestFile.setHeaderValue("NewKey", "NewValue");
					}
				}.execute();
			}
		});
		decorator.setRemoveAction(new AnActionButtonRunnable()
		{
			@Override
			public void run(AnActionButton anActionButton)
			{
				final Header value = fileTableModel.getRowValue(headersTable.getSelectedRow());
				if(value == null)
				{
					return;
				}
				ApplicationManager.getApplication().runWriteAction(new Runnable()
				{
					@Override
					public void run()
					{
						if(headersTable.isEditing())
						{
							headersTable.removeEditor();
						}
						value.delete();

						selectHeader(null);
					}
				});
			}
		});
		disableActionsIfNeed(decorator);

		splitter.setFirstComponent(decorator.createPanel());
		splitter.setSecondComponent(myContentPanel);

		myRoot.add(splitter);
	}

	public void selectHeader(@Nullable final Header header)
	{
		myContentPanel.removeAll();

		if(header == null)
		{
			return;
		}

		final String key = header.getName();
		final HeaderParser headerParser = HeaderUtil.getHeaderParser(key);
		if(headerParser.isSimpleHeader())
		{
			final TextFieldWithAutoCompletion<Object> textField = new TextFieldWithAutoCompletion<Object>(myProject, new HeaderValueCompletionProvider(header, headerParser), false, null);

			textField.setOneLineMode(false);
			textField.setEnabled(!myIsReadonlyFile);
			Object simpleConvertedValue = header.getSimpleConvertedValue();
			if(simpleConvertedValue != null)
			{
				textField.setText(simpleConvertedValue.toString());
			}

			textField.addDocumentListener(new DocumentAdapter()
			{
				@Override
				public void documentChanged(DocumentEvent e)
				{
					ApplicationManager.getApplication().runWriteAction(new Runnable()
					{
						@Override
						public void run()
						{
							final Clause[] clauses = header.getClauses();
							if(clauses.length != 1)
							{
								return;
							}
							final HeaderValuePart value = clauses[0].getValue();
							if(value != null)
							{
								value.setText(textField.getText());
							}
						}
					});
				}
			});

			myContentPanel.add(new JBScrollPane(textField), BorderLayout.CENTER);
		}
		else
		{
			JBSplitter splitter = new JBSplitter(true);
			splitter.setSplitterProportionKey(getClass().getName() + "#" + key);

			final HeaderTableModel valueListModel = new HeaderTableModel(header, headerParser, myIsReadonlyFile);
			final JBTable valueList = new JBTable(valueListModel);
			valueList.getColumnModel().getColumn(0).setCellEditor(new AbstractTableCellEditor()
			{

				private TextFieldWithAutoCompletion<Object> myTextField;

				@Override
				public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
				{
					myTextField = new TextFieldWithAutoCompletion<Object>(myProject, new HeaderValueCompletionProvider(header, headerParser),

							false, null);
					myTextField.setText((String) value);
					return myTextField;
				}

				@Override
				public Object getCellEditorValue()
				{
					return myTextField.getText();
				}
			});
			valueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

			ToolbarDecorator valueDecorator = ToolbarDecorator.createDecorator(valueList);
			valueDecorator.setAddAction(new AnActionButtonRunnable()
			{
				@Override
				public void run(AnActionButton anActionButton)
				{
					new WriteCommandAction.Simple<Object>(myProject, myManifestFile)
					{

						@Override
						protected void run() throws Throwable
						{
							header.addClause("newValue");
						}
					}.execute();
				}
			});
			disableActionsIfNeed(valueDecorator);

			splitter.setFirstComponent(valueDecorator.createPanel());

			final ClauseTableModel model = new ClauseTableModel(myIsReadonlyFile);
			final JBTable propertiesList = new JBTable(model);
			propertiesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

			valueList.getSelectionModel().addListSelectionListener(new ListSelectionListener()
			{
				@Override
				public void valueChanged(ListSelectionEvent e)
				{
					Clause value = valueListModel.getRowValue(e.getFirstIndex());
					model.setClause(value);
				}
			});
			ToolbarDecorator propertiesDecorator = ToolbarDecorator.createDecorator(propertiesList);
			disableActionsIfNeed(propertiesDecorator);

			splitter.setSecondComponent(propertiesDecorator.createPanel());

			myContentPanel.add(splitter, BorderLayout.CENTER);
		}

		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				myContentPanel.revalidate();
				myContentPanel.repaint();
			}
		});
	}

	private void disableActionsIfNeed(ToolbarDecorator toolbarDecorator)
	{
		toolbarDecorator.disableUpDownActions();
		if(myIsReadonlyFile)
		{
			toolbarDecorator.disableUpDownActions();
			toolbarDecorator.disableAddAction();
			toolbarDecorator.disableRemoveAction();
		}
	}

	@NotNull
	@Override
	public JComponent getComponent()
	{
		return myRoot;
	}

	@Nullable
	@Override
	public JComponent getPreferredFocusedComponent()
	{
		return myRoot;
	}

	@NotNull
	@Override
	public String getName()
	{
		return "UI Editor";
	}

	@NotNull
	@Override
	public FileEditorState getState(@NotNull FileEditorStateLevel level)
	{
		return FileEditorState.INSTANCE;
	}

	@Override
	public void setState(@NotNull FileEditorState state)
	{
	}

	@Override
	public boolean isModified()
	{
		return false;
	}

	@Override
	public boolean isValid()
	{
		return myVirtualFile.isValid();
	}

	@Override
	public void selectNotify()
	{
	}

	@Override
	public void deselectNotify()
	{
	}

	@Override
	public void addPropertyChangeListener(@NotNull PropertyChangeListener listener)
	{
	}

	@Override
	public void removePropertyChangeListener(@NotNull PropertyChangeListener listener)
	{
	}

	@Nullable
	@Override
	public BackgroundEditorHighlighter getBackgroundHighlighter()
	{
		return null;
	}

	@Nullable
	@Override
	public FileEditorLocation getCurrentLocation()
	{
		return null;
	}

	@Nullable
	@Override
	public StructureViewBuilder getStructureViewBuilder()
	{
		return null;
	}

	@Nullable
	@Override
	public VirtualFile getVirtualFile()
	{
		return myVirtualFile;
	}

	@Override
	public void dispose()
	{
	}
}
