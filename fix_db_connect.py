# fix_db_connect.py
# Corrige o metodo connect() da classe DB no ExampleApplication.java
# Coloque em C:\GABARITO\capas\fast\ e rode: python fix_db_connect.py

JAVA_FILE = r"C:\GABARITO\capas\fast\src\main\java\com\fastjava\core\ExampleApplication.java"

OLD = '''    static Connection connect() throws SQLException {
        // Converte formato postgres:// para jdbc:postgresql://
        String jdbc = URL.startsWith("jdbc:") ? URL : "jdbc:" + URL;
        return DriverManager.getConnection(jdbc);
    }'''

NEW = '''    static Connection connect() throws SQLException {
        // Extrai user:password@host do formato postgresql://user:pass@host/db?params
        // e passa via Properties para evitar conflito de parsing com @ na senha
        try {
            String raw = URL.replaceFirst("^postgresql://", "").replaceFirst("^jdbc:postgresql://", "");
            // raw = "user:pass@host/db?params"
            int atIdx   = raw.lastIndexOf('@');
            String userInfo = raw.substring(0, atIdx);           // "user:pass"
            String hostPart = raw.substring(atIdx + 1);          // "host/db?params"

            int colonIdx = userInfo.indexOf(':');
            String user = userInfo.substring(0, colonIdx);
            String pass = userInfo.substring(colonIdx + 1);

            // Monta JDBC URL sem credenciais
            String jdbcUrl = "jdbc:postgresql://" + hostPart;

            java.util.Properties props = new java.util.Properties();
            props.setProperty("user", user);
            props.setProperty("password", pass);
            props.setProperty("ssl", "true");
            props.setProperty("sslmode", "require");

            return DriverManager.getConnection(jdbcUrl, props);
        } catch (Exception e) {
            if (e instanceof SQLException) throw (SQLException) e;
            throw new SQLException("Falha ao parsear DATABASE_URL: " + e.getMessage(), e);
        }
    }'''

def fix():
    with open(JAVA_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    if "lastIndexOf('@')" in content:
        print("AVISO: fix ja foi aplicado anteriormente. Nenhuma alteracao.")
        return

    if OLD not in content:
        print("ERRO: trecho original nao encontrado. Cole o output abaixo para diagnostico:")
        # mostra os primeiros 200 chars da classe DB
        idx = content.find("static Connection connect()")
        print(content[idx:idx+400] if idx >= 0 else "metodo connect() nao encontrado")
        return

    content = content.replace(OLD, NEW)

    with open(JAVA_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)

    print("OK: metodo connect() corrigido para parsear URL com @ na senha")
    print()
    print("Agora rode:")
    print("  mvn clean package -DskipTests")
    print("  java -jar target\\fastjava.jar")

fix()
