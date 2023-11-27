#!/usr/bin/env bash

source test/truffle/common.sh.inc

file=${1:-host-inlining.txt}

too_big='Out of budget|too big to explore|too many fast-path invokes'

if [ -n "$TRUFFLERUBY_HOST_INLINING_TEST" ]; then
  ruby tool/extract_host_inlining.rb 'org.truffleruby.language.methods.CallForeignMethodNodeGen.execute' "$file" > out.txt
  grep -F 'Root[org.truffleruby.language.methods.CallForeignMethodNodeGen.execute]' out.txt
  if ! grep -E "$too_big" out.txt; then
    echo "CallForeignMethodNodeGen.execute should be /$too_big/, did host inlining output change?"
    cat out.txt
    exit 1
  fi

  ruby tool/extract_host_inlining.rb org.truffleruby.language.dispatch.RubyCallNode.execute "$file" > out.txt
  grep -F 'Root[org.truffleruby.language.dispatch.RubyCallNode.execute]' out.txt
  if grep -E "$too_big" out.txt; then
    echo 'RubyCallNode.execute no longer fits in host inlining budget'
    cat out.txt
    exit 1
  fi

  ruby tool/extract_host_inlining.rb --simplify org.truffleruby.language.dispatch.RubyCallNode.execute "$file" | grep CUTOFF > simplified.txt
  if grep -v -E 'not direct call|not inlinable|marked to be not used for inlining|leads to unwind' simplified.txt; then
    echo 'RubyCallNode.execute has unexpected CUTOFFs'
    cat out.txt
    exit 1
  fi

  rm out.txt
  rm simplified.txt
else
  echo "$file does not exist, skipping test"
fi
