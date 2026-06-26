package com.fastjava.core;

import com.sun.net.httpserver.*;
import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import javax.crypto.*;
import javax.crypto.spec.*;

// ── Annotations ───────────────────────────────────────────────────────────────
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)  @interface GET {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)  @interface POST {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)  @interface PUT {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)  @interface DELETE {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)  @interface PATCH {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)  @interface Authenticated {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)  @interface Path { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface PathParam { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface QueryParam { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface Header { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface Body {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)    @interface Controller { String value() default ""; }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)    @interface Service {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)   @interface Inject {}

// ── Exceptions ────────────────────────────────────────────────────────────────
class HttpException extends RuntimeException {
    final int status;
    HttpException(int s, String msg) { super(msg); this.status = s; }
}
class NotFoundException     extends HttpException { NotFoundException(String m)     { super(404, m); } }
class UnauthorizedException extends HttpException { UnauthorizedException(String m) { super(401, m); } }
class ForbiddenException    extends HttpException { ForbiddenException(String m)    { super(403, m); } }
class BadRequestException   extends HttpException { BadRequestException(String m)   { super(400, m); } }
class ConflictException     extends HttpException { ConflictException(String m)     { super(409, m); } }

// ── Structured Logger ─────────────────────────────────────────────────────────
class Log {
    enum Level { DEBUG, INFO, WARN, ERROR, FATAL }

    private static Level minLevel = Level.DEBUG;
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    static void setLevel(Level l) { minLevel = l; }

    private static void log(Level lvl, String msg, Object... args) {
        if (lvl.ordinal() < minLevel.ordinal()) return;
        String filled = fill(msg, args);
        String ts     = LocalDateTime.now().format(FMT);
        String caller = caller();
        String line   = String.format("%s [%-5s] [%s] %s", ts, lvl, caller, filled);
        if (lvl.ordinal() >= Level.ERROR.ordinal()) System.err.println(line);
        else                                         System.out.println(line);
    }

    private static String fill(String msg, Object[] args) {
        if (args == null || args.length == 0) return msg;
        StringBuilder sb = new StringBuilder(); int ai = 0;
        for (int i = 0; i < msg.length(); i++) {
            if (i + 1 < msg.length() && msg.charAt(i) == '{' && msg.charAt(i+1) == '}') {
                sb.append(ai < args.length ? args[ai++] : "{}"); i++;
            } else sb.append(msg.charAt(i));
        }
        return sb.toString();
    }

    private static String caller() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 3; i < st.length; i++) {
            String cn = st[i].getClassName();
            if (!cn.equals("com.fastjava.core.Log")) {
                String simple = cn.contains(".") ? cn.substring(cn.lastIndexOf('.')+1) : cn;
                return simple + ":" + st[i].getLineNumber();
            }
        }
        return "unknown";
    }

    static void debug(String m, Object... a) { log(Level.DEBUG, m, a); }
    static void info (String m, Object... a) { log(Level.INFO,  m, a); }
    static void warn (String m, Object... a) { log(Level.WARN,  m, a); }
    static void error(String m, Object... a) { log(Level.ERROR, m, a); }
    static void fatal(String m, Object... a) { log(Level.FATAL, m, a); }
}

// ── Simple JSON ───────────────────────────────────────────────────────────────
class Json {
    static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String)  return "\"" + escape((String)obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?,?> m = (Map<?,?>)obj; StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?,?> e : m.entrySet()) {
                if (!first) sb.append(","); first = false;
                sb.append("\"").append(e.getKey()).append("\":").append(toJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (obj instanceof List) {
            List<?> l = (List<?>)obj; StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : l) { if (!first) sb.append(","); first = false; sb.append(toJson(item)); }
            return sb.append("]").toString();
        }
        // POJO via reflection
        if (obj instanceof java.time.temporal.Temporal) return "\"" + obj + "\"";
        StringBuilder sb = new StringBuilder("{"); boolean first = true;
        for (Field f : getAllFields(obj.getClass())) {
            f.setAccessible(true);
            try {
                Object val = f.get(obj);
                if (!first) sb.append(","); first = false;
                sb.append("\"").append(f.getName()).append("\":");
                // Evita recursão infinita em tipos do JDK
                if (val == null || val instanceof String || val instanceof Number || val instanceof Boolean) {
                    sb.append(toJson(val));
                } else if (val instanceof java.time.temporal.Temporal || val instanceof java.util.Date) {
                    sb.append("\"").append(val).append("\"");
                } else if (val instanceof Map || val instanceof List) {
                    sb.append(toJson(val));
                } else if (val.getClass().getName().startsWith("com.fastjava") ||
                           val.getClass().getName().startsWith("java.lang")) {
                    sb.append(toJson(val));
                } else {
                    sb.append("\"").append(escape(val.toString())).append("\"");
                }
            } catch (IllegalAccessException ignored) {}
        }
        return sb.append("}").toString();
    }

    private static List<Field> getAllFields(Class<?> c) {
        List<Field> fields = new ArrayList<>();
        while (c != null && c != Object.class) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
            c = c.getSuperclass();
        }
        return fields;
    }

    static Map<String,Object> parseObject(String json) {
        Map<String,Object> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return m;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1, json.lastIndexOf('}'));
        String[] pairs = splitPairs(json);
        for (String pair : pairs) {
            int colon = findColon(pair);
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim().replaceAll("\"","");
            String val = pair.substring(colon+1).trim();
            m.put(key, parseValue(val));
        }
        return m;
    }

    private static String[] splitPairs(String s) {
        List<String> parts = new ArrayList<>(); int depth = 0; int start = 0;
        boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i-1) != '\\')) inStr = !inStr;
            if (!inStr) { if (c=='{'||c=='['||c=='(') depth++; if (c=='}'||c==']'||c==')') depth--; }
            if (!inStr && depth == 0 && c == ',') { parts.add(s.substring(start,i)); start = i+1; }
        }
        if (start < s.length()) parts.add(s.substring(start));
        return parts.toArray(new String[0]);
    }

    private static int findColon(String s) {
        boolean inStr = false; int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i-1) != '\\')) inStr = !inStr;
            if (!inStr) { if (c=='{'||c=='[') depth++; if (c=='}'||c==']') depth--; }
            if (!inStr && depth == 0 && c == ':') return i;
        }
        return -1;
    }

    static Object parseValue(String v) {
        v = v.trim();
        if (v.startsWith("\"")) return v.substring(1, v.lastIndexOf('"'))
            .replace("\\n","\n").replace("\\t","\t").replace("\\\"","\"");
        if ("true".equals(v))  return Boolean.TRUE;
        if ("false".equals(v)) return Boolean.FALSE;
        if ("null".equals(v))  return null;
        if (v.startsWith("{"))  return parseObject(v);
        if (v.startsWith("[")) { /* simplified */ return new ArrayList<>(); }
        try { if (v.contains(".")) return Double.parseDouble(v); return Long.parseLong(v); }
        catch (NumberFormatException e) { return v; }
    }

    static <T> T fromJson(String json, Class<T> cls) {
        Map<String,Object> map = parseObject(json);
        try {
            T obj = cls.getDeclaredConstructor().newInstance();
            for (Field f : getAllFields(cls)) {
                f.setAccessible(true);
                Object val = map.get(f.getName());
                if (val == null) continue;
                try {
                    if      (f.getType() == String.class)  f.set(obj, val.toString());
                    else if (f.getType() == int.class || f.getType() == Integer.class)
                        f.set(obj, ((Number)val).intValue());
                    else if (f.getType() == long.class || f.getType() == Long.class)
                        f.set(obj, ((Number)val).longValue());
                    else if (f.getType() == double.class || f.getType() == Double.class)
                        f.set(obj, ((Number)val).doubleValue());
                    else if (f.getType() == boolean.class || f.getType() == Boolean.class)
                        f.set(obj, val);
                    else f.set(obj, val);
                } catch (Exception e) {
                    Log.warn("Json.fromJson campo ignorado: {}={} ({})", f.getName(), val, e.getMessage());
                }
            }
            return obj;
        } catch (Exception e) {
            Log.error("Json.fromJson falha ao desserializar {}: {}", cls.getSimpleName(), e.getMessage());
            throw new BadRequestException("Invalid request body");
        }
    }

    private static String escape(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
}

