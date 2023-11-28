# Copyright (c) 2023 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../ruby/spec_helper'

describe "The standalone" do
  llvm_toolchain = "#{Truffle::Boot.ruby_home}/lib/llvm-toolchain"
  guard -> { Dir.exist? llvm_toolchain } do
    it "should not include Fortran-related files in the toolchain" do
      Dir.glob("#{llvm_toolchain}/bin/flang*").should.empty?
      Dir.glob("#{llvm_toolchain}/lib/libFortran*").should.empty?
      Dir.glob("#{llvm_toolchain}/include/flang/*").should.empty?
    end
  end
end