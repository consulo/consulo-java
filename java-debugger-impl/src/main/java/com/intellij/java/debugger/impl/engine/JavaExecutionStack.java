/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsAsync;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import com.intellij.java.debugger.impl.ui.impl.watch.MethodsTracker;
import com.intellij.java.debugger.impl.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.application.util.registry.Registry;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.debug.setting.XDebuggerSettingsManager;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.internal.com.sun.tools.jdi.ThreadGroupReferenceImpl;
import consulo.internal.com.sun.tools.jdi.ThreadReferenceImpl;
import consulo.logging.Logger;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author egor
 */
public class JavaExecutionStack extends XExecutionStack {
    private static final Logger LOG = Logger.getInstance(JavaExecutionStack.class);

    private final ThreadReferenceProxyImpl myThreadProxy;
    private final DebugProcessImpl myDebugProcess;
    private final CompletableFuture<List<XStackFrame>> myTopFrames = new CompletableFuture<>();
    private final MethodsTracker myTracker = new MethodsTracker();

    public JavaExecutionStack(ThreadReferenceProxyImpl threadProxy, DebugProcessImpl debugProcess, boolean current) {
        super(calcRepresentation(threadProxy), calcIcon(threadProxy, current));
        myThreadProxy = threadProxy;
        myDebugProcess = debugProcess;
    }

    private JavaExecutionStack(String displayName,
                               @Nullable Image icon,
                               ThreadReferenceProxyImpl threadProxy,
                               DebugProcessImpl debugProcess) {
        super(displayName, icon);
        myThreadProxy = threadProxy;
        myDebugProcess = debugProcess;
    }

    public static CompletableFuture<@Nullable JavaExecutionStack> create(ThreadReferenceProxyImpl threadProxy,
                                                                         DebugProcessImpl debugProcess,
                                                                         boolean current) {
        return calcRepresentationAsync(threadProxy)
            .thenCombine(calcIconAsync(threadProxy, current),
                (text, icon) -> {
                    return new JavaExecutionStack(text, icon, threadProxy, debugProcess);
                })
            .handle((stack, throwable) -> {
                if (throwable instanceof ObjectCollectedException) {
                    return null;
                }
                if (throwable != null) {
                    throw new CompletionException(throwable);
                }
                return stack;
            });
    }

    private static Image calcIcon(ThreadReferenceProxyImpl threadProxy, boolean current) {
        // D-A1: thread daemon/virtual icon overlays are not ported, the base icon is the final icon
        if (current) {
            return threadProxy.isSuspended()
                ? ExecutionDebugIconGroup.threadThreadcurrent()
                : ExecutionDebugIconGroup.threadThreadrunning();
        }
        else if (threadProxy.isAtBreakpoint()) {
            return ExecutionDebugIconGroup.threadThreadatbreakpoint();
        }
        else if (threadProxy.isSuspended()) {
            return ExecutionDebugIconGroup.threadThreadsuspended();
        }
        else {
            return ExecutionDebugIconGroup.threadThreadrunning();
        }
    }

    private static CompletableFuture<Image> calcIconAsync(ThreadReferenceProxyImpl threadProxy, boolean current) {
        ThreadReference ref = threadProxy.getThreadReference();
        if (!DebuggerUtilsAsync.isAsyncEnabled() || !(ref instanceof ThreadReferenceImpl threadReference)) {
            return CompletableFuture.completedFuture(calcIcon(threadProxy, current));
        }
        // D-A1: thread daemon/virtual icon overlays are not ported, the base icon is the final icon
        return calcBaseIconAsync(threadReference, current);
    }

    private static CompletableFuture<Image> calcBaseIconAsync(ThreadReferenceImpl threadReference, boolean current) {
        if (current) {
            return threadReference.isSuspendedAsync()
                .<Image>thenApply(suspended -> suspended
                    ? ExecutionDebugIconGroup.threadThreadcurrent()
                    : ExecutionDebugIconGroup.threadThreadrunning());
        }
        return threadReference.isAtBreakpointAsync().thenCompose(atBreakpoint -> {
            if (atBreakpoint) {
                return CompletableFuture.<Image>completedFuture(ExecutionDebugIconGroup.threadThreadatbreakpoint());
            }
            return threadReference.isSuspendedAsync()
                .<Image>thenApply(suspended -> suspended
                    ? ExecutionDebugIconGroup.threadThreadsuspended()
                    : ExecutionDebugIconGroup.threadThreadrunning());
        });
    }

