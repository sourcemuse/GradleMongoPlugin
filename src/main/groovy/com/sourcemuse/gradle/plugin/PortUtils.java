/**
 * Copyright © 2012 Joe Littlejohn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Minor modifications The MITRE Corporation, 2014.
 */
package com.sourcemuse.gradle.plugin;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * This is stolen from the Maven embed mongo plugin.
 *
 * @author Joe Littlejohn
 */
public final class PortUtils {
    private PortUtils() {
    }

    /**
     * Grab a random free port. Note that this is vulnerable to a race condition attack.
     *
     * @return the number of an availabl port.
     * @throws IOException if there is an error grabbing a random port.
     */
    public static int allocateRandomPort() throws IOException {
        try {
            ServerSocket server = new ServerSocket(0);
            int port = server.getLocalPort();
            server.close();
            return port;
        } catch (IOException e) {
            throw new IOException("Failed to acquire a random free port", e);
        }
    }
}
