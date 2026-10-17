/*
 * Copyright (c) 2022 WallPanel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.wallpanel.pro.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.wallpanel.pro.utils.GeckoWebClientAdapter.Companion.isExternalUri

class GeckoWebClientAdapterTest {

    @Test
    fun webPagesStayInTheBrowser() {
        assertFalse(isExternalUri("http://192.168.8.246:8123/lovelace/0"))
        assertFalse(isExternalUri("HTTPS://example.com/"))
        assertFalse(isExternalUri("about:blank"))
        assertFalse(isExternalUri("javascript:console.log('x')"))
        assertFalse(isExternalUri("data:text/html,hello"))
        assertFalse(isExternalUri("blob:http://example.com/1234"))
        assertFalse(isExternalUri(GeckoWebClientAdapter.ERROR_PAGE))
    }

    @Test
    fun appLinksGoToAndroid() {
        assertTrue(isExternalUri("intent:#Intent;action=android.settings.SETTINGS;end"))
        assertTrue(isExternalUri("tel:123"))
        assertTrue(isExternalUri("market://details?id=xyz.wallpanel.pro"))
        assertTrue(isExternalUri("monopolygo://open"))
    }

    @Test
    fun aUriWithoutASchemeIsNotHandedOver() {
        assertFalse(isExternalUri("no-scheme-here"))
    }
}