// ── JWT ───────────────────────────────────────────────────────────────────────
class JWT {
    private static String SECRET = System.getenv().getOrDefault("JWT_SECRET","fastjava-default-secret-change-in-prod");
    private static final long EXP_MS = 86_400_000L; // 24h

    static { if ("fastjava-default-secret-change-in-prod".equals(SECRET))
        Log.warn("JWT_SECRET nao definida - usando padrao!"); }

    static String generate(Map<String,Object> claims) {
        String header  = b64(Json.toJson(Map.of("alg","HS256","typ","JWT")));
        Map<String,Object> payload = new LinkedHashMap<>(claims);
        payload.put("iat", System.currentTimeMillis()/1000);
        payload.put("exp", (System.currentTimeMillis()+EXP_MS)/1000);
        String content = header + "." + b64(Json.toJson(payload));
        Log.debug("JWT gerado para sub={}", claims.get("sub"));
        return content + "." + sign(content);
    }

    static Map<String,Object> verify(String token) {
        if (token == null) { Log.warn("JWT: token nulo"); throw new UnauthorizedException("Token required"); }
        if (token.startsWith("Bearer ")) token = token.substring(7);
        String[] p = token.split("\\.");
        if (p.length != 3) { Log.warn("JWT: formato invalido (partes={}})", p.length); throw new UnauthorizedException("Invalid token"); }
        if (!sign(p[0]+"."+p[1]).equals(p[2])) { Log.warn("JWT: assinatura invalida"); throw new UnauthorizedException("Invalid token signature"); }
        Map<String,Object> claims = Json.parseObject(new String(Base64.getUrlDecoder().decode(p[1])));
        long exp = ((Number)claims.getOrDefault("exp",0L)).longValue();
        if (exp < System.currentTimeMillis()/1000) { Log.warn("JWT: token expirado sub={}", claims.get("sub")); throw new UnauthorizedException("Token expired"); }
        Log.debug("JWT verificado ok sub={}", claims.get("sub"));
        return claims;
    }

