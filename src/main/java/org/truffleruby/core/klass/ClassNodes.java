/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.klass;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.CoreLibrary.ShapeDynamicObjectFactory;
import org.truffleruby.core.basicobject.BasicObjectType;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.objects.InitializeClassNode;
import org.truffleruby.language.objects.InitializeClassNodeGen;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

@CoreModule(value = "Class", isClass = true)
public abstract class ClassNodes {

    @TruffleBoundary
    public static void setMetaClass(DynamicObject object, RubyClass singletonClass) {
        final BasicObjectType objectType = (BasicObjectType) object.getShape().getObjectType();
        final BasicObjectType newObjectType = objectType.setMetaClass(singletonClass);
        DynamicObjectLibrary.getUncached().setDynamicType(object, newObjectType);
    }

    @TruffleBoundary
    public static void setLogicalAndMetaClass(DynamicObject object, RubyClass rubyClass) {
        final BasicObjectType objectType = (BasicObjectType) object.getShape().getObjectType();
        final BasicObjectType newObjectType = objectType.setLogicalClass(rubyClass).setMetaClass(rubyClass);
        DynamicObjectLibrary.getUncached().setDynamicType(object, newObjectType);
    }

    /** Special constructor for class Class */
    @TruffleBoundary
    public static RubyClass createClassClass(RubyContext context, SourceSection sourceSection) {
        final ModuleFields model = new ModuleFields(context, sourceSection, null, "Class");
        model.setFullName("Class");

        final Shape tempShape = CoreLibrary.createShape(RubyClass.class, null);
        final RubyClass rubyClass = new RubyClass(tempShape, model, false, null);

        setLogicalAndMetaClass(rubyClass, rubyClass);

        model.rubyModuleObject = rubyClass;

        assert Layouts.BASIC_OBJECT.getLogicalClass(rubyClass) == rubyClass;
        assert Layouts.BASIC_OBJECT.getMetaClass(rubyClass) == rubyClass;

        return rubyClass;
    }

    /** This constructor supports initialization and solves boot-order problems and should not normally be used from
     * outside this class. */
    @TruffleBoundary
    public static RubyClass createBootClass(RubyContext context, SourceSection sourceSection,
            Shape classShape, RubyClass superclass, String name) {
        final ModuleFields fields = new ModuleFields(context, sourceSection, null, name);
        final RubyClass rubyClass = new RubyClass(classShape, fields, false, null);

        fields.rubyModuleObject = rubyClass;
        fields.setFullName(name);

        if (superclass != null) {
            fields.setSuperClass(superclass, true);
        }

        return rubyClass;
    }

    @TruffleBoundary
    public static RubyClass createSingletonClassOfObject(RubyContext context, SourceSection sourceSection,
            RubyClass superclass, DynamicObject attached, String name) {
        // We also need to create the singleton class of a singleton class for proper lookup and consistency.
        // See rb_singleton_class() documentation in MRI.
        // Allocator is null here, we cannot create instances of singleton classes.
        assert attached != null;
        return ensureItHasSingletonClassCreated(
                context,
                createRubyClass(
                        context,
                        sourceSection,
                        getClassShapeFromClass(superclass),
                        null,
                        superclass,
                        name,
                        true,
                        attached,
                        true));
    }

    @TruffleBoundary
    public static RubyClass createInitializedRubyClass(RubyContext context, SourceSection sourceSection,
            RubyModule lexicalParent, RubyClass superclass, String name) {
        final RubyClass rubyClass = createRubyClass(
                context,
                sourceSection,
                getClassShapeFromClass(superclass),
                lexicalParent,
                superclass,
                name,
                false,
                null,
                true);
        ensureItHasSingletonClassCreated(context, rubyClass);
        return rubyClass;
    }

    @TruffleBoundary
    public static RubyClass createRubyClass(RubyContext context,
            SourceSection sourceSection,
            Shape classShape,
            RubyModule lexicalParent,
            RubyClass superclass,
            String name,
            boolean isSingleton,
            DynamicObject attached,
            boolean initialized) {
        final ModuleFields fields = new ModuleFields(context, sourceSection, lexicalParent, name);

        final RubyClass rubyClass = new RubyClass(classShape, fields, isSingleton, attached);
        if (initialized) {
            rubyClass.superclass = superclass;
        }

        fields.rubyModuleObject = rubyClass;

        if (lexicalParent != null) {
            fields.getAdoptedByLexicalParent(context, lexicalParent, name, null);
        } else if (name != null) { // bootstrap module
            fields.setFullName(name);
        }

        if (superclass != null) {
            fields.setSuperClass(superclass, false);
        }

        // Singleton classes cannot be instantiated
        if (!isSingleton) {
            setInstanceFactory(rubyClass, superclass);
        }

        return rubyClass;
    }

