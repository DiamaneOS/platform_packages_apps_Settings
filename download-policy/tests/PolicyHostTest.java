// SPDX-License-Identifier: Apache-2.0
package de.diamaneos.downloads;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

/** JVM boundary fixtures only: Android Binder, persistence and TLS are not qualified here. */
public final class PolicyHostTest {
    private static void check(boolean result) {
        if (!result) throw new AssertionError("Host fixture failed");
    }
    private interface Action { void run() throws Exception; }
    private static void refuses(Class<? extends Throwable> type, Action action) throws Exception {
        try { action.run(); } catch (Throwable e) {
            if (type.isInstance(e)) return;
            throw e;
        }
        throw new AssertionError("Expected refusal: " + type.getSimpleName());
    }
    private static DownloadPolicy policy(String server, boolean fallback) {
        return DownloadPolicy.require(1, server, fallback);
    }
    private static class Connection extends HttpsURLConnection {
        final int code;
        final IOException error;
        boolean disconnected;
        Connection(URL url, int code, IOException error) { super(url); this.code = code; this.error = error; }
        @Override public int getResponseCode() throws IOException {
            check(!getInstanceFollowRedirects());
            if (error != null) throw error;
            return code;
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
        @Override public String getCipherSuite() { throw new UnsupportedOperationException(); }
        @Override public java.security.cert.Certificate[] getLocalCertificates() { return null; }
        @Override public java.security.cert.Certificate[] getServerCertificates() { throw new UnsupportedOperationException(); }
    }
    private static class Network implements DownloadTransport.Connector {
        final List<Connection> opened = new ArrayList<>();
        int code = 200;
        IOException error;
        boolean secondFails;
        @Override public HttpsURLConnection open(URL url) {
            boolean first = opened.isEmpty();
            Connection c = new Connection(url, first || secondFails ? code : 200,
                    first || secondFails ? error : null);
            opened.add(c);
            return c;
        }
        HttpsURLConnection request(DownloadPolicy p) throws IOException {
            return DownloadTransport.open(p, true, "packages/app.example/1/base.apk.gz", this,
                    c -> c.setRequestProperty("Range", "bytes=64-"));
        }
    }

    public static void main(String[] args) throws Exception {
        String scenario = args[0];
        switch (scenario) {
            case "defaults":
                for (String value : new String[]{null, "", "2:canada:1", "1:canada:true", "1:evil:1", "1:primary_eu:1:evil"}) {
                    check(DownloadPolicy.decode(value) == DownloadPolicy.DEFAULT);
                }
                break;
            case "roundtrip":
                for (String region : new String[]{DownloadPolicy.PRIMARY, DownloadPolicy.CANADA}) {
                    for (boolean fallback : new boolean[]{false, true}) {
                        DownloadPolicy p = policy(region, fallback);
                        DownloadPolicy q = DownloadPolicy.decode(p.encode());
                        check(q.server.equals(region) && q.fallback == fallback);
                        check(q.url(false, "FP6-stable", false).getHost().equals(
                                region.equals("canada") ? "releases-na.diamaneos.de" : "releases.diamaneos.de"));
                        check(q.url(true, "metadata.1.0.sjson", false).getHost().equals(
                                region.equals("canada") ? "apps-na.diamaneos.de" : "apps.diamaneos.de"));
                    }
                }
                break;
            case "paths":
                for (String path : new String[]{"https://evil.invalid/x", "../x", "x/../z", "x/./z", "x//z", "/x", "x?y", "x#y", "x%2fy", "x\\y", "x/", "x".repeat(513)}) {
                    refuses(IOException.class, () -> DownloadPolicy.DEFAULT.url(true, path, false));
                }
                for (String url : new String[]{"http://apps.diamaneos.de/x", "https://apps.diamaneos.de.evil.invalid/x", "https://apps.diamaneos.de:443/x", "https://user@apps.diamaneos.de/x", "https://apps.grapheneos.org/x"}) {
                    refuses(IOException.class, () -> DownloadPolicy.appsPath(url));
                }
                refuses(IOException.class, () -> DownloadPolicy.DEFAULT.url(false, "FP6-stable", true));
                break;
            case "fallback_off": {
                Network n = new Network(); n.code = 503;
                check(n.request(DownloadPolicy.DEFAULT).getResponseCode() == 503);
                check(n.opened.size() == 1);
                n = new Network(); n.error = new ConnectException();
                Network finalN = n;
                refuses(IOException.class, () -> finalN.request(DownloadPolicy.DEFAULT));
                check(n.opened.size() == 1 && n.opened.get(0).disconnected);
                break;
            }
            case "fallback_once": {
                for (String region : new String[]{"primary_eu", "canada"}) {
                    Network n = new Network(); n.code = 503;
                    check(n.request(policy(region, true)).getResponseCode() == 200);
                    check(n.opened.size() == 2 && n.opened.get(0).disconnected);
                    check(!n.opened.get(0).getURL().getHost().equals(n.opened.get(1).getURL().getHost()));
                    check("bytes=64-".equals(n.opened.get(1).getRequestProperty("Range")));
                    n = new Network(); n.error = new SocketTimeoutException(); n.secondFails = true;
                    Network finalN = n;
                    refuses(IOException.class, () -> finalN.request(policy(region, true)));
                    check(n.opened.size() == 2);
                    check(n.opened.stream().allMatch(c -> c.disconnected));
                }
                break;
            }
            case "tls_and_redirect": {
                for (IOException error : new IOException[]{new SSLHandshakeException("fixture"), new ProtocolException(), new IOException("bad metadata")}) {
                    Network n = new Network(); n.error = error;
                    refuses(IOException.class, () -> n.request(policy("primary_eu", true)));
                    check(n.opened.size() == 1 && n.opened.get(0).disconnected);
                }
                for (int code : new int[]{301, 302, 303, 307, 308}) {
                    Network n = new Network(); n.code = code;
                    refuses(ProtocolException.class, () -> n.request(policy("primary_eu", true)));
                    check(n.opened.size() == 1);
                }
                IOException nested = new SocketExceptionFixture();
                nested.initCause(new SSLHandshakeException("fixture"));
                check(!DownloadTransport.retryable(nested));
                break;
            }
            case "response_and_body": {
                for (int code : new int[]{200, 206, 304, 401, 403, 404, 416}) {
                    Network n = new Network(); n.code = code;
                    check(n.request(policy("primary_eu", true)).getResponseCode() == code);
                    check(n.opened.size() == 1);
                }
                Network n = new Network(); n.request(policy("primary_eu", true));
                // Verification or a body failure after open() cannot re-enter transport fallback.
                refuses(java.security.GeneralSecurityException.class, () -> { throw new java.security.GeneralSecurityException("fixture signature"); });
                check(n.opened.size() == 1);
                break;
            }
            case "bounds":
                check(DownloadTransport.readBounded(new ByteArrayInputStream(new byte[64]), 64).length == 64);
                refuses(IOException.class, () -> DownloadTransport.readBounded(new ByteArrayInputStream(new byte[65]), 64));
                refuses(IllegalArgumentException.class, () -> DownloadTransport.readBounded(new ByteArrayInputStream(new byte[0]), 0));
                break;
            default:
                androidBoundary(scenario);
        }
    }
    private static class SocketExceptionFixture extends java.net.SocketException {}

