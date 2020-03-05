//
// StringUtilsTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays


class StringUtilsTest {

    @Test
    fun testIsEmpty() {
        assertTrue(StringUtils.isEmpty(null))
        assertTrue(StringUtils.isEmpty(""))
        assertFalse(StringUtils.isEmpty(" "))
    }

    @Test
    fun testJoin() {
        assertEquals("", StringUtils.join("", emptyList<String>()))
        assertEquals("", StringUtils.join("", Arrays.asList("")))
        assertEquals("", StringUtils.join("", Arrays.asList("", "")))
        assertEquals("a", StringUtils.join("", Arrays.asList("a")))
        assertEquals("a", StringUtils.join("", Arrays.asList("a", "")))
        assertEquals("aa", StringUtils.join("", Arrays.asList("a", "a")))

        assertEquals("", StringUtils.join(".", emptyList<String>()))
        assertEquals("", StringUtils.join(".", Arrays.asList("")))
        assertEquals(".", StringUtils.join(".", Arrays.asList("", "")))
        assertEquals("a", StringUtils.join(".", Arrays.asList("a")))
        assertEquals("a.", StringUtils.join(".", Arrays.asList("a", "")))
        assertEquals(".a", StringUtils.join(".", Arrays.asList("", "a")))
        assertEquals("a.a", StringUtils.join(".", Arrays.asList("a", "a")))
    }
}
