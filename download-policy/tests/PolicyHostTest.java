// SPDX-License-Identifier: Apache-2.0
package de.diamaneos.downloads;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

/** JVM boundary fixtures only: Android Binder, persistence and TLS are not qualified here. */
public final class PolicyHostTest {
    private static final String EU = "releases.diamaneos.de", CA = "releases-na.diamaneos.de";
    private static final String PACKAGE = "FP6-ota_update-2026101500.zip";
    private static final byte[] FILE = new byte[50_000];
    static {
        for (int i = 0; i < FILE.length; i++) FILE[i] = (byte) (i * 31 + i / 256);
    }

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

    /** One server. A body failure at an offset throws bodyError, or ends the body early when null. */
    private static class Server {
        int code = 200;
        IOException error, probeError, bodyError = new SocketException("fixture reset");
        boolean probeBusy, ignoresRange;
        long bodyFailsAt = -1;
        String range;
    }
    private static class Connection extends HttpsURLConnection {
        final Server server;
        boolean disconnected;
        Connection(URL url, Server server) { super(url); this.server = server; }
        boolean head() { return "HEAD".equals(getRequestMethod()); }
        long start() {
            String range = getRequestProperty("Range");
            if (range == null || server.ignoresRange) return 0;
            check(range.startsWith("bytes=") && range.endsWith("-"));
            return Long.parseLong(range.substring(6, range.length() - 1));
        }
        @Override public int getResponseCode() throws IOException {
            check(!getInstanceFollowRedirects());
            if (head()) {
                if (server.probeError != null) throw server.probeError;
                return server.probeBusy ? 503 : 200;
            }
            if (server.error != null) throw server.error;
            return server.code == 200 && start() > 0 ? 206 : server.code;
        }
        @Override public long getContentLengthLong() { return FILE.length - start(); }
        @Override public String getHeaderField(String name) {
            if (!"Content-Range".equals(name) || start() == 0) return null;
            return server.range != null ? server.range
                    : "bytes " + start() + "-" + (FILE.length - 1) + "/" + FILE.length;
        }
        @Override public InputStream getInputStream() {
            return new InputStream() {
                long at = start();
                @Override public int read() { throw new UnsupportedOperationException(); }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    if (at == server.bodyFailsAt) {
                        if (server.bodyError == null) return -1;
                        throw server.bodyError;
                    }
                    long end = server.bodyFailsAt > at ? server.bodyFailsAt : FILE.length;
                    if (at >= end) return -1;
                    int count = (int) Math.min(len, end - at);
                    System.arraycopy(FILE, (int) at, b, off, count);
                    at += count;
                    return count;
                }
            };
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
        @Override public String getCipherSuite() { throw new UnsupportedOperationException(); }
        @Override public java.security.cert.Certificate[] getLocalCertificates() { return null; }
        @Override public java.security.cert.Certificate[] getServerCertificates() { throw new UnsupportedOperationException(); }
    }
    private static class Network implements DownloadTransport.Connector {
        final Server eu = new Server(), ca = new Server();
        final List<Connection> opened = Collections.synchronizedList(new ArrayList<>());
        @Override public HttpsURLConnection open(URL url) {
            Connection c = new Connection(url, url.getHost().equals(CA) ? ca : eu);
            opened.add(c);
            return c;
        }
        List<Connection> gets() {
            synchronized (opened) {
                return opened.stream().filter(c -> !c.head()).collect(Collectors.toList());
            }
        }
        List<String> hosts() {
            return gets().stream().map(c -> c.getURL().getHost()).collect(Collectors.toList());
        }
        long probes() {
            synchronized (opened) {
                return opened.stream().filter(Connection::head).count();
            }
        }
        DownloadTransport.Route route(String mode) throws IOException {
            return DownloadTransport.route(DownloadPolicy.require(mode), false, "FP6-stable", this, c -> {});
        }
        HttpsURLConnection request(DownloadTransport.Route route, long offset) throws IOException {
            return DownloadTransport.open(route, PACKAGE, offset, this, c -> {});
        }
        byte[] download(DownloadTransport.Route route, ByteArrayOutputStream out) throws IOException {
            HttpsURLConnection first = request(route, 0);
            long end = DownloadTransport.copy(route, PACKAGE, first, 0, FILE.length,
                    (buffer, count) -> out.write(buffer, 0, count), this, c -> {});
            check(end == FILE.length);
            return out.toByteArray();
        }
    }
    /** Automatic with Europe first, since Canada's probe gets no 200. */
    private static DownloadTransport.Route europeFirst(Network n) throws IOException {
        n.ca.probeBusy = true;
        DownloadTransport.Route route = n.route(DownloadPolicy.AUTOMATIC);
        check(route.server().equals(DownloadPolicy.PRIMARY));
        return route;
    }
    private static String[] order(long eu, long ca) {
        return DownloadTransport.order(new String[]{DownloadPolicy.PRIMARY, DownloadPolicy.CANADA}, new long[]{eu, ca});
    }

