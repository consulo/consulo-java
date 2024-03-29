/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.disposer.Disposable;
import consulo.ide.ServiceManager;
import consulo.logging.Logger;
import consulo.proxy.EventDispatcher;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.SkipDefaultsSerializationFilter;
import consulo.util.xml.serializer.XmlSerializer;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Tag;
import consulo.util.xml.serializer.annotation.Transient;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import org.jdom.Element;

import java.util.*;

@Singleton
@State(name = "JavaDebuggerSettings", storages = @Storage("java.debugger.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class DebuggerSettings implements Cloneable, PersistentStateComponent<Element> {
	private static final Logger LOG = Logger.getInstance(DebuggerSettings.class);
	public static final int SOCKET_TRANSPORT = 0;
	public static final int SHMEM_TRANSPORT = 1;

	public static final String SUSPEND_ALL = "SuspendAll";
	public static final String SUSPEND_THREAD = "SuspendThread";
	public static final String SUSPEND_NONE = "SuspendNone";

	public static final String RUN_HOTSWAP_ALWAYS = "RunHotswapAlways";
	public static final String RUN_HOTSWAP_NEVER = "RunHotswapNever";
	public static final String RUN_HOTSWAP_ASK = "RunHotswapAsk";

	public static final String EVALUATE_FINALLY_ALWAYS = "EvaluateFinallyAlways";
	public static final String EVALUATE_FINALLY_NEVER = "EvaluateFinallyNever";
	public static final String EVALUATE_FINALLY_ASK = "EvaluateFinallyAsk";

	private static final ClassFilter[] DEFAULT_STEPPING_FILTERS = new ClassFilter[]{
		new ClassFilter("com.sun.*"),
		new ClassFilter("java.*"),
		new ClassFilter("javax.*"),
		new ClassFilter("org.omg.*"),
		new ClassFilter("sun.*"),
		new ClassFilter("jdk.internal.*"),
		new ClassFilter("junit.*"),
		new ClassFilter("org.junit.*"),
		new ClassFilter("com.intellij.rt.*"),
		new ClassFilter("com.yourkit.runtime.*"),
		new ClassFilter("com.springsource.loaded.*"),
		new ClassFilter("org.springsource.loaded.*"),
		new ClassFilter("javassist.*"),
		new ClassFilter("org.apache.webbeans.*"),
		new ClassFilter("com.ibm.ws.*"),
	};

	public boolean TRACING_FILTERS_ENABLED = true;
	public int DEBUGGER_TRANSPORT;
	public boolean FORCE_CLASSIC_VM = true;
	public boolean DISABLE_JIT;
	public boolean SHOW_ALTERNATIVE_SOURCE = true;
	public boolean HOTSWAP_IN_BACKGROUND = true;
	public boolean SKIP_SYNTHETIC_METHODS = true;
	public boolean SKIP_CONSTRUCTORS;
	public boolean SKIP_GETTERS;
	public boolean SKIP_CLASSLOADERS = true;

	public String RUN_HOTSWAP_AFTER_COMPILE = RUN_HOTSWAP_ASK;
	public boolean COMPILE_BEFORE_HOTSWAP = true;
	public boolean HOTSWAP_HANG_WARNING_ENABLED = false;

	public volatile boolean WATCH_RETURN_VALUES = false;
	public volatile boolean AUTO_VARIABLES_MODE = false;

	public volatile boolean KILL_PROCESS_IMMEDIATELY = false;

	public String EVALUATE_FINALLY_ON_POP_FRAME = EVALUATE_FINALLY_ASK;

	public boolean RESUME_ONLY_CURRENT_THREAD = false;

	private ClassFilter[] mySteppingFilters = DEFAULT_STEPPING_FILTERS;

	private List<CapturePoint> myCapturePoints = new ArrayList<>();
	public boolean CAPTURE_VARIABLES;
	private final EventDispatcher<CapturePointsSettingsListener> myDispatcher = EventDispatcher.create(CapturePointsSettingsListener.class);

	private Map<String, ContentState> myContentStates = new LinkedHashMap<>();

	// transient - custom serialization
	@Transient
	public ClassFilter[] getSteppingFilters() {
		final ClassFilter[] rv = new ClassFilter[mySteppingFilters.length];
		for (int idx = 0; idx < rv.length; idx++) {
			rv[idx] = mySteppingFilters[idx].clone();
		}
		return rv;
	}

	public static DebuggerSettings getInstance() {
		return ServiceManager.getService(DebuggerSettings.class);
	}

	public void setSteppingFilters(ClassFilter[] steppingFilters) {
		mySteppingFilters = steppingFilters != null ? steppingFilters : ClassFilter.EMPTY_ARRAY;
	}

	@Nullable
	@Override
	public Element getState() {
		Element state = XmlSerializer.serialize(this, new SkipDefaultsSerializationFilter());

		if (!Arrays.equals(DEFAULT_STEPPING_FILTERS, mySteppingFilters)) {
			DebuggerUtilsEx.writeFilters(state, "filter", mySteppingFilters);
		}

		for (ContentState eachState : myContentStates.values()) {
			final Element content = new Element("content");
			if (eachState.write(content)) {
				state.addContent(content);
			}
		}
		return state;
	}

	@Override
	public void loadState(Element state) {
		XmlSerializer.deserializeInto(this, state);

		List<Element> steppingFiltersElement = state.getChildren("filter");
		if (steppingFiltersElement.isEmpty()) {
			setSteppingFilters(DEFAULT_STEPPING_FILTERS);
		}
		else {
			setSteppingFilters(DebuggerUtilsEx.readFilters(steppingFiltersElement));
		}

		myContentStates.clear();
		for (Element content : state.getChildren("content")) {
			ContentState contentState = new ContentState(content);
			myContentStates.put(contentState.getType(), contentState);
		}
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof DebuggerSettings)) {
			return false;
		}
		DebuggerSettings secondSettings = (DebuggerSettings)obj;

		return TRACING_FILTERS_ENABLED == secondSettings.TRACING_FILTERS_ENABLED && DEBUGGER_TRANSPORT == secondSettings.DEBUGGER_TRANSPORT && StringUtil
			.equals(EVALUATE_FINALLY_ON_POP_FRAME,
							secondSettings.EVALUATE_FINALLY_ON_POP_FRAME) && FORCE_CLASSIC_VM == secondSettings.FORCE_CLASSIC_VM && DISABLE_JIT == secondSettings.DISABLE_JIT && SHOW_ALTERNATIVE_SOURCE ==
			secondSettings.SHOW_ALTERNATIVE_SOURCE && KILL_PROCESS_IMMEDIATELY == secondSettings.KILL_PROCESS_IMMEDIATELY && HOTSWAP_IN_BACKGROUND == secondSettings.HOTSWAP_IN_BACKGROUND &&
			SKIP_SYNTHETIC_METHODS == secondSettings.SKIP_SYNTHETIC_METHODS && SKIP_CLASSLOADERS == secondSettings.SKIP_CLASSLOADERS && SKIP_CONSTRUCTORS == secondSettings.SKIP_CONSTRUCTORS &&
			SKIP_GETTERS == secondSettings.SKIP_GETTERS && RESUME_ONLY_CURRENT_THREAD == secondSettings.RESUME_ONLY_CURRENT_THREAD && COMPILE_BEFORE_HOTSWAP == secondSettings
			.COMPILE_BEFORE_HOTSWAP && HOTSWAP_HANG_WARNING_ENABLED == secondSettings.HOTSWAP_HANG_WARNING_ENABLED && (RUN_HOTSWAP_AFTER_COMPILE != null ? RUN_HOTSWAP_AFTER_COMPILE
			.equals
				(secondSettings.RUN_HOTSWAP_AFTER_COMPILE) : secondSettings.RUN_HOTSWAP_AFTER_COMPILE == null) && DebuggerUtilsEx.filterEquals(
			mySteppingFilters,
			secondSettings.mySteppingFilters) &&
			myCapturePoints.equals(((DebuggerSettings)obj).myCapturePoints);
	}

	@Override
	public DebuggerSettings clone() {
		try {
			final DebuggerSettings cloned = (DebuggerSettings)super.clone();
			cloned.myContentStates = new HashMap<>();
			for (Map.Entry<String, ContentState> entry : myContentStates.entrySet()) {
				cloned.myContentStates.put(entry.getKey(), entry.getValue().clone());
			}
			cloned.mySteppingFilters = new ClassFilter[mySteppingFilters.length];
			for (int idx = 0; idx < mySteppingFilters.length; idx++) {
				cloned.mySteppingFilters[idx] = mySteppingFilters[idx].clone();
			}
			cloned.myCapturePoints = cloneCapturePoints();
			return cloned;
		}
		catch (CloneNotSupportedException e) {
			LOG.error(e);
		}
		return null;
	}

	List<CapturePoint> cloneCapturePoints() {
		try {
			ArrayList<CapturePoint> res = new ArrayList<>(myCapturePoints.size());
			for (CapturePoint point : myCapturePoints) {
				res.add(point.clone());
			}
			return res;
		}
		catch (CloneNotSupportedException e) {
			LOG.error(e);
		}
		return Collections.emptyList();
	}

	@Tag("capture-points")
	@AbstractCollection(surroundWithTag = false)
	public List<CapturePoint> getCapturePoints() {
		return myCapturePoints;
	}

	// for serialization, do not remove
	@SuppressWarnings("unused")
	public void setCapturePoints(List<CapturePoint> capturePoints) {
		myCapturePoints = capturePoints;
		myDispatcher.getMulticaster().capturePointsChanged();
	}

	public void addCapturePointsSettingsListener(CapturePointsSettingsListener listener, Disposable disposable) {
		myDispatcher.addListener(listener, disposable);
	}

	public static class ContentState implements Cloneable {
		private final String myType;
		private boolean myMinimized;
		private String mySelectedTab;
		private double mySplitProportion;
		private boolean myDetached;
		private boolean myHorizontalToolbar;
		private boolean myMaximized;

		public ContentState(final String type) {
			myType = type;
		}

		public ContentState(Element element) {
			myType = element.getAttributeValue("type");
			myMinimized = "true".equalsIgnoreCase(element.getAttributeValue("minimized"));
			myMaximized = "true".equalsIgnoreCase(element.getAttributeValue("maximized"));
			mySelectedTab = element.getAttributeValue("selected");
			final String split = element.getAttributeValue("split");
			if (split != null) {
				mySplitProportion = Double.valueOf(split);
			}
			myDetached = "true".equalsIgnoreCase(element.getAttributeValue("detached"));
			myHorizontalToolbar = !"false".equalsIgnoreCase(element.getAttributeValue("horizontal"));
		}

		public boolean write(final Element element) {
			element.setAttribute("type", myType);
			element.setAttribute("minimized", Boolean.valueOf(myMinimized).toString());
			element.setAttribute("maximized", Boolean.valueOf(myMaximized).toString());
			if (mySelectedTab != null) {
				element.setAttribute("selected", mySelectedTab);
			}
			element.setAttribute("split", Double.toString(mySplitProportion));
			element.setAttribute("detached", Boolean.valueOf(myDetached).toString());
			element.setAttribute("horizontal", Boolean.valueOf(myHorizontalToolbar).toString());
			return true;
		}

		public String getType() {
			return myType;
		}

		public String getSelectedTab() {
			return mySelectedTab;
		}

		public boolean isMinimized() {
			return myMinimized;
		}

		public void setMinimized(final boolean minimized) {
			myMinimized = minimized;
		}

		public void setMaximized(final boolean maximized) {
			myMaximized = maximized;
		}

		public boolean isMaximized() {
			return myMaximized;
		}

		public void setSelectedTab(final String selectedTab) {
			mySelectedTab = selectedTab;
		}

		public void setSplitProportion(double splitProportion) {
			mySplitProportion = splitProportion;
		}

		public double getSplitProportion(double defaultValue) {
			return mySplitProportion <= 0 || mySplitProportion >= 1 ? defaultValue : mySplitProportion;
		}

		public void setDetached(final boolean detached) {
			myDetached = detached;
		}

		public boolean isDetached() {
			return myDetached;
		}

		public boolean isHorizontalToolbar() {
			return myHorizontalToolbar;
		}

		public void setHorizontalToolbar(final boolean horizontalToolbar) {
			myHorizontalToolbar = horizontalToolbar;
		}

		@Override
		public ContentState clone() throws CloneNotSupportedException {
			return (ContentState)super.clone();
		}
	}

	public interface CapturePointsSettingsListener extends EventListener {
		void capturePointsChanged();
	}
}