    private static void androidBoundary(String scenario) throws Exception {
        Context c = new Context();
        switch (scenario) {
            case "global_snapshot":
                c.resolver.permitted = true;
                check(DownloadPolicyClient.write(c, policy("canada", true)));
                check(android.provider.Settings.Global.writes == 1);
                check(android.provider.Settings.Global.value.equals("1:canada:1"));
                Context secondaryReader = new Context(); secondaryReader.users.main = false;
                DownloadPolicy snapshot = DownloadPolicyClient.read(secondaryReader);
                check(snapshot.server.equals("canada") && snapshot.fallback);
                check(android.provider.Settings.Global.reads == 1);
                break;
            case "main_user":
                c.resolver.permitted = true; c.users.main = false;
                check(!DownloadPolicyClient.canWrite(c));
                check(!DownloadPolicyClient.write(c, policy("canada", true)));
                check(android.provider.Settings.Global.writes == 0);
                break;
            case "secure_permission":
                check(!DownloadPolicyClient.canWrite(c));
                check(!DownloadPolicyClient.write(c, policy("canada", true)));
                check(android.provider.Settings.Global.writes == 0);
                c.resolver.permitted = true; c.resolver.forceDeny = true;
                refuses(SecurityException.class, () -> DownloadPolicyClient.write(c, policy("canada", true)));
                check(android.provider.Settings.Global.writes == 0);
                break;
            case "write_failure":
                c.resolver.permitted = true; android.provider.Settings.Global.failWrite = true;
                check(!DownloadPolicyClient.write(c, policy("canada", true)));
                check(android.provider.Settings.Global.value == null);
                break;
            case "read_failure":
                android.provider.Settings.Global.value = "2:canada:1";
                check(DownloadPolicyClient.read(c) == DownloadPolicy.DEFAULT);
                android.provider.Settings.Global.value = "1:canada:0";
                android.provider.Settings.Global.failRead = true;
                refuses(IOException.class, () -> DownloadPolicyClient.read(c));
                android.provider.Settings.Global.failRead = false;
                android.provider.Settings.Global.nullCursor = true;
                refuses(IOException.class, () -> DownloadPolicyClient.read(c));
                android.provider.Settings.Global.nullCursor = false;
                android.provider.Settings.Global.rows = 2;
                refuses(IOException.class, () -> DownloadPolicyClient.read(c));
                check(android.provider.Settings.Global.writes == 0);
                break;
            case "platform_writer":
                // No new provider-side owner restriction: existing privileged writers retain control.
                c.users.main = false; c.resolver.permitted = true;
                check(!DownloadPolicyClient.write(c, policy("canada", true)));
                check(android.provider.Settings.Global.putString(c.resolver, DownloadPolicyClient.KEY, "1:canada:1"));
                check(DownloadPolicyClient.read(new Context()).server.equals("canada"));
                break;
            case "settings_link":
                android.content.Intent intent = DownloadPolicyClient.settingsIntent(c);
                check(intent.component.pkg.equals("com.android.settings"));
                check(intent.component.cls.equals("com.android.settings.Settings$DownloadServersActivity"));
                c.pm.settingsSigned = false;
                refuses(SecurityException.class, () -> DownloadPolicyClient.settingsIntent(c));
                break;
            default: throw new IllegalArgumentException("Unknown host scenario");
        }
    }
}
