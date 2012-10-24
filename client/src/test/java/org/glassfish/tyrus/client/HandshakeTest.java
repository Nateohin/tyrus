/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.tyrus.client;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.websocket.Session;
import javax.net.websocket.extensions.Extension;
import javax.net.websocket.extensions.FrameHandler;
import org.glassfish.tyrus.server.Server;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests whether the HandShake parameters (sub-protoxols, extensions) are sent correctly.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class HandshakeTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "hello";

    @Test
    public void testClient() {
        Server server = new Server("org.glassfish.tyrus.client.TestBean");
        server.start();

        try {
            messageLatch = new CountDownLatch(1);

            ArrayList<String> subprotocols = new ArrayList<String>();
            subprotocols.add("asd");
            subprotocols.add("ghi");

            ArrayList<Extension> extensions = new ArrayList<Extension>();
            extensions.add(new TestExtension("ext1"));
            extensions.add(new TestExtension("ext2"));

            DefaultClientEndpointConfiguration.Builder builder = new DefaultClientEndpointConfiguration.Builder(new URI("ws://localhost:8025/websockets/tests/echo"));
//            builder.protocols(subprotocols);
            builder.extensions(extensions);
            DefaultClientEndpointConfiguration dcec = builder.build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new AbstractTestEndpoint() {
                @Override
                public void messageReceived(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message = " + message);
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getRemote().sendString(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, dcec);

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The received message is the same as the sent one", receivedMessage.equals(SENT_MESSAGE));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    private class TestExtension implements Extension {

        private final String name;

        private TestExtension(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, String> getParameters() {
            return null;
        }

        @Override
        public FrameHandler createIncomingFrameHandler(FrameHandler downstream) {
            return null;
        }

        @Override
        public FrameHandler createOutgoingFrameHandler(FrameHandler upstream) {
            return null;
        }
    }
}