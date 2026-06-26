# Contribuindo com o FastJava

Obrigado pelo interesse em contribuir com o **FastJava Framework**!  
Este documento explica como participar do projeto de forma organizada.

---

## Quem mantém este projeto

O FastJava é desenvolvido e mantido pela **STR Software Co.**  
Contato: strsitemas@gmail.com | https://strsoftware.com.br

---

## Como contribuir

### 1. Reporte um bug

Abra uma [Issue](https://github.com/strsitemas/fastjava/issues) com:

- Descrição clara do problema
- Passos para reproduzir
- Output de erro completo
- Versão do Java e sistema operacional

### 2. Sugira uma feature

Abra uma Issue com o label `enhancement` descrevendo:

- O problema que a feature resolve
- Como você imagina o comportamento
- Se possível, um exemplo de uso

### 3. Envie um Pull Request

```bash
# 1. Faça um fork do repositório
# 2. Clone o seu fork
git clone https://github.com/SEU_USUARIO/fastjava.git

# 3. Crie uma branch com nome descritivo
git checkout -b feat/nome-da-feature
# ou
git checkout -b fix/nome-do-bug

# 4. Faça as alterações e commit
git commit -m "feat: descricao clara da mudanca"

# 5. Push para o seu fork
git push origin feat/nome-da-feature

# 6. Abra um Pull Request no GitHub
```

---

## Padrão de commits

Use o formato [Conventional Commits](https://www.conventionalcommits.org/):

| Prefixo | Quando usar |
|---|---|
| `feat:` | Nova funcionalidade |
| `fix:` | Correção de bug |
| `docs:` | Documentação |
| `refactor:` | Refatoração sem mudar comportamento |
| `test:` | Testes |
| `chore:` | Tarefas de manutenção |

Exemplos:
```
feat: adicionar suporte a @PATCH nas rotas
fix: corrigir parse de URL com @ na senha do PostgreSQL
docs: atualizar README com exemplos de QueryParam
```

---

## Como rodar localmente

**Requisitos:**
- Java 17+
- Maven 3.8+
- Python 3.x (para scripts de geração)

```bash
# Clonar
git clone https://github.com/strsitemas/fastjava.git
cd fastjava

# Compilar e empacotar
mvn clean package -DskipTests

# Rodar
java -jar target/fastjava.jar
```

O servidor sobe na porta `8080`. Teste com:
```bash
curl http://localhost:8080/health
curl http://localhost:8080/openapi.json
```

---

## O que aceitamos

- Correções de bugs com reprodução documentada
- Melhorias de performance com benchmark comparativo
- Novas anotações compatíveis com a arquitetura atual
- Melhorias na documentação e exemplos
- Testes automatizados (JUnit 5 já está no projeto)

## O que não aceitamos

- Dependências externas pesadas (o projeto preza por zero dependências no core)
- Mudanças que quebrem compatibilidade com Java 17
- Pull Requests sem descrição ou sem Issue associada
- Código sem testes quando a mudança afeta comportamento existente

---

## Processo de revisão

1. Toda contribuição passa por revisão da STR Software Co.
2. PRs pequenos e focados são revisados mais rápido
3. PRs grandes podem levar mais tempo ou ser pedido para dividir
4. Feedback construtivo sempre será dado — nenhum PR é ignorado

---

## Dúvidas?

Abra uma Issue com o label `question` ou entre em contato direto:  
**strsitemas@gmail.com**
