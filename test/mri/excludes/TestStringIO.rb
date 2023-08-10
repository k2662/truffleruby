exclude :test_frozen, "[ruby-core:33648]."
exclude :test_gets_chomp, "<\"abc\\n\"> expected but was"
exclude :test_read, "Expected \"あい\" (oid=1192) to be the same as \"あい\" (oid=1208)."
exclude :test_ungetc, "<\"x\"> expected but was"
exclude :test_ungetbyte, "<\"q紅玉bar\\n\"> expected but was"
exclude :test_ungetbyte_padding, "<\"\\u0000a\"> expected but was"
exclude :test_close_write, "Exception raised:"
exclude :test_each, "<[\"foo\\n\" + \"bar\\n\", \"baz\"]> expected but was"
exclude :test_set_encoding, "<\"foo\\x83\"> (ASCII-8BIT) expected but was"
exclude :test_new_block_warning, "expected: /does not take block/"
exclude :test_initialize, "ArgumentError expected but nothing was raised."
exclude :test_close_read, "Exception raised:"
exclude :"test_strip_bom:UTF-16BE", "TypeError: no implicit conversion of Hash into String"
exclude :test_encoding_read, "Encoding::CompatibilityError: incompatible character encodings: UTF-32BE and UTF-8"
exclude :test_write_with_multiple_arguments, "ArgumentError: wrong number of arguments (given 2, expected 1)"
exclude :"test_strip_bom:UTF-32LE", "TypeError: no implicit conversion of Hash into String"
exclude :"test_strip_bom:UTF-16LE", "TypeError: no implicit conversion of Hash into String"
exclude :"test_strip_bom:UTF-32BE", "TypeError: no implicit conversion of Hash into String"
exclude :"test_strip_bom:UTF-8", "TypeError: no implicit conversion of Hash into String"
exclude :test_gets2, "Encoding::CompatibilityError: incompatible character encodings: UTF-16BE and UTF-8"
exclude :test_encoding_write, "Encoding::CompatibilityError: incompatible character encodings: UTF-32BE and UTF-8"
exclude :test_write_encoding_conversion, "ArgumentError: wrong number of arguments (given 2, expected 1)"
exclude :test_overflow, "it hangs up on darwin"
exclude :test_frozen_string, "[ruby-core:48530]."
exclude :test_gets_chomp_eol, "<\"abc\\r\\n\"> expected but was"
exclude :test_ungetc_padding, "<\"b\\u0000a\"> expected but was"
exclude :test_ungetc_fill, "<500> expected but was"
exclude :test_truncate, "<0> expected but was"
