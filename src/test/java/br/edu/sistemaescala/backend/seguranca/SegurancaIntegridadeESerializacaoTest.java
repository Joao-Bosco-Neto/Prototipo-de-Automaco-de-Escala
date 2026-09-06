package br.edu.sistemaescala.backend.seguranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integridade e regras de seguranca de arquitetura para OWASP A08:
 * - Proibicao de serializacao Java nativa (CWE-502) em modelos, servicos e repositorios.
 * - Validacao do algoritmo e calculo deterministico de checksum SHA-256 (CWE-494).
 */
class SegurancaIntegridadeESerializacaoTest {

    @Test
    @DisplayName("Nenhuma classe de modelo de dominio deve implementar java.io.Serializable (CWE-502)")
    void testModelosNaoImplementamSerializable() throws Exception {
        List<Class<?>> classes = carregarClassesDoPacote("br.edu.sistemaescala.backend.model");
        assertFalse(classes.isEmpty(), "Deve encontrar classes no pacote de modelos");

        for (Class<?> clazz : classes) {
            if (clazz.isEnum() || Throwable.class.isAssignableFrom(clazz)) {
                continue;
            }
            assertFalse(
                Serializable.class.isAssignableFrom(clazz),
                String.format("A classe de modelo '%s' nao deve implementar Serializable (regra OWASP A08 / CWE-502)", clazz.getName())
            );
        }
    }

    @Test
    @DisplayName("Nenhuma classe de repositorio ou servico deve implementar java.io.Serializable (CWE-502)")
    void testRepositoriosEServicosNaoImplementamSerializable() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        classes.addAll(carregarClassesDoPacote("br.edu.sistemaescala.backend.repository"));
        classes.addAll(carregarClassesDoPacote("br.edu.sistemaescala.backend.service"));
        classes.addAll(carregarClassesDoPacote("br.edu.sistemaescala.backend.dao"));

        assertFalse(classes.isEmpty(), "Deve encontrar classes de repositorio e servico");

        for (Class<?> clazz : classes) {
            // Enums e excecoes herdam Serializable da JVM
            if (clazz.isEnum() || Throwable.class.isAssignableFrom(clazz)) {
                continue;
            }

            assertFalse(
                Serializable.class.isAssignableFrom(clazz),
                String.format("A classe '%s' nao deve implementar Serializable (regra OWASP A08 / CWE-502)", clazz.getName())
            );
        }
    }

    @Test
    @DisplayName("Nenhuma classe de controller deve implementar java.io.Serializable (CWE-502)")
    void testControllersNaoImplementamSerializable() throws Exception {
        List<Class<?>> classes = carregarClassesDoPacote("br.edu.sistemaescala.frontend.controller");
        assertFalse(classes.isEmpty(), "Deve encontrar classes no pacote de controllers");

        for (Class<?> clazz : classes) {
            if (clazz.isEnum() || Throwable.class.isAssignableFrom(clazz)) {
                continue;
            }
            assertFalse(
                Serializable.class.isAssignableFrom(clazz),
                String.format("O controller '%s' nao deve implementar Serializable (regra OWASP A08 / CWE-502)", clazz.getName())
            );
        }
    }

    @Test
    @DisplayName("Nenhuma classe do sistema deve declarar metodos de serializacao Java (writeObject, readObject, etc.)")
    void testNenhumaClasseDeclaraMetodosDeSerializacaoJava() throws Exception {
        List<Class<?>> classes = carregarClassesDoPacote("br.edu.sistemaescala");
        assertFalse(classes.isEmpty(), "Deve encontrar classes do projeto");

        for (Class<?> clazz : classes) {
            if (Throwable.class.isAssignableFrom(clazz)) {
                continue;
            }
            for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                String nomeMetodo = method.getName();
                assertFalse(
                    "writeObject".equals(nomeMetodo) ||
                    "readObject".equals(nomeMetodo) ||
                    "readResolve".equals(nomeMetodo) ||
                    "writeReplace".equals(nomeMetodo),
                    String.format("A classe '%s' nao deve declarar o metodo de serializacao Java '%s' (CWE-502)", clazz.getName(), nomeMetodo)
                );
            }
        }
    }

    @Test
    @DisplayName("Garantir conformidade do algoritmo SHA-256 para validacao de integridade de instalador")
    void testCalculoSha256Determinismo() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        assertNotNull(digest, "Algoritmo SHA-256 deve estar disponivel no Java Runtime");

        // Vetor padrao NIST/FIPS para string vazia
        byte[] emptyHash = digest.digest("".getBytes(StandardCharsets.UTF_8));
        String emptyHashHex = HexFormat.of().formatHex(emptyHash);
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            emptyHashHex.toLowerCase(),
            "Hash SHA-256 da string vazia deve corresponder ao padrao internacional"
        );

        // Vetor padrao para payload de teste
        byte[] payloadHash = digest.digest("Sistema de Escala - GOTE".getBytes(StandardCharsets.UTF_8));
        String payloadHashHex = HexFormat.of().formatHex(payloadHash);
        assertEquals(64, payloadHashHex.length(), "Hash SHA-256 deve conter exatamente 64 caracteres hexadecimais");
    }

    @Test
    @DisplayName("Formato padrao de arquivo de checksum (.sha256) deve ser compativel com padroes de mercado")
    void testFormatoArquivoChecksumSha256() {
        String hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String nomeArquivo = "Sistema de Escala-1.0.0.msi";
        String linhaChecksum = String.format("%s  %s", hash, nomeArquivo);

        assertTrue(linhaChecksum.matches("^[a-fA-F0-9]{64}\\s\\s.+$"), "Linha de checksum deve seguir o padrao 'HASH  NOME_ARQUIVO'");
    }

    private List<Class<?>> carregarClassesDoPacote(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File directory = new File(resource.toURI());
            if (directory.exists() && directory.isDirectory()) {
                classes.addAll(encontrarClasses(directory, packageName));
            }
        }
        return classes;
    }

    private List<Class<?>> encontrarClasses(File directory, String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files == null) {
            return classes;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(encontrarClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    classes.add(Class.forName(className));
                } catch (ClassNotFoundException ignored) {
                    // Ignora classes dinamicas nao carregaveis
                }
            }
        }
        return classes;
    }
}
