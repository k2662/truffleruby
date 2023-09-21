Q$(MX_VERBOSE) = @

OS := $(shell uname)
ifeq ($(OS),Darwin)
SOEXT := dylib
else
SOEXT := so
endif

# CC := cc # but missing graalvm/llvm/polyglot.h
CC := $(GRAALVM_TOOLCHAIN_CC)
CFLAGS := -Wall -Werror -fPIC -std=c99 -g
LDFLAGS := -m64

ROOT := $(realpath ../../../..)
RUBY_HDR_DIR := $(ROOT)/lib/cext/include

OBJECT_FILES := trampoline.o st.o cext_constants.o

libtrufflerubytrampoline.$(SOEXT): $(OBJECT_FILES) Makefile
	$(Q) $(CC) -shared $(LDFLAGS) -o $@ $(OBJECT_FILES)

%.o: %.c Makefile
	$(Q) $(CC) -o $@ -c $(CFLAGS) $(LDFLAGS) -I$(RUBY_HDR_DIR) -I$(ROOT)/lib/cext/include/stubs $<
