/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

@ReportPolymorphism
@GenerateUncached
public abstract class ReadObjectFieldNode extends RubyBaseNode {

    public static ReadObjectFieldNode create() {
        return ReadObjectFieldNodeGen.create();
    }

    public abstract Object execute(RubyDynamicObject object, Object name, Object defaultValue);

    @Specialization(limit = "getCacheLimit()")
    protected Object read(RubyDynamicObject receiver, Object name, Object defaultValue,
            @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary) {
        return objectLibrary.getOrDefault(receiver, name, defaultValue);
    }

    protected int getCacheLimit() {
        return RubyLanguage.getCurrentContext().getOptions().INSTANCE_VARIABLE_CACHE;
    }
}