    private static String b64(String s)  { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String sign(String d) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(d.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { Log.error("JWT sign error: {}", e.getMessage()); throw new RuntimeException(e); }
    }
}

// ── Rate Limiter ──────────────────────────────────────────────────────────────
class RateLimiter {
    private final int max; private final long windowMs;
    private final Map<String, long[]> buckets = new ConcurrentHashMap<>();

    RateLimiter(int max, long windowMs) { this.max = max; this.windowMs = windowMs; }

    boolean allow(String key) {
        long now = System.currentTimeMillis();
        long[] b = buckets.computeIfAbsent(key, k -> new long[]{now, 0});
        synchronized (b) {
            if (now - b[0] > windowMs) { b[0] = now; b[1] = 0; }
            if (b[1] >= max) { Log.warn("RateLimit excedido: key={} count={}", key, (long)b[1]); return false; }
            b[1]++;
        }
        return true;
    }
}

// ── DI Container ─────────────────────────────────────────────────────────────
class Container {
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    <T> T get(Class<T> cls) {
        Object existing = singletons.get(cls);
        if (existing != null) return cls.cast(existing);
        try {
            Log.debug("Container: criando singleton {}", cls.getSimpleName());
            T inst = cls.getDeclaredConstructor().newInstance();
            singletons.put(cls, inst);
            injectFields(inst);
            return inst;
        } catch (Exception e) {
            Log.error("Container: falha ao criar {}: {}", cls.getSimpleName(), e.getMessage());
            throw new RuntimeException("DI failed for " + cls.getSimpleName(), e);
        }
    }

    void injectFields(Object obj) throws IllegalAccessException {
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(Inject.class)) {
                f.setAccessible(true);
                Object dep = get(f.getType());
                f.set(obj, dep);
                Log.debug("Container: injetado {}#{}", obj.getClass().getSimpleName(), f.getName());
            }
        }
    }

    void register(Object obj) {
        Log.debug("Container: registrando {}", obj.getClass().getSimpleName());
        singletons.put(obj.getClass(), obj);
    }
}

// ── Route ─────────────────────────────────────────────────────────────────────
class Route {
    final String method, pattern;
    final boolean authenticated;
    final Object ctrl;
    final Method handler;
    final List<String> paramNames = new ArrayList<>();

    Route(String method, String pattern, boolean auth, Object ctrl, Method handler) {
        this.method = method; this.pattern = pattern; this.authenticated = auth;
        this.ctrl = ctrl; this.handler = handler;
        for (String seg : pattern.split("/")) if (seg.startsWith(":")) paramNames.add(seg.substring(1));
    }

