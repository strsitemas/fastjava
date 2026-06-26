# patch_postgres.py
# Adiciona suporte a PostgreSQL (Neon) ao FastJava
# Coloque em C:\GABARITO\capas\fast\ e rode: python patch_postgres.py

import os
import re

POM_FILE  = r"C:\GABARITO\capas\fast\pom.xml"
JAVA_FILE = r"C:\GABARITO\capas\fast\src\main\java\com\fastjava\core\ExampleApplication.java"

# ─── 1. PATCH DO POM.XML ────────────────────────────────────────────────────

POM_ANCHOR = "        <dependency>\n            <groupId>org.junit.jupiter</groupId>"

POM_NEW_DEP = """        <!-- PostgreSQL JDBC Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.3</version>
        </dependency>

        """

# ─── 2. NOVO ExampleApplication.java COMPLETO ───────────────────────────────

NEW_EXAMPLE = r'''package com.fastjava.core;

import java.io.IOException;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

// ══════════════════════════════════════════════════════════════════════════════
//  DB — PostgreSQL connection helper (Neon-compatible)
// ══════════════════════════════════════════════════════════════════════════════
class DB {
    private static final String URL = System.getenv().getOrDefault(
        "DATABASE_URL",
        "postgresql://neondb_owner:npg_3xvAtFUKwZG2@ep-wandering-bar-acfrua1q-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"
    );

    static Connection connect() throws SQLException {
        // Converte formato postgres:// para jdbc:postgresql://
        String jdbc = URL.startsWith("jdbc:") ? URL : "jdbc:" + URL;
        return DriverManager.getConnection(jdbc);
    }

    static void migrate() {
        Log.info("DB.migrate: criando tabelas se nao existirem...");
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS users (
                id         BIGSERIAL PRIMARY KEY,
                username   VARCHAR(100) UNIQUE NOT NULL,
                email      VARCHAR(200) UNIQUE NOT NULL,
                password   VARCHAR(255) NOT NULL,
                role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
                created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS products (
                id          BIGSERIAL PRIMARY KEY,
                name        VARCHAR(200) NOT NULL,
                price       DOUBLE PRECISION NOT NULL,
                category    VARCHAR(100),
                description TEXT,
                stock       INTEGER NOT NULL DEFAULT 0,
                created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """
        };
        try (Connection conn = connect()) {
            for (String sql : ddl) {
                try (Statement st = conn.createStatement()) {
                    st.execute(sql);
                }
            }
            Log.info("DB.migrate: tabelas ok (users, products)");
        } catch (SQLException e) {
            Log.error("DB.migrate: falha: {}", e.getMessage());
            throw new RuntimeException("Migration failed", e);
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Modelos
// ══════════════════════════════════════════════════════════════════════════════
class User {
    long   id;
    String username, email, password, role;
    Instant createdAt;
    User() {}
}

class UserView {
    long   id;
    String username, email, role;
    Instant createdAt;
    UserView(User u) {
        this.id = u.id; this.username = u.username;
        this.email = u.email; this.role = u.role;
        this.createdAt = u.createdAt;
    }
}

class Product {
    long   id;
    String name, category, description;
    double price;
    int    stock;
    Instant createdAt;
    Product() {}
}

// ══════════════════════════════════════════════════════════════════════════════
//  UserService — PostgreSQL
// ══════════════════════════════════════════════════════════════════════════════
@Service
class UserService {

    private static final String SHA256_PEPPER = "fastjava-pepper";

    private String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((password + SHA256_PEPPER).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    User create(String username, String email, String password) {
        Log.info("UserService.create: username={} email={}", username, email);
        if (username == null || username.isBlank())
            throw new BadRequestException("Username obrigatorio");
        if (password == null || password.length() < 6)
            throw new BadRequestException("Senha deve ter ao menos 6 caracteres");

        String sql = "INSERT INTO users(username,email,password,role) VALUES(?,?,?,'USER') RETURNING id,created_at";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, hash(password));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                User u = new User();
                u.id = rs.getLong("id");
                u.username = username; u.email = email;
                u.role = "USER";
                u.createdAt = rs.getTimestamp("created_at").toInstant();
                Log.info("UserService.create: ok id={} username={}", u.id, u.username);
                return u;
            }
            throw new RuntimeException("Insert nao retornou id");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("unique")) {
                Log.warn("UserService.create: conflito username={}", username);
                throw new ConflictException("Username already exists");
            }
            Log.error("UserService.create: SQL error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    boolean validatePassword(String username, String password) {
        String sql = "SELECT password FROM users WHERE username=?";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return false;
            boolean ok = rs.getString("password").equals(hash(password));
            if (!ok) Log.warn("UserService.validatePassword: senha incorreta para username={}", username);
            return ok;
        } catch (SQLException e) {
            Log.error("UserService.validatePassword: {}", e.getMessage());
            return false;
        }
    }

    User findByUsername(String username) {
        String sql = "SELECT id,username,email,role,created_at FROM users WHERE username=?";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { Log.warn("UserService.findByUsername: nao encontrado username={}", username); return null; }
            return mapUser(rs);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    User findById(long id) {
        String sql = "SELECT id,username,email,role,created_at FROM users WHERE id=?";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { Log.warn("UserService.findById: nao encontrado id={}", id); return null; }
            return mapUser(rs);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    List<UserView> listAll() {
        List<UserView> list = new ArrayList<>();
        String sql = "SELECT id,username,email,role,created_at FROM users ORDER BY id";
        try (Connection conn = DB.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(new UserView(mapUser(rs)));
            Log.debug("UserService.listAll: {} usuarios", list.size());
            return list;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    User update(long id, String callerSub, String role, String email) {
        User existing = findById(id);
        if (existing == null) throw new NotFoundException("User not found");
        long callerId = Long.parseLong(callerSub);
        if (callerId != id && !"ADMIN".equals(role))
            throw new ForbiddenException("Sem permissao");
        String sql = "UPDATE users SET email=? WHERE id=? RETURNING id,username,email,role,created_at";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email != null ? email : existing.email);
            ps.setLong(2, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) { Log.info("UserService.update: ok id={}", id); return mapUser(rs); }
            throw new NotFoundException("User not found");
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    void delete(long id, String callerSub, String role) {
        long callerId = Long.parseLong(callerSub);
        if (callerId != id && !"ADMIN".equals(role))
            throw new ForbiddenException("Sem permissao");
        String sql = "DELETE FROM users WHERE id=?";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new NotFoundException("User not found");
            Log.info("UserService.delete: ok id={}", id);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.email = rs.getString("email");
        u.role = rs.getString("role");
        u.createdAt = rs.getTimestamp("created_at").toInstant();
        return u;
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ProductService — PostgreSQL
// ══════════════════════════════════════════════════════════════════════════════
@Service
class ProductService {

    Product create(String name, double price, String category, String description, int stock) {
        Log.info("ProductService.create: name={} price={}", name, price);
        if (name == null || name.isBlank()) throw new BadRequestException("Nome obrigatorio");
        if (price < 0) throw new BadRequestException("Preco nao pode ser negativo");
        String sql = "INSERT INTO products(name,price,category,description,stock) VALUES(?,?,?,?,?) RETURNING id,created_at";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.setString(3, category);
            ps.setString(4, description);
            ps.setInt(5, stock);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Product p = new Product();
                p.id = rs.getLong("id"); p.name = name; p.price = price;
                p.category = category; p.description = description; p.stock = stock;
                p.createdAt = rs.getTimestamp("created_at").toInstant();
                Log.info("ProductService.create: ok id={}", p.id);
                return p;
            }
            throw new RuntimeException("Insert nao retornou id");
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    List<Product> listAll(String category) {
        List<Product> list = new ArrayList<>();
        String sql = category != null
            ? "SELECT * FROM products WHERE category=? ORDER BY id"
            : "SELECT * FROM products ORDER BY id";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (category != null) ps.setString(1, category);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapProduct(rs));
            Log.debug("ProductService.listAll: {} produtos category={}", list.size(), category);
            return list;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    Product findById(long id) {
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM products WHERE id=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new NotFoundException("Product not found");
            return mapProduct(rs);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    Product update(long id, String name, Double price, String category, String description, Integer stock) {
        Product existing = findById(id);
        String sql = """
            UPDATE products
               SET name=?, price=?, category=?, description=?, stock=?
             WHERE id=?
             RETURNING *
            """;
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name != null ? name : existing.name);
            ps.setDouble(2, price != null ? price : existing.price);
            ps.setString(3, category != null ? category : existing.category);
            ps.setString(4, description != null ? description : existing.description);
            ps.setInt(5, stock != null ? stock : existing.stock);
            ps.setLong(6, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) { Log.info("ProductService.update: ok id={}", id); return mapProduct(rs); }
            throw new NotFoundException("Product not found");
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    void delete(long id) {
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM products WHERE id=?")) {
            ps.setLong(1, id);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new NotFoundException("Product not found");
            Log.info("ProductService.delete: ok id={}", id);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    Map<String,Object> getStats() {
        String sql = """
            SELECT COUNT(*) as total,
                   COALESCE(SUM(price * stock), 0) as total_value,
                   category,
                   COUNT(*) as cat_count
              FROM products
             GROUP BY category
            """;
        Map<String,Object> stats = new LinkedHashMap<>();
        Map<String,Long> byCategory = new LinkedHashMap<>();
        long totalProducts = 0; double totalValue = 0;
        try (Connection conn = DB.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                totalProducts += rs.getLong("total");
                totalValue    += rs.getDouble("total_value");
                String cat = rs.getString("category");
                if (cat != null) byCategory.put(cat, rs.getLong("cat_count"));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        stats.put("totalProducts", totalProducts);
        stats.put("totalValue", totalValue);
        stats.put("byCategory", byCategory);
        Log.info("ProductService.getStats: totalProducts={} totalValue={}", totalProducts, totalValue);
        return stats;
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.id = rs.getLong("id"); p.name = rs.getString("name");
        p.price = rs.getDouble("price"); p.category = rs.getString("category");
        p.description = rs.getString("description"); p.stock = rs.getInt("stock");
        p.createdAt = rs.getTimestamp("created_at").toInstant();
        return p;
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Controllers
// ══════════════════════════════════════════════════════════════════════════════
@Controller("/api/auth")
class AuthController {
    @Inject UserService userService;

    static class RegisterReq { public String username, email, password; }
    static class LoginReq    { public String username, password; }

    @POST @Path("/login")
    Object login(@Body LoginReq req) {
        Log.info("AuthController.login: tentativa username={}", req.username);
        User u = userService.findByUsername(req.username);
        if (u == null || !userService.validatePassword(req.username, req.password)) {
            Log.warn("AuthController.login: falhou username={}", req.username);
            throw new UnauthorizedException("Invalid credentials");
        }
        String token = JWT.generate(Map.of("sub", String.valueOf(u.id), "username", u.username, "role", u.role));
        Log.info("AuthController.login: sucesso username={} id={}", u.username, u.id);
        return Map.of("token", token, "user", new UserView(u));
    }

    @POST @Path("/register")
    Object register(@Body RegisterReq req) {
        Log.info("AuthController.register: username={}", req.username);
        User u = userService.create(req.username, req.email, req.password);
        Log.info("AuthController.register: sucesso id={} username={}", u.id, u.username);
        return new UserView(u);
    }
}

@Controller("/api/users")
class UserController {
    @Inject UserService userService;

    @GET @Authenticated
    Object listUsers(Map<String,Object> claims) {
        Log.info("UserController.listUsers: sub={}", claims.get("sub"));
        return userService.listAll();
    }

    @GET @Path("/:id") @Authenticated
    Object getUser(@PathParam("id") long id, Map<String,Object> claims) {
        Log.info("UserController.getUser: id={} sub={}", id, claims.get("sub"));
        User u = userService.findById(id);
        if (u == null) throw new NotFoundException("User not found");
        return new UserView(u);
    }

    @GET @Path("/async/:id") @Authenticated
    Object getUserAsync(@PathParam("id") long id, Map<String,Object> claims) {
        Log.info("UserController.getUserAsync: id={}", id);
        return CompletableFuture.supplyAsync(() -> {
            User u = userService.findById(id);
            if (u == null) throw new NotFoundException("User not found");
            return new UserView(u);
        }).join();
    }

    @PUT @Path("/:id") @Authenticated
    Object updateUser(@PathParam("id") long id, @Body Map<String,Object> body, Map<String,Object> claims) {
        String email = body != null ? (String) body.get("email") : null;
        return new UserView(userService.update(id, (String)claims.get("sub"), (String)claims.get("role"), email));
    }

    @DELETE @Path("/:id") @Authenticated
    Object deleteUser(@PathParam("id") long id, Map<String,Object> claims) {
        userService.delete(id, (String)claims.get("sub"), (String)claims.get("role"));
        return Map.of("message", "User deleted");
    }
}

@Controller("/api/products")
class ProductController {
    @Inject ProductService productService;

    static class ProductReq {
        public String name, category, description;
        public double price;
        public int stock;
    }

    @GET
    Object listProducts(@QueryParam("category") String category) {
        Log.info("ProductController.listProducts: category={}", category);
        return productService.listAll(category);
    }

    @GET @Path("/stats") @Authenticated
    Object getStats(Map<String,Object> claims) {
        return productService.getStats();
    }

    @GET @Path("/:id")
    Object getProduct(@PathParam("id") long id) {
        return productService.findById(id);
    }

    @POST @Authenticated
    Object createProduct(@Body ProductReq req, Map<String,Object> claims) {
        return productService.create(req.name, req.price, req.category, req.description, req.stock);
    }

    @PUT @Path("/:id") @Authenticated
    Object updateProduct(@PathParam("id") long id, @Body ProductReq req, Map<String,Object> claims) {
        return productService.update(id, req.name, req.price, req.category, req.description, req.stock);
    }

    @DELETE @Path("/:id") @Authenticated
    Object deleteProduct(@PathParam("id") long id, Map<String,Object> claims) {
        productService.delete(id);
        return Map.of("message", "Product deleted");
    }
}

@Controller("")
class HealthController {
    @GET @Path("/health")
    Object health() {
        // Testa conexao com banco
        try (Connection conn = DB.connect();
             Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
            return Map.of("status","ok","db","connected","framework","FastJava 2.0");
        } catch (SQLException e) {
            Log.error("HealthController: DB offline: {}", e.getMessage());
            return Map.of("status","degraded","db","disconnected","error", e.getMessage());
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ExampleApplication
// ══════════════════════════════════════════════════════════════════════════════
public class ExampleApplication {

    public static void main(String[] args) throws IOException {
        System.out.println();
        System.out.println("  ====================================================");
        System.out.println("               FastJava Framework 2.0");
        System.out.println("          by STR Software Co.");
        System.out.println("  Open Source  |  Java 17+  |  Lightweight REST");
        System.out.println("  ====================================================");
        System.out.println();

        Log.info("Iniciando ExampleApplication...");

        // Migration — cria tabelas se nao existirem
        DB.migrate();

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
        Log.info("GET  /health             - health check (testa DB)");
        Log.info("GET  /openapi.json       - documentacao OpenAPI 3.0");
    }
}
'''

# ─── APLICAR PATCHES ────────────────────────────────────────────────────────

def patch_pom():
    with open(POM_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    if "org.postgresql" in content:
        print("AVISO: driver PostgreSQL ja esta no pom.xml — nenhuma alteracao.")
        return

    if POM_ANCHOR not in content:
        print("ERRO: ancora nao encontrada no pom.xml")
        return

    content = content.replace(POM_ANCHOR, POM_NEW_DEP + POM_ANCHOR)

    with open(POM_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("OK: driver PostgreSQL adicionado ao pom.xml")


def patch_example():
    with open(JAVA_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write(NEW_EXAMPLE)
    print("OK: ExampleApplication.java reescrito com PostgreSQL")


if __name__ == "__main__":
    print("=== patch_postgres.py ===")
    patch_pom()
    patch_example()
    print()
    print("Agora rode:")
    print("  mvn clean package -DskipTests")
    print("  java -jar target\\fastjava.jar")
    print()
    print("Teste o health check:")
    print("  curl http://localhost:8080/health")
