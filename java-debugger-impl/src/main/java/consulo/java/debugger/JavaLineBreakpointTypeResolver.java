package consulo.java.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.debugger.ui.breakpoints.JavaFieldBreakpointType;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointTypeBase;
import com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import consulo.annotations.RequiredReadAction;
import consulo.xdebugger.breakpoints.XLineBreakpointTypeResolver;

/**
 * @author VISTALL
 * @since 5/7/2016
 */
public class JavaLineBreakpointTypeResolver implements XLineBreakpointTypeResolver
{
	@Nullable
	@RequiredReadAction
	@Override
	public XLineBreakpointType<?> resolveBreakpointType(@NotNull Project project, @NotNull VirtualFile virtualFile, final int line)
	{
		PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
		if(file == null)
		{
			return null;
		}
		final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
		assert document != null;
		final Ref<JavaLineBreakpointTypeBase> result = Ref.create();
		XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>()
		{
			@Override
			@RequiredReadAction
			public boolean process(PsiElement element)
			{
				// avoid comments
				if((element instanceof PsiWhiteSpace) || (PsiTreeUtil.getParentOfType(element, PsiComment.class, PsiImportStatementBase.class, PsiPackageStatement.class) != null))
				{
					return true;
				}
				PsiElement parent = element;
				while(element != null)
				{
					// skip modifiers
					if(element instanceof PsiModifierList)
					{
						element = element.getParent();
						continue;
					}

					final int offset = element.getTextOffset();
					if(offset >= 0)
					{
						if(document.getLineNumber(offset) != line)
						{
							break;
						}
					}
					parent = element;
					element = element.getParent();
				}

				if(parent instanceof PsiMethod)
				{
					if(parent.getTextRange().getEndOffset() >= document.getLineEndOffset(line))
					{
						PsiCodeBlock body = ((PsiMethod) parent).getBody();
						if(body != null)
						{
							PsiStatement[] statements = body.getStatements();
							if(statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == line)
							{
								result.set(JavaLineBreakpointType.getInstance());
							}
						}
					}
					if(result.isNull())
					{
						result.set(JavaMethodBreakpointType.getInstance());
					}
				}
				else if(parent instanceof PsiField)
				{
					if(result.isNull())
					{
						result.set(JavaFieldBreakpointType.getInstance());
					}
				}
				else
				{
					result.set(JavaLineBreakpointType.getInstance());
				}
				return true;
			}
		});
		return result.get();
	}
}