    public static void main(String[] args) throws Exception {
        String scenario = args[0];
        switch (scenario) {
            case "defaults":
                for (String value : new String[]{null, "", "3:canada", "2:evil", "2:canada:1", "1:canada:true", "1:evil:0", "2:primary_eu:x", "x".repeat(40)}) {
                    check(DownloadPolicy.decode(value) == DownloadPolicy.DEFAULT);
                }
                check(DownloadPolicy.DEFAULT.automatic());
                refuses(IllegalArgumentException.class, () -> DownloadPolicy.require("evil"));
                refuses(IllegalArgumentException.class, () -> DownloadPolicy.require(null));
                break;
            case "migration":
                check(DownloadPolicy.decode("1:primary_eu:0").mode.equals(DownloadPolicy.PRIMARY));
                check(DownloadPolicy.decode("1:canada:0").mode.equals(DownloadPolicy.CANADA));
                check(DownloadPolicy.decode("1:primary_eu:1").automatic());
                check(DownloadPolicy.decode("1:canada:1").automatic());
                break;
            case "roundtrip":
                for (String mode : new String[]{DownloadPolicy.AUTOMATIC, DownloadPolicy.PRIMARY, DownloadPolicy.CANADA}) {
                    DownloadPolicy p = DownloadPolicy.decode(DownloadPolicy.require(mode).encode());
                    check(p.mode.equals(mode) && p.encode().equals("2:" + mode));
                }
                check(Arrays.equals(DownloadPolicy.DEFAULT.servers(), new String[]{DownloadPolicy.PRIMARY, DownloadPolicy.CANADA}));
                check(Arrays.equals(DownloadPolicy.require("canada").servers(), new String[]{DownloadPolicy.CANADA}));
                check(DownloadPolicy.url(false, "FP6-stable", DownloadPolicy.PRIMARY).getHost().equals(EU));
                check(DownloadPolicy.url(false, "FP6-stable", DownloadPolicy.CANADA).getHost().equals(CA));
                check(DownloadPolicy.url(true, "metadata.1.0.sjson", DownloadPolicy.PRIMARY).getHost().equals("apps.diamaneos.de"));
                check(DownloadPolicy.require("canada").url(true, "metadata.1.0.sjson").getHost().equals("apps-na.diamaneos.de"));
                break;
            case "paths":
                for (String path : new String[]{"https://evil.invalid/x", "../x", "x/../z", "x/./z", "x//z", "/x", "x?y", "x#y", "x%2fy", "x\\y", "x/", "x".repeat(513)}) {
                    refuses(IOException.class, () -> DownloadPolicy.url(true, path, DownloadPolicy.PRIMARY));
                }
                for (String url : new String[]{"http://apps.diamaneos.de/x", "https://apps.diamaneos.de.evil.invalid/x", "https://apps.diamaneos.de:443/x", "https://user@apps.diamaneos.de/x", "https://apps.grapheneos.org/x"}) {
                    refuses(IOException.class, () -> DownloadPolicy.appsPath(url));
                }
                for (String server : new String[]{null, DownloadPolicy.AUTOMATIC, "evil"}) {
                    refuses(IOException.class, () -> DownloadPolicy.url(false, "FP6-stable", server));
                }
                break;
            case "choice": {
                String[] eu = {DownloadPolicy.PRIMARY, DownloadPolicy.CANADA};
                String[] ca = {DownloadPolicy.CANADA, DownloadPolicy.PRIMARY};
                check(Arrays.equals(order(10_000_000, 90_000_000), eu));
                check(Arrays.equals(order(90_000_000, 10_000_000), ca));
                // A lead within the tie margin keeps Europe first.
                check(Arrays.equals(order(30_000_000, 30_000_000), eu));
                check(Arrays.equals(order(30_000_000, 30_000_000 - DownloadTransport.TIE_NANOS), eu));
                check(Arrays.equals(order(30_000_000, 30_000_000 - DownloadTransport.TIE_NANOS - 1), ca));
                check(Arrays.equals(order(-1, 400_000_000), ca));
                check(Arrays.equals(order(400_000_000, -1), eu));
                check(Arrays.equals(order(-1, -1), eu));
                break;
            }
            case "probe": {
                Network n = new Network();
                n.ca.probeBusy = true;
                DownloadTransport.Route first = n.route(DownloadPolicy.AUTOMATIC);
                check(first.server().equals(DownloadPolicy.PRIMARY) && n.probes() == 2 && n.gets().isEmpty());
                check(n.opened.stream().allMatch(c -> c.disconnected));
                // The order is reused until a route moves on after a failure.
                n.route(DownloadPolicy.AUTOMATIC);
                check(n.probes() == 2);
                check(first.next());
                n.ca.probeBusy = false;
                n.eu.probeError = new ConnectException();
                DownloadTransport.Route second = n.route(DownloadPolicy.AUTOMATIC);
                check(second.server().equals(DownloadPolicy.CANADA) && n.probes() == 4);
                check(second.next() && !second.next());
                n.eu.probeError = new SSLHandshakeException("fixture");
                check(n.route(DownloadPolicy.AUTOMATIC).server().equals(DownloadPolicy.CANADA) && n.probes() == 6);
                for (String mode : new String[]{DownloadPolicy.PRIMARY, DownloadPolicy.CANADA}) {
                    Network single = new Network();
                    DownloadTransport.Route route = single.route(mode);
                    check(route.server().equals(mode) && !route.next() && single.probes() == 0);
                }
                break;
            }
            case "single_server":
                for (String mode : new String[]{DownloadPolicy.PRIMARY, DownloadPolicy.CANADA}) {
                    String host = mode.equals(DownloadPolicy.CANADA) ? CA : EU;
                    Network n = new Network();
                    n.eu.code = n.ca.code = 503;
                    check(n.request(n.route(mode), 0).getResponseCode() == 503);
                    n.eu.code = n.ca.code = 200;
                    n.eu.error = n.ca.error = new ConnectException();
                    refuses(IOException.class, () -> n.request(n.route(mode), 0));
                    n.eu.error = n.ca.error = null;
                    n.eu.bodyFailsAt = n.ca.bodyFailsAt = 100;
                    refuses(SocketException.class, () -> n.download(n.route(mode), new ByteArrayOutputStream()));
                    check(n.hosts().equals(List.of(host, host, host)) && n.probes() == 0);
                }
                break;
            case "failover_before_body": {
                for (IOException error : new IOException[]{new ConnectException(), new SocketTimeoutException()}) {
                    Network n = new Network();
                    n.eu.error = error;
                    check(n.request(europeFirst(n), 64).getResponseCode() == 206);
                    check(n.hosts().equals(List.of(EU, CA)) && n.gets().get(0).disconnected);
                    check("bytes=64-".equals(n.gets().get(1).getRequestProperty("Range")));
                }
                Network both = new Network();
                both.eu.code = both.ca.code = 503;
                check(both.request(europeFirst(both), 0).getResponseCode() == 503);
                check(both.hosts().equals(List.of(EU, CA)));
                for (IOException error : new IOException[]{new SSLHandshakeException("fixture"), new ProtocolException(), new IOException("fixture")}) {
                    Network n = new Network();
                    n.eu.error = error;
                    refuses(IOException.class, () -> n.request(europeFirst(n), 0));
                    check(n.hosts().equals(List.of(EU)) && n.gets().get(0).disconnected);
                }
                for (int code : new int[]{301, 302, 307, 308}) {
                    Network n = new Network();
                    n.eu.code = code;
                    refuses(ProtocolException.class, () -> n.request(europeFirst(n), 0));
                    check(n.hosts().equals(List.of(EU)));
                }
                for (int code : new int[]{304, 401, 403, 404, 416}) {
                    Network n = new Network();
                    n.eu.code = code;
                    check(n.request(europeFirst(n), 0).getResponseCode() == code && n.hosts().equals(List.of(EU)));
                }
                IOException nested = new SocketException();
                nested.initCause(new SSLHandshakeException("fixture"));
                check(!DownloadTransport.retryable(nested));
                break;
            }
            case "resume_other_server": {
                IOException[] stops = {new SocketException("reset"), new SocketTimeoutException("stall"),
                        new ProtocolException("unexpected end of stream"), null};
                for (IOException stop : stops) {
                    Network n = new Network();
                    n.eu.bodyFailsAt = 20_000;
                    n.eu.bodyError = stop;
                    check(Arrays.equals(n.download(europeFirst(n), new ByteArrayOutputStream()), FILE));
                    check(n.hosts().equals(List.of(EU, CA)));
                    check("bytes=20000-".equals(n.gets().get(1).getRequestProperty("Range")));
                    check(n.gets().stream().allMatch(c -> c.disconnected));
                }
                // Canada first: Europe continues it.
                Network n = new Network();
                n.ca.bodyFailsAt = 1;
                check(Arrays.equals(n.download(new DownloadTransport.Route(false,
                        new String[]{DownloadPolicy.CANADA, DownloadPolicy.PRIMARY}), new ByteArrayOutputStream()), FILE));
                check(n.hosts().equals(List.of(CA, EU)));
                break;
            }
            case "resume_checks": {
                String[] ranges = {"bytes 20000-49999/50001", "bytes 20000-50000/50000", "bytes 0-49999/50000", "bytes 20000-49999/*"};
                for (int i = 0; i < 4 + ranges.length; i++) {
                    Network n = new Network();
                    n.eu.bodyFailsAt = 20_000;
                    if (i == 0) n.ca.ignoresRange = true;
                    else if (i == 1) n.ca.code = 503;
                    else if (i == 2) n.ca.error = new ConnectException();
                    else if (i == 3) n.ca.bodyFailsAt = 30_000;
                    else n.ca.range = ranges[i - 4];
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    refuses(IOException.class, () -> n.download(europeFirst(n), out));
                    check(n.hosts().equals(List.of(EU, CA)) && n.gets().stream().allMatch(c -> c.disconnected));
                    byte[] kept = out.toByteArray();
                    check(Arrays.equals(kept, Arrays.copyOf(FILE, kept.length)) && kept.length <= 30_000);
                }
                for (IOException error : new IOException[]{new SSLException("fixture"), wrapped(new SSLException("fixture"))}) {
                    Network n = new Network();
                    n.eu.bodyFailsAt = 20_000;
                    n.eu.bodyError = error;
                    refuses(IOException.class, () -> n.download(europeFirst(n), new ByteArrayOutputStream()));
                    check(n.hosts().equals(List.of(EU)));
                }
                Network sinkFailure = new Network();
                refuses(IllegalStateException.class, () -> {
                    DownloadTransport.Route route = europeFirst(sinkFailure);
                    DownloadTransport.copy(route, PACKAGE, sinkFailure.request(route, 0), 0, FILE.length,
                            (buffer, count) -> { throw new IllegalStateException("fixture cancel"); }, sinkFailure, c -> {});
                });
                check(sinkFailure.hosts().equals(List.of(EU)));
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
    private static IOException wrapped(IOException cause) {
        IOException e = new SocketException("fixture");
        e.initCause(cause);
        return e;
    }

    private static void androidBoundary(String scenario) throws Exception {
        Context c = new Context();
        DownloadPolicy canada = DownloadPolicy.require("canada");
        switch (scenario) {
            case "global_snapshot":
                c.resolver.permitted = true;
                check(DownloadPolicyClient.write(c, canada));
                check(android.provider.Settings.Global.writes == 1);
                check(android.provider.Settings.Global.value.equals("2:canada"));
                Context secondaryReader = new Context(); secondaryReader.users.main = false;
                check(DownloadPolicyClient.read(secondaryReader).mode.equals("canada"));
                check(android.provider.Settings.Global.reads == 1);
                break;
            case "main_user":
                c.resolver.permitted = true; c.users.main = false;
                check(!DownloadPolicyClient.canWrite(c));
                check(!DownloadPolicyClient.write(c, canada));
                check(android.provider.Settings.Global.writes == 0);
                break;
            case "secure_permission":
                check(!DownloadPolicyClient.canWrite(c));
                check(!DownloadPolicyClient.write(c, canada));
                check(android.provider.Settings.Global.writes == 0);
                c.resolver.permitted = true; c.resolver.forceDeny = true;
                refuses(SecurityException.class, () -> DownloadPolicyClient.write(c, canada));
                check(android.provider.Settings.Global.writes == 0);
                break;
            case "write_failure":
                c.resolver.permitted = true; android.provider.Settings.Global.failWrite = true;
                check(!DownloadPolicyClient.write(c, canada));
                check(android.provider.Settings.Global.value == null);
                break;
            case "read_failure":
                check(DownloadPolicyClient.read(c) == DownloadPolicy.DEFAULT);
                android.provider.Settings.Global.value = "3:canada";
                check(DownloadPolicyClient.read(c) == DownloadPolicy.DEFAULT);
                android.provider.Settings.Global.value = "1:canada:0";
                check(DownloadPolicyClient.read(c).mode.equals("canada"));
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
                // No new provider-side restriction: existing privileged writers retain control.
                c.users.main = false; c.resolver.permitted = true;
                check(!DownloadPolicyClient.write(c, canada));
                check(android.provider.Settings.Global.putString(c.resolver, DownloadPolicyClient.KEY, "2:canada"));
                check(DownloadPolicyClient.read(new Context()).mode.equals("canada"));
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
