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
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerUtilsAsync;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.SimpleStackFrameContext;
import com.intellij.java.debugger.impl.engine.CompoundPositionManager;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.settings.ThreadsViewSettings;
import com.intellij.java.debugger.impl.ui.tree.StackFrameDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import consulo.application.ReadAction;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.frame.XValueMarkers;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.internal.com.sun.jdi.*;
import consulo.language.editor.FileColorManager;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Nodes of this type cannot be updated, because StackFrame objects become invalid as soon as VM has been resumed
 */
public class StackFrameDescriptorImpl extends NodeDescriptorImpl implements StackFrameDescriptor {
    private final StackFrameProxyImpl myFrame;
    private int myUiIndex;
    private String myName = null;
    private Location myLocation;
    private @Nullable Method myMethod;
    private @Nullable MethodsTracker.MethodOccurrence myMethodOccurrence;
    private boolean myIsSynthetic;
    private boolean myIsInLibraryContent;
    private ObjectReference myThisObject;
    private Color myBackgroundColor;
    private SourcePosition mySourcePosition;

    /**
     * Prefer this constructor over {@link #StackFrameDescriptorImpl(MethodsTracker, StackFrameProxyImpl)}
     * if tracking recursive calls is not required.
     */
    public StackFrameDescriptorImpl(StackFrameProxyImpl frame) {
        this(frame, null);
    }

    /**
     * @deprecated Use {@link #StackFrameDescriptorImpl(MethodsTracker, StackFrameProxyImpl)} if you aim at tracking recusrion calls,
     *             or {@link #StackFrameDescriptorImpl(StackFrameProxyImpl)} otherwise.
     */
    @Deprecated(forRemoval = true)
    public StackFrameDescriptorImpl(StackFrameProxyImpl frame,
                                    @Nullable MethodsTracker tracker) {
        this(frame, false, null, tracker,
             ContextUtil.getSourcePosition(new SimpleStackFrameContext(frame, frame.getVirtualMachine().getDebugProcess())));
    }

    /**
     * @param tracker Used to show recursion count. If your implementation doesn't need it,
     *                consider using {@link #StackFrameDescriptorImpl(StackFrameProxyImpl)} instead.
     *                <b>{@code tracker} should be shared between all frames in the stacktrace!</b>
     */
    public StackFrameDescriptorImpl(MethodsTracker tracker,
                                    StackFrameProxyImpl frame) {
        this(frame, false, null, tracker,
             ContextUtil.getSourcePosition(new SimpleStackFrameContext(frame, frame.getVirtualMachine().getDebugProcess())));
    }

    private StackFrameDescriptorImpl(StackFrameProxyImpl frame,
                                     boolean useMethod,
                                     @Nullable Method method,
                                     @Nullable MethodsTracker tracker,
                                     @Nullable SourcePosition sourcePosition) {
        myFrame = frame;

        try {
            myUiIndex = frame.getFrameIndex();
            myLocation = frame.location();
            if (!getValueMarkers().isEmpty()) {
                getThisObject(); // init this object for markup
            }
            myMethod = useMethod ? method : DebuggerUtilsEx.getMethod(myLocation);
            myMethodOccurrence = tracker == null ? null : tracker.getMethodOccurrence(myUiIndex, myMethod);
            myIsSynthetic = DebuggerUtils.isSynthetic(myMethod);
            mySourcePosition = sourcePosition;
            PsiFile psiFile = mySourcePosition != null ? mySourcePosition.getFile() : null;
            VirtualFile file = psiFile != null ? psiFile.getVirtualFile() : null;
            Project project = getDebugProcess().getProject();
            myIsInLibraryContent = DebuggerUtilsEx.isInLibraryContent(file, project);
            // Consulo-specific: StackFrameDescriptor.getBackgroundColor() contract
            myBackgroundColor = file != null ? ReadAction.compute(() -> FileColorManager.getInstance(project).getFileColor(file)) : null;
        }
        catch (InternalException | EvaluateException e) {
            LOG.info(e);
            myLocation = null;
            myMethodOccurrence = null;
            myIsSynthetic = false;
            myIsInLibraryContent = false;
        }
    }

