package com.mvv.java22sample

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle


fun getStringLengthUsingCStrLen(string: String): Long {

    val linker = Linker.nativeLinker()
    val strlen: MethodHandle = linker.downcallHandle(
        linker.defaultLookup().find("strlen").orElseThrow(),
        FunctionDescriptor.of(JAVA_LONG, ADDRESS)
    )

    return Arena.ofConfined().use { arena ->
        val str = arena.allocateFrom(string) // allocateUtf8String("Hello")
        strlen.invokeExact(str) as Long
    }
}
