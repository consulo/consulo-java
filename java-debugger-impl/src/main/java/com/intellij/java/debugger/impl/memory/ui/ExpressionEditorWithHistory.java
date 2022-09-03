/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.memory.ui;

import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingWorker;

import javax.annotation.Nullable;

import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.language.editor.completion.lookup.LookupManager;
import com.intellij.java.debugger.engine.DebuggerUtils;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.application.ApplicationManager;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.project.Project;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.PopupStep;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ide.impl.idea.ui.popup.list.ListPopupImpl;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XExpressionImpl;
import consulo.ide.impl.idea.xdebugger.impl.ui.XDebuggerExpressionEditor;
import consulo.disposer.Disposable;

class ExpressionEditorWithHistory extends XDebuggerExpressionEditor
{
	private static final String HISTORY_ID_PREFIX = "filtering";

	ExpressionEditorWithHistory(final @Nonnull Project project,
			final @Nonnull String className,
			final @Nonnull XDebuggerEditorsProvider debuggerEditorsProvider,
			final @Nullable Disposable parentDisposable)
	{
		super(project, debuggerEditorsProvider, HISTORY_ID_PREFIX + className, null, XExpressionImpl.EMPTY_EXPRESSION, false, true, true);

		new AnAction("InstancesWindow.ShowHistory")
		{
			@Override
			public void actionPerformed(AnActionEvent e)
			{
				showHistory();
			}

			@Override
			public void update(AnActionEvent e)
			{
				e.getPresentation().setEnabled(LookupManager.getActiveLookup(getEditor()) == null);
			}
		}.registerCustomShortcutSet(CustomShortcutSet.fromString("DOWN"), getComponent(), parentDisposable);

		new SwingWorker<Void, Void>()
		{
			@Override
			protected Void doInBackground() throws Exception
			{
				ApplicationManager.getApplication().runReadAction(() ->
				{
					final PsiClass psiClass = DebuggerUtils.findClass(className, project, GlobalSearchScope.allScope(project));
					ApplicationManager.getApplication().invokeLater(() -> setContext(psiClass));
				});
				return null;
			}
		}.execute();
	}

	private void showHistory()
	{
		List<XExpression> expressions = getRecentExpressions();
		if(!expressions.isEmpty())
		{
			ListPopupImpl historyPopup = new ListPopupImpl(new BaseListPopupStep<XExpression>(null, expressions)
			{
				@Override
				public PopupStep onChosen(XExpression selectedValue, boolean finalChoice)
				{
					setExpression(selectedValue);
					requestFocusInEditor();
					return FINAL_CHOICE;
				}
			})
			{
				@Override
				protected ListCellRenderer getListElementRenderer()
				{
					return new ColoredListCellRenderer<XExpression>()
					{
						@Override
						protected void customizeCellRenderer(@Nonnull JList list, XExpression value, int index, boolean selected, boolean hasFocus)
						{
							append(value.getExpression(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
						}
					};
				}
			};

			historyPopup.getList().setFont(EditorUtil.getEditorFont());
			historyPopup.showUnderneathOf(getEditorComponent());
		}
	}
}