    public ThreadReferenceProxyImpl getThreadProxy() {
        return myThreadProxy;
    }

    public final void initTopFrame() {
        if (myTopFrames.isDone()) {
            return;
        }
        DebuggerManagerThreadImpl.assertIsManagerThread();
        try {
            StackFrameProxyImpl frame = myThreadProxy.frame(0);
            if (frame != null) {
                myTopFrames.complete(createStackFrames(frame));
            }
            // D-A4: UsageTracker.topFrameInitialized(getTopFrame()) is not ported
        }
        catch (EvaluateException e) {
            LOG.info(e);
        }
    }

    public List<XStackFrame> createStackFrames(StackFrameProxyImpl stackFrameProxy) {
        return createFrames(new StackFrameDescriptorImpl(myTracker, stackFrameProxy));
    }

    private CompletableFuture<List<XStackFrame>> createStackFramesAsync(StackFrameProxyImpl stackFrameProxy) {
        if (!Registry.is("debugger.async.frames", true)) {
            return CompletableFuture.completedFuture(createStackFrames(stackFrameProxy));
        }

        return StackFrameDescriptorImpl.createAsync(stackFrameProxy, myTracker)
            .thenCompose(this::createFramesAsync);
    }

    private List<XStackFrame> createFrames(StackFrameDescriptorImpl descriptor) {
        // D-A4: markCallerFrame(descriptor) is not ported

        // a custom frame from a position manager (Consulo's PositionManagerEx.createStackFrame(proxy, process, location) API)
        Location location = descriptor.getLocation();
        if (location != null) {
            XStackFrame customFrame =
                myDebugProcess.getPositionManager().createStackFrame(descriptor.getFrameProxy(), myDebugProcess, location);
            if (customFrame != null) {
                return Collections.singletonList(customFrame);
            }
        }

        return Collections.singletonList(new JavaStackFrame(descriptor, true));
    }

    private CompletableFuture<List<XStackFrame>> createFramesAsync(StackFrameDescriptorImpl descriptor) {
        // D-A4: markCallerFrame(descriptor) is not ported

        return myDebugProcess.getPositionManager().createStackFramesAsync(descriptor)
            .thenApply(customFrames -> {
                if (customFrames != null) {
                    return customFrames;
                }
                return Collections.singletonList(new JavaStackFrame(descriptor, true));
            });
    }

    @Override
    public @Nullable XStackFrame getTopFrame() {
        List<XStackFrame> topFrames = myTopFrames.getNow(Collections.emptyList());
        return ContainerUtil.getFirstItem(topFrames);
    }

    /**
     * Not an override: Consulo's {@link XExecutionStack} has no asynchronous top frame accessor (D-A5).
     */
    public CompletableFuture<@Nullable XStackFrame> getTopFrameAsync() {
        return myTopFrames.thenApply(frames -> ContainerUtil.getFirstItem(frames));
    }

    @Override
    public void computeStackFrames(XStackFrameContainer container) {
        // Consulo's XExecutionStack has no firstFrameIndex parameter: the frames are always computed from the top one
        computeStackFrames(0, container);
    }

