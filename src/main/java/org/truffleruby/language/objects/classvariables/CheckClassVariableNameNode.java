/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.classvariables;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.symbol.SymbolTable;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.parser.Identifiers;

@ImportStatic(Identifiers.class)
public abstract class CheckClassVariableNameNode extends RubyContextNode {

    public static CheckClassVariableNameNode create() {
        return CheckClassVariableNameNodeGen.create();
    }

    public abstract void execute(RubyDynamicObject object, String name);

    @Specialization(guards = { "name == cachedName", "isValidClassVariableName(cachedName)" })
    protected void cached(RubyDynamicObject object, String name,
            @Cached("name") String cachedName) {
    }

    @Specialization(replaces = "cached")
    protected void uncached(RubyDynamicObject object, String name) {
        SymbolTable.checkClassVariableName(getContext(), name, object, this);
    }

}
