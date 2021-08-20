/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core;

import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;

import org.truffleruby.RubyContext;
import org.truffleruby.cext.ValueWrapperManager;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.queue.UnsizedQueue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.language.Nil;

/** Class to provide GC marking and other facilities to keep objects alive for native extensions.
 *
 * Native extensions expect objects on the stack to be kept alive even when they have been stored in native structures
 * on the stack (e.g. pg keeps the VALUE of a ruby array in a structure on the stack, and places other objects in that
 * array to keep them alive). They also expect structs in objects with custom mark functions to keep marked objects
 * alive.
 *
 * Since we are not running on a VM that allows us to add custom mark functions to our garbage collector we keep objects
 * alive in 2 ways. Any object converted to a native handle can be kept alive by executing a
 * {@link MarkingServiceNodes.KeepAliveNode}. This will add the object to two lists, a list of all objects converted to
 * native during this call to a C extension function which will be popped when the we return to Ruby code, and a fixed
 * sized list of objects converted to native handles. When the latter of these two lists is full all mark functions will
 * be run the next time an object is added.
 *
 * Marker references only keep a week reference to their owning object to ensure they don't themselves stop the object
 * from being garbage collected. */
public class MarkingService extends ReferenceProcessingService<MarkerReference> {

    public static interface MarkerAction {
        public abstract void mark(Object owner);
    }

    public static class MarkRunnerReference extends WeakProcessingReference<MarkRunnerReference, Object> {

        public MarkRunnerReference(Object object, ReferenceQueue<? super Object> queue, MarkRunnerService service) {
            super(object, queue, service);
        }
    }

    /** This service handles actually running the mark functions when this is needed. It's done this way so that mark
     * functions and finalizers are run on the same thread, and so that we can avoid the use of any additional locks in
     * this process (as these may cause deadlocks). */
    public static class MarkRunnerService extends ReferenceProcessingService<MarkingService.MarkRunnerReference> {

        private final MarkingService markingService;

        public MarkRunnerService(
                ReferenceQueue<Object> processingQueue,
                MarkingService markingService) {
            super(processingQueue);
            this.markingService = markingService;
        }

        @Override
        protected void processReference(RubyContext context, ProcessingReference<?> reference) {
            /* We need to keep all the objects that might be marked alive during the marking process itself, so we add
             * the arrays to a list to achieve this. */
            super.processReference(context, reference);
            ArrayList<ValueWrapperManager.HandleBlock> keptObjectLists = new ArrayList<>();
            ValueWrapperManager.HandleBlock block;
            while (true) {
                block = (ValueWrapperManager.HandleBlock) markingService.keptObjectQueue.poll();
                if (block == null) {
                    break;
                } else {
                    keptObjectLists.add(block);
                }
            }
            if (!keptObjectLists.isEmpty()) {
                runAllMarkers(context);
            }
            keptObjectLists.clear();
        }

        @TruffleBoundary
        public void runAllMarkers(RubyContext context) {
            ExtensionCallStack stack = markingService.getThreadLocalData().getExtensionCallStack();
            stack.push(stack.getVariables(), stack.getBlock());
            try {
                // TODO (eregon, 15 Sept 2020): there seems to be no synchronization here while walking the list of
                // markingService, and concurrent mutations seem to be possible.
                MarkerReference currentMarker = markingService.getFirst();
                MarkerReference nextMarker;
                while (currentMarker != null) {
                    nextMarker = currentMarker.getNext();
                    markingService.runMarker(context, currentMarker);
                    if (nextMarker == currentMarker) {
                        throw new Error("The MarkerReference linked list structure has become broken.");
                    }
                    currentMarker = nextMarker;
                }
            } finally {
                stack.pop();
            }
        }
    }

    private final ThreadLocal<MarkerThreadLocalData> threadLocalData;

    private final MarkRunnerService runnerService;

    private final UnsizedQueue keptObjectQueue = new UnsizedQueue();

    public static class MarkerThreadLocalData {
        private final ExtensionCallStack extensionCallStack;

        public MarkerThreadLocalData(MarkingService service) {
            this.extensionCallStack = new ExtensionCallStack(Nil.INSTANCE, Nil.INSTANCE);
        }

        public ExtensionCallStack getExtensionCallStack() {
            return extensionCallStack;
        }
    }

