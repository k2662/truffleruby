/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.truffleruby.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.truffleruby.parser.ast.ArgsParseNode;
import org.truffleruby.parser.ast.ArgumentParseNode;
import org.truffleruby.parser.ast.AssignableParseNode;
import org.truffleruby.parser.ast.DAsgnParseNode;
import org.truffleruby.parser.ast.KeywordArgParseNode;
import org.truffleruby.parser.ast.LocalAsgnParseNode;
import org.truffleruby.parser.ast.MultipleAsgnParseNode;
import org.truffleruby.parser.ast.NoKeywordsArgParseNode;
import org.truffleruby.parser.ast.OptArgParseNode;
import org.truffleruby.parser.ast.ParseNode;
import org.truffleruby.parser.ast.RequiredKeywordArgumentValueParseNode;
import org.truffleruby.parser.ast.UnnamedRestArgParseNode;
import org.truffleruby.parser.ast.types.INameNode;
import org.truffleruby.parser.parser.ParserSupport;

public final class Helpers {

    public static final Map<String, String> map(String... keyValues) {
        HashMap<String, String> map = new HashMap<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length;) {
            map.put(keyValues[i++], keyValues[i++]);
        }
        return map;
    }

    /** Use an ArgsParseNode (used for blocks) to generate ArgumentDescriptors */
    public static ArgumentDescriptor[] argsNodeToArgumentDescriptors(ArgsParseNode argsNode) {
        ArrayList<ArgumentDescriptor> descs = new ArrayList<>();
        ParseNode[] args = argsNode.getArgs();
        int preCount = argsNode.getPreCount();

        if (preCount > 0) {
            for (int i = 0; i < preCount; i++) {
                if (args[i] instanceof MultipleAsgnParseNode) {
                    descs.add(new ArgumentDescriptor(ArgumentType.anonreq));
                } else {
                    descs.add(new ArgumentDescriptor(ArgumentType.req, ((ArgumentParseNode) args[i]).getName()));
                }
            }
        }


        int optCount = argsNode.getOptionalArgsCount();
        if (optCount > 0) {
            int optIndex = argsNode.getOptArgIndex();

            for (int i = 0; i < optCount; i++) {
                ArgumentType type = ArgumentType.opt;
                ParseNode optNode = args[optIndex + i];
                String name = null;
                if (optNode instanceof OptArgParseNode) {
                    name = ((OptArgParseNode) optNode).getName();
                } else if (optNode instanceof LocalAsgnParseNode) {
                    name = ((LocalAsgnParseNode) optNode).getName();
                } else if (optNode instanceof DAsgnParseNode) {
                    name = ((DAsgnParseNode) optNode).getName();
                } else {
                    type = ArgumentType.anonopt;
                }
                descs.add(new ArgumentDescriptor(type, name));
            }
        }

        ArgumentParseNode restArg = argsNode.getRestArgNode();
        if (restArg != null) {
            if (restArg instanceof UnnamedRestArgParseNode) {
                if (((UnnamedRestArgParseNode) restArg).isStar()) {
                    descs.add(new ArgumentDescriptor(ArgumentType.anonrest));
                }
            } else {
                descs.add(new ArgumentDescriptor(ArgumentType.rest, restArg.getName()));
            }
        }

        int postCount = argsNode.getPostCount();
        if (postCount > 0) {
            int postIndex = argsNode.getPostIndex();
            for (int i = 0; i < postCount; i++) {
                ParseNode postNode = args[postIndex + i];
                if (postNode instanceof MultipleAsgnParseNode) {
                    descs.add(new ArgumentDescriptor(ArgumentType.anonreq));
                } else {
                    descs.add(new ArgumentDescriptor(ArgumentType.req, ((ArgumentParseNode) postNode).getName()));
                }
            }
        }

        int keywordsCount = argsNode.getKeywordCount();
        if (keywordsCount > 0) {
            int keywordsIndex = argsNode.getKeywordsIndex();
            for (int i = 0; i < keywordsCount; i++) {
                KeywordArgParseNode keyWordNode = (KeywordArgParseNode) args[keywordsIndex + i];
                ParseNode asgnNode = keyWordNode.getAssignable();
                if (isRequiredKeywordArgumentValueNode(asgnNode)) {
                    descs.add(new ArgumentDescriptor(ArgumentType.keyreq, ((INameNode) asgnNode).getName()));
                } else {
                    descs.add(new ArgumentDescriptor(ArgumentType.key, ((INameNode) asgnNode).getName()));
                }
            }
        }

        if (argsNode.getKeyRest() != null) {
            String argName = argsNode.getKeyRest().getName();
            assert !(argName == null || argName.length() == 0);

            if (argName.equals(ParserSupport.KWREST_VAR)) {
                descs.add(new ArgumentDescriptor(ArgumentType.anonkeyrest, argName));
            } else if (argsNode.getKeyRest() instanceof NoKeywordsArgParseNode) {
                descs.add(new ArgumentDescriptor(ArgumentType.nokey));
            } else {
                descs.add(new ArgumentDescriptor(ArgumentType.keyrest, argsNode.getKeyRest().getName()));
            }
        }
        if (argsNode.getBlock() != null) {
            descs.add(new ArgumentDescriptor(ArgumentType.block, argsNode.getBlock().getName()));
        }

        return descs.toArray(ArgumentDescriptor.EMPTY_ARRAY);
    }

    public static boolean isRequiredKeywordArgumentValueNode(ParseNode asgnNode) {
        return asgnNode instanceof AssignableParseNode &&
                ((AssignableParseNode) asgnNode).getValueNode() instanceof RequiredKeywordArgumentValueParseNode;
    }

}
