# patch_openapi.py
# Adiciona endpoint /openapi.json ao FastJava.java
# Coloque em C:\GABARITO\capas\fast\ e rode: python patch_openapi.py

import os

JAVA_FILE = r"C:\GABARITO\capas\fast\src\main\java\com\fastjava\core\FastJava.java"

# --- Trecho a localizar (ancora para inserir a rota) ---
ANCHOR_EXECUTOR = "        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());"

# --- Linha nova a inserir ANTES do anchor ---
NEW_ROUTE_LINE = "        server.createContext(\"/openapi.json\", this::handleOpenApi);\n"

# --- Trecho a localizar (ancora para inserir os metodos) ---
ANCHOR_STOP = "    public void stop() { if (server != null) { server.stop(0); Log.info(\"Servidor parado\"); } }"

# --- Metodos novos a inserir ANTES do stop() ---
NEW_METHODS = '''
    private void handleOpenApi(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin","*");
        String json = buildOpenApi();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private String buildOpenApi() {
        Map<String,Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.3");

        Map<String,Object> info = new LinkedHashMap<>();
        info.put("title", "FastJava API");
        info.put("description", "Generated automatically by FastJava Framework");
        info.put("version", "2.0.0");
        info.put("contact", Map.of("name","STR Software Co.","url","https://strsoftware.com.br","email","strsitemas@gmail.com"));
        root.put("info", info);

        root.put("servers", List.of(Map.of("url","http://localhost:8080","description","Local")));

        Map<String,Object> paths = new LinkedHashMap<>();
        for (Route route : routes) {
            String pathKey = route.pattern.replaceAll(":([a-zA-Z0-9_]+)", "{$1}");
            @SuppressWarnings("unchecked")
            Map<String,Object> pathItem = (Map<String,Object>) paths.computeIfAbsent(pathKey, k -> new LinkedHashMap<>());

            Map<String,Object> op = new LinkedHashMap<>();
            op.put("summary", route.method + " " + route.pattern);
            op.put("operationId", route.handler.getName());

            if (route.authenticated) {
                op.put("security", List.of(Map.of("bearerAuth", List.of())));
            }

            List<Map<String,Object>> paramsList = new ArrayList<>();
            for (String pn : route.paramNames) {
                Map<String,Object> param = new LinkedHashMap<>();
                param.put("name", pn);
                param.put("in", "path");
                param.put("required", true);
                param.put("schema", Map.of("type","string"));
                paramsList.add(param);
            }

            for (Parameter p : route.handler.getParameters()) {
                if (p.isAnnotationPresent(QueryParam.class)) {
                    String qName = p.getAnnotation(QueryParam.class).value();
                    Map<String,Object> param = new LinkedHashMap<>();
                    param.put("name", qName);
                    param.put("in", "query");
                    param.put("required", false);
                    param.put("schema", Map.of("type","string"));
                    paramsList.add(param);
                }
            }
            if (!paramsList.isEmpty()) op.put("parameters", paramsList);

            for (Parameter p : route.handler.getParameters()) {
                if (p.isAnnotationPresent(Body.class)) {
                    Map<String,Object> reqBody = new LinkedHashMap<>();
                    reqBody.put("required", true);
                    reqBody.put("content", Map.of(
                        "application/json", Map.of(
                            "schema", Map.of("type","object")
                        )
                    ));
                    op.put("requestBody", reqBody);
                    break;
                }
            }

            op.put("responses", Map.of(
                "200", Map.of("description","Success"),
                "400", Map.of("description","Bad Request"),
                "401", Map.of("description","Unauthorized"),
                "404", Map.of("description","Not Found"),
                "500", Map.of("description","Internal Server Error")
            ));

            pathItem.put(route.method.toLowerCase(), op);
        }
        root.put("paths", paths);

        Map<String,Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", Map.of(
            "bearerAuth", Map.of(
                "type","http",
                "scheme","bearer",
                "bearerFormat","JWT"
            )
        ));
        root.put("components", components);

        return Json.toJson(root);
    }

'''

def apply_patch():
    with open(JAVA_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    # Verificar se ja foi aplicado
    if "buildOpenApi" in content:
        print("AVISO: patch ja foi aplicado anteriormente (buildOpenApi ja existe).")
        print("Nenhuma alteracao feita.")
        return

    # Passo 1: inserir rota /openapi.json no start()
    if ANCHOR_EXECUTOR not in content:
        print("ERRO: ancora nao encontrada:", ANCHOR_EXECUTOR)
        return

    content = content.replace(
        ANCHOR_EXECUTOR,
        NEW_ROUTE_LINE + ANCHOR_EXECUTOR
    )
    print("OK: rota /openapi.json inserida no start()")

    # Passo 2: inserir metodos handleOpenApi e buildOpenApi antes do stop()
    if ANCHOR_STOP not in content:
        print("ERRO: ancora stop() nao encontrada")
        return

    content = content.replace(
        ANCHOR_STOP,
        NEW_METHODS + ANCHOR_STOP
    )
    print("OK: metodos handleOpenApi e buildOpenApi inseridos")

    # Salvar sem BOM
    with open(JAVA_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)

    print("Arquivo salvo:", JAVA_FILE)
    print("\nAgora rode:")
    print("  mvn clean package -DskipTests")
    print("  java -jar target\\fastjava.jar")
    print("\nDepois acesse: http://localhost:8080/openapi.json")

apply_patch()