    @TruffleBoundary
    public static void initialize(RubyContext context, RubyClass rubyClass, RubyClass superclass) {
        assert !rubyClass.isSingleton : "Singleton classes can only be created internally";

        rubyClass.fields.setSuperClass(superclass, true);

        ensureItHasSingletonClassCreated(context, rubyClass);

        setInstanceFactory(rubyClass, superclass);
    }

    public static void setInstanceFactory(RubyClass rubyClass, RubyClass baseClass) {
        assert !rubyClass.isSingleton : "Singleton classes cannot be instantiated";
        final DynamicObjectFactory factory = baseClass.instanceFactory;
        final Shape parentShape = factory.getShape();
        final BasicObjectType objectType = (BasicObjectType) parentShape.getObjectType();
        final Shape newShape = parentShape.changeType(objectType.setLogicalClass(rubyClass).setMetaClass(rubyClass));
        final DynamicObjectFactory newFactory;
        if (factory instanceof ShapeDynamicObjectFactory) {
            newFactory = new ShapeDynamicObjectFactory(newShape);
        } else {
            newFactory = newShape.createFactory();
        }
        rubyClass.instanceFactory = newFactory;
    }

    private static RubyClass ensureItHasSingletonClassCreated(RubyContext context, RubyClass rubyClass) {
        getLazyCreatedSingletonClass(context, rubyClass);
        return rubyClass;
    }

    public static RubyClass getSingletonClass(RubyContext context, RubyClass rubyClass) {
        // We also need to create the singleton class of a singleton class for proper lookup and consistency.
        // See rb_singleton_class() documentation in MRI.
        return ensureItHasSingletonClassCreated(context, getLazyCreatedSingletonClass(context, rubyClass));
    }

    public static RubyClass getSingletonClassOrNull(RubyContext context, RubyClass rubyClass) {
        RubyClass metaClass = Layouts.BASIC_OBJECT.getMetaClass(rubyClass);
        if (metaClass.isSingleton) {
            return ensureItHasSingletonClassCreated(context, metaClass);
        } else {
            return null;
        }
    }

    private static RubyClass getLazyCreatedSingletonClass(RubyContext context, RubyClass rubyClass) {
        synchronized (rubyClass) {
            RubyClass metaClass = Layouts.BASIC_OBJECT.getMetaClass(rubyClass);
            if (metaClass.isSingleton) {
                return metaClass;
            }

            return createSingletonClass(context, rubyClass);
        }
    }

    @TruffleBoundary
    private static RubyClass createSingletonClass(RubyContext context, RubyClass rubyClass) {
        final RubyClass singletonSuperclass;
        final RubyClass superclass = getSuperClass(rubyClass);
        if (superclass == null) {
            singletonSuperclass = Layouts.BASIC_OBJECT.getLogicalClass(rubyClass);
        } else {
            singletonSuperclass = getLazyCreatedSingletonClass(context, superclass);
        }

        String name = StringUtils.format("#<Class:%s>", rubyClass.fields.getName());
        RubyClass metaClass = ClassNodes.createRubyClass(
                context,
                rubyClass.fields.getSourceSection(),
                getClassShapeFromClass(rubyClass),
                null,
                singletonSuperclass,
                name,
                true,
                rubyClass,
                true);
        SharedObjects.propagate(context, rubyClass, metaClass);
        setMetaClass(rubyClass, metaClass);

        return Layouts.BASIC_OBJECT.getMetaClass(rubyClass);
    }

    /** The same as {@link CoreLibrary#classShape} but available while executing the CoreLibrary constructor */
    private static Shape getClassShapeFromClass(RubyClass rubyClass) {
        return Layouts.BASIC_OBJECT.getLogicalClass(rubyClass).instanceFactory.getShape();
    }

