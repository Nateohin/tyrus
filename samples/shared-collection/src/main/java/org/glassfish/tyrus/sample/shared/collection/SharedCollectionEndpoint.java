/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.sample.shared.collection;

import java.io.IOException;
import java.io.Reader;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@ServerEndpoint("/ws/collection")
public class SharedCollectionEndpoint {

    private static final Deque<Tuple<Session, JsonObject>> broadcastQueue =
            new ConcurrentLinkedDeque<Tuple<Session, JsonObject>>();
    private static volatile Session session;
    private static volatile boolean broadcasting;

    @OnOpen
    public void onOpen(Session s) {
        SharedCollectionEndpoint.session = s;

        final JsonObjectBuilder mapRepresentation = Json.createObjectBuilder();

        for (Map.Entry<String, String> entry : SharedCollection.map.entrySet()) {
            mapRepresentation.add(entry.getKey(), entry.getValue());
        }

        final JsonObjectBuilder event = Json.createObjectBuilder();
        event.add("event", "init");
        event.add("map", mapRepresentation.build());

        try {
            s.getBasicRemote().sendText(event.build().toString());
        } catch (IOException e) {
            // we don't care about that for now.
        }
    }

    @OnMessage
    public void onMessage(Reader message) {
        final JsonObject jsonObject = Json.createReader(message).readObject();
        final String event = jsonObject.getString("event");

        switch (event) {
            case "put":
                SharedCollection.map.put(jsonObject.getString("key"), jsonObject.getString("value"));
                SharedCollection.broadcast(jsonObject);
                break;
            case "remove":
                SharedCollection.map.remove(jsonObject.getString("key"));
                SharedCollection.broadcast(jsonObject);
                break;
            case "clear":
                SharedCollection.map.clear();
                SharedCollection.broadcast(jsonObject);
                break;
        }
    }

    static void broadcast(JsonObject object) {
        broadcastQueue.add(new Tuple<Session, JsonObject>(null, object));

        processQueue();
    }

    private static void processQueue() {

        if (broadcasting) {
            return;
        }

        try {
            synchronized (broadcastQueue) {
                broadcasting = true;

                if (!broadcastQueue.isEmpty()) {
                    while (!broadcastQueue.isEmpty()) {
                        final Tuple<Session, JsonObject> t = broadcastQueue.remove();

                        final Session s = SharedCollectionEndpoint.session;
                        final String message = t.second.toString();

                        for (Session session : s.getOpenSessions()) {
                            // if (!session.getId().equals(s.getId())) {
                            try {
                                session.getBasicRemote().sendText(message);
                            } catch (IOException e) {
                                // we don't care about that for now.
                            }
                            // }
                        }
                    }
                }
            }
        } finally {
            broadcasting = false;
            if (!broadcastQueue.isEmpty()) {
                processQueue();
            }
        }
    }

    @OnError
    public void onError(Throwable t) {
        System.out.println("# onError");
        t.printStackTrace();
    }

    private static class Tuple<T, U> {
        public final T first;
        public final U second;

        private Tuple(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }
}
