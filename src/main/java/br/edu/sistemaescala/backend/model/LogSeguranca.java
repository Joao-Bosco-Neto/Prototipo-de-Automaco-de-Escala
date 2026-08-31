package br.edu.sistemaescala.backend.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Uma linha da trilha de auditoria de eventos de seguranca
 * ({@code log_seguranca}).
 *
 * <p><b>Nunca</b> carrega senha, hash de BCrypt ou equivalente (CWE-532):
 * {@code detalhes} guarda so o que a acao mudou, em texto curto.</p>
 *
 * <p>{@code identificacao} e a string de login de quem agiu — no login falho,
 * a string tentada, que pode nem corresponder a um usuario existente. Ela vem
 * de entrada livre do usuario, entao chega aqui ja sanitizada de quebras de
 * linha pela camada de servico (CWE-117).</p>
 */
public class LogSeguranca {

    private Integer id;
    private LocalDateTime dataHora;
    private String identificacao;
    private AcaoSeguranca acao;
    private ResultadoSeguranca resultado;
    private String detalhes;

    public LogSeguranca() {
    }

    public LogSeguranca(Integer id, LocalDateTime dataHora, String identificacao,
                        AcaoSeguranca acao, ResultadoSeguranca resultado, String detalhes) {
        this.id = id;
        this.dataHora = dataHora;
        this.identificacao = identificacao;
        this.acao = acao;
        this.resultado = resultado;
        this.detalhes = detalhes;
    }

    public Integer getId() { return id; }
    public void setId(Integer valor) { id = valor; }
    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime valor) { dataHora = valor; }
    public String getIdentificacao() { return identificacao; }
    public void setIdentificacao(String valor) { identificacao = valor; }
    public AcaoSeguranca getAcao() { return acao; }
    public void setAcao(AcaoSeguranca valor) { acao = valor; }
    public ResultadoSeguranca getResultado() { return resultado; }
    public void setResultado(ResultadoSeguranca valor) { resultado = valor; }
    public String getDetalhes() { return detalhes; }
    public void setDetalhes(String valor) { detalhes = valor; }

    @Override
    public boolean equals(Object objeto) {
        return this == objeto || objeto instanceof LogSeguranca outro
                && id != null && Objects.equals(id, outro.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "LogSeguranca{id=" + id + ", dataHora=" + dataHora
                + ", identificacao='" + identificacao + "', acao=" + acao
                + ", resultado=" + resultado + "}";
    }
}
