# FastJava Framework

```
====================================================
             FastJava Framework 2.0
        by STR Software Co.
Open Source  •  Java 17+  •  Lightweight REST Framework
====================================================
```

> **FastJava** is an open-source lightweight Java REST framework  
> developed and maintained by **STR Software Co.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Version](https://img.shields.io/badge/version-2.0.0-green.svg)](https://github.com/strsoftware/fastjava/releases)
[![STR Software](https://img.shields.io/badge/by-STR%20Software%20Co.-gold.svg)](https://strsoftware.com.br)

---

## O que é o FastJava?

FastJava é um framework Java minimalista para construção de APIs REST.  
Não depende do Spring, Jakarta EE ou qualquer container externo.  
Basta Java 17+ e um único JAR para subir um servidor HTTP completo.

**Filosofia:** menos magia, mais clareza. Você lê o código do framework e entende tudo.

---

## Funcionalidades

| Recurso | Descrição |
|---|---|
| 🚀 HTTP Server | Servidor embutido via `com.sun.net.httpserver` |
| 📌 Roteamento | `@GET` `@POST` `@PUT` `@DELETE` com `@Path` |
| 💉 Injeção de Dependência | `@Controller` `@Service` `@Inject` por reflexão |
| 🔐 JWT | Geração e verificação com HMAC-SHA256 |
| 🛡️ Autenticação | `@Authenticated` com extração automática de claims |
| 📝 Logs Estruturados | `Log.info()` `Log.warn()` `Log.error()` com caller automático |
| ⚡ Virtual Threads | Concorrência via Java 21 Virtual Threads |
| 🚦 Rate Limiting | Controle de requisições por IP |
| 🔄 Parâmetros | `@Body` `@PathParam` `@QueryParam` `@Header` com conversão de tipos |
| ❌ Exceções HTTP | `NotFoundException` `UnauthorizedException` `ForbiddenException` etc. |

---

## Início Rápido

### Pré-requisitos

- Java 17+
- Maven 3.8+

### Clonar e rodar

```bash
git clone https://github.com/strsoftware/fastjava.git
cd fastjava
mvn clean package -DskipTests
java -jar target/fastjava.jar
```

O servidor sobe na porta `8080` em menos de 1 segundo.

---

## Exemplo de uso

```java
@Controller("/api/products")
class ProductController {

    @Inject ProductService productService;

    @GET
    Object listAll(@QueryParam("category") String category) {
        return productService.findAll(category);
    }

    @GET @Path("/:id")
    Object getOne(@PathParam("id") long id) {
        return productService.findById(id);
    }

    @POST @Authenticated
    Object create(@Body Product p, Map<String, Object> claims) {
        Log.info("Criando produto: name={} caller={}", p.name, claims.get("sub"));
        return productService.create(p);
    }

    @PUT @Path("/:id") @Authenticated
    Object update(@PathParam("id") long id, @Body Product p, Map<String, Object> claims) {
        return productService.update(id, p);
    }

    @DELETE @Path("/:id") @Authenticated
    Object delete(@PathParam("id") long id, Map<String, Object> claims) {
        productService.delete(id);
        return Map.of("message", "Product deleted");
    }
}
```

### Registrar e iniciar

```java
public class Application {
    public static void main(String[] args) throws IOException {
        new FastJava()
            .port(8080)
            .register(
                AuthController.class,
                ProductController.class
            )
            .start();
    }
}
```

---

## Autenticação JWT

### Registro e Login

```bash
# Criar conta
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@test.com","password":"123456"}'

# Login — retorna JWT
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

### Usar o token

```bash
curl http://localhost:8080/api/users \
  -H "Authorization: Bearer SEU_TOKEN_AQUI"
```

### Rotas protegidas

```java
@GET @Authenticated
Object listUsers(Map<String, Object> claims) {
    Log.info("Listando usuarios caller=sub={}", claims.get("sub"));
    return userService.findAll();
}
```

---

## Logs Estruturados

Todos os logs incluem timestamp, nível, classe e linha automaticamente:

```
2026-06-25 06:17:47.539 [INFO ] [ExampleApplication:359] Iniciando ExampleApplication...
2026-06-25 06:17:47.685 [INFO ] [FastJava:410]            Rota: POST /api/auth/register
2026-06-25 06:17:47.686 [INFO ] [FastJava:410]            Rota: POST /api/auth/login
2026-06-25 06:17:48.034 [INFO ] [FastJava:421]            Servidor iniciado na porta 8080
2026-06-25 06:18:03.550 [DEBUG] [FastJava:431]            Req: POST /api/auth/register ip=127.0.0.1
2026-06-25 06:18:03.555 [INFO ] [AuthController:210]      AuthController.register: username=admin
2026-06-25 06:18:03.563 [INFO ] [UserService:59]          UserService.create: ok id=1 username=admin
2026-06-25 06:18:03.565 [INFO ] [FastJava:458]            POST /api/auth/register -> 200 (28ms)
2026-06-25 06:18:15.113 [WARN ] [UserService:65]          UserService.validatePassword: senha incorreta para username=admin
2026-06-25 06:18:15.116 [WARN ] [AuthController:221]      AuthController.login: falhou username=admin
2026-06-25 06:18:15.118 [WARN ] [FastJava:468]            POST /api/auth/login -> 401 Invalid credentials (14ms)
```

Use nos seus services e controllers:

```java
Log.debug("Buscando produto id={}", id);
Log.info("Produto criado: id={} name={}", p.id, p.name);
Log.warn("Produto nao encontrado: id={}", id);
Log.error("Falha inesperada: {}", e.getMessage());
```

---

## Exceções HTTP

```java
throw new NotFoundException("Product not found: " + id);      // 404
throw new UnauthorizedException("Invalid credentials");        // 401
throw new ForbiddenException("Admin role required");           // 403
throw new BadRequestException("Username required");            // 400
throw new ConflictException("Username already exists");        // 409
```

O framework captura automaticamente e retorna o JSON correto:

```json
{"error": "Product not found: 42"}
```

---

## Variáveis de Ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `JWT_SECRET` | `fastjava-default-secret` | Chave de assinatura JWT — **troque em produção** |
| `PORT` | `8080` | Porta do servidor |

```bash
JWT_SECRET=minha-chave-secreta java -jar target/fastjava.jar
```

---

## Estrutura do Projeto

```
fastjava/
├── src/
│   └── main/java/com/fastjava/core/
│       ├── FastJava.java            # Framework core
│       └── ExampleApplication.java  # Aplicação de exemplo
├── pom.xml
├── target/
│   └── fastjava.jar                 # JAR executável
└── README.md
```

---

## Roadmap

- [x] HTTP Server embutido
- [x] Roteamento por anotações
- [x] Injeção de Dependência
- [x] JWT com HMAC-SHA256
- [x] Logs estruturados
- [x] Rate Limiting
- [x] Virtual Threads
- [x] JAR executável
- [ ] OpenAPI / Swagger automático
- [ ] Persistência SQLite/H2
- [ ] Persistência PostgreSQL
- [ ] Separação em módulos Maven
- [ ] Publicação no Maven Central

---

## Contribuindo

1. Fork o repositório
2. Crie uma branch: `git checkout -b feature/minha-feature`
3. Commit: `git commit -m 'feat: adiciona minha feature'`
4. Push: `git push origin feature/minha-feature`
5. Abra um Pull Request

---

## Licença

MIT License — veja [LICENSE](LICENSE) para detalhes.

---

## Sobre a STR Software Co.

FastJava é desenvolvido e mantido pela **STR Software Co.**  
🌐 [strsoftware.com.br](https://strsoftware.com.br)  
📦 [github.com/strsoftware/fastjava](https://github.com/strsoftware/fastjava)

---

*FastJava — porque nem todo projeto precisa do Spring.*
