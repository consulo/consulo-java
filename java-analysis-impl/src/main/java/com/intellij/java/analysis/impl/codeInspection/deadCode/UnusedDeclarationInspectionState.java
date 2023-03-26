package com.intellij.java.analysis.impl.codeInspection.deadCode;

import com.intellij.java.analysis.codeInspection.ex.EntryPointProvider;
import com.intellij.java.analysis.codeInspection.ex.EntryPointState;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 20/03/2023
 */
public class UnusedDeclarationInspectionState implements InspectionToolState<UnusedDeclarationInspectionState>
{
	public boolean ADD_MAINS_TO_ENTRIES = true;
	public boolean ADD_APPLET_TO_ENTRIES = true;
	public boolean ADD_SERVLET_TO_ENTRIES = true;
	public boolean ADD_NONJAVA_TO_ENTRIES = true;

	public Map<String, EntryPointState> ENTRY_POINTS = new LinkedHashMap<>();

	public boolean isAddMainsEnabled()
	{
		return ADD_MAINS_TO_ENTRIES;
	}

	public boolean isAddAppletEnabled()
	{
		return ADD_APPLET_TO_ENTRIES;
	}

	public boolean isAddServletEnabled()
	{
		return ADD_SERVLET_TO_ENTRIES;
	}

	public boolean isAddNonJavaUsedEnabled()
	{
		return ADD_NONJAVA_TO_ENTRIES;
	}

	@Nonnull
	@SuppressWarnings("unchecked")
	public <State extends EntryPointState> State getEntryPointState(EntryPointProvider<State> provider)
	{
		EntryPointState state = ENTRY_POINTS.get(provider.getId());
		if(state != null)
		{
			return (State) state;
		}

		return provider.createState();
	}

	@Nullable
	@Override
	public UnusedDeclarationInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(UnusedDeclarationInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