    boolean matches(String httpMethod, String path) {
        if (!this.method.equals(httpMethod)) return false;
        String[] pp = pattern.split("/"), rp = path.split("/");
        if (pp.length != rp.length) return false;
        for (int i = 0; i < pp.length; i++) if (!pp[i].startsWith(":") && !pp[i].equals(rp[i])) return false;
        return true;
    }

    Map<String,String> extractParams(String path) {
        Map<String,String> m = new LinkedHashMap<>();
        String[] pp = pattern.split("/"), rp = path.split("/");
        for (int i = 0; i < pp.length; i++) if (pp[i].startsWith(":")) m.put(pp[i].substring(1), rp[i]);
        return m;
    }
}

// ── FastJava Framework ────────────────────────────────────────────────────────
public class FastJava {
    private final List<Route>  routes    = new ArrayList<>();
    private final Container    container = new Container();
    private final RateLimiter  limiter   = new RateLimiter(100, 60_000);
    private HttpServer          server;
    private int                 port      = 8080;

    public FastJava port(int p) { this.port = p; return this; }

    public FastJava register(Class<?>... classes) {
        for (Class<?> cls : classes) {
            Object inst = container.get(cls);
            Controller ctrl = cls.getAnnotation(Controller.class);
            if (ctrl == null) { container.register(inst); continue; }
            String base = ctrl.value();
            Log.debug("Registrando controller {} base={}", cls.getSimpleName(), base);
            List<Route> ctrlRoutes = new ArrayList<>();
            for (Method m : cls.getDeclaredMethods()) {
                String httpMethod = null; String subPath = "";
                Path pa = m.getAnnotation(Path.class);
                if (pa != null) subPath = pa.value();
                if (m.isAnnotationPresent(GET.class))    httpMethod = "GET";
                if (m.isAnnotationPresent(POST.class))   httpMethod = "POST";
                if (m.isAnnotationPresent(PUT.class))    httpMethod = "PUT";
                if (m.isAnnotationPresent(DELETE.class)) httpMethod = "DELETE";
                if (m.isAnnotationPresent(PATCH.class))  httpMethod = "PATCH";
                if (httpMethod == null) continue;
                String fullPath = (base + subPath).replaceAll("//","/");
                boolean auth = m.isAnnotationPresent(Authenticated.class);
                ctrlRoutes.add(new Route(httpMethod, fullPath, auth, inst, m));
            }
            // Rotas fixas (sem :param) primeiro, depois as com parametros
            ctrlRoutes.sort((a, b) -> {
                boolean aHasParam = a.pattern.contains(":");
                boolean bHasParam = b.pattern.contains(":");
                if (aHasParam == bHasParam) return 0;
                return aHasParam ? 1 : -1;
            });
            for (Route r : ctrlRoutes) {
                routes.add(r);
                Log.info("Rota: {} {}", r.method, r.pattern);
            }
        }
        return this;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        Log.info("Servidor iniciado na porta {}", port);
    }

    private void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path   = ex.getRequestURI().getPath();
        String query  = ex.getRequestURI().getQuery();
        String ip     = ex.getRemoteAddress().getAddress().getHostAddress();
        long   t0     = System.currentTimeMillis();

        Log.debug("Req: {} {} ip={}", method, path, ip);

        ex.getResponseHeaders().add("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods","GET,POST,PUT,DELETE,PATCH,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers","Content-Type,Authorization");

        if ("OPTIONS".equals(method)) { ex.sendResponseHeaders(204,-1); return; }
        if (!limiter.allow(ip)) {
            Log.warn("RateLimit bloqueado: ip={} {} {}", ip, method, path);
            send(ex, 429, Map.of("error","Too many requests"));
            return;
        }

