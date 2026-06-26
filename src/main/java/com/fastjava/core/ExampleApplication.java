package com.fastjava.core;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

// ── Domain Models ─────────────────────────────────────────────────────────────
class User {
    long id; String username; String email; String passwordHash;
    String role = "USER"; Instant createdAt;
    User() {}
    User(long id, String u, String e, String ph) {
        this.id=id; this.username=u; this.email=e;
        this.passwordHash=ph; this.createdAt=Instant.now();
    }
}

class UserView {
    long id; String username; String email; String role; Instant createdAt;
    UserView(User u){ this.id=u.id; this.username=u.username; this.email=u.email; this.role=u.role; this.createdAt=u.createdAt; }
}

class Product {
    long id; String name; double price; String category; String description;
    int stock; Instant createdAt;
    Product() {}
}

// ── Services ──────────────────────────────────────────────────────────────────
@Service
class UserService {
    private final Map<Long,User>    byId   = new ConcurrentHashMap<>();
    private final Map<String,User>  byName = new ConcurrentHashMap<>();
    private final AtomicLong        seq    = new AtomicLong(0);

    User create(String username, String email, String password) {
        Log.info("UserService.create: username={} email={}", username, email);
        if (username == null || username.isBlank()) {
            Log.warn("UserService.create: username vazio");
            throw new BadRequestException("Username required");
        }
        if (email == null || email.isBlank()) {
            Log.warn("UserService.create: email vazio");
            throw new BadRequestException("Email required");
        }
        if (password == null || password.length() < 6) {
            Log.warn("UserService.create: senha fraca para username={}", username);
            throw new BadRequestException("Password must be at least 6 characters");
        }
        if (byName.containsKey(username)) {
            Log.warn("UserService.create: conflito username={}", username);
            throw new ConflictException("Username already exists");
        }
        User u = new User(seq.incrementAndGet(), username, email, hash(password));
        byId.put(u.id, u); byName.put(u.username, u);
        Log.info("UserService.create: ok id={} username={}", u.id, u.username);
        return u;
    }

    boolean validatePassword(User u, String raw) {
        boolean ok = u.passwordHash.equals(hash(raw));
        if (!ok) Log.warn("UserService.validatePassword: senha incorreta para username={}", u.username);
        return ok;
    }

    User findByUsername(String username) {
        User u = byName.get(username);
        if (u == null) Log.debug("UserService.findByUsername: nao encontrado username={}", username);
        return u;
    }

    User findById(long id) {
        User u = byId.get(id);
        if (u == null) { Log.warn("UserService.findById: nao encontrado id={}", id); throw new NotFoundException("User not found: " + id); }
        Log.debug("UserService.findById: encontrado id={} username={}", id, u.username);
        return u;
    }

    List<UserView> findAll() {
        Log.debug("UserService.findAll: total={}", byId.size());
        List<UserView> list = new ArrayList<>();
        for (User u : byId.values()) list.add(new UserView(u));
        return list;
    }

    UserView update(long id, Map<String,Object> data, Map<String,Object> claims) {
        Log.info("UserService.update: id={} caller={}", id, claims.get("sub"));
        User u = findById(id);
        long callerId = ((Number)claims.get("sub")).longValue();
        String role   = (String)claims.getOrDefault("role","USER");
        if (callerId != id && !"ADMIN".equals(role)) {
            Log.warn("UserService.update: permissao negada sub={} tentando editar id={}", callerId, id);
            throw new ForbiddenException("Cannot edit another user");
        }
        if (data.containsKey("email")) { u.email = data.get("email").toString(); Log.debug("UserService.update: email atualizado id={}", id); }
        if (data.containsKey("password")) { u.passwordHash = hash(data.get("password").toString()); Log.debug("UserService.update: senha atualizada id={}", id); }
        Log.info("UserService.update: ok id={}", id);
        return new UserView(u);
    }

    void delete(long id, Map<String,Object> claims) {
        Log.info("UserService.delete: id={} caller={}", id, claims.get("sub"));
        String role = (String)claims.getOrDefault("role","USER");
        if (!"ADMIN".equals(role)) {
            Log.warn("UserService.delete: permissao negada sub={} role={}", claims.get("sub"), role);
            throw new ForbiddenException("Admin role required");
        }
        User u = byId.remove(id);
        if (u != null) { byName.remove(u.username); Log.info("UserService.delete: ok id={} username={}", id, u.username); }
        else { Log.warn("UserService.delete: id={} nao encontrado", id); throw new NotFoundException("User not found: " + id); }
    }

    private String hash(String p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest((p+"fastjava-salt").getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x",x));
            return sb.toString();
        } catch (Exception e) { Log.error("hash error: {}", e.getMessage()); throw new RuntimeException(e); }
    }
}

