package com.imgood.textech.webae.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;

public class WebApiRouterWorldMapTest {

    private static final String OWNER = "01234567-89ab-cdef-0123-456789abcdef";

    @Test
    public void normalizesDynamicAnnotationRoutes() throws Exception {
        assertEquals(
            "/api/worldmap/annotations/{id}",
            invokeStatic(
                "normalizeRoute",
                new Class<?>[] { String.class },
                "/api/worldmap/annotations/01234567-89ab-cdef-0123-456789abcdef?network=7"));
        assertEquals(
            "/api/worldmap/annotations",
            invokeStatic(
                "normalizeRoute",
                new Class<?>[] { String.class },
                "/api/worldmap/annotations?network=7"));
    }

    @Test
    public void requiresQueryNetworkExceptForBodyBackedAnnotationMutations() throws Exception {
        assertFalse(requiresNetwork("/api/worldmap/annotations", NanoHTTPD.Method.POST));
        assertFalse(requiresNetwork(
            "/api/worldmap/annotations/01234567-89ab-cdef-0123-456789abcdef",
            NanoHTTPD.Method.PUT));

        assertTrue(requiresNetwork("/api/worldmap/annotations", NanoHTTPD.Method.GET));
        assertTrue(requiresNetwork(
            "/api/worldmap/annotations/01234567-89ab-cdef-0123-456789abcdef",
            NanoHTTPD.Method.DELETE));
        assertTrue(requiresNetwork("/api/worldmap/versions", NanoHTTPD.Method.GET));
        assertTrue(requiresNetwork("/api/worldmap/diff", NanoHTTPD.Method.GET));
        assertFalse(requiresNetwork("/api/worldmap/dynmap-tiles/0/0/0.png", NanoHTTPD.Method.GET));
    }

    @Test
    public void acceptsAnnotationBodyAtExactLimit() throws Exception {
        byte[] payload = new byte[16 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = 'a';
        }

        Object body = readAnnotationBody(session(
            "/api/worldmap/annotations",
            NanoHTTPD.Method.POST,
            Collections.<String, String>emptyMap(),
            payload,
            Integer.toString(payload.length)));

        assertTrue((Boolean) field(body, "valid"));
        assertFalse((Boolean) field(body, "tooLarge"));
        assertEquals(payload.length, ((String) field(body, "value")).length());
    }

    @Test
    public void rejectsAnnotationBodyAboveLimitWith413Code() throws Exception {
        Object body = readAnnotationBody(session(
            "/api/worldmap/annotations",
            NanoHTTPD.Method.POST,
            Collections.<String, String>emptyMap(),
            new byte[0],
            Integer.toString(16 * 1024 + 1)));

        assertFalse((Boolean) field(body, "valid"));
        assertTrue((Boolean) field(body, "tooLarge"));

        NanoHTTPD.Response response = limitedBodyError(body);
        assertEquals(NanoHTTPD.Response.Status.PAYLOAD_TOO_LARGE, response.getStatus());
        assertTrue(responseBody(response).contains("\"code\":\"payload_too_large\""));
    }

    @Test
    public void rejectsTruncatedAndMalformedAnnotationBodies() throws Exception {
        Object truncated = readAnnotationBody(session(
            "/api/worldmap/annotations",
            NanoHTTPD.Method.POST,
            Collections.<String, String>emptyMap(),
            "abc".getBytes(StandardCharsets.UTF_8),
            "4"));
        assertFalse((Boolean) field(truncated, "valid"));
        assertFalse((Boolean) field(truncated, "tooLarge"));
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, limitedBodyError(truncated).getStatus());

        Object malformed = readAnnotationBody(session(
            "/api/worldmap/annotations",
            NanoHTTPD.Method.POST,
            Collections.<String, String>emptyMap(),
            new byte[0],
            "not-a-number"));
        assertFalse((Boolean) field(malformed, "valid"));
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, limitedBodyError(malformed).getStatus());
    }

    @Test
    public void routeRejectsMissingDeleteNetworkAndUnsupportedMethods() throws Exception {
        WebApiRouter router = new WebApiRouter();
        WebAuthSession auth = new WebAuthSession("token", WebAuthSession.TYPE_OWNER, OWNER, OWNER, "owner");

        NanoHTTPD.Response missingNetwork = invokeRouteInner(
            router,
            session(
                "/api/worldmap/annotations/01234567-89ab-cdef-0123-456789abcdef",
                NanoHTTPD.Method.DELETE,
                Collections.<String, String>emptyMap(),
                new byte[0],
                null),
            auth);
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, missingNetwork.getStatus());
        assertTrue(responseBody(missingNetwork).contains("Missing 'network' parameter"));

        Map<String, String> params = new HashMap<String, String>();
        params.put("network", "7");
        NanoHTTPD.Response unsupported = invokeRouteInner(
            router,
            session(
                "/api/worldmap/versions",
                NanoHTTPD.Method.POST,
                params,
                new byte[0],
                null),
            auth);
        assertEquals(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, unsupported.getStatus());
    }

    private static boolean requiresNetwork(String uri, NanoHTTPD.Method method) throws Exception {
        return (Boolean) invokeStatic(
            "requiresWorldMapQueryNetwork",
            new Class<?>[] { String.class, NanoHTTPD.Method.class },
            uri,
            method);
    }

    private static Object readAnnotationBody(NanoHTTPD.IHTTPSession session) throws Exception {
        return invokeStatic(
            "readWorldMapAnnotationBody",
            new Class<?>[] { NanoHTTPD.IHTTPSession.class },
            session);
    }

    private static NanoHTTPD.Response limitedBodyError(Object body) throws Exception {
        return (NanoHTTPD.Response) invokeStatic(
            "limitedBodyError",
            new Class<?>[] { body.getClass() },
            body);
    }

    private static NanoHTTPD.Response invokeRouteInner(
        WebApiRouter router,
        NanoHTTPD.IHTTPSession session,
        WebAuthSession auth) throws Exception {
        Method method = WebApiRouter.class.getDeclaredMethod(
            "routeInner",
            NanoHTTPD.IHTTPSession.class,
            WebAuthSession.class);
        method.setAccessible(true);
        return (NanoHTTPD.Response) method.invoke(router, session, auth);
    }

    private static Object invokeStatic(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = WebApiRouter.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static NanoHTTPD.IHTTPSession session(
        final String uri,
        final NanoHTTPD.Method httpMethod,
        final Map<String, String> params,
        final byte[] body,
        String declaredLength) {
        final Map<String, String> headers = new HashMap<String, String>();
        if (declaredLength != null) {
            headers.put("content-length", declaredLength);
        }
        return (NanoHTTPD.IHTTPSession) Proxy.newProxyInstance(
            NanoHTTPD.IHTTPSession.class.getClassLoader(),
            new Class<?>[] { NanoHTTPD.IHTTPSession.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getUri".equals(name)) return uri;
                if ("getMethod".equals(name)) return httpMethod;
                if ("getParms".equals(name)) return params;
                if ("getHeaders".equals(name)) return headers;
                if ("getInputStream".equals(name)) return new ByteArrayInputStream(body);
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                return null;
            });
    }

    private static String responseBody(NanoHTTPD.Response response) throws Exception {
        InputStream in = response.getData();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read > 0) {
                out.write(buffer, 0, read);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
