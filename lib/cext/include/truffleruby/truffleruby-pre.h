/*
* Copyright (c) 2016, 2023 Oracle and/or its affiliates. All rights reserved. This
* code is released under a tri EPL/GPL/LGPL license. You can use it,
* redistribute it and/or modify it under the terms of the:
*
* Eclipse Public License version 2.0
* GNU General Public License version 2
* GNU Lesser General Public License version 2.1
*
* This file contains code that is based on the Ruby API headers,
* copyright (C) Yukihiro Matsumoto, licensed under the 2-clause BSD licence.
*/

#ifndef TRUFFLERUBY_PRE_H
#define TRUFFLERUBY_PRE_H

#if defined(__cplusplus)
extern "C" {
#endif

RUBY_SYMBOL_EXPORT_BEGIN

#include <truffleruby/truffleruby-abi-version.h>

#include <graalvm/llvm/polyglot.h>

#include <ctype.h> // isdigit

// Configuration

// We disable USE_FLONUM, as we do not use pointer tagging for Float.
// Enabling USE_FLONUM also changes the value of Qtrue/Qnil/Qundef.
#define USE_FLONUM 0

// We don't have a transient heap
#define USE_TRANSIENT_HEAP 0

// Sulong enforces calling functions with the correct arity, so we set this
// to catch rb_block_call_func_t* functions with incorrect arity faster.
#define RB_BLOCK_CALL_FUNC_STRICT 1

// To avoid extra write barrier code
#define USE_RINCGC 0

// Skip DTrace-generated code
#define DTRACE_PROBES_DISABLED 1

// Support

// extern void* rb_tr_cext;
// #define RUBY_CEXT rb_tr_cext

#ifndef TRUFFLERUBY_ABI_VERSION
#error "TRUFFLERUBY_ABI_VERSION must be defined when compiling native extensions."
#endif

const char* rb_tr_abi_version(void) __attribute__((weak));
const char* rb_tr_abi_version(void) {
  return TRUFFLERUBY_ABI_VERSION;
}

// Wrapping and unwrapping of values.

#include "ruby/internal/value.h"

// TODO: remove/comment-out those, at least rb_tr_unwrap() and rb_tr_wrap()
extern void* (*rb_tr_unwrap)(VALUE obj);
extern VALUE (*rb_tr_wrap)(void *obj);
extern void* (*rb_tr_id2sym)(ID obj);

// Helpers

#ifndef offsetof
#define offsetof(p_type,field) ((size_t)&(((p_type *)0)->field))
#endif

// Non-standard. rb_tr_* is all private, the rest is there because the C API does not have a good replacement for it.

NORETURN(void rb_tr_not_implemented(const char *function_name));

unsigned long rb_tr_flags(VALUE object);
void rb_tr_set_flags(VALUE object, unsigned long flags);

#define RBASIC_FLAGS(object) rb_tr_flags(object)
#define RBASIC_SET_FLAGS(obj, flags_to_set) rb_tr_set_flags(obj, flags_to_set)

VALUE rb_tr_zlib_crc_table(void);
VALUE rb_tr_cext_lock_owned_p(void);
VALUE rb_tr_invoke(VALUE recv, const char* meth);

void rb_exc_set_message(VALUE exc, VALUE message);

// Defines

// To support racc releases before https://github.com/ruby/racc/pull/165
#define HAVE_RB_BLOCK_CALL

RUBY_SYMBOL_EXPORT_END

#if defined(__cplusplus)
}
#endif

#endif
