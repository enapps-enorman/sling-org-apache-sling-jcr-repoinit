/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.repoinit.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

/** Test utilities */
public class TestUtil {

    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public final Session adminSession;
    public final String id;
    public final String username;

    public TestUtil(SlingContext ctx) {
        adminSession = ctx.resourceResolver().adaptTo(Session.class);
        id = UUID.randomUUID().toString();
        username = "user_" + id;
    }

    public List<Operation> parse(String input) throws RepoInitParsingException {
        final Reader r = new StringReader(input);
        try {
            return new RepoInitParserService().parse(r);
        } finally {
            IOUtils.closeQuietly(r);
        }
    }

    private void assertPathContains(User u, String pathShouldContain) throws RepositoryException {
        if(pathShouldContain != null) {
            final String path = u.getPath();
            assertTrue(
                    "Expecting path " + path + " to contain " + pathShouldContain,
                    path.contains(pathShouldContain)
            );
        }
    }

    public void assertUser(String info, String id, boolean expectToExist) throws RepositoryException {
        assertUser(info, id, expectToExist, null);
    }

    public void assertUser(String info, String id, boolean expectToExist, String pathShouldContain) throws RepositoryException {
        final Authorizable a = UserUtil.getUserManager(adminSession).getAuthorizable(id);
        if(!expectToExist) {
            assertNull(info + ", expecting Principal to be absent:" + id, a);
        } else {
            assertNotNull(info + ", expecting Principal to exist:" + id, a);
            final User u = (User)a;
            assertFalse(info + ", expecting Principal to be a plain user:" + id, u.isSystemUser());
            assertPathContains(u, pathShouldContain);
        }
    }

    public void assertServiceUser(String info, String id, boolean expectToExist) throws RepositoryException {
        assertServiceUser(info, id, expectToExist, null);
    }

    public void assertServiceUser(String info, String id, boolean expectToExist, String pathShouldContain) throws RepositoryException {
        final Authorizable a = UserUtil.getUserManager(adminSession).getAuthorizable(id);
        if(!expectToExist) {
            assertNull(info + ", expecting Principal to be absent:" + id, a);
        } else {
            assertNotNull(info + ", expecting Principal to exist:" + id, a);
            final User u = (User)a;
            assertTrue(info + ", expecting Principal to be a System user:" + id, u.isSystemUser());
            assertPathContains(u, pathShouldContain);
        }
    }

    public void assertEnabledUser(String info, String id) throws RepositoryException {
        final Authorizable a = UserUtil.getUserManager(adminSession).getAuthorizable(id);
        assertFalse(info + ", expecting Principal to not be disabled: " + id, ((User) a).isDisabled());
    }

    public void assertDisabledUser(String info, String id) throws RepositoryException {
        final Authorizable a = UserUtil.getUserManager(adminSession).getAuthorizable(id);
        assertTrue(info + ", expecting Principal to be disabled: " + id, ((User) a).isDisabled());
    }

    public void assertNodeExists(String path) throws RepositoryException {
        assertNodeExists(path, null, null);
    }

    public void assertNodeExists(String path, String primaryType) throws RepositoryException {
        assertNodeExists(path, primaryType, null);
    }

    public void assertNodeExists(String path, List<String> mixins) throws RepositoryException {
        assertNodeExists(path, null, mixins);
    }

    public void assertNodeExists(String path, String primaryType, List<String> mixins) throws RepositoryException {
        if(!adminSession.nodeExists(path)) {
            fail("Node does not exist:" + path);
        }
        if(primaryType != null) {
            final Node n = adminSession.getNode(path);
            if(!n.hasProperty(JCR_PRIMARY_TYPE)) {
                fail("No " + JCR_PRIMARY_TYPE + " property at " + path);
            }
            final String actual = n.getProperty(JCR_PRIMARY_TYPE).getString();
            if(!primaryType.equals(actual)) {
                fail("Primary type mismatch for " + path + ", expected " + primaryType + " but got " + actual);
            }
        }
        if (mixins != null) {
            final Node n = adminSession.getNode(path);
            NodeType[] mixinNodeTypes = n.getMixinNodeTypes();
            if (mixinNodeTypes.length != mixins.size()) {
                fail("Number of mixin node types does not match, expected " + mixins.size() + " but got " + mixinNodeTypes.length);
            }
            for (NodeType mixinNodeType : mixinNodeTypes) {
                if (! mixins.contains(mixinNodeType.getName())) {
                    fail("Node contains the unexpected mixin node type " + mixinNodeType.getName());
                }
            }
        }
    }

    public void parseAndExecute(String input) throws RepositoryException, RepoInitParsingException {
        final JcrRepoInitOpsProcessorImpl p = new JcrRepoInitOpsProcessorImpl();
        p.apply(adminSession, parse(input));
        adminSession.save();
    }

    public void cleanupUser() throws RepositoryException, RepoInitParsingException {
        cleanupServiceUser(username);
    }
    
    public void cleanupServiceUser(String principalName) throws RepositoryException, RepoInitParsingException {
        parseAndExecute("delete service user " + principalName);
        assertServiceUser("in cleanupUser()", principalName, false);
    }

    public Session loginService(String serviceUsername) throws RepositoryException {
        final SimpleCredentials cred = new SimpleCredentials(serviceUsername, new char[0]);
        return adminSession.impersonate(cred);
    }

    public Session getAdminSession() {
        return adminSession;
    }

    public void cleanup() {
        adminSession.logout();
    }

    public String getTestCndStatement(String nsPrefix, String nsURI) throws RepositoryException, RepoInitParsingException {
        return "register nodetypes\n"
                + "<<===\n"
                + getTestCND(nsPrefix, nsURI)
                + "===>>\n"
        ;
    }

    public String getTestCND(String nsPrefix, String nsURI) {
        return "<" + nsPrefix + "='" + nsURI + "'>\n"
                + "[" + nsPrefix + ":foo] > nt:unstructured\n";
    }
}