@Service
class ProductService {
    private final Map<Long,Product> db  = new ConcurrentHashMap<>();
    private final AtomicLong        seq = new AtomicLong(0);

    Product create(Product p) {
        Log.info("ProductService.create: name={} price={} category={}", p.name, p.price, p.category);
        if (p.name == null || p.name.isBlank()) {
            Log.warn("ProductService.create: nome vazio");
            throw new BadRequestException("Product name required");
        }
        if (p.price < 0) {
            Log.warn("ProductService.create: preco negativo price={}", p.price);
            throw new BadRequestException("Price cannot be negative");
        }
        p.id = seq.incrementAndGet(); p.createdAt = Instant.now();
        db.put(p.id, p);
        Log.info("ProductService.create: ok id={} name={}", p.id, p.name);
        return p;
    }

    List<Product> findAll(String category) {
        Log.debug("ProductService.findAll: category={} total={}", category, db.size());
        List<Product> list = new ArrayList<>();
        for (Product p : db.values()) {
            if (category == null || category.equalsIgnoreCase(p.category)) list.add(p);
        }
        Log.debug("ProductService.findAll: retornando {} itens", list.size());
        return list;
    }

    Product findById(long id) {
        Product p = db.get(id);
        if (p == null) { Log.warn("ProductService.findById: nao encontrado id={}", id); throw new NotFoundException("Product not found: " + id); }
        Log.debug("ProductService.findById: ok id={} name={}", id, p.name);
        return p;
    }

    Product update(long id, Product upd) {
        Log.info("ProductService.update: id={}", id);
        Product p = findById(id);
        if (upd.name != null && !upd.name.isBlank())    { Log.debug("ProductService.update: name {} -> {}", p.name, upd.name); p.name = upd.name; }
        if (upd.price > 0)                               { Log.debug("ProductService.update: price {} -> {}", p.price, upd.price); p.price = upd.price; }
        if (upd.category != null)                        { p.category = upd.category; }
        if (upd.description != null)                     { p.description = upd.description; }
        if (upd.stock >= 0)                              { Log.debug("ProductService.update: stock {} -> {}", p.stock, upd.stock); p.stock = upd.stock; }
        Log.info("ProductService.update: ok id={}", id);
        return p;
    }

    void delete(long id) {
        Log.info("ProductService.delete: id={}", id);
        Product p = db.remove(id);
        if (p == null) { Log.warn("ProductService.delete: id={} nao encontrado", id); throw new NotFoundException("Product not found: " + id); }
        Log.info("ProductService.delete: ok id={} name={}", id, p.name);
    }

    Map<String,Object> getStats() {
        Log.debug("ProductService.getStats: calculando...");
        double total = 0; Map<String,Long> byCat = new LinkedHashMap<>();
        for (Product p : db.values()) {
            total += p.price * p.stock;
            byCat.merge(p.category != null ? p.category : "Sem categoria", 1L, Long::sum);
        }
        Map<String,Object> stats = new LinkedHashMap<>();
        stats.put("totalProducts", db.size());
        stats.put("totalValue", total);
        stats.put("byCategory", byCat);
        Log.info("ProductService.getStats: total={} valorTotal={}", db.size(), total);
        return stats;
    }
}

// ── Controllers ───────────────────────────────────────────────────────────────
@Controller("/api/auth")
class AuthController {
    @Inject UserService userService;

    static class RegisterReq { String username; String email; String password; }
    static class LoginReq    { String username; String password; }

    @POST @Path("/register")
    Object register(@Body RegisterReq req) {
        Log.info("AuthController.register: username={}", req.username);
        User u = userService.create(req.username, req.email, req.password);
        Log.info("AuthController.register: sucesso id={} username={}", u.id, u.username);
        return new UserView(u);
    }

    @POST @Path("/login")
    Object login(@Body LoginReq req) {
        Log.info("AuthController.login: tentativa username={}", req.username);
        User user = userService.findByUsername(req.username);
        if (user == null || !userService.validatePassword(user, req.password)) {
            Log.warn("AuthController.login: falhou username={}", req.username);
            throw new UnauthorizedException("Invalid credentials");
        }
        Map<String,Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(user.id));
        claims.put("username", user.username);
        claims.put("role", user.role);
        String token = JWT.generate(claims);
        Log.info("AuthController.login: sucesso username={} id={}", user.username, user.id);
        Map<String,Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("username", user.username);
        resp.put("role", user.role);
        return resp;
    }
}

@Controller("/api/users")
class UserController {
    @Inject UserService userService;

    @GET @Authenticated
    Object listUsers(Map<String,Object> claims) {
        Log.info("UserController.listUsers: caller=sub={}", claims.get("sub"));
        List<UserView> users = userService.findAll();
        Log.info("UserController.listUsers: retornando {} usuarios", users.size());
        return users;
    }