    public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
        if (container.isObsolete()) {
            return;
        }
        DebuggerContextImpl debuggerContext = myDebugProcess.getDebuggerContext();
        myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext, myThreadProxy) {
            @Override
            public Priority getPriority() {
                return Priority.NORMAL;
            }

            @Override
            public void threadAction(SuspendContextImpl suspendContext) {
                if (!myThreadProxy.isSuspended()) {
                    container.errorOccurred(JavaDebuggerLocalize.framePanelFramesNotAvailable());
                    return;
                }

                if (container.isObsolete()) {
                    return;
                }
                int status = myThreadProxy.status();
                if (status == ThreadReference.THREAD_STATUS_ZOMBIE) {
                    container.errorOccurred(JavaDebuggerLocalize.framePanelThreadFinished());
                }
                // isCollected is not needed as ObjectCollectedException was handled in status call
                else if (/*!myThreadProxy.isCollected() && */myDebugProcess.getSuspendManager().isSuspended(myThreadProxy)) {
                    if (!(status == ThreadReference.THREAD_STATUS_UNKNOWN) && !(status == ThreadReference.THREAD_STATUS_NOT_STARTED)) {
                        try {
                            int added = 0;
                            Iterator<StackFrameProxyImpl> iterator = myThreadProxy.frames().iterator();
                            if (iterator.hasNext() && firstFrameIndex > 0) {
                                iterator.next();
                                added++;
                            }
                            myDebugProcess.getManagerThread().schedule(
                                new AppendFrameCommand(suspendContext, iterator, container, added, firstFrameIndex));
                        }
                        catch (EvaluateException e) {
                            container.errorOccurred(e.getMessage());
                        }
                    }
                }
                else {
                    container.errorOccurred(JavaDebuggerLocalize.framePanelFramesNotAvailable());
                }
            }

            @Override
            protected void commandCancelled() {
                container.errorOccurred(JavaDebuggerLocalize.framePanelFramesNotAvailable());
            }
        });
    }

    private class AppendFrameCommand extends SuspendContextCommandImpl {
        private final @Nullable Iterator<StackFrameProxyImpl> myStackFramesIterator;
        private final XStackFrameContainer myContainer;
        private int myAdded;
        private final int mySkip;
        private final @Nullable List<? extends StackFrameItem> myAsyncStack;
        private final @Nullable List<? extends StackFrameItem> myCreationStack;
        private int myAddedAsync;
        private boolean mySeparator;

        private AppendFrameCommand(SuspendContextImpl suspendContext,
                                   @Nullable Iterator<StackFrameProxyImpl> stackFramesIterator,
                                   XStackFrameContainer container,
                                   int added,
                                   int skip,
                                   @Nullable List<? extends StackFrameItem> asyncStack,
                                   @Nullable List<? extends StackFrameItem> creationStack,
                                   int addedAsync,
                                   boolean separator) {
            super(suspendContext);
            myStackFramesIterator = stackFramesIterator;
            myContainer = container;
            myAdded = added;
            mySkip = skip;
            myAsyncStack = asyncStack;
            myCreationStack = creationStack;
            myAddedAsync = addedAsync;
            mySeparator = separator;
        }

        AppendFrameCommand(SuspendContextImpl suspendContext,
                           Iterator<StackFrameProxyImpl> iterator,
                           XStackFrameContainer container,
                           int added,
                           int firstFrameIndex) {
            this(suspendContext, iterator, container, added, firstFrameIndex, null, null, 0, true);
        }

        @Override
        public Priority getPriority() {
            return myAdded <= StackFrameProxyImpl.FRAMES_BATCH_MAX ? Priority.NORMAL : Priority.LOW;
        }

        private void addStackFrames(List<XStackFrame> frames, boolean last) {
            myAdded += frames.size();
            myContainer.addStackFrames(frames, last);
            // Consulo's MethodsTracker has no finish(): JB's myTracker.finish() on the last batch is not ported
        }

        private boolean addFrameIfNeeded(XStackFrame frame, boolean last) {
            if (myAdded >= mySkip) {
                addStackFrames(Collections.singletonList(frame), last);
                return true;
            }
            if (last) {
                addStackFrames(Collections.emptyList(), true);
            }
            return false;
        }

        @Override
        public void contextAction(SuspendContextImpl suspendContext) {
            if (myContainer.isObsolete()) {
                return;
            }
            if (myStackFramesIterator != null && myStackFramesIterator.hasNext()) {
                StackFrameProxyImpl frameProxy;
                CompletableFuture<List<XStackFrame>> framesAsync;
                boolean first = myAdded == 0;
                frameProxy = myStackFramesIterator.next();
                if (first && myTopFrames.isDone()) {
                    framesAsync = myTopFrames;
                }
                else {
                    framesAsync = createStackFramesAsync(frameProxy).thenApply(fs -> {
                        if (first) {
                            myTopFrames.complete(fs);
                        }
                        return fs;
                    });
                }

                framesAsync.thenAccept(frames -> {
                    for (XStackFrame frame : Lists.notNullize(frames)) {
                        if (first || showFrame(frame)) {
                            // JB re-runs descriptor.updateRepresentationNoNotify(null, repaint-on-icon-change listener) here.
                            // In Consulo JavaStackFrame(descriptor, true) already updates the representation in its constructor
                            // and StackFrameDescriptorImpl.calcRepresentation never notifies the listener (no calcIconLater),
                            // so the call is omitted (NodeDescriptorImpl.updateRepresentationNoNotify is also protected here).
                            addFrameIfNeeded(frame, false);
                        }
                        // D7: hidden frames are not folded into a placeholder (no rememberHiddenFrame)
                    }

                    // replace the rest with the related stack (if available)
                    if (myAsyncStack != null) {
                        schedule(suspendContext, null, myAsyncStack, null, true);
                        return;
                    }

                    List<StackFrameItem> relatedStack = null;
                    List<? extends StackFrameItem> creationStack = myCreationStack;
                    XStackFrame topFrame = ContainerUtil.getFirstItem(frames);
                    JavaDebugProcess xdebugProcess = suspendContext.getDebugProcess().getXdebugProcess();
                    if (xdebugProcess != null &&
                        AsyncStacksUtils.isAsyncStacksEnabled(xdebugProcess.getSession()) &&
                        topFrame instanceof JavaStackFrame frame) {
                        if (creationStack == null) {
                            creationStack =
                                CreationStackTraceProvider.EP.computeSafeIfAny(p -> p.getCreationStackTrace(frame, suspendContext));
                        }
                        relatedStack = AsyncStackTraceProvider.EP.computeSafeIfAny(p -> p.getAsyncStackTrace(frame, suspendContext));
                        if (relatedStack != null) {
                            schedule(suspendContext, null, relatedStack, null, true);
                            return;
                        }
                        // append agent stack after the next frame
                        relatedStack = AsyncStacksUtils.getAgentRelatedStack(frameProxy, suspendContext);
                    }

                    schedule(suspendContext, myStackFramesIterator, relatedStack, creationStack, false);
                }).exceptionally(throwable -> DebuggerUtilsAsync.logError(throwable));
            }
            else if (myAsyncStack != null && myAddedAsync < myAsyncStack.size()) {
                appendRelatedStack(suspendContext, myAsyncStack.subList(myAddedAsync, myAsyncStack.size()));
            }
            else if (myCreationStack != null && myAddedAsync < myCreationStack.size()) {
                appendRelatedStack(suspendContext, myCreationStack.subList(myAddedAsync, myCreationStack.size()));
            }
            else {
                addStackFrames(Collections.emptyList(), true);
            }
        }

        private void schedule(SuspendContextImpl suspendContext,
                              @Nullable Iterator<StackFrameProxyImpl> stackFramesIterator,
                              @Nullable List<? extends StackFrameItem> asyncStackFrames,
                              @Nullable List<? extends StackFrameItem> creationStackFrames,
                              boolean separator) {
            myDebugProcess.getManagerThread().schedule(
                new AppendFrameCommand(suspendContext, stackFramesIterator, myContainer,
                    myAdded, mySkip, asyncStackFrames, creationStackFrames, myAddedAsync, separator));
        }

        void appendRelatedStack(SuspendContextImpl suspendContext, List<? extends StackFrameItem> asyncStack) {
            for (StackFrameItem stackFrame : asyncStack) {
                if (myAddedAsync > AsyncStacksUtils.getMaxStackLength()) {
                    addFrameIfNeeded(new XStackFrame() {
                        @Override
                        public void customizePresentation(ColoredTextContainer component) {
                            component.append(JavaDebuggerLocalize.labelTooManyFramesRestTruncated(),
                                SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
                        }
                    }, true);
                    return;
                }
                myAddedAsync++;
                if (stackFrame == null) {
                    mySeparator = true;
                    continue;
                }
                myDebugProcess.getPositionManager().getSourcePositionFuture(stackFrame.location()).thenAccept(sourcePosition -> {
                    XStackFrame newFrame = stackFrame.createFrame(myDebugProcess, sourcePosition);
                    appendFrame(suspendContext, newFrame);
                });
                return;
            }
            // only separators were left: continue with the creation stack (if any) or finish
            // (JB falls through without scheduling, which leaves the frame list incomplete)
            schedule(suspendContext, null, myAsyncStack, myCreationStack, mySeparator);
        }

        private void appendFrame(SuspendContextImpl suspendContext, @Nullable XStackFrame newFrame) {
            if (newFrame != null) {
                if (showFrame(newFrame)) {
                    if (mySeparator) {
                        StackFrameItem.setWithSeparator(newFrame);
                    }
                    if (addFrameIfNeeded(newFrame, false)) {
                        // No need to propagate the separator further, because it was added.
                        mySeparator = false;
                    }
                }
                else { // Hidden frame case.
                    // D7: hidden frames are not folded (no XFramesView.shouldFoldHiddenFrames branch)
                    boolean frameHasSeparator = StackFrameItem.hasSeparatorAbove(newFrame);
                    if (!mySeparator && frameHasSeparator) {
                        // Frame has a separator, but it wasn't added; we need to propagate the separator further.
                        mySeparator = true;
                    }
                }
            }
            schedule(suspendContext, null, myAsyncStack, myCreationStack, mySeparator);
        }
    }

    private static boolean showFrame(XStackFrame frame) {
        if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowLibraryStackFrames() &&
            frame instanceof JVMStackFrameInfoProvider info) {
            return !info.shouldHide();
        }
        return true;
    }

    private static String calcRepresentation(ThreadReferenceProxyImpl thread) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        String name = thread.name();
        ThreadGroupReferenceProxyImpl gr = thread.threadGroupProxy();
        String grname = (gr != null) ? gr.name() : null;
        String threadStatusText = DebuggerUtilsEx.getThreadStatusText(thread.status());
        if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
            return JavaDebuggerLocalize.labelThreadNodeInGroup(name, thread.uniqueID(), threadStatusText, grname).get();
        }
        return JavaDebuggerLocalize.labelThreadNode(name, thread.uniqueID(), threadStatusText).get();
    }

    private static CompletableFuture<String> calcRepresentationAsync(ThreadReferenceProxyImpl thread) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        ThreadReference ref = thread.getThreadReference();
        if (!DebuggerUtilsAsync.isAsyncEnabled() || !(ref instanceof ThreadReferenceImpl threadReference)) {
            return CompletableFuture.completedFuture(calcRepresentation(thread));
        }
        CompletableFuture<String> nameFuture = threadReference.nameAsync();
        CompletableFuture<String> groupNameFuture = threadReference.threadGroupAsync().thenCompose(gr -> {
            if (gr instanceof ThreadGroupReferenceImpl) {
                return ((ThreadGroupReferenceImpl) gr).nameAsync();
            }
            return CompletableFuture.completedFuture(null);
        });
        CompletableFuture<String> statusTextFuture = threadReference.statusAsync().thenApply(DebuggerUtilsEx::getThreadStatusText);

        long uniqueID = threadReference.uniqueID();
        return DebuggerUtilsAsync.reschedule(groupNameFuture).thenCompose(grname -> {
            return nameFuture.thenCombine(statusTextFuture, (name, threadStatusText) -> {
                if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
                    return JavaDebuggerLocalize.labelThreadNodeInGroup(name, uniqueID, threadStatusText, grname).get();
                }
                return JavaDebuggerLocalize.labelThreadNode(name, uniqueID, threadStatusText).get();
            });
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JavaExecutionStack stack = (JavaExecutionStack) o;

        if (!myThreadProxy.equals(stack.myThreadProxy)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return myThreadProxy.hashCode();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