    private static CompletableFuture<SourcePosition> getSourcePositionAsync(Location location, StackFrameProxyImpl frame) {
        try {
            CompoundPositionManager positionManager = ((DebugProcessImpl) frame.getVirtualMachine().getDebugProcess()).getPositionManager();
            return positionManager.getSourcePositionFuture(location);
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static CompletableFuture<StackFrameDescriptorImpl> createAsync(StackFrameProxyImpl frame,
                                                                          MethodsTracker tracker) {
        CompletableFuture<Location> locationAsync = frame.locationAsync();
        CompletableFuture<SourcePosition> positionAsync =
            locationAsync.thenCompose(location -> getSourcePositionAsync(location, frame));
        return locationAsync
            .thenCompose(DebuggerUtilsAsync::method)
            .thenCombine(positionAsync, (method, position) -> {
                DebuggerManagerThreadImpl.assertIsManagerThread();
                return new StackFrameDescriptorImpl(frame, true, method, tracker, position);
            })
            .exceptionally(throwable -> {
                Throwable exception = DebuggerUtilsAsync.unwrap(throwable);
                if (exception instanceof EvaluateException) {
                    // TODO: simplify when only async method left
                    if (!(exception.getCause() instanceof InvalidStackFrameException)) {
                        LOG.error(new Exception(exception));
                    }
                    DebuggerManagerThreadImpl.assertIsManagerThread();
                    return new StackFrameDescriptorImpl(frame, tracker); // fallback to sync
                }
                throw (RuntimeException) throwable;
            });
    }

    public boolean canDrop() {
        return !myFrame.isBottom() && myMethodOccurrence != null && myMethodOccurrence.canDrop();
    }

    public int getUiIndex() {
        return myUiIndex;
    }

    @Override
    public StackFrameProxyImpl getFrameProxy() {
        return myFrame;
    }

    @Override
    public DebugProcess getDebugProcess() {
        return myFrame.getVirtualMachine().getDebugProcess();
    }

    @Override
    public Color getBackgroundColor() {
        return myBackgroundColor;
    }

    public @Nullable Method getMethod() {
        return myMethod;
    }

    public int getOccurrenceIndex() {
        return myMethodOccurrence == null ? 0 : myMethodOccurrence.getIndex();
    }

    public boolean isRecursiveCall() {
        return myMethodOccurrence != null && myMethodOccurrence.isRecursive();
    }

    public @Nullable ValueMarkup getValueMarkup() {
        Map<?, ValueMarkup> markers = getValueMarkers();
        if (!markers.isEmpty() && myThisObject != null) {
            return markers.get(myThisObject);
        }
        return null;
    }

    private Map<?, ValueMarkup> getValueMarkers() {
        DebugProcess process = myFrame.getVirtualMachine().getDebugProcess();
        if (process instanceof DebugProcessImpl debugProcess) {
            XDebugSession session = debugProcess.getSession().getXDebugSession();
            XValueMarkers<?, ?> markers = session == null ? null : session.getValueMarkers();
            if (markers != null) {
                return markers.getAllMarkers();
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    protected LocalizeValue calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
        DebuggerManagerThreadImpl.assertIsManagerThread();

        if (myLocation == null) {
            return LocalizeValue.empty();
        }
        ThreadsViewSettings settings = ThreadsViewSettings.getInstance();
        final StringBuilder label = new StringBuilder();
        Method method = myMethod;
        if (method != null) {
            myName = method.name();
            label.append(settings.SHOW_ARGUMENTS_TYPES ? DebuggerUtilsEx.methodNameWithArguments(method) : myName);
        }
        if (settings.SHOW_LINE_NUMBER) {
            String lineNumber;
            try {
                lineNumber = Integer.toString(myLocation.lineNumber());
            }
            catch (InternalError e) {
                lineNumber = e.toString();
            }
            if (lineNumber != null) {
                label.append(':');
                label.append(lineNumber);
            }
        }
        if (settings.SHOW_CLASS_NAME) {
            String name;
            try {
                ReferenceType refType = myLocation.declaringType();
                name = refType != null ? refType.name() : null;
            }
            catch (InternalError e) {
                name = e.toString();
            }
            if (name != null) {
                label.append(", ");
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex < 0) {
                    label.append(name);
                }
                else {
                    label.append(name.substring(dotIndex + 1));
                    if (settings.SHOW_PACKAGE_NAME) {
                        label.append(" {");
                        label.append(name.substring(0, dotIndex));
                        label.append("}");
                    }
                }
            }
        }
        if (settings.SHOW_SOURCE_NAME) {
            try {
                String sourceName;
                try {
                    sourceName = myLocation.sourceName();
                }
                catch (InternalError e) {
                    sourceName = e.toString();
                }
                label.append(", ");
                label.append(sourceName);
            }
            catch (AbsentInformationException ignored) {
            }
        }
        return LocalizeValue.of(label.toString());
    }

    public final boolean stackFramesEqual(StackFrameDescriptorImpl d) {
        return getFrameProxy().equals(d.getFrameProxy());
    }

    @Override
    public boolean isExpandable() {
        return true;
    }

    @Override
    public final void setContext(EvaluationContextImpl context) {
    }

    public boolean isSynthetic() {
        return myIsSynthetic;
    }

    public boolean isInLibraryContent() {
        return myIsInLibraryContent;
    }

    public @Nullable Location getLocation() {
        return myLocation;
    }

    public SourcePosition getSourcePosition() {
        return mySourcePosition;
    }

    public @Nullable ObjectReference getThisObject() {
        if (myThisObject == null) {
            try {
                myThisObject = myFrame.thisObject();
            }
            catch (EvaluateException e) {
                LOG.info(e);
            }
        }
        return myThisObject;
    }
}
