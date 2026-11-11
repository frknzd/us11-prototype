/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2026 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.restextraction.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded counts, independent of report calls/verdicts. Locks cover only map operations. */
public final class UnmatchedPaths {
    private final Map<UrlPath, Integer> counts = new LinkedHashMap<>();
    private int overflow;
    private long version;

    public synchronized void add(UrlPath path) {
        Objects.requireNonNull(path, "path");
        if (counts.containsKey(path)) counts.merge(path, 1, Integer::sum);
        else if (counts.size() < 1000) counts.put(path, 1);
        else overflow++;
        version++;
    }

    public synchronized Map<UrlPath, Integer> counts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    public synchronized int getOverflow() {
        return overflow;
    }

    synchronized long version() {
        return version;
    }

    public synchronized void clear() {
        counts.clear();
        overflow = 0;
        version++;
    }
}