    @GET @Path("/:id") @Authenticated
    Object getUser(@PathParam("id") long id, Map<String,Object> claims) {
        Log.info("UserController.getUser: id={} caller=sub={}", id, claims.get("sub"));
        return new UserView(userService.findById(id));
    }

    @GET @Path("/async/:id") @Authenticated
    Object getUserAsync(@PathParam("id") long id, Map<String,Object> claims) {
        Log.info("UserController.getUserAsync: id={} caller=sub={}", id, claims.get("sub"));
        CompletableFuture<UserView> f = CompletableFuture.supplyAsync(() -> {
            Log.debug("UserController.getUserAsync: buscando id={} em thread virtual", id);
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new UserView(userService.findById(id));
        });
        try {
            UserView v = f.get(5, TimeUnit.SECONDS);
            Log.info("UserController.getUserAsync: ok id={}", id);
            return v;
        } catch (Exception e) {
            Log.error("UserController.getUserAsync: timeout/erro id={}: {}", id, e.getMessage());
            throw new RuntimeException("Async fetch failed", e);
        }
    }

    @PUT @Path("/:id") @Authenticated
    Object updateUser(@PathParam("id") long id, @Body Map<String,Object> body, Map<String,Object> claims) {
        Log.info("UserController.updateUser: id={} caller=sub={}", id, claims.get("sub"));
        return userService.update(id, body, claims);
    }

    @DELETE @Path("/:id") @Authenticated
    Object deleteUser(@PathParam("id") long id, Map<String,Object> claims) {
        Log.info("UserController.deleteUser: id={} caller=sub={}", id, claims.get("sub"));
        userService.delete(id, claims);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("message", "User deleted");
        Log.info("UserController.deleteUser: ok id={}", id);
        return r;
    }
}

@Controller("/api/products")
class ProductController {
    @Inject ProductService productService;

    @GET @Path("/stats") @Authenticated
    Object getStats(Map<String,Object> claims) {
        Log.info("ProductController.getStats: caller=sub={}", claims.get("sub"));
        return productService.getStats();
    }

    @GET
    Object listProducts(@QueryParam("category") String category) {
        Log.info("ProductController.listProducts: category={}", category);
        List<Product> list = productService.findAll(category);
        Log.info("ProductController.listProducts: retornando {} produtos", list.size());
        return list;
    }

    @GET @Path("/:id")
    Object getProduct(@PathParam("id") long id) {
        Log.info("ProductController.getProduct: id={}", id);
        return productService.findById(id);
    }

    @POST @Authenticated
    Object createProduct(@Body Product p, Map<String,Object> claims) {
        Log.info("ProductController.createProduct: caller=sub={}", claims.get("sub"));
        return productService.create(p);
    }

    @PUT @Path("/:id") @Authenticated
    Object updateProduct(@PathParam("id") long id, @Body Product upd, Map<String,Object> claims) {
        Log.info("ProductController.updateProduct: id={} caller=sub={}", id, claims.get("sub"));
        return productService.update(id, upd);
    }

    @DELETE @Path("/:id") @Authenticated
    Object deleteProduct(@PathParam("id") long id, Map<String,Object> claims) {
        Log.info("ProductController.deleteProduct: id={} caller=sub={}", id, claims.get("sub"));
        productService.delete(id);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("message", "Product deleted");
        return r;
    }
}

// ── Health ────────────────────────────────────────────────────────────────────
@Controller("")
class HealthController {
    @GET @Path("/health")
    Object health() {
        Log.debug("HealthController.health: ping");
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("status","ok"); r.put("ts", Instant.now().toString());
        return r;
    }
}

// ── Main ──────────────────────────────────────────────────────────────────────
public class ExampleApplication {
    public static void main(String[] args) throws IOException {
        System.out.println("");
        System.out.println("  ====================================================");
        System.out.println("               FastJava Framework 2.0");
        System.out.println("          by STR Software Co.");
        System.out.println("  Open Source  |  Java 17+  |  Lightweight REST");
        System.out.println("  ====================================================");
        System.out.println("");
        Log.info("Iniciando ExampleApplication...");
        new FastJava()
            .port(8080)
            .register(
                AuthController.class,
                UserController.class,
                ProductController.class,
                HealthController.class
            )
            .start();
        Log.info("=== API pronta! Porta 8080 ===");
        Log.info("POST /api/auth/register  - criar conta");
        Log.info("POST /api/auth/login     - login JWT");
        Log.info("GET  /api/users          - listar usuarios (JWT)");
        Log.info("GET  /api/products       - listar produtos");
        Log.info("GET  /health             - health check");
    }
}