        for (Route route : routes) {
            if (!route.matches(method, path)) continue;
            try {
                Map<String,Object> claims = null;
                if (route.authenticated) {
                    String auth = ex.getRequestHeaders().getFirst("Authorization");
                    Log.debug("Auth: {} {}", method, path);
                    claims = JWT.verify(auth);
                }
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String,String> pathParams = route.extractParams(path);
                Map<String,String> qParams    = parseQuery(query);
                Object result = invoke(route, pathParams, qParams, claims, body, ex);
                long ms = System.currentTimeMillis() - t0;
                Log.info("{} {} -> 200 ({}ms)", method, path, ms);
                send(ex, 200, result);
            } catch (HttpException he) {
                long ms = System.currentTimeMillis() - t0;
                Log.warn("{} {} -> {} {} ({}ms)", method, path, he.status, he.getMessage(), ms);
                send(ex, he.status, Map.of("error", he.getMessage()));
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause();
                long ms = System.currentTimeMillis() - t0;
                if (cause instanceof HttpException he) {
                    Log.warn("{} {} -> {} {} ({}ms)", method, path, he.status, he.getMessage(), ms);
                    send(ex, he.status, Map.of("error", he.getMessage()));
                } else {
                    Log.error("{} {} -> 500 {} ({}ms)", method, path, cause != null ? cause.getMessage() : ite.getMessage(), ms);
                    if (cause != null) cause.printStackTrace();
                    send(ex, 500, Map.of("error", "Internal server error: " + (cause != null ? cause.getMessage() : ite.getMessage())));
                }
            } catch (Exception e) {
                long ms = System.currentTimeMillis() - t0;
                Log.error("{} {} -> 500 {} ({}ms)", method, path, e.getMessage(), ms);
                e.printStackTrace();
                send(ex, 500, Map.of("error","Internal server error"));
            }
            return;
        }
        long ms = System.currentTimeMillis() - t0;
        Log.warn("404: {} {} ({}ms)", method, path, ms);
        send(ex, 404, Map.of("error","Not found"));
    }

    private Object invoke(Route route, Map<String,String> pp, Map<String,String> qp,
                          Map<String,Object> claims, String body, HttpExchange ex) throws Exception {
        Method m = route.handler;
        Object[] args = new Object[m.getParameterCount()];
        Parameter[] params = m.getParameters();
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            if (p.isAnnotationPresent(PathParam.class)) {
                String key = p.getAnnotation(PathParam.class).value();
                String val = pp.get(key);
                Log.debug("PathParam {}={}", key, val);
                args[i] = convertType(val, p.getType());
            } else if (p.isAnnotationPresent(QueryParam.class)) {
                String key = p.getAnnotation(QueryParam.class).value();
                String val = qp.get(key);
                Log.debug("QueryParam {}={}", key, val);
                args[i] = val == null ? null : convertType(val, p.getType());
            } else if (p.isAnnotationPresent(Header.class)) {
                String key = p.getAnnotation(Header.class).value();
                args[i] = ex.getRequestHeaders().getFirst(key);
            } else if (p.isAnnotationPresent(Body.class)) {
                Log.debug("Body desserializando para {}", p.getType().getSimpleName());
                args[i] = Json.fromJson(body, p.getType());
            } else if (p.getType() == Map.class) {
                args[i] = claims;
            }
        }
        return m.invoke(route.ctrl, args);
    }

    private Object convertType(String val, Class<?> t) {
        if (val == null) return null;
        if (t == String.class)  return val;
        if (t == long.class || t == Long.class) {
            try { return Long.parseLong(val); }
            catch (NumberFormatException e) {
                Log.warn("Falha converter '{}' -> long", val);
                throw new BadRequestException("Invalid numeric parameter: " + val);
            }
        }
        if (t == int.class || t == Integer.class) {
            try { return Integer.parseInt(val); }
            catch (NumberFormatException e) {
                Log.warn("Falha converter '{}' -> int", val);
                throw new BadRequestException("Invalid numeric parameter: " + val);
            }
        }
        if (t == double.class || t == Double.class) return Double.parseDouble(val);
        if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(val);
        return val;
    }

    private Map<String,String> parseQuery(String q) {
        Map<String,String> m = new LinkedHashMap<>();
        if (q == null || q.isBlank()) return m;
        for (String p : q.split("&")) {
            int eq = p.indexOf('=');
            if (eq > 0) m.put(URLDecoder.decode(p.substring(0,eq), StandardCharsets.UTF_8),
                               URLDecoder.decode(p.substring(eq+1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private void send(HttpExchange ex, int status, Object body) throws IOException {
        String json = body instanceof String ? (String)body : Json.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public void stop() { if (server != null) { server.stop(0); Log.info("Servidor parado"); } }
}
