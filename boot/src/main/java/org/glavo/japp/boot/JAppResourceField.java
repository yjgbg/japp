/*
 * Copyright 2023 Glavo
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
package org.glavo.japp.boot;

public enum JAppResourceField {
    END,

    /**
     * XXH64 Checksum (8byte)
     */
    CHECKSUM,

    FILE_CREATE_TIME,
    FILE_LAST_MODIFIED_TIME,
    FILE_LAST_ACCESS_TIME
    ;

    private static final JAppResourceField[] FIELDS = values();

    public static JAppResourceField of(int i)  {
        return i >= 0 && i < FIELDS.length ? FIELDS[i] : null;
    }

    public byte id() {
        return (byte) ordinal();
    }
}