    protected static class ExtensionCallStackEntry {
        protected final ExtensionCallStackEntry previous;
        protected final ArrayList<Object> preservedObjects = new ArrayList<>();
        protected final Object block;
        protected Object variables;

        protected ExtensionCallStackEntry(ExtensionCallStackEntry previous, Object variables, Object block) {
            this.previous = previous;
            this.block = block;
            this.variables = variables;
        }
    }

    public static class ExtensionCallStack {
        protected ExtensionCallStackEntry current;

        public ExtensionCallStack(Object variables, Object block) {
            current = new ExtensionCallStackEntry(null, variables, block);
        }

        public ArrayList<Object> getKeptObjects() {
            return current.preservedObjects;
        }

        public void pop() {
            current = current.previous;
        }

        public void push(Object variable, Object block) {
            current = new ExtensionCallStackEntry(current, variable, block);
        }

        public Object getVariables() {
            return current.variables;
        }

        public void setVariables(Object variables) {
            current.variables = variables;
        }

        public Object getBlock() {
            return current.block;
        }
    }

    @TruffleBoundary
    public MarkerThreadLocalData getThreadLocalData() {
        return threadLocalData.get();
    }

    @TruffleBoundary
    public void cleanupThreadLocalData() {
        threadLocalData.remove();
    }

    public MarkingService(ReferenceProcessor referenceprocessor) {
        this(referenceprocessor.processingQueue);
    }

    public MarkingService(ReferenceQueue<Object> processingQueue) {
        super(processingQueue);
        threadLocalData = ThreadLocal.withInitial(this::makeThreadLocalData);
        runnerService = new MarkRunnerService(processingQueue, this);
    }

    @TruffleBoundary
    public MarkerThreadLocalData makeThreadLocalData() {
        MarkerThreadLocalData data = new MarkerThreadLocalData(this);
        /* This finalizer will ensure all the objects remaining in our kept objects buffer will be queue at some point.
         * We don't need to do a queue and reset as the MarkerKeptObjects is going to be GC'ed anyway. We also don't
         * simply queue the keptObjects buffer because it may only have zero, or very few, objects in it and we don't
         * want to run mark functions more often than we have to. */
        //        context.getFinalizationService().addFinalizer(data, null, MarkingService.class, () -> getThreadLocalData().keptObjects.keepObjects(keptObjects), null);
        return data;
    }

    @TruffleBoundary
    public void queueForMarking(ValueWrapperManager.HandleBlock objects) {
        if (objects != null) {
            keptObjectQueue.add(objects);
            runnerService.add(new MarkRunnerReference(new Object(), processingQueue, runnerService));
        }
    }

    /* Convenience method to schedule marking now. Puts an empty array on the queue. */
    public void queueMarking() {
        queueForMarking(ValueWrapperManager.HandleBlock.DUMMY_BLOCK);
    }

    @TruffleBoundary
    public void addMarker(Object object, MarkerAction action) {
        add(new MarkerReference(object, processingQueue, action, this));
    }

    private void runMarker(RubyContext context, MarkerReference markerReference) {
        runCatchingErrors(context, this::runMarkerInternal, markerReference);
    }

    private void runMarkerInternal(RubyContext context, MarkerReference markerReference) {
        if (!context.isFinalizing()) {
            Object owner = markerReference.get();
            if (owner != null) {
                final MarkerAction action = markerReference.action;
                action.mark(owner);
            } else {
                remove(markerReference);
            }
        }
    }

    private Object[] marks;
    private int index;

    public void startMarking(Object[] oldMarks) {
        if (oldMarks == null) {
            marks = ArrayUtils.EMPTY_ARRAY;
        } else {
            marks = new Object[oldMarks.length];
        }
        index = 0;
    }

    @TruffleBoundary
    public void addMark(Object obj) {
        if (marks.length == index) {
            Object[] oldMarks = marks;
            marks = new Object[Integer.max(oldMarks.length * 2, 1)];
            System.arraycopy(oldMarks, 0, marks, 0, oldMarks.length);
        }
        marks[index] = obj;
        index++;
    }

    @TruffleBoundary
    public Object[] finishMarking() {
        if (index != marks.length) {
            for (int i = index; i < marks.length; i++) {
                marks[i] = null;
            }
        }
        Object[] result = marks;
        marks = null;
        return result;
    }

}