    @TruffleBoundary
    public static RubyClass getSuperClass(RubyClass rubyClass) {
        for (DynamicObject ancestor : rubyClass.fields.ancestors()) {
            if (ancestor instanceof RubyClass && ancestor != rubyClass) {
                return (RubyClass) ancestor;
            }
        }

        return null;
    }

    /** #allocate should only be defined as an instance method of Class (Class#allocate), which is required for
     * compatibility. __allocate__ is our version of the "allocation function" as defined by rb_define_alloc_func() in
     * MRI to define how to create instances of specific classes. */
    @CoreMethod(names = "allocate")
    public abstract static class AllocateInstanceNode extends CoreMethodArrayArgumentsNode {

        @Child private CallDispatchHeadNode allocateNode = CallDispatchHeadNode.createPrivate();

        @Specialization
        protected Object newInstance(VirtualFrame frame, RubyClass rubyClass) {
            return allocateNode.call(rubyClass, "__allocate__");
        }
    }

    @CoreMethod(names = "new", needsBlock = true, rest = true)
    public abstract static class NewNode extends CoreMethodArrayArgumentsNode {

        @Child private CallDispatchHeadNode allocateNode = CallDispatchHeadNode.createPrivate();
        @Child private CallDispatchHeadNode initialize = CallDispatchHeadNode.createPrivate();

        @Specialization
        protected Object newInstance(VirtualFrame frame, RubyClass rubyClass, Object[] args, NotProvided block) {
            return doNewInstance(frame, rubyClass, args, null);
        }

        @Specialization
        protected Object newInstance(VirtualFrame frame, RubyClass rubyClass, Object[] args, RubyProc block) {
            return doNewInstance(frame, rubyClass, args, block);
        }

        private Object doNewInstance(VirtualFrame frame, RubyClass rubyClass, Object[] args, RubyProc block) {
            final Object instance = allocateNode.call(rubyClass, "__allocate__");
            initialize.callWithBlock(instance, "initialize", block, args);
            return instance;
        }
    }

    @CoreMethod(names = "initialize", optional = 1, needsBlock = true)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Child private InitializeClassNode initializeClassNode;

        @Specialization
        protected RubyClass initialize(RubyClass rubyClass, Object maybeSuperclass, Object maybeBlock) {
            if (initializeClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initializeClassNode = insert(InitializeClassNodeGen.create(true));
            }

            return initializeClassNode.executeInitialize(rubyClass, maybeSuperclass, maybeBlock);
        }

    }

    @CoreMethod(names = "inherited", needsSelf = false, required = 1, visibility = Visibility.PRIVATE)
    public abstract static class InheritedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected Object inherited(Object subclass) {
            return nil;
        }

    }

    @CoreMethod(names = "superclass")
    public abstract static class SuperClassNode extends CoreMethodArrayArgumentsNode {

        @Specialization(
                guards = { "rubyClass == cachedRubyClass", "cachedSuperclass != null" },
                limit = "getCacheLimit()")
        protected Object getSuperClass(RubyClass rubyClass,
                @Cached("rubyClass") RubyClass cachedRubyClass,
                @Cached("fastLookUp(cachedRubyClass)") Object cachedSuperclass) {
            // caches only initialized classes, just allocated will go through slow look up
            return cachedSuperclass;
        }

        @Specialization(replaces = "getSuperClass")
        protected Object getSuperClassUncached(RubyClass rubyClass,
                @Cached BranchProfile errorProfile) {
            final Object superclass = fastLookUp(rubyClass);
            if (superclass != null) {
                return superclass;
            } else {
                errorProfile.enter();
                throw new RaiseException(
                        getContext(),
                        getContext().getCoreExceptions().typeError("uninitialized class", this));
            }
        }

        protected Object fastLookUp(RubyClass rubyClass) {
            return rubyClass.superclass;
        }

        protected int getCacheLimit() {
            return getContext().getOptions().CLASS_CACHE;
        }
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateClassNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected RubyClass allocate(RubyClass classClass) {
            assert classClass == coreLibrary().classClass : "Subclasses of class Class are forbidden in Ruby";
            return createRubyClass(
                    getContext(),
                    getEncapsulatingSourceSection(),
                    coreLibrary().classShape,
                    null,
                    coreLibrary().objectClass,
                    null,
                    false,
                    null,
                    false);
        }

    }
}
